package com.kert0n.medapp.queue

import com.kert0n.medapp.network.pack.PackageSnapshotNetworkDTO
import com.kert0n.medapp.network.server.ApiResult
import com.kert0n.medapp.network.server.RawResponse
import kotlin.uuid.Uuid

/**
 * Что очереди нужно от сервера: отправить замороженный запрос как есть и получить ответ как
 * есть — статус и тело, — и прочитать снимок пачки. Ответ записывается до разбора: разбирает его
 * [Expected.read], чистой функцией, и потому его можно разобрать заново из записи.
 */
interface QueueTransport {

    suspend fun send(request: PreparedRequest): ApiResult<RawResponse>

    suspend fun packageSnapshot(packageId: Uuid): ApiResult<PackageSnapshotNetworkDTO>
}
