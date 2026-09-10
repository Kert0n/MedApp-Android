package com.kert0n.medapp.storage.value

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kert0n.medapp.domain.value.DosageForm
import kotlin.uuid.Uuid

/** Словарь форм выпуска; идентификаторы серверные, как и у единиц (PLAN F1). */
@Entity(tableName = "form_types")
class DosageFormStorageEntity(
    @PrimaryKey val id: Uuid,
    val name: String
) {
    fun toDomain(): DosageForm = DosageForm(id = id, name = name)
}

fun DosageForm.toStorageEntity(): DosageFormStorageEntity =
    DosageFormStorageEntity(id = id, name = name)
