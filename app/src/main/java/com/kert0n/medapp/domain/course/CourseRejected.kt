package com.kert0n.medapp.domain.course

/**
 * Отказ курса в действии человека — обычный ответ внутри `Result`, а не сбой. Текста в нём нет:
 * сообщение по [reason] берёт экран из `R.string.*` (PLAN H1).
 */
class CourseRejected(val reason: Reason) : IllegalStateException(reason.name) {

    /** Причины различаются, потому что ведут человека к разным действиям. */
    enum class Reason {

        /** Пачка израсходована, утилизирована или доступ к ней утрачен. */
        PACKAGE_UNUSABLE,

        /** Пачка уже в препарате курса. */
        ALREADY_ATTACHED,

        /** У пачки не заполнена форма, и нельзя сказать, тот ли это препарат (PLAN D5). */
        FORM_UNKNOWN,

        /** Форма пачки отличается от формы препарата. */
        FORM_MISMATCH,

        /** Единица пачки отличается от единицы препарата. */
        UNIT_MISMATCH,

        /** Активировать нельзя: нет расписания. */
        SCHEDULE_MISSING,

        /** Активировать нельзя: нет разовой дозы. */
        DOSE_MISSING,

        /** Активировать нельзя: нет ни одной пачки. */
        SOURCES_MISSING
    }
}
