package com.kert0n.medapp.queue

import com.kert0n.medapp.network.pack.PackageSnapshotNetworkDTO
import com.kert0n.medapp.network.server.ApiResult
import kotlin.uuid.Uuid

/**
 * Что очереди нужно от сервера: отправить замороженный запрос как есть и прочитать снимок пачки.
 * Работнику всё равно, что он отправляет, — у него [PreparedRequest]; тело ответа он читает
 * сам, потому что знает, какая команда его ждала.
 */
interface QueueTransport {

    /** Тело успешного ответа; `null` — ответ без тела (204 или пачка уничтожена). */
    suspend fun send(request: PreparedRequest): ApiResult<String?>

    suspend fun packageSnapshot(packageId: Uuid): ApiResult<PackageSnapshotNetworkDTO>
}
