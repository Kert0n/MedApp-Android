package com.kert0n.medapp.network.value

import com.kert0n.medapp.domain.Unavailability
import com.kert0n.medapp.domain.value.Vocabulary
import com.kert0n.medapp.domain.value.VocabularyLibrary
import com.kert0n.medapp.network.account.asUnavailability
import com.kert0n.medapp.network.server.ApiResult
import javax.inject.Inject

/**
 * Словарь с этого сервера: выполняет доменный порт тем, что умеет сеть. Чтение и запись снимка
 * держит [VocabularyResolver]; здесь — перевод исхода на язык домена.
 */
class ServerVocabularyLibrary @Inject constructor(
    private val resolver: VocabularyResolver
) : VocabularyLibrary {

    override suspend fun known(): Vocabulary = resolver.snapshot()

    override suspend fun refresh(): Unavailability? = when (val read = resolver.refresh()) {
        is ApiResult.Success -> null
        is ApiResult.Failure -> read.failure.asUnavailability()
    }
}
