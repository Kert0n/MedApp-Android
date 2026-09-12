package com.kert0n.medapp.queue.pack

import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.network.pack.ClaimPatchNetworkDTO
import com.kert0n.medapp.network.pack.ClaimPostNetworkDTO
import com.kert0n.medapp.network.pack.PackagePatchNetworkDTO
import com.kert0n.medapp.network.pack.PackagePostNetworkDTO
import com.kert0n.medapp.network.pack.PackageSyncNetworkDTO
import com.kert0n.medapp.network.pack.PackageSyncState
import com.kert0n.medapp.network.pack.toPatchNetworkDTO
import com.kert0n.medapp.network.server.MedAppRoutes
import com.kert0n.medapp.network.server.ResourceVersion
import com.kert0n.medapp.network.server.medAppJson
import com.kert0n.medapp.network.value.toNetworkAmount
import com.kert0n.medapp.queue.Preparation
import com.kert0n.medapp.queue.PreparedRequest
import com.kert0n.medapp.queue.RefusalReason
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * «Подумали» перед отправкой: команда смотрит на пачку, какой её знает устройство после свежего
 * чтения, и решает — уходит запрос, закрывается отказ или желаемое уже так (PLAN E2, E3).
 * Число в единице, которой пачку больше не считают, на провод не идёт — ни расход, ни бронь, ни
 * пересчёт ([PackageSyncCommand.measuredIn]). Бронь, равная желаемой, и снятие отсутствующей
 * брони — уже так. Всё остальное становится запросом по [toPreparedRequest].
 */
fun PackageSyncCommand.prepare(operationId: Uuid, pkg: Package, sync: PackageSyncState, at: Instant): Preparation {
    val mine = pkg.claims?.mine?.let { Quantity(it, pkg.quantity.unit) }
    val unit = measuredIn
    return when {
        unit != null && unit != pkg.quantity.unit -> Preparation.Refuse(RefusalReason.UNIT_CHANGED)
        this is PackageSyncCommand.SetClaim && mine == amount -> Preparation.AlreadyApplied
        this is PackageSyncCommand.ReleaseClaim && mine == null && sync.claimsVersion != null -> Preparation.AlreadyApplied
        else -> Preparation.Request(toPreparedRequest(operationId, sync, confirmed = pkg.quantity, mine = mine, at = at))
    }
}

/**
 * Команда по пачке как запрос — с теми предусловиями, что у пачки в этот момент: версией
 * состояния, версией броней, подтверждённым остатком и своей бронью. Собранный запрос не
 * пересобирается: повтор с неизвестным исходом идёт тем же, а устаревший сбрасывается и
 * собирается заново по свежему состоянию (PLAN E2, E3).
 *
 * Расход — всегда `sync` под [operationId]: у него есть номер, и повтор под ним сервер применит
 * один раз; внеплановый расход — тот же `sync` без блока брони (решение владельца, B4).
 * Ноль пересчёта — `DELETE`: это форма провода, а не смысл команды.
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
        is PackageSyncCommand.Consume -> prepared(
            method = "PUT",
            path = MedAppRoutes.sync(packageId, operationId),
            body = medAppJson.encodeToString(
                PackageSyncNetworkDTO.serializer(),
                PackageSyncNetworkDTO(
                    consumed = amount.quantity.toNetworkAmount(),
                    packageVersion = version,
                    // Внеплановый расход и нулевая бронь — без блока брони: первому бронь не
                    // нужна, у второго снятие уезжает зависимым `ReleaseClaim` (PLAN E2).
                    claim = claimAfter?.takeUnless { it.isZero }?.let {
                        PackageSyncNetworkDTO.Claim(it.toNetworkAmount(), claimsVersion)
                    }
                )
            ),
            sync = sync, confirmed = confirmed, mine = mine, at = at
        )
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

