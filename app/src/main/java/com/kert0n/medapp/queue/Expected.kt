package com.kert0n.medapp.queue

import com.kert0n.medapp.network.pack.ClaimNetworkDTO
import com.kert0n.medapp.network.pack.PackageSnapshotNetworkDTO
import com.kert0n.medapp.network.server.ApiFailure
import com.kert0n.medapp.network.server.ApiResult
import com.kert0n.medapp.network.server.RawResponse
import com.kert0n.medapp.network.server.medAppJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException

/**
 * Какой формы успешный ответ ждёт команда — по контракту той операции, в которую она
 * превращается (PLAN B4, B5). Форму проверяет [read]: ответ не по форме — не «пустая пачка» и
 * не исключение из прохода, а [ApiFailure.Protocol] — исход мог быть применён, и запрос
 * повторяется тем же. Разбор чистый: записанный ответ разбирается заново так же, как свежий.
 */
enum class Expected {

    /** Тело обязательно: снимок пачки. Пустое тело — сбой протокола. */
    SNAPSHOT,

    /** Снимок пачки либо ноль байтов — пачка израсходована и уничтожена (B5). */
    SNAPSHOT_OR_GONE,

    /** Бронь: `{drugId, amount}` без версии картины (B6) — снимок читается следом. */
    CLAIM,

    /** Тела не ждут: `204`, либо тело, которое команде не нужно. */
    NOTHING;

    fun read(response: RawResponse): ApiResult<QueueAnswer> = when (this) {
        SNAPSHOT ->
            if (response.body.isEmpty()) protocol("пустое тело там, где контракт обещает снимок")
            else decode(response.body, PackageSnapshotNetworkDTO.serializer()) { QueueAnswer.Snapshot(it) }
        SNAPSHOT_OR_GONE ->
            if (response.body.isEmpty()) ApiResult.Success(QueueAnswer.Gone)
            else decode(response.body, PackageSnapshotNetworkDTO.serializer()) { QueueAnswer.Snapshot(it) }
        CLAIM ->
            if (response.body.isEmpty()) protocol("пустое тело там, где контракт обещает бронь")
            else decode(response.body, ClaimNetworkDTO.serializer()) { QueueAnswer.Claim(it) }
        NOTHING -> ApiResult.Success(QueueAnswer.Nothing)
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
