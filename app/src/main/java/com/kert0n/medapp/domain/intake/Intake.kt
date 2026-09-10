package com.kert0n.medapp.domain.intake

import kotlin.uuid.Uuid

/**
 * Приём: пункт курса вместе с ответом на него ([CourseIntake]) или внеплановый факт
 * ([UnplannedIntake]); типов два, потому что у второго нет плана. Сущность: подтверждённый приём —
 * тот же приём, равенство по [id]. Учёт расхода и связь с очередью живут в `IntakeSyncState`
 * слоя данных (PLAN E1).
 */
sealed interface Intake {

    val id: Uuid

    /** Единица НА МОМЕНТ СОБЫТИЯ: смена единицы прошлые отчёты не переписывает. */
    val unitId: Uuid

    /** Производное от ответа: отдельного поля, способного с ним разойтись, нет. */
    val status: IntakeStatus

    /** Что фактически принято. `null` — приёма не было (PLAN D6). */
    val taken: TakenDose?
}
