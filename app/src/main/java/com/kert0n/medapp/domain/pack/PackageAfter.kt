package com.kert0n.medapp.domain.pack

import com.kert0n.medapp.domain.stock.StockMovement

/**
 * Что стало с коробкой после перехода: она либо осталась, либо кончилась. Два случая названы
 * типом, а не `null`: отсутствие ничего не говорит о том, что при этом должно быть записано, и
 * потому его легко не заметить — а у конца есть и след, и зависимые, которые о нём узнают
 * (PLAN D3, D7).
 *
 * [trace] — запись о том, что произошло, а не о том, чем это кончилось: у утилизации и пересчёта
 * она есть в обоих случаях, у расхода нет ни в одном — приём и есть учётная запись о нём (H6).
 */
sealed interface PackageAfter {

    /** Чем переход объясняется в истории; `null` — объяснение уже записано в другом месте. */
    val trace: StockMovement?

    /** Коробка осталась: в ней [pkg] после перехода. */
    data class Left(val pkg: Package, override val trace: StockMovement? = null) : PackageAfter

    /** Коробки больше нет; что от неё осталось и чем это объясняется, несёт [ending]. */
    data class Ended(val ending: PackageEnding) : PackageAfter {
        override val trace: StockMovement? get() = ending.trace
    }
}
