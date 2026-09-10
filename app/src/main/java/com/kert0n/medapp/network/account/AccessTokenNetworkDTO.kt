package com.kert0n.medapp.network.account

import kotlinx.serialization.Serializable

/**
 * Короткий пропуск (`TokenResponse`), выдаваемый по учётке. Живёт только в памяти процесса и в
 * `toString` не показывается (PLAN G2).
 *
 * Пустой пропуск отвергается на границе разбора: в заголовке он даёт `Bearer ` без значения, и
 * каждый запрос получал бы 401 там, где на деле сервер ответил не по контракту.
 */
@Serializable
data class AccessTokenNetworkDTO(val accessToken: String) {
    init {
        require(accessToken.isNotEmpty()) { "выдача пропуска называет сам пропуск" }
    }

    override fun toString(): String = "AccessTokenNetworkDTO(accessToken=***)"
}
