package com.kert0n.medapp.queue

import com.kert0n.medapp.network.pack.PackageSnapshotNetworkDTO
import com.kert0n.medapp.network.pack.requireKnownIn
import com.kert0n.medapp.network.server.ApiFailure
import com.kert0n.medapp.network.server.ApiResult
import com.kert0n.medapp.network.value.VocabularyResolver
import com.kert0n.medapp.queue.medkit.MedKitSyncCommand
import com.kert0n.medapp.queue.pack.PackageSyncCommand
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

    /**
     * Проход повторяется, пока предыдущий переподготовил хоть одну операцию: она снова ждёт и
     * должна уйти сейчас, а не при следующем вызове. Предел повторов — защита от сервера,
     * отвергающего свежую версию раз за разом: дальше операция ждёт по обычной задержке.
     */
    suspend fun drain(): Report {
        var report = pass(vocabularyRefreshable = true)
        var rounds = 1
        while (report.reprepared > 0 && rounds < MAX_REPREPARE_ROUNDS) {
            report = report + pass(vocabularyRefreshable = false)
            rounds++
        }
        return report
    }

    /**
     * Один проход по готовым операциям. Промах словаря дочитывается один раз, и проход начинается
     * заново уже без права на второе чтение: строка, которой не помог и свежий словарь, —
     * пропуск, а не бесконечный круг.
     */
    private suspend fun pass(vocabularyRefreshable: Boolean): Report {
        val now = clock.instant()
        var sent = 0
        var reprepared = 0
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
                        if (read.stop) return Report(sent, skipped, retryAt, reprepared)
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
                    when (step.delivery) {
                        is Delivery.Retry -> {
                            // Следующая команда той же пачки везёт предусловие, которое эта ещё
                            // не сдвинула: в этом проходе пачка дальше не трогается.
                            packageId?.let(heldPackages::add)
                            val wait = step.retryAfter ?: backoff(attempts = taken.attempts + 1)
                            retryAt = earliest(retryAt, clock.instant().plus(wait.toJavaDuration()))
                        }
                        is Delivery.Stale -> {
                            // Операция снова первая по своей пачке и уйдёт следующим проходом;
                            // остальные её команды ждут её, как ждали.
                            packageId?.let(heldPackages::add)
                            reprepared++
                        }
                        is Delivery.Applied, is Delivery.Refused, Delivery.AccessLost -> sent++
                    }
                    if (step.stop) return Report(sent, skipped, retryAt, reprepared)
                }
                Step.Unauthorized -> return Report(sent, skipped, retryAt, reprepared)
            }
        }
        return Report(sent, skipped, retryAt, reprepared)
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
                ApiFailure.Conflict, ApiFailure.PreconditionFailed ->
                    // Версия устарела: сервер отверг запрос до применения. Что делать дальше,
                    // знает команда; истина в любом случае читается.
                    Step.Settled(stale(command, request))
                ApiFailure.PreconditionRequired, is ApiFailure.Invalid ->
                    Step.Settled(refused(command, RefusalReason.INVALID))
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
            is QueueAnswer.Snapshot -> Delivery.Applied(PackageState.Present(answer.snapshot))
            QueueAnswer.Gone -> Delivery.Applied(PackageState.Gone)
            is QueueAnswer.Claim, QueueAnswer.Nothing ->
                if (command is PackageSyncCommand.Delete || (command is PackageSyncCommand.CorrectStock && command.actual.isZero)) {
                    Delivery.Applied(PackageState.Gone)
                } else {
                    snapshotRead(command.packageId) { Delivery.Applied(PackageState.Present(it)) }
                }
        }
        is MedKitSyncCommand -> Delivery.Applied(PackageState.None)
        else -> command.unknownRoot()
    }

    /**
     * Версия устарела — сервер отверг запрос до применения, в журнал он не попал. Расход и бронь
     * готовятся заново по свежему состоянию под тем же номером; описание, пересчёт, перенос и
     * удаление перекрыты чужой правкой — отказ, человек смотрит заново (PLAN E3). У курсового
     * расхода прежде смотрится бронь: потерянный ответ, за которым пришёл отказ по версии,
     * оставляет след в `mine`, и тогда расход применён.
     */
    private suspend fun stale(command: SyncCommand, request: PreparedRequest): Delivery = when (command) {
        is PackageSyncCommand -> snapshotRead(command.packageId) { snapshot ->
            when {
                command is PackageSyncCommand.Consume && command.provenAppliedBy(snapshot, request) ->
                    Delivery.Applied(PackageState.Present(snapshot))
                command.onStale == StalePolicy.REPREPARE -> Delivery.Stale(snapshot)
                else -> Delivery.Refused(RefusalReason.STALE, PackageState.Present(snapshot))
            }
        }
        is MedKitSyncCommand -> Delivery.Refused(RefusalReason.STALE, PackageState.None)
        else -> command.unknownRoot()
    }

    /** Отказ по вводу закрывает операцию: повторять нечем и незачем. Что теперь правда, говорит снимок. */
    private suspend fun refused(command: SyncCommand, reason: RefusalReason): Delivery = when (command) {
        is PackageSyncCommand -> snapshotRead(command.packageId) { Delivery.Refused(reason, PackageState.Present(it)) }
        else -> Delivery.Refused(reason, PackageState.None)
    }

    /** Истина по пачке, прочитанная следом; пачки нет — доступа нет, что бы ни значил ответ до того. */
    private suspend fun snapshotRead(packageId: Uuid, then: (PackageSnapshotNetworkDTO) -> Delivery): Delivery =
        when (val read = transport.packageSnapshot(packageId)) {
            is ApiResult.Success -> then(read.value)
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
        val retryAt: Instant?,
        val reprepared: Int = 0
    ) {
        /** Итог нескольких проходов подряд: закрытое складывается, срок — ближайший из названных. */
        operator fun plus(next: Report): Report = Report(
            settled = settled + next.settled,
            skipped = skipped + next.skipped,
            retryAt = listOfNotNull(retryAt, next.retryAt).minOrNull(),
            reprepared = next.reprepared
        )
    }

    private companion object {
        const val MAX_REPREPARE_ROUNDS = 3
        val INITIAL_BACKOFF: Duration = 2.seconds
        val MAX_BACKOFF: Duration = 5.minutes
        const val MAX_BACKOFF_STEPS = 8
    }
}
