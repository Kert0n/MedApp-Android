package com.kert0n.medapp.queue

import com.kert0n.medapp.domain.medkit.MedKit
import java.time.Instant
import javax.inject.Inject

/**
 * Держит пару «изменение и его команда»: изменение проходит через хранилище, команда встаёт в
 * очередь — одной транзакцией, которую служба открывает сама (PLAN F5). Репозитории про очередь
 * не знают, а сервер узнаёт обо всём, что случилось, когда есть связь.
 *
 * Местной аптечке команд не ставится: на сервере её нет, и везти туда нечего (PLAN E1). Это
 * единственное, что служба решает сама; что именно изменилось, ей всё равно.
 */
class QueueService @Inject constructor(
    private val storage: QueueStorage,
    private val sending: QueueSending
) {

    /**
     * [change] — запись изменения; `false` значит «писать было некуда», и команды тогда тоже не
     * ставятся: расход, которого не записали, серверу не везут.
     *
     * Поставленные команды уходят сразу: отправка просится **после коммита** и ответа не ждёт
     * (PLAN E4). Ждать её человеку незачем — изменение уже записано, и от сети его исход не
     * зависит; ставить это в обязанность каждому сценарию — значит однажды забыть.
     */
    suspend fun change(
        medKit: MedKit,
        commands: List<QueuedCommand>,
        at: Instant,
        change: suspend () -> Boolean
    ): Boolean {
        var queued = false
        val applied = storage.transaction {
            val applied = change()
            if (applied && medKit.publication == MedKit.Publication.PUBLISHED) {
                for (command in commands) storage.enqueue(command, at)
                queued = commands.isNotEmpty()
            }
            applied
        }
        if (queued) sending.soon()
        return applied
    }
}
