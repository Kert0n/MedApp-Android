package com.kert0n.medapp.domain.model.sync

import com.kert0n.medapp.domain.model.value.Quantity
import kotlin.uuid.Uuid

/**
 * Списать фактически принятое.
 *
 * [intakeId] отдельным полем: **намерение приёма не поглощает следующий факт**, у каждого
 * подтверждения свой идентификатор, и повтор отправки не превращается во второе списание
 * (PLAN E2).
 *
 * [claimAfter] — новая **абсолютная** бронь после расхода, и три её значения означают три разных
 * действия (PLAN E2):
 * - `null` — внеплановый расход, брони не касается;
 * - положительное — курсовой расход с новым объёмом брони;
 * - ноль — курсовой расход без блока брони, а снятие уезжает зависимым `ReleaseClaimIntent`.
 *
 * Величина брони не всегда уменьшается на физический расход: при частичной или увеличенной дозе
 * она пересчитывается по правилам D5, поэтому здесь два независимых числа, а не одно.
 */
data class ConsumeIntent(
    val packageId: Uuid,
    val amount: Quantity,
    val intakeId: Uuid,
    val claimAfter: Quantity? = null
) : SyncIntent {
    init {
        require(!amount.isZero) { "расход нулевого количества не является приёмом" }
        require(claimAfter == null || claimAfter.unitId == amount.unitId) {
            "бронь измеряется той же единицей, что расход"
        }
    }
}
