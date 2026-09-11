package com.kert0n.medapp.queue

import com.kert0n.medapp.network.pack.PackageSnapshotNetworkDTO
import com.kert0n.medapp.network.pack.requireKnownIn
import com.kert0n.medapp.network.server.ApiFailure
import com.kert0n.medapp.network.server.ApiResult
import com.kert0n.medapp.network.value.VocabularyResolver
import com.kert0n.medapp.queue.medkit.MedKitSyncCommand
import com.kert0n.medapp.queue.pack.PackageSyncCommand
import com.kert0n.medapp.queue.pack.isSync
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlin.uuid.Uuid

/**
 * Работник очереди: читает, что у сервера сейчас, готовит запрос по прочитанному, отправляет,
 * читает исход и отпускает. Сервер — истина по количеству; устройство доставляет случившееся
 * поверх свежего состояния и читает истину обратно (PLAN E2, E3). Поэтому проход по пачке
 * начинается с чтения её снимка, а следующие операции той же пачки готовятся по ответу
 * предыдущей — он уже лёг в базу. Запрос, замороженный раньше, — повтор с неизвестным исходом
 * либо отправка, пережившая смерть процесса, — уходит как есть: чтение перед ним ничего не
 * меняет и не делается.
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
        // Пачки, чьё серверное состояние в этом проходе уже лежит в базе: прочитано перед первой
        // подготовкой либо пришло ответом на предыдущую операцию.
        val freshPackages = HashSet<Uuid>()
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
            val fresh = if (operation.prepared == null && packageId != null && packageId !in freshPackages &&
                operation.command !is PackageSyncCommand.Create
            ) {
                when (val read = readFresh(packageId)) {
                    is Fresh.Read -> read.snapshot
                    is Fresh.Failed -> {
                        storage.settle(operation.id, read.delivery, clock.instant())
                        if (read.delivery is Delivery.Retry) {
                            heldPackages += packageId
                            retryAt = earliest(retryAt, clock.instant().plus(backoff(operation.attempts + 1).toJavaDuration()))
                        }
                        if (read.stop) return Report(sent, skipped, retryAt)
                        continue
                    }
                }
            } else {
                null
            }
            val taken = storage.take(operation.id, fresh, now) ?: continue
            packageId?.let(freshPackages::add)
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

    /**
     * Что у сервера сейчас по этой пачке — перед первой подготовкой в проходе. Снимок ложится в
     * базу только словами, которые словарь знает: промах дочитывается, а без связи операция ждёт.
     * Пачки нет — доступа к ней нет, и отправлять нечего.
     */
    private suspend fun readFresh(packageId: Uuid): Fresh = when (val read = transport.packageSnapshot(packageId)) {
        is ApiResult.Success -> when (val known = vocabulary.resolve { read.value.requireKnownIn(it) }) {
            is VocabularyResolver.Resolution.Resolved -> Fresh.Read(read.value)
            is VocabularyResolver.Resolution.Unresolved ->
                Fresh.Failed(Delivery.Retry("словарь не знает ${known.miss.message}"), stop = known.failure != null)
        }
        is ApiResult.Failure -> when (val failure = read.failure) {
            ApiFailure.NotFound -> Fresh.Failed(Delivery.AccessLost)
            ApiFailure.Unauthorized, ApiFailure.RegistrationRefused -> Fresh.Failed(Delivery.Retry("нет пропуска"), stop = true)
            is ApiFailure.TooManyRequests -> Fresh.Failed(Delivery.Retry("429"), stop = true)
            ApiFailure.Unavailable -> Fresh.Failed(Delivery.Retry("связи нет"), stop = true)
            else -> Fresh.Failed(Delivery.Retry("снимок не прочитан: $failure"))
        }
    }

    private sealed interface Fresh {
        data class Read(val snapshot: PackageSnapshotNetworkDTO) : Fresh
        data class Failed(val delivery: Delivery, val stop: Boolean = false) : Fresh
    }

    /** Отправка одной операции и чтение исхода; отказы сервера — исходы, а не сбои. */
    private suspend fun deliver(operation: SyncOperation, request: PreparedRequest): Step {
        val command = operation.command
        return when (val result = transport.send(request, command.expects)) {
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

    /**
     * Успех, прочитанный по форме, которую ждала команда: снимок ложится как есть, «пачки нет»
     * — как есть, а после брони и удаления без тела снимок читается следом; команде аптечки
     * состояние пачки не нужно.
     */
    private suspend fun done(command: SyncCommand, answer: QueueAnswer): Delivery = when (command) {
        is PackageSyncCommand -> when (answer) {
            is QueueAnswer.Snapshot -> Delivery.Done(PackageState.Present(answer.snapshot))
            QueueAnswer.Gone -> Delivery.Done(PackageState.Gone)
            is QueueAnswer.Claim, QueueAnswer.Nothing ->
                if (command is PackageSyncCommand.Delete || (command is PackageSyncCommand.CorrectStock && command.actual.isZero)) {
                    Delivery.Done(PackageState.Gone)
                } else {
                    snapshotRead(command.packageId, refusal = null)
                }
        }
        is MedKitSyncCommand -> Delivery.Done(PackageState.None)
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
        else -> Delivery.Done(PackageState.None, refusal = failure.toString())
    }

    private suspend fun snapshotRead(packageId: Uuid, refusal: String?): Delivery =
        when (val read = transport.packageSnapshot(packageId)) {
            is ApiResult.Success -> Delivery.Done(PackageState.Present(read.value), refusal)
            is ApiResult.Failure -> when (read.failure) {
                ApiFailure.NotFound -> Delivery.AccessLost
                else -> Delivery.Retry("снимок не прочитан: ${read.failure}")
            }
        }

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
