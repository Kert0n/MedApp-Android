package com.kert0n.medapp.domain.course

import com.kert0n.medapp.domain.value.Doses
import com.kert0n.medapp.domain.value.Quantity
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Обеспечение курса: сколько из оставшихся приёмов покрывают пачки препарата и с какого приёма
 * не хватает. Вычисляется и не хранится (PLAN D5); нехватка меняет обеспечение, а не расписание.
 * [requiresRecount] — по какой-то пачке число неизвестно, и обеспечение неполно до сверки.
 */
data class CourseCoverage(
    val requiredDoses: Doses,        // сколько приёмов ещё впереди
    val coveredDoses: Doses,         // сколько из них обеспечено
    val coveredUntil: Instant?,      // до какого приёма хватит
    val firstUncoveredAt: Instant?,  // с какого приёма не хватает
    val perSource: List<Source>,
    val requiresRecount: Boolean = false
) {
    init {
        require(coveredDoses <= requiredDoses) {
            "обеспечено больше, чем нужно: $coveredDoses из $requiredDoses"
        }
    }

    val missingDoses: Doses get() = requiredDoses - coveredDoses

    val isFullyCovered: Boolean get() = missingDoses.isNone

    /**
     * Строка по одной пачке. [coveredDoses] — часть выделения, которую подтверждает остаток;
     * разница с [allocatedDoses] объясняет человеку, почему обеспечено меньше выделенного.
     * [leftover] — остаток меньше дозы, не переливающийся в следующую пачку; `null` — число
     * неизвестно.
     */
    data class Source(
        val packageId: Uuid,
        val allocatedDoses: Doses,
        val coveredDoses: Doses,
        val leftover: Quantity?
    ) {
        init {
            require(coveredDoses <= allocatedDoses) {
                "покрыто больше, чем выделено: $coveredDoses из $allocatedDoses"
            }
        }
    }
}
