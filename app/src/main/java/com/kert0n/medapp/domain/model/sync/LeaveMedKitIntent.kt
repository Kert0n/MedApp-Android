package com.kert0n.medapp.domain.model.sync

import kotlin.uuid.Uuid

/**
 * Выйти из общей аптечки.
 *
 * Сервер снимает брони каскадом по участию, источники становятся недоступны, а **курс и его
 * история остаются** (PLAN D5, E6). Это не удаление аптечки: она цела, изменился наш доступ.
 */
data class LeaveMedKitIntent(val medKitId: Uuid) : SyncIntent
