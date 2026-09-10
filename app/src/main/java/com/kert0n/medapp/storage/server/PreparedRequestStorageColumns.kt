package com.kert0n.medapp.storage.server

import androidx.room.ColumnInfo
import com.kert0n.medapp.network.server.PreparedRequest
import com.kert0n.medapp.storage.value.storedQuantity
import com.kert0n.medapp.storage.value.toStorageAmount
import java.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Подготовленный запрос в колонках строки очереди. Отдельной таблицы у него нет: он рождается и
 * умирает вместе со своей операцией и живёт в единственном экземпляре (PLAN E2).
 *
 * Единица предусловий записана рядом с ними: остаток и бронь до запроса измеряются одной
 * единицей, и брать её из пачки позже нельзя — там она уже могла смениться.
 */
class PreparedRequestStorageColumns(
    val method: String,
    val path: String,
    val query: String,
    val body: String? = null,
    @ColumnInfo(name = "drug_version") val drugVersion: Long? = null,
    @ColumnInfo(name = "claims_version") val claimsVersion: Long? = null,
    @ColumnInfo(name = "quantity_before") val quantityBefore: String? = null,
    @ColumnInfo(name = "mine_before") val mineBefore: String? = null,
    @ColumnInfo(name = "unit_id") val unitId: Uuid? = null,
    val at: Instant
) {
    fun toDomain(): PreparedRequest = PreparedRequest(
        method = method,
        path = path,
        query = Json.decodeFromString(queryFormat, query),
        body = body,
        drugVersion = drugVersion,
        claimsVersion = claimsVersion,
        quantityBefore = quantityBefore?.let { storedQuantity(it, requireUnit()) },
        mineBefore = mineBefore?.let { storedQuantity(it, requireUnit()) },
        preparedAt = at
    )

    private fun requireUnit(): Uuid =
        requireNotNull(unitId) { "предусловие по остатку записано вместе со своей единицей" }
}

fun PreparedRequest.toStorageColumns(): PreparedRequestStorageColumns =
    PreparedRequestStorageColumns(
        method = method,
        path = path,
        query = Json.encodeToString(queryFormat, query),
        body = body,
        drugVersion = drugVersion,
        claimsVersion = claimsVersion,
        quantityBefore = quantityBefore?.toStorageAmount(),
        mineBefore = mineBefore?.toStorageAmount(),
        unitId = unitId,
        at = preparedAt
    )

private val queryFormat = MapSerializer(String.serializer(), String.serializer())
