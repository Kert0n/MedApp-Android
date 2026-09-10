package com.kert0n.medapp.domain.model.sync

import com.kert0n.medapp.domain.model.pack.PackageSharedFacts
import kotlin.uuid.Uuid

/**
 * Изменить описание препарата на сервере.
 *
 * Хранит **и исходное, и желаемое** состояние: неизменённые поля не отправляются, очистка текста
 * становится `""`, а ограничение очистки формы не теряется (PLAN E2, D3). Одного «желаемого» не
 * хватило бы — по нему нельзя отличить «поле не трогали» от «поле очистили».
 *
 * Правку, не выходящую за границу публикации, отправлять незачем: `before == after` — это
 * намерение, которому нечего делать на проводе.
 */
data class DescribePackageIntent(
    val packageId: Uuid,
    val before: PackageSharedFacts,
    val after: PackageSharedFacts
) : SyncIntent {
    init {
        require(before != after) { "описание не изменилось: отправлять нечего" }
    }
}
