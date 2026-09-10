package com.kert0n.medapp.domain.pack

import java.time.LocalDate
import java.time.YearMonth

/**
 * Срок годности — последний день, когда пачка ещё годна. `null` вместо него значит «срока не
 * знаем», и просроченной такая пачка не бывает (PLAN D8).
 */
data class ExpiryDate(val lastDay: LocalDate) {

    /** Дата **включительная**: пачка, годная «до 31 марта», просрочена только 1 апреля. */
    fun isExpiredOn(date: LocalDate): Boolean = lastDay.isBefore(date)

    /**
     * Истекает ли срок не позже чем через [days] дней, считая [date]: окно, а не точный день,
     * потому что фоновая проверка может опоздать (D8). Просроченная пачка «скоро» не истекает.
     */
    fun expiresWithin(date: LocalDate, days: Long): Boolean {
        require(days >= 0) { "окно предупреждения не бывает отрицательным" }
        if (isExpiredOn(date)) return false
        return !lastDay.isAfter(date.plusDays(days))
    }

    companion object {
        /** С какого числа дней до конца срока пачка «истекает скоро» — больший из порогов D8. */
        const val SOON_DAYS = 3L

        /** «03.2027» на упаковке значит «годен весь март»: срок — последний день месяца (D3). */
        fun of(month: YearMonth): ExpiryDate = ExpiryDate(month.atEndOfMonth())
    }
}
