package com.kert0n.medapp.storage.intake

import com.kert0n.medapp.domain.course.Course
import com.kert0n.medapp.domain.intake.CourseIntake
import com.kert0n.medapp.domain.intake.Intake
import com.kert0n.medapp.domain.intake.IntakeStatus
import com.kert0n.medapp.domain.intake.UnplannedIntake
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.stock.StockMovement
import com.kert0n.medapp.network.intake.IntakeAccounting
import com.kert0n.medapp.network.intake.IntakeSyncState
import com.kert0n.medapp.storage.server.QueuedCommand
import java.time.Instant

/**
 * Что записывается вместе с ответом на приём — всё то, чего порознь не бывает (PLAN F5).
 *
 * Ответ, локальный остаток либо команда расхода, движение, пересчитанные выделения курса и
 * учёт расхода ложатся одной транзакцией. Откат не оставляет ни отдельного расхода, ни факта,
 * ни брони.
 *
 * Решения принимает домен и передаёт сюда готовыми: хранение не выбирает, из какой пачки
 * принято и как изменилось выделение.
 */
class IntakeOutcome(
    val intake: Intake,
    expected: Set<IntakeStatus>,
    val sync: IntakeSyncState = IntakeSyncState(intake.id),
    val spent: Package? = null,
    val movement: StockMovement? = null,
    val course: Course? = null,
    val command: QueuedCommand? = null
) {
    /** Ожидаемые статусы условного перехода: повтор уже совершённого ничего не меняет (D6). */
    val expected: Set<IntakeStatus> = expected.toSet()

    /**
     * Момент ответа. Часы приходят из домена вместе с ответом: хранение системного времени не
     * читает, иначе записанное время зависело бы от того, когда дошла транзакция (PLAN H1).
     */
    val answeredAt: Instant = when (intake) {
        is UnplannedIntake -> intake.dose.at
        is CourseIntake -> requireNotNull(intake.answer) { "записывается ответ, а не его отсутствие" }.at
    }

    init {
        require(sync.intakeId == intake.id) { "учёт расхода принадлежит своему приёму" }
        require(expected.isNotEmpty()) { "условный переход называет, из какого состояния идёт" }
        require(intake.status !in expected) { "переход в тот же статус не является переходом" }
        require(movement == null || movement.packageId == spent?.id) {
            "движение записывается по той пачке, остаток которой изменился"
        }
        require(
            intake.status != IntakeStatus.TAKEN ||
                sync.accounting != IntakeAccounting.NOT_APPLICABLE
        ) { "у подтверждённого приёма расход учтён" }
        require(
            intake.status == IntakeStatus.TAKEN ||
                sync.accounting == IntakeAccounting.NOT_APPLICABLE
        ) { "у неподтверждённого приёма расхода нет" }
    }
}
