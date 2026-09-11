package com.kert0n.medapp.queue

import com.kert0n.medapp.network.pack.ClaimNetworkDTO
import com.kert0n.medapp.network.pack.PackageSnapshotNetworkDTO
import com.kert0n.medapp.network.server.ApiFailure
import com.kert0n.medapp.network.server.ApiResult
import com.kert0n.medapp.network.server.MedAppApi
import com.kert0n.medapp.network.server.RawResponse
import com.kert0n.medapp.network.server.medAppJson
import javax.inject.Inject
import kotlin.uuid.Uuid
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException

/**
 * Очередь ходит к серверу через тот же клиент, что и всё остальное: пропуск, лог, таймауты.
 * Адаптер лежит в очереди: он знает её запрос и форму ожидаемого ответа, а сеть про очередь не
 * знает (PLAN H1). Ответ не по форме — [ApiFailure.Protocol]: исход мог быть применён, и
 * работник повторит тот же запрос, как после повреждённого ответа команды (B5).
 */
class QueueHttpTransport @Inject constructor(private val api: MedAppApi) : QueueTransport {

    override suspend fun send(request: PreparedRequest, expects: Expected): ApiResult<QueueAnswer> =
        when (val sent = api.send(request.method, request.path, request.query, request.body)) {
            is ApiResult.Success -> read(sent.value, expects)
            is ApiResult.Failure -> sent
        }

    override suspend fun packageSnapshot(packageId: Uuid): ApiResult<PackageSnapshotNetworkDTO> =
        api.packageSnapshot(packageId)

    private fun read(response: RawResponse, expects: Expected): ApiResult<QueueAnswer> = when (expects) {
        Expected.SNAPSHOT ->
            if (response.body.isEmpty()) protocol("пустое тело там, где контракт обещает снимок")
            else decode(response.body, PackageSnapshotNetworkDTO.serializer()) { QueueAnswer.Snapshot(it) }
        Expected.SNAPSHOT_OR_GONE ->
            if (response.body.isEmpty()) ApiResult.Success(QueueAnswer.Gone)
            else decode(response.body, PackageSnapshotNetworkDTO.serializer()) { QueueAnswer.Snapshot(it) }
        Expected.CLAIM ->
            if (response.body.isEmpty()) protocol("пустое тело там, где контракт обещает бронь")
            else decode(response.body, ClaimNetworkDTO.serializer()) { QueueAnswer.Claim(it) }
        Expected.NOTHING -> ApiResult.Success(QueueAnswer.Nothing)
    }

    private fun <T> decode(body: String, serializer: KSerializer<T>, answer: (T) -> QueueAnswer): ApiResult<QueueAnswer> =
        try {
            ApiResult.Success(answer(medAppJson.decodeFromString(serializer, body)))
        } catch (broken: SerializationException) {
            protocol("ответ не разбирается: ${broken.message}")
        } catch (broken: IllegalArgumentException) {
            protocol("ответ вне контракта: ${broken.message}")
        }

    private fun protocol(reason: String): ApiResult<QueueAnswer> = ApiResult.Failure(ApiFailure.Protocol(reason))
}
