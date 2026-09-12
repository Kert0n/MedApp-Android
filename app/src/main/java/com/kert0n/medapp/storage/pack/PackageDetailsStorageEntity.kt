package com.kert0n.medapp.storage.pack

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.kert0n.medapp.domain.pack.ExpiryDate
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.storage.value.toStorageAmount
import com.kert0n.medapp.storage.value.toStorageCurrency
import java.time.Instant
import java.time.LocalDate
import kotlin.uuid.Uuid

/**
 * Сведения, которых сервер не знает: срок годности, доза-подсказка, заметка, цена и даты
 * покупки и вскрытия. **Строка заводится всегда**, включая пачки из чужой опубликованной
 * аптечки: тогда `added_at` — момент первого наблюдения, и обязательное поле домена никогда
 * не отсутствует (PLAN F1).
 */
@Entity(
    tableName = "package_details",
    foreignKeys = [
        ForeignKey(
            entity = PackageStorageEntity::class,
            parentColumns = ["id"],
            childColumns = ["package_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ]
)
class PackageDetailsStorageEntity(
    @PrimaryKey @ColumnInfo(name = "package_id") val packageId: Uuid,
    @ColumnInfo(name = "added_at") val addedAt: Instant,
    @ColumnInfo(name = "expires_on") val expiresOn: LocalDate? = null,
    @ColumnInfo(name = "default_intake_amount") val defaultIntakeAmount: String? = null,
    @ColumnInfo(name = "default_intake_unit_id") val defaultIntakeUnitId: Uuid? = null,
    val note: String? = null,
    val price: String? = null,
    val currency: String? = null,
    @ColumnInfo(name = "purchased_on") val purchasedOn: LocalDate? = null,
    @ColumnInfo(name = "opened_on") val openedOn: LocalDate? = null,
    @ColumnInfo(name = "template_id") val templateId: Uuid? = null
)

fun Package.toDetailsStorageEntity(): PackageDetailsStorageEntity = PackageDetailsStorageEntity(
    packageId = id,
    addedAt = addedAt,
    expiresOn = facts.expiresOn?.lastDay,
    defaultIntakeAmount = facts.defaultIntakeAmount?.quantity?.toStorageAmount(),
    defaultIntakeUnitId = facts.defaultIntakeAmount?.unit?.id,
    note = facts.note,
    price = facts.price?.toStorageAmount(),
    currency = facts.price?.toStorageCurrency(),
    purchasedOn = facts.purchasedOn,
    openedOn = facts.openedOn,
    templateId = templateId
)

/** Момент первого наблюдения: у чужой пачки заводить детали больше нечем (PLAN F1). */
fun observedPackageDetails(packageId: Uuid, at: Instant): PackageDetailsStorageEntity =
    PackageDetailsStorageEntity(packageId = packageId, addedAt = at)

internal fun PackageDetailsStorageEntity.expiry(): ExpiryDate? = expiresOn?.let(::ExpiryDate)
