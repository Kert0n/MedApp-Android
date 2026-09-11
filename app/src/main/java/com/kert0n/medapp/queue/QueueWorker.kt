package com.kert0n.medapp.queue

import com.kert0n.medapp.queue.medkit.MedKitSyncCommand
import com.kert0n.medapp.network.pack.PackageSnapshotNetworkDTO
import com.kert0n.medapp.queue.pack.PackageSyncCommand
import com.kert0n.medapp.queue.pack.answersWithSnapshot
import com.kert0n.medapp.queue.pack.isSync
import com.kert0n.medapp.network.server.ApiFailure
import com.kert0n.medapp.network.server.ApiResult
import com.kert0n.medapp.network.server.medAppJson
import com.kert0n.medapp.network.value.VocabularyResolver
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlin.uuid.Uuid

/**
 * Работник очереди: берёт готовое, отправляет замороженным запросом, читает исход и отпускает.
 * Сервер — истина по количеству; устройство доставляет случившееся и читает истину обратно.
 * Повтор безопасен не журналом идемпотентности, а предусловием, замороженным при первой
 * отправке: устаревшее сервер отвергнет, отказ закрывает операцию, а истину даёт снимок (PLAN E3).
 *
 * Один проход — [drain]: пока есть связь, по одной операции в порядке очереди. Обрыв оставляет
 * операцию на повтор тем же запросом и останавливает проход; ограничение частоты соблюдает
 * `Retry-After`; строка, которую нечем прочитать, пропускается, а промах словаря дочитывается.
 * Задержка между повторами растёт с попытками — от двух секунд до пяти минут.
 */
