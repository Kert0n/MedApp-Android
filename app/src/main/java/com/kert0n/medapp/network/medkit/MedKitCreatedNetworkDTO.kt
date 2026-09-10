package com.kert0n.medapp.network.medkit

import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

/** Ответ на публикацию аптечки (`MedKitCreatedDTO`): тот же идентификатор, что был отправлен. */
@Serializable
data class MedKitCreatedNetworkDTO(val id: Uuid)
