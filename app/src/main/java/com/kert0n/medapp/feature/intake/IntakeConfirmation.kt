package com.kert0n.medapp.feature.intake

import com.kert0n.medapp.domain.course.CourseProgress
import com.kert0n.medapp.domain.course.CourseRecord
import com.kert0n.medapp.domain.intake.CourseIntake
import com.kert0n.medapp.domain.intake.IntakeRejected
import com.kert0n.medapp.domain.intake.IntakeStatus
import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.domain.value.Dose
import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.network.intake.IntakeAccounting
import com.kert0n.medapp.network.intake.IntakeSyncState
import com.kert0n.medapp.queue.QueueService
import com.kert0n.medapp.queue.QueueStorage
import com.kert0n.medapp.queue.QueuedCommand
import com.kert0n.medapp.queue.pack.PackageSyncCommand
import com.kert0n.medapp.storage.course.CourseReallocation
import com.kert0n.medapp.storage.course.CourseStorageRepository
import com.kert0n.medapp.storage.intake.IntakeOutcome
import com.kert0n.medapp.storage.intake.IntakeStorageRepository
import com.kert0n.medapp.storage.pack.PackageStorageRepository
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlin.uuid.Uuid

/**
 * Целое действие «принял» по пункту курса: факт, остаток, прогресс, обеспечение пачки, конец
 * эпизода и доставка согласуются одной транзакцией и по тому, что лежит в базе, а не по тому, что
 * экран прочитал раньше (PLAN D5, D6, F5). Своя аптечка списывает локально, общая ставит расход
 * командой — и отправку после коммита просит служба очереди, а человек её не ждёт: подтверждение
 * записано, и от сети оно не зависит (PLAN E4).
 */
class IntakeConfirmation @Inject constructor(
    private val intakes: IntakeStorageRepository,
    private val courses: CourseStorageRepository,
    private val packages: PackageStorageRepository,
    private val transactions: QueueStorage,
    private val queue: QueueService,
    private val clock: Clock
) {

    /**
     * Принято [amount] из пачки [packageId] в момент [at], который называет человек: сейчас или
     * вчера — проверка одна и та же. Отказ — [IntakeRejected] внутри `Result`, и тогда не записано
     * ничего. Повтор по уже принятому пункту ничего не меняет и отвечает тем, что записано.
     */
    suspend fun confirm(intakeId: Uuid, packageId: Uuid, amount: Dose, at: Instant): Result<Confirmed> =
        transactions.transaction { write(intakeId, packageId, amount, at) }

    private suspend fun write(intakeId: Uuid, packageId: Uuid, amount: Dose, at: Instant): Result<Confirmed> {
        val now = clock.instant()
        val intake = requireNotNull(intakes.find(intakeId) as? CourseIntake) { "подтверждается пункт курса" }
        val record = checkNotNull(courses.findRecord(intake.courseId)) { "у пункта курса есть запись эпизода" }
        if (intake.status == IntakeStatus.TAKEN) {
            val sync = checkNotNull(intakes.syncStateOf(intake.id)) { "принятый пункт записан" }
            return Result.success(Confirmed(intake, sync.accounting, episodeClosed = !record.isOpen))
        }
        if (!record.isOpen) return rejected(IntakeRejected.Reason.EPISODE_CLOSED)
        val course = checkNotNull(courses.findPlan(intake.courseId)) { "у идущего эпизода есть план" }
        val pkg = packages.find(packageId) ?: return rejected(IntakeRejected.Reason.PACKAGE_UNUSABLE)
        if (amount.unit != intake.unit) return rejected(IntakeRejected.Reason.UNIT_MISMATCH)
        val confirmed = intake.confirm(pkg.take(amount, at).getOrElse { return Result.failure(it) })

        val others = intakes.ofCourse(course.id).filterIsInstance<CourseIntake>().filter { it != intake }
        val progress = CourseProgress(
            taken = others.filter { it.status == IntakeStatus.TAKEN }.mapTo(HashSet()) { it.slot } + confirmed.slot,
            missed = others.filter { it.status == IntakeStatus.MISSED }.mapTo(HashSet()) { it.slot }
        )
        val finished = course.remainingDoses(progress).isNone

        // Выделение пачки после приёма и бронь, которая уезжает вместе с расходом (PLAN D5, E2).
        val allocated = course.sources.firstOrNull { it.pkg == pkg.ref }?.allocatedDoses
        val reallocation = if (allocated == null || finished) null else {
            val availableAfter = checkNotNull(packages.availability(pkg.id)).availableToMe.minusOrZero(amount.quantity)
            val doses = course.dosesAfterIntake(pkg.ref, amount, availableAfter)
            if (doses == allocated) null else CourseReallocation(course.allocate(pkg.ref, doses, now), course.revision)
        }
        val claimAfter = when {
            allocated == null -> null
            finished -> Quantity.zero(amount.unit)
            else -> (reallocation?.course ?: course).allocatedOf(pkg.ref)
        }

        val consume = QueuedCommand(Uuid.random(), PackageSyncCommand.Consume(pkg.id, amount, intake.id, claimAfter))
        val release = QueuedCommand(Uuid.random(), PackageSyncCommand.ReleaseClaim(pkg.id), dependsOn = setOf(consume.id))
            .takeIf { claimAfter?.isZero == true }
        val sync = if (pkg.medKit.publication == MedKit.Publication.PUBLISHED) {
            IntakeSyncState(intake.id, IntakeAccounting.PENDING, consume.id)
        } else {
            IntakeSyncState(intake.id, IntakeAccounting.LOCAL_APPLIED)
        }
        val outcome = IntakeOutcome(confirmed, setOf(IntakeStatus.PLANNED, IntakeStatus.MISSED), sync, reallocation)
        val recorded = queue.change(pkg.medKit, listOfNotNull(consume, release), now) { intakes.record(outcome) }
        check(recorded) { "пункт и пачка прочитаны этой же транзакцией" }

        if (finished) {
            val cancelled = intakes.ofCourse(course.id).filterIsInstance<CourseIntake>()
                .filter { it.status == IntakeStatus.PLANNED }
                .map { it.cancel(now) }
            courses.close(record.close(CourseRecord.Outcome.COMPLETED, now), cancelled)
            for (source in course.sources) {
                if (source.pkg == pkg.ref) continue
                val released = QueuedCommand(Uuid.random(), PackageSyncCommand.ReleaseClaim(source.pkg.id))
                queue.change(source.pkg.medKit, listOf(released), now) { true }
            }
        } else {
            intakes.prunePlanned(course.id, course.remainingOccurrences(progress).toSet())
        }
        return Result.success(Confirmed(confirmed, sync.accounting, episodeClosed = finished))
    }

    private fun rejected(reason: IntakeRejected.Reason): Result<Confirmed> = Result.failure(IntakeRejected(reason))

    /**
     * Что стало после подтверждения: принятый пункт, где его расход — в локальном остатке или в
     * очереди, — и закончилось ли им лечение.
     */
    data class Confirmed(
        val intake: CourseIntake,
        val accounting: IntakeAccounting,
        val episodeClosed: Boolean
    )
}
