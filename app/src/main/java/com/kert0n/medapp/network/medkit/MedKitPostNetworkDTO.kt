package com.kert0n.medapp.network.medkit

import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

/**
 * Публикация аптечки (`MedKitCreateRequest`). Сервер узнаёт о ней только идентификатор,
 * придуманный клиентом: название и место хранения остаются на устройстве (PLAN C0). Повтор с тем
 * же `id` даёт 409, а не вторую аптечку.
 */
@Serializable
data class MedKitPostNetworkDTO(val id: Uuid)
