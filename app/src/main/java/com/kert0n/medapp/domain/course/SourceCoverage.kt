package com.kert0n.medapp.domain.course

import com.kert0n.medapp.domain.value.Doses
import com.kert0n.medapp.domain.value.Quantity
import kotlin.uuid.Uuid

/**
 * Строка обеспечения по одному источнику.
 *
 * [leftover] — физический остаток пачки, **не покрывающий целую дозу**. Он остаётся видимым в
 * строке источника и **не переливается** в следующий: доза берётся из одной упаковки и между
 * пачками не делится (PLAN D5). Одна таблетка при дозе в две — не половина приёма, а свободный
 * остаток, который не держит брони и годится для разового приёма.
 *
 * [coveredDoses] отделено от [allocatedDoses] намеренно: выделение — намерение человека, а
 * покрытие — то, что из него подтверждается свежим остатком. Разошлись они, когда чужая бронь
 * выросла или пачку пересчитали вниз, и именно эта разница объясняет человеку, почему
 * обеспечение меньше выделенного.
 */
data class SourceCoverage(
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
