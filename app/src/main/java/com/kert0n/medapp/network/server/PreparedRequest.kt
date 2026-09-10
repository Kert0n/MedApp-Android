package com.kert0n.medapp.network.server

import com.kert0n.medapp.domain.value.Quantity
import java.time.Instant

/**
 * Подготовленный изменяющий запрос: путь, тело и версии предусловий, замороженные **до** первой
 * отправки. На повторе он не пересобирается — иначе неустановленный расход ушёл бы со свежим
 * предусловием и списался бы дважды (PLAN E2, E3).
 *
 * Доменным расчётам он недоступен: это транспорт, а не смысл.
 */
data class PreparedRequest(
    val method: String,
    val path: String,
    val query: Map<String, String> = emptyMap(),
    val body: String? = null,
    val drugVersion: Long? = null,
    val claimsVersion: Long? = null,
    val quantityBefore: Quantity? = null,
    val mineBefore: Quantity? = null,
    val preparedAt: Instant
) {
    init {
        require(method.isNotBlank()) { "у запроса есть метод" }
        require(path.isNotBlank()) { "у запроса есть путь" }
        require(drugVersion == null || drugVersion >= 0) { "версия пачки не бывает отрицательной" }
        require(claimsVersion == null || claimsVersion >= 0) {
            "версия картины броней не бывает отрицательной"
        }
        require(
            quantityBefore == null || mineBefore == null ||
                quantityBefore.unitId == mineBefore.unitId
        ) { "остаток и бронь до запроса измеряются одной единицей" }
    }

    /** Единица предусловий: она одна на обе величины и остаётся той, что была при подготовке. */
    val unitId get() = quantityBefore?.unitId ?: mineBefore?.unitId
}