class QueueWorker @Inject constructor(
    private val storage: QueueStorage,
    private val transport: QueueTransport,
    private val vocabulary: VocabularyResolver,
    private val clock: Clock
) {

    suspend fun drain(): Report = pass(vocabularyRefreshable = true)

    /**
     * Один проход по готовым операциям. Промах словаря дочитывается один раз, и проход начинается
     * заново уже без права на второе чтение: строка, которой не помог и свежий словарь, —
     * пропуск, а не бесконечный круг.
     */
    private suspend fun pass(vocabularyRefreshable: Boolean): Report {
        val now = clock.instant()
        var sent = 0
        val skipped = ArrayList<StoredSyncOperation.Unreadable>()
        var retryAt: Instant? = null
        val heldPackages = HashSet<Uuid>()
        for (entry in storage.ready()) {
            val operation = when (entry) {
                is StoredSyncOperation.Readable -> entry.operation
                is StoredSyncOperation.Unreadable -> {
                    if (entry.reason is StoredSyncOperation.Reason.VocabularyStale && vocabularyRefreshable &&
                        vocabulary.refresh() is ApiResult.Success
                    ) {
                        return pass(vocabularyRefreshable = false)
                    }
                    skipped += entry
                    continue
                }
            }
            val packageId = (operation.command as? PackageSyncCommand)?.packageId
            if (packageId != null && packageId in heldPackages) continue
            val notBefore = operation.notBefore()
            if (notBefore != null && notBefore.isAfter(now)) {
                retryAt = earliest(retryAt, notBefore)
                continue
            }
            val taken = storage.take(operation.id, now) ?: continue
            val request = checkNotNull(taken.prepared) { "взятая в отправку операция несёт запрос" }
            when (val step = deliver(taken, request)) {
                is Step.Settled -> {
                    storage.settle(taken.id, step.delivery, clock.instant())
                    if (step.delivery is Delivery.Retry) {
                        // Следующая команда той же пачки везёт предусловие, которое эта ещё не
                        // сдвинула: в этом проходе пачка дальше не трогается.
                        packageId?.let(heldPackages::add)
                        val wait = step.retryAfter ?: backoff(attempts = taken.attempts + 1)
                        retryAt = earliest(retryAt, clock.instant().plus(wait.toJavaDuration()))
                    } else {
                        sent++
                    }
                    if (step.stop) return Report(sent, skipped, retryAt)
                }
                Step.Unauthorized -> return Report(sent, skipped, retryAt)
            }
        }
        return Report(sent, skipped, retryAt)
    }

    /** Отправка одной операции и чтение исхода; отказы сервера — исходы, а не сбои. */
    private suspend fun deliver(operation: SyncOperation, request: PreparedRequest): Step {
        val command = operation.command
        return when (val result = transport.send(request)) {
            is ApiResult.Success -> Step.Settled(done(command, result.value))
            is ApiResult.Failure -> when (val failure = result.failure) {
                ApiFailure.Conflict, ApiFailure.PreconditionFailed, ApiFailure.PreconditionRequired,
                is ApiFailure.Invalid ->
                    // Отказ по предусловию или уже применённый `sync`: истина на сервере, читаем её.
                    Step.Settled(refused(command, failure))
                ApiFailure.NotFound -> Step.Settled(Delivery.AccessLost)
                ApiFailure.Unauthorized, ApiFailure.RegistrationRefused -> Step.Unauthorized
                is ApiFailure.TooManyRequests ->
                    Step.Settled(Delivery.Retry("429"), retryAfter = failure.retryAfter, stop = true)
                ApiFailure.Unavailable -> Step.Settled(Delivery.Retry("связи нет"), stop = true)
                ApiFailure.OutcomeUnknown -> Step.Settled(Delivery.Retry("ответ потерян"))
                is ApiFailure.Protocol -> Step.Settled(Delivery.Retry(failure.reason))
            }
        }
    }

    /** Успех: снимок из ответа, где он есть, иначе — чтением следом; команде аптечки снимок не нужен. */
    private suspend fun done(command: SyncCommand, body: String?): Delivery = when (command) {
        is PackageSyncCommand ->
            if (command.answersWithSnapshot) Delivery.Done(body?.let(::decodeSnapshot))
            else if (command is PackageSyncCommand.Delete || (command is PackageSyncCommand.CorrectStock && command.actual.isZero)) Delivery.Done(null)
            else snapshotRead(command.packageId, refusal = null)
        is MedKitSyncCommand -> Delivery.Done(null)
        else -> command.unknownRoot()
    }

    /**
     * Отказ сервера закрывает операцию: повторять её нечем и незачем. Что теперь правда, говорит
     * снимок; для `sync` 409 — штатный исход «уже применено» (PLAN B4).
     */
    private suspend fun refused(command: SyncCommand, failure: ApiFailure): Delivery = when (command) {
        is PackageSyncCommand -> {
            val refusal = if (command.isSync && failure == ApiFailure.Conflict) null else failure.toString()
            snapshotRead(command.packageId, refusal)
        }
        else -> Delivery.Done(null, refusal = failure.toString())
    }

    private suspend fun snapshotRead(packageId: Uuid, refusal: String?): Delivery =
        when (val read = transport.packageSnapshot(packageId)) {
            is ApiResult.Success -> Delivery.Done(read.value, refusal)
            is ApiResult.Failure -> when (read.failure) {
                ApiFailure.NotFound -> Delivery.AccessLost
                else -> Delivery.Retry("снимок не прочитан: ${read.failure}")
            }
        }

    private fun decodeSnapshot(body: String): PackageSnapshotNetworkDTO =
        medAppJson.decodeFromString(PackageSnapshotNetworkDTO.serializer(), body)

    /** Не раньше чем: последняя попытка плюс задержка по их числу; первая попытка — сразу. */
    private fun SyncOperation.notBefore(): Instant? =
        lastTriedAt?.takeIf { attempts > 0 }?.plus(backoff(attempts).toJavaDuration())

    /** Две секунды после первой неудачи, удвоение с каждой следующей, не дольше пяти минут. */
    private fun backoff(attempts: Int): Duration =
        (INITIAL_BACKOFF * (1 shl minOf(attempts - 1, MAX_BACKOFF_STEPS).coerceAtLeast(0))).coerceAtMost(MAX_BACKOFF)

    private fun earliest(a: Instant?, b: Instant): Instant = if (a == null || b.isBefore(a)) b else a

    private sealed interface Step {
        data class Settled(val delivery: Delivery, val retryAfter: Duration? = null, val stop: Boolean = false) : Step
        data object Unauthorized : Step
    }

    /**
     * Что сделал проход: сколько операций закрыто, какие строки пропущены как нечитаемые и когда
     * приходить снова — `null`, если ждать нечего.
     */
    data class Report(
        val settled: Int,
        val skipped: List<StoredSyncOperation.Unreadable>,
        val retryAt: Instant?
    )

    private companion object {
        val INITIAL_BACKOFF: Duration = 2.seconds
        val MAX_BACKOFF: Duration = 5.minutes
        const val MAX_BACKOFF_STEPS = 8
    }
}
