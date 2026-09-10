package com.kert0n.medapp.data.intake

import kotlin.uuid.Uuid

/**
 * Обвязка синхронизации приёма: где находится записанный им расход и какой операцией он уехал.
 *
 * **Не свойство приёма.** Пока `accounting` жил в доменной модели, каждое правило о приёме
 * приходилось читать вместе с состоянием сети, а локальная аптечка — где исходящих операций нет
 * вовсе (PLAN E1) — носила поле, которое у неё всегда `LOCAL_APPLIED`. Довод тот же, что снял
 * версию предусловия с упаковки: домен содержит правила о приёме, а не о том, доехал ли он.
 *
 * Инвариант «у подтверждённого приёма расход не бывает неприменим» проверяется транзакцией,
 * которая меняет статус и учёт вместе (PLAN F5, D6): здесь виден только учёт, и статуса приёма
 * этот тип не знает — иначе он снова стал бы половиной приёма.
 */
data class IntakeSyncState(
    val intakeId: Uuid,
    val accounting: IntakeAccounting = IntakeAccounting.NOT_APPLICABLE,
    val operationId: Uuid? = null,       // связь с расходом в очереди
    val reconciliationId: Uuid? = null   // ручная сверка, если факт включён в неё
) {
    init {
        // Расхода нет — и связывать не с чем: ни операции, ни сверки у такого приёма быть не
        // может, иначе «не применимо» скрывало бы уехавшее списание.
        require(
            accounting != IntakeAccounting.NOT_APPLICABLE ||
                (operationId == null && reconciliationId == null)
        ) { "у приёма без расхода нет ни операции, ни сверки" }
        require(
            accounting != IntakeAccounting.PENDING &&
                accounting != IntakeAccounting.NEEDS_RECOUNT || operationId != null
        ) { "неустановленный расход называет свою операцию" }
        require(
            accounting != IntakeAccounting.RECONCILED || reconciliationId != null
        ) { "учтённый сверкой расход называет сверку" }
    }
}
