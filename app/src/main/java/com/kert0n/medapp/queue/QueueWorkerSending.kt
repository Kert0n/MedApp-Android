package com.kert0n.medapp.queue

import com.kert0n.medapp.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Отправка очереди работником: проход начинается сразу после изменения и живёт своей жизнью.
 *
 * Ждать его незачем: очередь долговечна, исполнитель один, и второй просьбе ждать тоже нечего —
 * она застанет очередь либо занятой, либо уже пустой (PLAN E2, E4). Область жизни — приложение,
 * а не экран: человек закрыл экран, а серверу узнать всё равно надо.
 */
@Singleton
class QueueWorkerSending @Inject constructor(
    private val worker: QueueWorker,
    @ApplicationScope private val scope: CoroutineScope
) : QueueSending {

    override fun soon() {
        scope.launch { worker.drain() }
    }
}
