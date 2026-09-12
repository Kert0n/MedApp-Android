package com.kert0n.medapp.queue

import com.kert0n.medapp.network.pack.PackageSnapshot
import com.kert0n.medapp.network.pack.PackageSnapshotNetworkDTO
import com.kert0n.medapp.network.pack.toDomain
import com.kert0n.medapp.network.value.VocabularyResolver
import java.time.Instant
import javax.inject.Inject

/**
 * Разрешает всё, что снимок пачки называет, — единицу, форму, аптечку — и собирает его в домен
 * одним исходом. Вопрос у всех ссылок один: «в снимке названо то, чего локально ещё нет», и
 * ответ на него один — ждать с названной причиной, не закрывая операцию (PLAN E3, E4). Словарь
 * дочитывается с сервера; аптечку дочитать нечем до полного снимка; снимок, нарушающий инварианты
 * домена, — данные сервера, а не ошибка программиста, и из прохода исключением не выходит.
 *
 * Хранение получает только [Resolution.Resolved] и ничего не разрешает само.
 */
class PackageSnapshotResolver @Inject constructor(
    private val vocabulary: VocabularyResolver,
    private val storage: QueueStorage
) {

    suspend fun resolve(snapshot: PackageSnapshotNetworkDTO, at: Instant): Resolution {
        val medKit = storage.medKit(snapshot.pack.medKitId)
            ?: return Resolution.Unresolved("аптечка ${snapshot.pack.medKitId} неизвестна", stop = false)
        val resolution = try {
            vocabulary.resolve { snapshot.toDomain(it, medKit, addedAt = at, observedAt = at) }
        } catch (invalid: IllegalArgumentException) {
            return Resolution.Unresolved("снимок вне контракта: ${invalid.message}", stop = false)
        }
        return when (resolution) {
            is VocabularyResolver.Resolution.Resolved -> Resolution.Resolved(resolution.value)
            is VocabularyResolver.Resolution.Unresolved ->
                Resolution.Unresolved(resolution.reason, stop = resolution.failure != null)
        }
    }

    /** Чем кончилось: снимок в домене — или что именно неизвестно и надо ли останавливать проход. */
    sealed interface Resolution {

        data class Resolved(val snapshot: PackageSnapshot) : Resolution

        /** [stop] — словарь не дочитался из-за связи: дальше в этом проходе идти незачем. */
        data class Unresolved(val reason: String, val stop: Boolean) : Resolution
    }
}
