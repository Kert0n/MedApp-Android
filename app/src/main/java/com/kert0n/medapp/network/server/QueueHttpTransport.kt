package com.kert0n.medapp.network.server

import com.kert0n.medapp.network.pack.PackageSnapshotNetworkDTO
import com.kert0n.medapp.queue.PreparedRequest
import com.kert0n.medapp.queue.QueueTransport
import javax.inject.Inject
import kotlin.uuid.Uuid

/** Очередь ходит к серверу через тот же клиент, что и всё остальное: пропуск, лог, таймауты. */
class QueueHttpTransport @Inject constructor(private val api: MedAppApi) : QueueTransport {

    override suspend fun send(request: PreparedRequest): ApiResult<String?> = api.send(request)

    override suspend fun packageSnapshot(packageId: Uuid): ApiResult<PackageSnapshotNetworkDTO> =
        api.packageSnapshot(packageId)
}
