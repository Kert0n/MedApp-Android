package com.kert0n.medapp.domain.intake

import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.value.Dose
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Состоявшийся приём: сколько, из какой пачки, в какой аптечке и когда — четыре поля, которые
 * бывают только вместе. Пачка может отличаться от плановой, и расход относится к фактической
 * (PLAN D5). Аптечка и единица записаны на момент события: перенос пачки прошлые отчёты не
 * переписывает (D6).
 */
data class TakenDose(
    val packageId: Uuid,
    val medKitId: Uuid,
    val amount: Dose,
    val at: Instant
) {
    /**
     * Из чего собирается факт: пачка, а не пара идентификаторов. Аптечка берётся у неё же,
     * поэтому «принял из пачки, лежащей в другой аптечке» здесь невыразимо, а не проверяется.
     * Первичный путь остаётся для восстановления сохранённого: там на руках только колонки.
     */
    constructor(pkg: Package, amount: Dose, at: Instant) : this(pkg.id, pkg.medKitId, amount, at) {
        // Пачку передают целиком как раз затем, чтобы проверить это отношение: две таблетки из
        // флакона, который меряют миллилитрами, — не факт, а испорченная история.
        require(amount.unitId == pkg.quantity.unitId) {
            "принятое измеряется единицей той пачки, из которой взято"
        }
    }
}
