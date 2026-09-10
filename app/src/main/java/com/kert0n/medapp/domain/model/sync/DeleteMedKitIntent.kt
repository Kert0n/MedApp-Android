package com.kert0n.medapp.domain.model.sync

import kotlin.uuid.Uuid

/**
 * Удалить аптечку для всех участников.
 *
 * [transferTo] — куда уносится содержимое: физическую аптечку унесли, а лекарства разместили в
 * другой. `null` означает удаление вместе с содержимым (PLAN E6). Два разных действия названы
 * одним видом намерения потому, что на проводе это одна операция с параметром.
 */
data class DeleteMedKitIntent(val medKitId: Uuid, val transferTo: Uuid? = null) : SyncIntent {
    init {
        require(transferTo != medKitId) { "содержимое не переносится в удаляемую аптечку" }
    }
}
