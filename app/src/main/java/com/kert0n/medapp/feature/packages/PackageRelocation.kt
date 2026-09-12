package com.kert0n.medapp.feature.packages

import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.domain.medkit.MedKitRef
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.queue.QueueService
import com.kert0n.medapp.queue.QueuedCommand
import com.kert0n.medapp.queue.Transactions
import com.kert0n.medapp.queue.pack.PackageSyncCommand
import com.kert0n.medapp.storage.course.CourseStorageRepository
import com.kert0n.medapp.storage.medkit.MedKitStorageRepository
import com.kert0n.medapp.storage.pack.PackageAdjustment
import com.kert0n.medapp.storage.pack.PackageStorageRepository
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlin.uuid.Uuid

/**
 * Человек переставил коробку на другую полку (PLAN E6). Коробка та же, запись та же, курс её не
 * теряет: к обеим полкам допущен тот же человек. Меняется только место — доменным `moveTo`, без
 * следа в истории (D7). Что ещё нужно сделать, решает граница публикации:
 *
 * - местная → местная: ничего;
 * - общая → общая: серверу — `Move`, свою бронь он сохраняет, потому что мы видим цель;
 * - местная → общая: коробка становится известна серверу — `Create` в целевой аптечке, а
 *   выделение курса, если оно есть, едет следом бронью `SetClaim`, иначе на сервере его бы не
 *   было. До ответа обвязка пуста и броней нет — первое подтверждённое число даст снимок ответа
 *   (E1); расход, поставленный позже, идёт после `Create` по номеру;
 * - общая → местная: сервер не умеет снять коробку на полку, которой не знает. Сначала целевая
 *   аптечка публикуется с согласия человека, потом перенос повторяется как общая → общая.
 */
class PackageRelocation @Inject constructor(
    private val packages: PackageStorageRepository,
    private val medKits: MedKitStorageRepository,
    private val courses: CourseStorageRepository,
    private val queue: QueueService,
    private val transactions: Transactions,
    private val clock: Clock
) {

    suspend fun move(packageId: Uuid, targetMedKitId: Uuid): Outcome = transactions.run {
        val pkg = packages.find(packageId) ?: return@run Outcome.GONE
        val target = medKits.find(targetMedKitId) ?: return@run Outcome.TARGET_GONE
        if (target.id == pkg.medKit.id) return@run Outcome.TARGET_IS_THE_SAME
        relocate(pkg, target, clock.instant())
    }

    /**
     * Шаг внутри чужой транзакции: одна коробка на другую полку со всем, что нужно серверу.
     * Аптечка, которую разбирают целиком, зовёт его по каждой местной коробке; свою общую она
     * переставляет одной командой аптечки и зовёт [place].
     */
    internal suspend fun relocate(pkg: Package, target: MedKit, at: Instant): Outcome {
        val from = pkg.medKit
        val to = target.ref
        if (from.answersToServer && !to.answersToServer) return Outcome.TARGET_NEEDS_PUBLICATION
        place(pkg, to, at)
        when {
            from.answersToServer -> queue.change(to, listOf(command(PackageSyncCommand.Move(pkg.id, to.id))), at) { true }
            to.answersToServer -> publish(pkg, to, at)
        }
        return Outcome.MOVED
    }

    /** Только место: переход пачки к прочитанному состоянию, без команд (PLAN D7). */
    internal suspend fun place(pkg: Package, to: MedKitRef, at: Instant) {
        check(packages.adjust(PackageAdjustment.Transfer(pkg.id, to), at = at)) { "пачка прочитана этой же транзакцией" }
    }

    /** Местная коробка на общей полке: рассказать о ней серверу, а с ней — о выделении курса. */
    private suspend fun publish(pkg: Package, to: MedKitRef, at: Instant) {
        val create = command(PackageSyncCommand.Create(pkg.id, to.id, pkg.quantity, pkg.facts.shared))
        val claim = courses.courseHolding(pkg.id)
            ?.let { courses.findPlan(it) }
            ?.allocatedOf(pkg.ref)
            ?.takeUnless { it.isZero }
            ?.let { QueuedCommand(Uuid.random(), PackageSyncCommand.SetClaim(pkg.id, it), dependsOn = setOf(create.id)) }
        queue.change(to, listOfNotNull(create, claim), at) { true }
    }

    private fun command(command: PackageSyncCommand) = QueuedCommand(Uuid.random(), command)

    /**
     * Чем кончилось. Переставили — экран показывает новую полку; коробки уже нет — закрывает
     * молча; цели нет — просит выбрать другую; та же полка — говорит об этом; цель местная, а
     * коробка общая — просит согласия на публикацию цели (PLAN E5, E6).
     */
    enum class Outcome { MOVED, GONE, TARGET_GONE, TARGET_IS_THE_SAME, TARGET_NEEDS_PUBLICATION }
}
