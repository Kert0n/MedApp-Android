package com.kert0n.medapp.storage.server

import com.kert0n.medapp.network.server.SyncOperation
import com.kert0n.medapp.network.value.VocabularyMiss
import kotlin.uuid.Uuid

/**
 * Что дала попытка собрать операцию из строки очереди. Случая два, и делают с ними разное:
 * собранную операцию отправляют, а несобранную работник пропускает и называет — очередь не
 * роняется из-за одной строки, но и молча её не теряет (PLAN F4). Нечитаемость сама двух
 * видов: промах по словарю дочитывается и проходит, формат — нет.
 *
 * Тождество строки известно в обоих случаях: [id] хранится колонкой и разбора не требует, а
 * без него о повреждённой операции нечего было бы сказать.
 */
sealed interface StoredSyncOperation {

    val id: Uuid

    data class Readable(val operation: SyncOperation) : StoredSyncOperation {
        override val id: Uuid get() = operation.id
    }

    data class Unreadable(override val id: Uuid, val reason: Reason) : StoredSyncOperation

    /** Чем именно строка не читается — и лечится ли это чтением словаря. */
    sealed interface Reason {

        /** Для журнала и для экрана: причина словами. */
        val text: String

        /** Снимок словаря старее строки: дочитать словарь — и строка прочитается. */
        data class VocabularyStale(val miss: VocabularyMiss) : Reason {
            override val text: String get() = miss.message.orEmpty()
        }

        /** Чужая версия payload, неизвестный вид, повреждённые поля: чтением не лечится. */
        data class Format(override val text: String) : Reason
    }
}
