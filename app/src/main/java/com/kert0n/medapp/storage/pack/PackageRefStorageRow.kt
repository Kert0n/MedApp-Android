package com.kert0n.medapp.storage.pack

import androidx.room.Embedded
import androidx.room.Relation
import com.kert0n.medapp.domain.pack.PackageRef
import com.kert0n.medapp.domain.value.Vocabulary
import com.kert0n.medapp.storage.medkit.MedKitStorageEntity
import com.kert0n.medapp.storage.value.storedUnit

/**
 * Пачка глазами чужого агрегата — источника курса, приёма, движения: серверная строка и её
 * аптечка, без личных сведений и броней. Room читает её связью той же транзакцией, что и
 * владельца, одним запросом на всю выборку; в домен уходит [PackageRef], а не вся пачка.
 */
class PackageRefStorageRow(
    @Embedded val pack: PackageStorageEntity,
    @Relation(parentColumn = "med_kit_id", entityColumn = "id")
    val medKit: MedKitStorageEntity? = null
) {
    fun toRef(vocabulary: Vocabulary): PackageRef = PackageRef(
        id = pack.id,
        name = pack.name,
        unit = vocabulary.storedUnit(pack.quantityUnitId),
        form = pack.sharedFacts(vocabulary).form,
        lifecycle = pack.lifecycle,
        access = pack.access,
        medKit = requireNotNull(medKit) { "пачка лежит в аптечке, которой нет: ${pack.medKitId}" }.toRef()
    )
}
