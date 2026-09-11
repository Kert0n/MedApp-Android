package com.kert0n.medapp.queue

import com.kert0n.medapp.network.pack.PackageSnapshotNetworkDTO
import com.kert0n.medapp.network.server.ApiResult
import com.kert0n.medapp.network.server.MedAppApi
import javax.inject.Inject
import kotlin.uuid.Uuid

/**
 * Очередь ходит к серверу через тот же клиент, что и всё остальное: пропуск, лог, таймауты.
 * Адаптер лежит в очереди: он знает её запрос, а сеть про очередь не знает (PLAN H1).
 */
class QueueHttpTransport @Inject constructor(private val api: MedAppApi) : QueueTransport {

    override suspend fun send(request: PreparedRequest): ApiResult<String?> =
        when (val sent = api.send(request.method, request.path, request.query, request.body)) {
            is ApiResult.Success -> ApiResult.Success(sent.value.body.takeIf { it.isNotEmpty() })
            is ApiResult.Failure -> sent
        }

    override suspend fun packageSnapshot(packageId: Uuid): ApiResult<PackageSnapshotNetworkDTO> =
        api.packageSnapshot(packageId)
}
