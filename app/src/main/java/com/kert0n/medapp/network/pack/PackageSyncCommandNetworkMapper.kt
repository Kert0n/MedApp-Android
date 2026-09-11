package com.kert0n.medapp.network.pack

import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.network.server.MedAppRoutes
import com.kert0n.medapp.network.server.ResourceVersion
import com.kert0n.medapp.network.server.medAppJson
import com.kert0n.medapp.network.value.toNetworkAmount
import com.kert0n.medapp.queue.PreparedRequest
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Команда по пачке становится запросом ровно один раз — перед первой отправкой — и с теми
 * предусловиями, что были у пачки в этот момент: версией состояния, версией броней, подтверждённым
 * остатком и своей бронью. На повторе запрос не пересобирается: устаревшее предусловие отвергнет
 * сервер, и в этом вся безопасность повтора (PLAN E2, E3).
 *
 * [operationId] — `syncId` курсового расхода: повтор под тем же номером сервер применит один раз.
 * Ноль пересчёта — `DELETE`: это дело провода, а не смысл команды.
 */
fun PackageSyncCommand.toPreparedRequest(
    operationId: Uuid,
    sync: PackageSyncState,
    confirmed: Quantity?,
    mine: Quantity?,
    at: Instant
): PreparedRequest {
    val version = sync.version
    val claimsVersion = sync.claimsVersion
    return when (this) {
        is PackageSyncCommand.Create -> prepared(
            method = "POST",
            path = MedAppRoutes.packagesOf(medKitId),
            body = medAppJson.encodeToString(
                PackagePostNetworkDTO.serializer(),
                PackagePostNetworkDTO(
                    id = packageId,
                    name = facts.name,
                    amount = quantity.toNetworkAmount(),
                    unitId = quantity.unit.id,
                    formId = facts.form?.id,
                    category = facts.category,
                    manufacturer = facts.manufacturer,
                    country = facts.country,
                    description = facts.description
                )
            ),
            sync = sync, confirmed = confirmed, mine = mine, at = at
        )
        is PackageSyncCommand.Describe -> prepared(
            method = "PATCH",
            path = MedAppRoutes.pack(packageId),
            body = medAppJson.encodeToString(
                PackagePatchNetworkDTO.serializer(),
                after.toPatchNetworkDTO(before, version)
            ),
            sync = sync, confirmed = confirmed, mine = mine, at = at
        )
        is PackageSyncCommand.CorrectStock ->
            if (actual.isZero) prepared(
                method = "DELETE",
                path = MedAppRoutes.pack(packageId),
                query = versionQuery(version),
                sync = sync, confirmed = confirmed, mine = mine, at = at
            ) else prepared(
                method = "PATCH",
                path = MedAppRoutes.pack(packageId),
                body = medAppJson.encodeToString(
                    PackagePatchNetworkDTO.serializer(),
                    PackagePatchNetworkDTO(amount = actual.toNetworkAmount(), version = version)
                ),
                sync = sync, confirmed = confirmed, mine = mine, at = at
            )
        is PackageSyncCommand.Move -> prepared(
            method = "PUT",
            path = MedAppRoutes.packageIn(targetMedKitId, packageId),
            query = versionQuery(version),
            sync = sync, confirmed = confirmed, mine = mine, at = at
        )
        is PackageSyncCommand.Delete -> prepared(
            method = "DELETE",
            path = MedAppRoutes.pack(packageId),
            query = versionQuery(version),
            sync = sync, confirmed = confirmed, mine = mine, at = at
        )
        is PackageSyncCommand.Consume -> {
            val claimAfter = claimAfter
            if (claimAfter == null) prepared(
                method = "POST",
                path = MedAppRoutes.intakes(packageId),
                body = medAppJson.encodeToString(
                    PackageConsumeNetworkDTO.serializer(),
                    PackageConsumeNetworkDTO(amount = amount.quantity.toNetworkAmount(), version = version)
                ),
                sync = sync, confirmed = confirmed, mine = mine, at = at
            ) else prepared(
                method = "PUT",
                path = MedAppRoutes.sync(packageId, operationId),
                body = medAppJson.encodeToString(
                    PackageSyncNetworkDTO.serializer(),
                    PackageSyncNetworkDTO(
                        consumed = amount.quantity.toNetworkAmount(),
                        packageVersion = version,
                        // Нулевая бронь — не блок брони, а зависимое снятие (PLAN E2).
                        claim = claimAfter.takeUnless { it.isZero }?.let {
                            PackageSyncNetworkDTO.Claim(it.toNetworkAmount(), claimsVersion)
                        }
                    )
                ),
                sync = sync, confirmed = confirmed, mine = mine, at = at
            )
        }
        is PackageSyncCommand.SetClaim ->
            // Бронь на сервере одна на пару «человек и пачка»: есть своя — правится, нет — заявляется.
            if (mine == null) prepared(
                method = "POST",
                path = MedAppRoutes.CLAIMS,
                body = medAppJson.encodeToString(
                    ClaimPostNetworkDTO.serializer(),
                    ClaimPostNetworkDTO(packageId, amount.toNetworkAmount(), claimsVersion)
                ),
                sync = sync, confirmed = confirmed, mine = mine, at = at
            ) else prepared(
                method = "PATCH",
                path = MedAppRoutes.claim(packageId),
                body = medAppJson.encodeToString(
                    ClaimPatchNetworkDTO.serializer(),
                    ClaimPatchNetworkDTO(amount.toNetworkAmount(), claimsVersion)
                ),
                sync = sync, confirmed = confirmed, mine = mine, at = at
            )
        is PackageSyncCommand.ReleaseClaim -> prepared(
            method = "DELETE",
            path = MedAppRoutes.claim(packageId),
            query = versionQuery(claimsVersion),
            sync = sync, confirmed = confirmed, mine = mine, at = at
        )
    }
}

/** Отвечает ли сервер на этот запрос снимком пачки — или снимок после него читается отдельно. */
val PackageSyncCommand.answersWithSnapshot: Boolean
    get() = when (this) {
        is PackageSyncCommand.Create, is PackageSyncCommand.Describe, is PackageSyncCommand.Move,
        is PackageSyncCommand.Consume -> true
        is PackageSyncCommand.CorrectStock -> !actual.isZero
        is PackageSyncCommand.Delete, is PackageSyncCommand.SetClaim, is PackageSyncCommand.ReleaseClaim -> false
    }

/** Курсовой расход едет `sync` под своим номером: повтор сервер применит один раз (PLAN B4). */
val PackageSyncCommand.isSync: Boolean
    get() = this is PackageSyncCommand.Consume && claimAfter != null

private fun prepared(
    method: String,
    path: String,
    query: Map<String, String> = emptyMap(),
    body: String? = null,
    sync: PackageSyncState,
    confirmed: Quantity?,
    mine: Quantity?,
    at: Instant
) = PreparedRequest(
    method = method,
    path = path,
    query = query,
    body = body,
    drugVersion = sync.version,
    claimsVersion = sync.claimsVersion,
    quantityBefore = confirmed,
    mineBefore = mine,
    preparedAt = at
)

private fun versionQuery(version: ResourceVersion?): Map<String, String> =
    version?.let { mapOf("version" to it.number.toString()) } ?: emptyMap()

