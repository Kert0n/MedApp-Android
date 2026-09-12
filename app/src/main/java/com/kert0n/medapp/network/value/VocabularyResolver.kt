package com.kert0n.medapp.network.value

import com.kert0n.medapp.domain.value.Vocabulary
import com.kert0n.medapp.network.server.ApiFailure
import com.kert0n.medapp.network.server.ApiResult
import com.kert0n.medapp.network.server.MedAppApi
import javax.inject.Inject

/**
 * Владеет снимком словаря и его дочитыванием. Разбор, назвавший единицу или форму, которых в
 * снимке нет, не отвергается: словарь дочитывается с сервера, и разбор повторяется. Без связи
 * промах остаётся промахом с названной причиной — снимок старый, а не запись негодная — и
 * повторится при следующей связи.
 */
class VocabularyResolver @Inject constructor(
    private val store: VocabularyStore,
    private val api: MedAppApi
) {

    suspend fun snapshot(): Vocabulary = store.snapshot()

    /** Свежий словарь с сервера, записанный поверх снимка; отказ чтения снимка не трогает. */
    suspend fun refresh(): ApiResult<Vocabulary> {
        val units = when (val read = api.quantityUnits()) {
            is ApiResult.Failure -> return read
            is ApiResult.Success -> read.value.map { it.toQuantityUnit() }
        }
        val forms = when (val read = api.formTypes()) {
            is ApiResult.Failure -> return read
            is ApiResult.Success -> read.value.map { it.toDosageForm() }
        }
        store.save(units, forms)
        return ApiResult.Success(store.snapshot())
    }

    /**
     * Разбор по снимку с одним повтором: промах дочитывает словарь и повторяет [read] по свежему.
     * Второй промах после свежего словаря — не задержка, а запись, называющая то, чего сервер не
     * знает; она отдаётся как [Resolution.Unresolved] с той же причиной.
     */
    suspend fun <T> resolve(read: (Vocabulary) -> T): Resolution<T> {
        val miss = try {
            return Resolution.Resolved(read(store.snapshot()))
        } catch (missed: VocabularyMiss) {
            missed
        }
        val fresh = when (val refreshed = refresh()) {
            is ApiResult.Failure -> return Resolution.Unresolved(miss, refreshed.failure)
            is ApiResult.Success -> refreshed.value
        }
        return try {
            Resolution.Resolved(read(fresh))
        } catch (missed: VocabularyMiss) {
            Resolution.Unresolved(missed, failure = null)
        }
    }

    /** Чем кончился разбор: объект — или промах, который дочитать не удалось, и почему. */
    sealed interface Resolution<out T> {

        data class Resolved<T>(val value: T) : Resolution<T>

        /** [failure] — почему словарь не дочитался; `null` — дочитался, а записи в нём всё равно нет. */
        data class Unresolved(val miss: VocabularyMiss, val failure: ApiFailure?) : Resolution<Nothing> {

            /** Почему разбор отложен — словами для журнала операции. */
            val reason: String get() = "словарь не знает ${miss.message}"
        }
    }
}
