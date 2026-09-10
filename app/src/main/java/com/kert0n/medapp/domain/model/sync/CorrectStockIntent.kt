package com.kert0n.medapp.domain.model.sync

import com.kert0n.medapp.domain.model.value.Quantity
import kotlin.uuid.Uuid

/**
 * Пересчитали и увидели столько.
 *
 * **Абсолютное значение, а не дельта** (PLAN E1): проекция заменяет остаток, а не вычитает.
 * Ноль допустим и переводится в DELETE сетевым маппером — это его дело, а не смысл намерения.
 */
data class CorrectStockIntent(val packageId: Uuid, val actual: Quantity) : SyncIntent
