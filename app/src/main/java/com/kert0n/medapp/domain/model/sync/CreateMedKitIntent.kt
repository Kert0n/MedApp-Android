package com.kert0n.medapp.domain.model.sync

import kotlin.uuid.Uuid

/**
 * Опубликовать аптечку: на сервере появляется её существование и участие.
 *
 * Название и место хранения не уезжают — сервер их не хранит и не будет (PLAN C0).
 */
data class CreateMedKitIntent(val medKitId: Uuid) : SyncIntent
