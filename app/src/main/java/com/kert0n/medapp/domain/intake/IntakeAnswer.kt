package com.kert0n.medapp.domain.intake

import java.time.Instant

/**
 * Ответ на пункт плана — он же его состояние (PLAN D6); `null` — пункт ещё ждёт. Количество лежит
 * только внутри [Taken]. [at] — когда ответили; у подтверждения это момент самого приёма.
 */
sealed interface IntakeAnswer {

    val at: Instant

    /** Принято. Фактическое количество может отличаться от планового (PLAN D5). */
    data class Taken(val dose: TakenDose) : IntakeAnswer {
        override val at: Instant get() = dose.at
    }

    /**
     * Не принято — отказался или не ответил до конца календарного дня курса в его зоне. Расхода
     * нет, доза уезжает вперёд, и подтвердить её ещё можно (PLAN C1, D6).
     */
    data class Missed(override val at: Instant) : IntakeAnswer

    /** Пункт отменён вместе с курсом. Состоявшиеся приёмы этим не затрагиваются. */
    data class Cancelled(override val at: Instant) : IntakeAnswer
}
