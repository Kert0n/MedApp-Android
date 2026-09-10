package com.kert0n.medapp.storage.server

import androidx.room.Embedded
import androidx.room.Relation
import com.kert0n.medapp.network.server.SyncOperation

/**
 * Операция очереди вместе со своими зависимостями.
 *
 * Команда может оказаться нечитаемой — чужая версия payload после обновления приложения или вид,
 * которого в этой сборке нет. Тогда операция не собирается, и вызывающий переводит её в
 * `CONFLICT`: очередь не роняется из-за одной строки (PLAN F4).
 */
class SyncOperationStorageRow(
    @Embedded val operation: SyncOperationStorageEntity,
    @Relation(parentColumn = "id", entityColumn = "operation_id")
    val dependencies: List<SyncOperationDependencyStorageEntity> = emptyList()
) {
    fun toDomainOrNull(): SyncOperation? {
        val command = SyncCommandStorageConverter.commandOf(
            kind = operation.kind,
            payload = operation.payload,
            payloadVersion = operation.payloadVersion
        ) ?: return null
        return SyncOperation(
            id = operation.id,
            command = command,
            sequence = operation.sequence,
            createdAt = operation.createdAt,
            payloadVersion = operation.payloadVersion,
            prepared = operation.prepared?.toDomain(),
            groupId = operation.groupId,
            dependsOn = dependencies.mapTo(LinkedHashSet()) { it.dependsOnId },
            status = operation.status,
            attempts = operation.attempts,
            lastError = operation.lastError,
            lastTriedAt = operation.lastTriedAt,
            reconciledBy = operation.reconciledBy
        )
    }
}
