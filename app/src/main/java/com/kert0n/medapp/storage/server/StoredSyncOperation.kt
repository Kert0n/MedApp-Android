package com.kert0n.medapp.storage.server

import com.kert0n.medapp.network.server.SyncOperation
import kotlin.uuid.Uuid

/**
 * Что дала попытка собрать операцию из строки очереди. Случая два, и делают с ними разное:
 * собранную операцию отправляют, а несобранную человек переводит в `CONFLICT` — очередь не
 * роняется из-за одной строки, но и молча её не теряет (PLAN F4).
 *
 * Тождество строки известно в обоих случаях: [id] хранится колонкой и разбора не требует, а
 * без него о повреждённой операции нечего было бы сказать.
 */
sealed interface StoredSyncOperation {

    val id: Uuid

    data class Readable(val operation: SyncOperation) : StoredSyncOperation {
        override val id: Uuid get() = operation.id
    }

    /** [reason] — для журнала и для экрана сверки: чем именно строка не читается. */
    data class Unreadable(override val id: Uuid, val reason: String) : StoredSyncOperation
}
