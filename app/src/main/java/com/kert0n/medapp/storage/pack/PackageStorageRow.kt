package com.kert0n.medapp.storage.pack

import androidx.room.Embedded
import androidx.room.Relation
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.pack.PackageFacts
import com.kert0n.medapp.storage.value.storedDose
import com.kert0n.medapp.storage.value.storedMoney
import com.kert0n.medapp.storage.value.storedQuantity

/**
 * Упаковка, собранная из своих строк: серверная часть и личные сведения хранятся порознь, а
 * домену пачка нужна целиком.
 *
 * Картина броней — тоже своя строка: её двигают чужие действия. Её отсутствие означает `null`
 * у пачки, а не ноль (PLAN F1).
 */
class PackageStorageRow(
    @Embedded val pack: PackageStorageEntity,
    @Relation(parentColumn = "id", entityColumn = "package_id")
    val details: PackageDetailsStorageEntity,
    @Relation(parentColumn = "id", entityColumn = "package_id")
    val claims: ClaimsStorageEntity? = null
) {
    fun toDomain(): Package = Package(
        id = pack.id,
        medKitId = pack.medKitId,
        facts = PackageFacts(
            shared = pack.sharedFacts(),
            expiresOn = details.expiry(),
            defaultIntakeAmount = details.defaultIntakeAmount?.let {
                storedDose(it, requireNotNull(details.defaultIntakeUnitId) {
                    "доза-подсказка без единицы не восстанавливается"
                })
            },
            note = details.note,
            price = details.price?.let {
                storedMoney(it, requireNotNull(details.currency) {
                    "цена без валюты не восстанавливается"
                })
            },
            purchasedOn = details.purchasedOn,
            openedOn = details.openedOn
        ),
        quantity = storedQuantity(pack.quantity, pack.quantityUnitId),
        addedAt = details.addedAt,
        templateId = details.templateId,
        claims = claims?.toDomain(),
        lifecycle = pack.lifecycle,
        access = pack.access
    )
}
