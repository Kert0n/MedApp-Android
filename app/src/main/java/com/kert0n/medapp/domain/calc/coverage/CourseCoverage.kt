package com.kert0n.medapp.domain.calc.coverage

import java.time.Instant

/**
 * Обеспечение курса: на сколько приёмов вперёд хватит подключённых источников.
 *
 * **Вычисляется и не хранится** (PLAN D5). Хранимое поле разъехалось бы с очередью и с
 * остатками при первом же чужом изменении, и «обеспечено 9» осталось бы на экране после того,
 * как пачку пересчитали вниз.
 *
 * [firstUncoveredAt] — то, что человеку нужно на самом деле: не «не хватает 19», а «не хватает
 * с 11 сентября». Нехватка меняет обеспечение, а не расписание: даты, времена и доза курса от
 * неё не двигаются (PLAN C1, D5).
 *
 * [requiresRecount] — обеспечение неполно: по какой-то пачке исход операции не установлен, и её
 * доступный остаток неизвестен. Такой источник сохраняется, но за обеспеченный не выдаётся —
 * выдуманный предел здесь был бы обещанием лекарства, которого может не быть (PLAN D5, H1).
 */
data class CourseCoverage(
    val requiredDoses: Int,          // сколько приёмов ещё впереди
    val coveredDoses: Int,           // сколько из них обеспечено
    val coveredUntil: Instant?,      // «доступный курс» — до какого момента хватит
    val firstUncoveredAt: Instant?,  // с какого приёма не хватает
    val perSource: List<SourceCoverage>,
    val requiresRecount: Boolean = false
) {
    init {
        require(requiredDoses >= 0) { "потребность не бывает отрицательной" }
        require(coveredDoses in 0..requiredDoses) {
            "обеспечено больше, чем нужно: $coveredDoses из $requiredDoses"
        }
    }

    val missingDoses: Int get() = requiredDoses - coveredDoses

    val isFullyCovered: Boolean get() = missingDoses == 0
}
