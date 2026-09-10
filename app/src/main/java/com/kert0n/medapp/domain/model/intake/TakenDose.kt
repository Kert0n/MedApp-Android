package com.kert0n.medapp.domain.model.intake

import com.kert0n.medapp.domain.model.value.Quantity
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Состоявшийся приём: сколько, откуда и когда принято.
 *
 * Четыре поля, которые бывают только вместе. Пока они лежали россыпью в приёме, «подтверждённый
 * знает момент, количество и пачку» приходилось проверять `require`-ом над сочетанием четырёх
 * `null`; здесь подтверждение без пачки или без момента **невыразимо**.
 *
 * [packageId] может отличаться от плановой пачки: человек взял таблетку из другой пачки курса, и
 * расход относится к фактической (PLAN D5).
 *
 * [medKitId] и единица пишутся **на момент события**: перенос пачки и смена единицы прошлые
 * отчёты не переписывают (PLAN D6).
 */
data class TakenDose(
    val packageId: Uuid,
    val medKitId: Uuid,
    val amount: Quantity,
    val at: Instant
) {
    init {
        require(!amount.isZero) { "принятый ноль — это пропуск, а не приём" }
    }
}
