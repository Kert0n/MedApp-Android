package com.kert0n.medapp.domain.model.sync

import kotlin.uuid.Uuid

/** Перенести упаковку в другую аптечку. */
data class MovePackageIntent(val packageId: Uuid, val targetMedKitId: Uuid) : SyncIntent
