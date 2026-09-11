package com.kert0n.medapp.queue

import com.kert0n.medapp.network.pack.PackageSnapshotNetworkDTO
import com.kert0n.medapp.network.server.ApiResult
import kotlin.uuid.Uuid

/**
 * Что очереди нужно от сервера: отправить замороженный запрос как есть и прочитать ответ той
 * формы, которую ждала команда, — и прочитать снимок пачки. Форму проверяет транспорт: ответ не
 * по форме — сбой протокола, а не исключение из прохода и не «пустая пачка».
 */
interface QueueTransport {

    suspend fun send(request: PreparedRequest, expects: Expected): ApiResult<QueueAnswer>

    suspend fun packageSnapshot(packageId: Uuid): ApiResult<PackageSnapshotNetworkDTO>
}
