package com.kert0n.medapp.storage.server

import com.kert0n.medapp.queue.SyncCommand
import kotlin.uuid.Uuid

/**
 * Команда, которую нужно поставить в очередь в той же транзакции, что и её причина. Номер
 * здесь не называется: его выдаёт база при постановке (PLAN E2).
 */
class QueuedCommand(
    val id: Uuid,
    val command: SyncCommand,
    val groupId: Uuid? = null,
    dependsOn: Set<Uuid> = emptySet()
) {
    val dependsOn: Set<Uuid> = dependsOn.toSet()
}
