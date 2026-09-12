package com.kert0n.medapp.storage.database

import androidx.room.withTransaction
import com.kert0n.medapp.queue.Transactions
import javax.inject.Inject

/** Транзакция базы: Room умеет вкладывать её в уже открытую, поэтому вложенность безопасна. */
class RoomTransactions @Inject constructor(private val database: MedAppDatabase) : Transactions {

    override suspend fun <T> run(block: suspend () -> T): T = database.withTransaction { block() }
}
