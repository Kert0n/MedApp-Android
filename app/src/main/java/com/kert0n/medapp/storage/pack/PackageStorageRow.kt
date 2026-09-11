package com.kert0n.medapp.storage.pack

import androidx.room.Embedded
import androidx.room.Relation
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.pack.PackageFacts
import com.kert0n.medapp.domain.value.Vocabulary
import com.kert0n.medapp.storage.value.storedDose
import com.kert0n.medapp.storage.value.storedMoney
import com.kert0n.medapp.storage.value.storedQuantity
import com.kert0n.medapp.storage.value.storedUnit

/**
 * Упаковка, собранная из своих строк: серверная часть и личные сведения хранятся порознь, а
 * домену пачка нужна целиком.
 *
 * Картина броней — тоже своя строка: её двигают чужие действия. Её отсутствие означает `null`
 * у пачки, а не ноль (PLAN F1). Единицу и форму строка держит идентификаторами, а объекты даёт
 * снимок словаря.
 */
class PackageStorageRow(
    @Embedded val pack: PackageStorageEntity,
    @Relation(parentColumn = "id", entityColumn = "package_id")
    val details: PackageDetailsStorageEntity,
    @Relation(parentColumn = "id", entityColumn = "package_id")
    val claims: ClaimsStorageEntity? = null
) {
    fun toDomain(vocabulary: Vocabulary): Package = Package(
        id = pack.id,
        medKitId = pack.medKitId,
        facts = PackageFacts(
            shared = pack.sharedFacts(vocabulary),
            expiresOn = details.expiry(),
            defaultIntakeAmount = details.defaultIntakeAmount?.let {
                val unitId = requireNotNull(details.defaultIntakeUnitId) {
                    "доза-подсказка без единицы не восстанавливается"
                }
                storedDose(it, vocabulary.storedUnit(unitId))
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
        quantity = storedQuantity(pack.quantity, vocabulary.storedUnit(pack.quantityUnitId)),
        addedAt = details.addedAt,
        templateId = details.templateId,
        claims = claims?.toDomain(),
        lifecycle = pack.lifecycle,
        access = pack.access
    )
}
