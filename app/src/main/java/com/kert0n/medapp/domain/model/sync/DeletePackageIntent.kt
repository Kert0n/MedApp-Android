package com.kert0n.medapp.domain.model.sync

import kotlin.uuid.Uuid

/**
 * Удалить упаковку на сервере.
 *
 * Локально пачка при этом **архивируется**, а не исчезает: строка остаётся, и приёмы с
 * движениями продолжают читаться по ней (PLAN D3). В проекции остатка это ноль и состояние
 * ожидающего архивирования (PLAN E1).
 */
data class DeletePackageIntent(val packageId: Uuid) : SyncIntent
