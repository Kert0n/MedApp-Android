package com.kert0n.medapp.storage.intake

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kert0n.medapp.domain.course.Revision
import com.kert0n.medapp.domain.course.ScheduledOccurrence
import com.kert0n.medapp.domain.intake.CourseIntake
import com.kert0n.medapp.domain.intake.Intake
import com.kert0n.medapp.domain.intake.IntakeAnswer
import com.kert0n.medapp.domain.intake.IntakeStatus
import com.kert0n.medapp.domain.intake.TakenDose
import com.kert0n.medapp.domain.intake.UnplannedIntake
import com.kert0n.medapp.domain.value.QuantityUnit
import com.kert0n.medapp.domain.value.Vocabulary
import com.kert0n.medapp.network.intake.IntakeAccounting
import com.kert0n.medapp.network.intake.IntakeSyncState
import com.kert0n.medapp.storage.course.CourseRecordStorageEntity
import com.kert0n.medapp.storage.pack.PackageStorageEntity
import com.kert0n.medapp.storage.value.storedDose
import com.kert0n.medapp.storage.value.storedUnit
import com.kert0n.medapp.storage.value.toStorageAmount
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.uuid.Uuid

/**
 * План и факт — одна запись: запланированный приём это приём, который ещё не состоялся. Вид
 * различается наличием курса, а не колонкой-признаком (PLAN D6, F1).
 *
 * `status` хранится, хотя домен выводит его из ответа: подтверждение идёт условным `UPDATE` по
 * ожидаемому статусу, и без колонки условие писать не по чему (PLAN F2).
 *
 * `accounting` и `operation_id` живут в той же строке, но доменная модель их не носит: это
 * `IntakeSyncState` сетевого слоя, и правила о приёме его не читают (PLAN D6).
 *
 * Ключи на пачки и на запись эпизода — `RESTRICT`: история не удаляется каскадом.
 */
@Entity(
    tableName = "intakes",
    foreignKeys = [
        ForeignKey(
            entity = CourseRecordStorageEntity::class,
            parentColumns = ["id"],
            childColumns = ["course_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = PackageStorageEntity::class,
            parentColumns = ["id"],
            childColumns = ["planned_package_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = PackageStorageEntity::class,
            parentColumns = ["id"],
            childColumns = ["taken_package_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["course_id", "scheduled_on", "scheduled_time"], unique = true),
        Index("planned_package_id"),
        Index("taken_package_id"),
        Index("operation_id")
    ]
)
class IntakeStorageEntity(
    @PrimaryKey val id: Uuid,
    @ColumnInfo(name = "unit_id") val unitId: Uuid,
    val status: IntakeStatus,
    @ColumnInfo(name = "course_id") val courseId: Uuid? = null,
    @ColumnInfo(name = "course_revision") val courseRevision: Long? = null,
    @ColumnInfo(name = "scheduled_on") val scheduledOn: LocalDate? = null,
    @ColumnInfo(name = "scheduled_time") val scheduledTime: LocalTime? = null,
    @ColumnInfo(name = "scheduled_at") val scheduledAt: Instant? = null,
    @ColumnInfo(name = "planned_amount") val plannedAmount: String? = null,
    @ColumnInfo(name = "planned_package_id") val plannedPackageId: Uuid? = null,
    @ColumnInfo(name = "answered_at") val answeredAt: Instant? = null,
    @ColumnInfo(name = "taken_package_id") val takenPackageId: Uuid? = null,
    @ColumnInfo(name = "taken_med_kit_id") val takenMedKitId: Uuid? = null,
    @ColumnInfo(name = "taken_amount") val takenAmount: String? = null,
    val accounting: IntakeAccounting = IntakeAccounting.NOT_APPLICABLE,
    @ColumnInfo(name = "operation_id") val operationId: Uuid? = null
) {
    fun toDomain(vocabulary: Vocabulary): Intake {
        val unit = vocabulary.storedUnit(unitId)
        return if (courseId == null) unplanned(unit) else scheduled(unit)
    }

    fun syncState(): IntakeSyncState = IntakeSyncState(
        intakeId = id,
        accounting = accounting,
        operationId = operationId
    )

    private fun scheduled(unit: QuantityUnit): CourseIntake = CourseIntake(
        id = id,
        courseId = requireNotNull(courseId),
        courseRevision = Revision(requireNotNull(courseRevision) {
            "пункт расписания порождён редакцией курса"
        }),
        slot = ScheduledOccurrence(
            localDate = requireNotNull(scheduledOn) { "у пункта расписания есть исходная дата" },
            localTime = requireNotNull(scheduledTime) { "у пункта расписания есть исходное время" },
            at = requireNotNull(scheduledAt) { "у пункта расписания есть разрешённый момент" }
        ),
        plannedAmount = storedDose(
            requireNotNull(plannedAmount) { "у пункта расписания есть плановая доза" },
            unit
        ),
        plannedPackageId = plannedPackageId,
        answer = answer(unit)
    )

    private fun unplanned(unit: QuantityUnit): UnplannedIntake =
        UnplannedIntake(id = id, dose = requireNotNull(taken(unit)) {
            "внеплановый приём состоялся по определению: другого статуса у него не бывает"
        })

    private fun answer(unit: QuantityUnit): IntakeAnswer? = when (status) {
        IntakeStatus.PLANNED -> null
        IntakeStatus.TAKEN -> IntakeAnswer.Taken(requireNotNull(taken(unit)))
        IntakeStatus.MISSED -> IntakeAnswer.Missed(answeredMoment())
        IntakeStatus.CANCELLED -> IntakeAnswer.Cancelled(answeredMoment())
    }

    private fun answeredMoment(): Instant =
        requireNotNull(answeredAt) { "у отвеченного приёма есть момент ответа" }

    private fun taken(unit: QuantityUnit): TakenDose? {
        val amount = takenAmount ?: return null
        return TakenDose(
            packageId = requireNotNull(takenPackageId) { "у принятой дозы есть своя пачка" },
            medKitId = requireNotNull(takenMedKitId) { "у принятой дозы есть аптечка на момент события" },
            amount = storedDose(amount, unit),
            at = answeredMoment()
        )
    }
}

fun Intake.toStorageEntity(sync: IntakeSyncState = IntakeSyncState(id)): IntakeStorageEntity {
    require(sync.intakeId == id) { "обвязка синхронизации принадлежит своему приёму" }
    val takenDose = taken
    val common = IntakeStorageEntity(
        id = id,
        unitId = unit.id,
        status = status,
        answeredAt = answerMoment(),
        takenPackageId = takenDose?.packageId,
        takenMedKitId = takenDose?.medKitId,
        takenAmount = takenDose?.amount?.quantity?.toStorageAmount(),
        accounting = sync.accounting,
        operationId = sync.operationId
    )
    return when (this) {
        is UnplannedIntake -> common
        is CourseIntake -> IntakeStorageEntity(
            id = common.id,
            unitId = common.unitId,
            status = common.status,
            courseId = courseId,
            courseRevision = courseRevision.number,
            scheduledOn = slot.localDate,
            scheduledTime = slot.localTime,
            scheduledAt = slot.at,
            plannedAmount = plannedAmount.quantity.toStorageAmount(),
            plannedPackageId = plannedPackageId,
            answeredAt = common.answeredAt,
            takenPackageId = common.takenPackageId,
            takenMedKitId = common.takenMedKitId,
            takenAmount = common.takenAmount,
            accounting = common.accounting,
            operationId = common.operationId
        )
    }
}

private fun Intake.answerMoment(): Instant? = when (this) {
    is UnplannedIntake -> dose.at
    is CourseIntake -> answer?.at
}
