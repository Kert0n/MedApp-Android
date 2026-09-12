package com.kert0n.medapp.storage.intake

import com.kert0n.medapp.domain.intake.CourseIntake
import com.kert0n.medapp.domain.intake.Intake
import com.kert0n.medapp.domain.intake.IntakeStatus
import com.kert0n.medapp.domain.intake.TakenDose
import com.kert0n.medapp.domain.intake.UnplannedIntake
import com.kert0n.medapp.queue.intake.IntakeAccounting
import com.kert0n.medapp.queue.intake.IntakeSyncState
import com.kert0n.medapp.storage.course.CourseReallocation
import java.time.Instant

/**
 * Что записывается вместе с ответом на приём — всё то, чего порознь не бывает (PLAN F5).
 *
 * Ответ, локальный остаток, пересчитанные выделения курса и учёт расхода ложатся одной
 * транзакцией; команду расхода, если факт уезжает на сервер, ставит служба очереди в ней же —
 * репозиторий про очередь не знает. Откат не оставляет ни отдельного расхода, ни факта, ни брони.
 *
 * Решения принимает домен: из какой пачки принято и сколько — сказано самим приёмом, а
 * применяет расход хранение — к тому состоянию пачки, которое лежит в базе. Готового нового
 * состояния пачки сюда не передают: посчитанное по прочитанному когда-то раньше, оно легло бы
 * поверх нынешнего.
 *
 * Движения по приёму нет: приём и есть учётная запись о своём расходе, а `StockMovement`
 * описывает изменения помимо приёма (ТЗ 4.1.1.10.2, PLAN D7).
 */
class IntakeOutcome(
    val intake: Intake,
    expected: Set<IntakeStatus>,
    val sync: IntakeSyncState = IntakeSyncState(intake.id),
    val reallocation: CourseReallocation? = null
) {
    /**
     * Ожидаемые статусы условного перехода: повтор уже совершённого ничего не меняет (D6).
     * У внепланового приёма их нет: строки до него не было, и он заводится вставкой.
     */
    val expected: Set<IntakeStatus> = expected.toSet()

    /**
     * Момент ответа. Часы приходят из домена вместе с ответом: хранение системного времени не
     * читает, иначе записанное время зависело бы от того, когда дошла транзакция (PLAN H1).
     */
    val answeredAt: Instant = when (intake) {
        is UnplannedIntake -> intake.dose.at
        is CourseIntake -> requireNotNull(intake.answer) { "записывается ответ, а не его отсутствие" }.at
    }

    /** Что и откуда принято; у подтверждённого приёма это есть по построению. */
    val taken: TakenDose? get() = intake.taken

    /**
     * Меняет ли эта запись локальный остаток. Ровно [IntakeAccounting.LOCAL_APPLIED] — так этот
     * случай и определён: «локальный остаток и факт записаны одной транзакцией». Расход,
     * уехавший командой, локальный остаток не трогает: там лежит подтверждённое сервером, а
     * незакрытые команды сворачивает очередь (PLAN E1).
     */
    val spendsLocally: Boolean get() = sync.accounting == IntakeAccounting.LOCAL_APPLIED

    /** Приём и учёт его расхода: правило об этой паре живёт на своём типе. */
    val recorded = RecordedIntake(intake, sync)

    init {
        require(intake is UnplannedIntake || expected.isNotEmpty()) {
            "условный переход называет, из какого состояния идёт"
        }
        require(intake.status !in expected) { "переход в тот же статус не является переходом" }
    }
}
