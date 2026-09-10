package com.kert0n.medapp.domain.model.intake

import java.time.Instant

/**
 * Ответ на пункт плана — и он же его состояние (PLAN D6).
 *
 * Ответ либо есть, либо его нет: `null` — пункт ещё ждёт. Поэтому «плановый пункт без факта» и
 * «подтверждённый пункт без количества» здесь не проверяются, а не существуют: количество лежит
 * внутри [Taken] и больше нигде.
 *
 * [at] — когда ответили. У подтверждения это момент самого приёма: два времени для одного
 * события расходились бы молча.
 */
sealed interface IntakeAnswer {

    val at: Instant

    /** Принято. Фактическое количество может отличаться от планового (PLAN D5). */
    data class Taken(val dose: TakenDose) : IntakeAnswer {
        override val at: Instant get() = dose.at
    }

    /** Человек отказался. Расхода нет: потребность уменьшается, выделение снимается с конца стека. */
    data class Skipped(override val at: Instant) : IntakeAnswer

    /** Ответа не было до конца календарного дня курса в его зоне (PLAN C1). Ещё подтверждаем. */
    data class Missed(override val at: Instant) : IntakeAnswer

    /** Пункт отменён вместе с курсом. Состоявшиеся приёмы этим не затрагиваются. */
    data class Cancelled(override val at: Instant) : IntakeAnswer
}
