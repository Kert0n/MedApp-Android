package com.kert0n.medapp.storage.pack

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.pack.PackageSharedFacts
import com.kert0n.medapp.network.pack.PackageSyncState
import com.kert0n.medapp.network.server.ResourceVersion
import com.kert0n.medapp.storage.value.toStorageAmount
import com.kert0n.medapp.storage.value.toStorageSortKey
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Подтверждённая серверная часть упаковки: то, что снимок переписывает целиком. Личные сведения
 * лежат отдельной строкой, иначе срок годности и цена стирались бы при каждом обновлении
 * (PLAN F1).
 *
 * `version`, `claims_version` и `synced_at` — обвязка доставки: это `PackageSyncState` сетевого
 * слоя, а не свойство пачки.
 *
 * Две производные колонки существуют ради одного запроса списка (PLAN H4). `quantity_sort` —
 * то же число, дополненное нулями до предельной ширины величины, поэтому порядок по остатку
 * получается без `CAST(… AS REAL)` (F3). `name_search` — название в нижнем регистре: `lower()`
 * и `COLLATE NOCASE` в SQLite знают только латиницу, и по-русски поиск без учёта регистра иначе
 * не работает.
 */
@Entity(
    tableName = "packages",
    indices = [Index("med_kit_id"), Index("name_search")]
)
class PackageStorageEntity(
    @PrimaryKey val id: Uuid,
    @ColumnInfo(name = "med_kit_id") val medKitId: Uuid,
    val name: String,
    @ColumnInfo(name = "name_search") val nameSearch: String,
    val quantity: String,
    @ColumnInfo(name = "quantity_sort") val quantitySort: String,
    @ColumnInfo(name = "quantity_unit_id") val quantityUnitId: Uuid,
    @ColumnInfo(name = "form_id") val formId: Uuid? = null,
    val category: String? = null,
    val manufacturer: String? = null,
    val country: String? = null,
    val description: String? = null,
    val version: Long? = null,
    @ColumnInfo(name = "claims_version") val claimsVersion: Long? = null,
    val lifecycle: Package.Lifecycle = Package.Lifecycle.ACTIVE,
    val access: Package.Access = Package.Access.AVAILABLE,
    @ColumnInfo(name = "synced_at") val syncedAt: Instant? = null
) {
    fun sharedFacts(): PackageSharedFacts = PackageSharedFacts(
        name = name,
        formId = formId,
        category = category,
        manufacturer = manufacturer,
        country = country,
        description = description
    )

    fun syncState(): PackageSyncState = PackageSyncState(
        packageId = id,
        version = version?.let(::ResourceVersion),
        claimsVersion = claimsVersion?.let(::ResourceVersion),
        syncedAt = syncedAt
    )
}

fun Package.toStorageEntity(sync: PackageSyncState = PackageSyncState(id)): PackageStorageEntity {
    require(sync.packageId == id) { "обвязка синхронизации принадлежит своей пачке" }
    return PackageStorageEntity(
        id = id,
        medKitId = medKitId,
        name = facts.name,
        nameSearch = facts.name.lowercase(),
        quantity = quantity.toStorageAmount(),
        quantitySort = quantity.toStorageSortKey(),
        quantityUnitId = quantity.unitId,
        formId = facts.formId,
        category = facts.category,
        manufacturer = facts.manufacturer,
        country = facts.country,
        description = facts.description,
        version = sync.version?.number,
        claimsVersion = sync.claimsVersion?.number,
        lifecycle = lifecycle,
        access = access,
        syncedAt = sync.syncedAt
    )
}
