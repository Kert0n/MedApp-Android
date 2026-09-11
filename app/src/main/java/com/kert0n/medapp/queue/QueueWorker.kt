package com.kert0n.medapp.queue

import com.kert0n.medapp.network.pack.PackageSnapshotNetworkDTO
import com.kert0n.medapp.network.pack.requireKnownIn
import com.kert0n.medapp.network.server.ApiFailure
import com.kert0n.medapp.network.server.ApiResult
import com.kert0n.medapp.network.server.RawResponse
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
 * записывает ответ, применяет его и отпускает. Сервер — истина по количеству; устройство
 * доставляет случившееся поверх свежего состояния и читает истину обратно (PLAN E2, E3).
 * Поэтому проход по пачке начинается с чтения её снимка, а следующие операции той же пачки
 * готовятся по ответу предыдущей — он уже лёг в базу. Запрос, замороженный раньше, — повтор с
 * неизвестным исходом либо отправка, пережившая смерть процесса, — уходит как есть.
 *
 * Полученный ответ записывается до того, как применён: если применить его нечем — словарь не
 * знает единицы, снимок следом не прочитался, — операция ждёт с ответом в руках и закрывается
 * из него, не спрашивая сервер второй раз.
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
     * Проход: пока в базе есть готовая операция — берётся первая по номеру, и так до тех пор,
     * пока готовых не останется или связь не оборвётся. Готовность — одно определение, и живёт
     * оно в запросе ([QueueStorage.ready]): срок, зависимости, порядок по пачке. Поэтому
     * переподготовленная операция уходит тем же проходом, а зависимая — сразу за родителем.
     * Промах словаря дочитывается один раз; строка, которой не помог и свежий словарь, —
     * пропуск, а не бесконечный круг. Переподготовка одной операции — не больше трёх раз
     * подряд: дальше она ждёт по обычной задержке.
     */
    suspend fun drain(): Report {
        val drain = Drain()
        var vocabularyRefreshable = true
        while (true) {
            val entry = storage.ready(clock.instant()).firstOrNull { it.id !in drain.skippedIds } ?: break
            val operation = when (entry) {
                is StoredSyncOperation.Readable -> entry.operation
                is StoredSyncOperation.Unreadable -> {
                    if (entry.reason is StoredSyncOperation.Reason.VocabularyStale && vocabularyRefreshable) {
                        vocabularyRefreshable = false
                        if (vocabulary.refresh() is ApiResult.Success) continue
                    }
                    drain.skip(entry)
                    continue
                }
            }
            val packageId = (operation.command as? PackageSyncCommand)?.packageId
            val step = if (operation.status == SyncOperationStatus.ANSWERED) {
                resume(operation)
            } else {
                attempt(operation, packageId, drain)
            }
            if (drain.record(operation, packageId, step)) break
        }
        return drain.report()
    }

    /** Подготовка по свежему состоянию, отправка, запись ответа и его применение — одна операция. */
    private suspend fun attempt(operation: SyncOperation, packageId: Uuid?, pass: Drain): Step {
        val fresh = if (operation.prepared == null && packageId != null && packageId !in pass.freshPackages &&
            operation.command !is PackageSyncCommand.Create
        ) {
            when (val read = snapshotRead(packageId)) {
                is Read.Snapshot -> read.snapshot
                is Read.Failed -> return Step.Settled(read.delivery, stop = read.stop)
            }
        } else {
            null
        }
        val taken = when (val take = storage.take(operation.id, fresh, clock.instant())) {
            null -> return Step.Skipped
            is Take.Closed -> {
                packageId?.let(pass.freshPackages::add)
                return Step.Closed(take.delivery)
            }
            is Take.Sending -> take.operation
        }
        packageId?.let(pass.freshPackages::add)
        val request = checkNotNull(taken.prepared) { "взятая в отправку операция несёт запрос" }
        return when (val result = transport.send(request)) {
            is ApiResult.Success -> {
                // Ответ записан до применения: полученное подтверждение не теряется.
                storage.answered(taken.id, result.value, clock.instant())
                resolve(taken.command, result.value)
            }
            is ApiResult.Failure -> when (val failure = result.failure) {
                ApiFailure.Conflict, ApiFailure.PreconditionFailed ->
                    // Версия устарела либо объект уже есть: сервер отверг запрос до применения.
                    // Что делать дальше, знает команда; истина в любом случае читается.
                    Step.Settled(stale(taken.command, request))
                ApiFailure.PreconditionRequired -> Step.Settled(refused(taken.command, RefusalReason.INVALID))
                is ApiFailure.Invalid ->
                    Step.Settled(refused(taken.command, (taken.command as? PackageSyncCommand)?.onInvalid ?: RefusalReason.INVALID))
                ApiFailure.NotFound -> Step.Settled(notFound(taken.command))
                ApiFailure.Unauthorized, ApiFailure.RegistrationRefused -> Step.Unauthorized
                is ApiFailure.TooManyRequests ->
                    Step.Settled(Delivery.Retry("429"), retryAfter = failure.retryAfter, stop = true)
                ApiFailure.Unavailable -> Step.Settled(Delivery.Retry("связи нет"), stop = true)
                ApiFailure.OutcomeUnknown -> Step.Settled(Delivery.Retry("ответ потерян"))
                is ApiFailure.Protocol -> Step.Settled(Delivery.Retry(failure.reason))
            }
        }
    }

    /** Ответ уже записан — применить его; сервер о нём больше не спрашивают. */
    private suspend fun resume(operation: SyncOperation): Step =
        resolve(operation.command, checkNotNull(operation.answer) { "операция с ответом несёт его" })

    /**
     * Применение записанного ответа: разбор по форме, которую ждала команда, и истина по пачке —
     * из ответа либо чтением следом. Ответ не по форме — исход неизвестен, повтор тем же
     * запросом. Применить нечем — операция ждёт с ответом в руках.
     */
    private suspend fun resolve(command: SyncCommand, answer: RawResponse): Step {
        val read = when (val parsed = command.expects.read(answer)) {
            is ApiResult.Success -> parsed.value
            is ApiResult.Failure -> return Step.Settled(Delivery.Retry((parsed.failure as ApiFailure.Protocol).reason))
        }
        return when (command) {
            is PackageSyncCommand -> when (read) {
                is QueueAnswer.Snapshot -> known(read.snapshot) { Step.Settled(Delivery.Applied(PackageState.Present(it))) }
                QueueAnswer.Gone -> Step.Settled(Delivery.Applied(PackageState.Gone))
                is QueueAnswer.Claim, QueueAnswer.Nothing ->
                    if (command is PackageSyncCommand.Delete || (command is PackageSyncCommand.CorrectStock && command.actual.isZero)) {
                        Step.Settled(Delivery.Applied(PackageState.Gone))
                    } else {
                        when (val snapshot = snapshotRead(command.packageId)) {
                            is Read.Snapshot -> Step.Settled(Delivery.Applied(PackageState.Present(snapshot.snapshot)))
                            is Read.Failed -> when (snapshot.delivery) {
                                Delivery.AccessLost -> Step.Settled(Delivery.AccessLost)
                                else -> Step.Deferred((snapshot.delivery as Delivery.Retry).error, stop = snapshot.stop)
                            }
                        }
                    }
            }
            is MedKitSyncCommand -> Step.Settled(Delivery.Applied(PackageState.None))
            else -> command.unknownRoot()
        }
    }

    /** Снимок из ответа ложится в базу только словами, которые словарь знает: промах дочитывается. */
    private suspend fun known(snapshot: PackageSnapshotNetworkDTO, then: (PackageSnapshotNetworkDTO) -> Step): Step =
        when (val resolution = vocabulary.resolve { snapshot.requireKnownIn(it) }) {
            is VocabularyResolver.Resolution.Resolved -> then(snapshot)
            is VocabularyResolver.Resolution.Unresolved ->
                Step.Deferred("словарь не знает ${resolution.miss.message}", stop = resolution.failure != null)
        }

    /**
     * Версия устарела — сервер отверг запрос до применения, в журнал он не попал. Расход и бронь
     * готовятся заново по свежему состоянию под тем же номером; описание, пересчёт, перенос и
     * удаление перекрыты чужой правкой — отказ, человек смотрит заново (PLAN E3). У курсового
     * расхода прежде смотрится бронь: потерянный ответ, за которым пришёл отказ по версии,
     * оставляет след в `mine`, и тогда расход применён.
     */
    private suspend fun stale(command: SyncCommand, request: PreparedRequest): Delivery = when (command) {
        is PackageSyncCommand -> snapshotThen(command.packageId) { snapshot ->
            when {
                // 409 у создания — «уже есть»: пачка с нашим номером видна нам, значит наша.
                command is PackageSyncCommand.Create -> Delivery.Applied(PackageState.Present(snapshot))
                command is PackageSyncCommand.Consume && command.provenAppliedBy(snapshot, request) ->
                    Delivery.Applied(PackageState.Present(snapshot))
                command.onStale == StalePolicy.REPREPARE -> Delivery.Stale(snapshot)
                else -> Delivery.Refused(RefusalReason.STALE, PackageState.Present(snapshot))
            }
        }
        is MedKitSyncCommand -> Delivery.Refused(RefusalReason.STALE, PackageState.None)
        else -> command.unknownRoot()
    }

    /**
     * Отказ по вводу закрывает операцию: повторять нечем и незачем. Что теперь правда, говорит
     * снимок — кроме создания: пачки на сервере нет по построению, и читать нечего.
     */
    private suspend fun refused(command: SyncCommand, reason: RefusalReason): Delivery = when (command) {
        is PackageSyncCommand.Create -> Delivery.Refused(reason, PackageState.None)
        is PackageSyncCommand -> snapshotThen(command.packageId) { Delivery.Refused(reason, PackageState.Present(it)) }
        else -> Delivery.Refused(reason, PackageState.None)
    }

    /** 404 значит разное для разных команд (PLAN B4): что именно — говорит команда. */
    private suspend fun notFound(command: SyncCommand): Delivery = when (command) {
        is PackageSyncCommand -> when (command.onNotFound) {
            NotFoundPolicy.ACCESS_LOST -> Delivery.AccessLost
            NotFoundPolicy.REPREPARE -> snapshotThen(command.packageId) { Delivery.Stale(it) }
            NotFoundPolicy.APPLIED ->
                if (command is PackageSyncCommand.ReleaseClaim) snapshotThen(command.packageId) { Delivery.Applied(PackageState.Present(it)) }
                else Delivery.Applied(PackageState.Gone)
        }
        // Аптечки нет или мы не участник: удаление и выход тем самым исполнены (PLAN E3).
        is MedKitSyncCommand -> Delivery.Applied(PackageState.None)
        else -> command.unknownRoot()
    }

    /** Истина по пачке, прочитанная следом, — и исход по ней; не прочиталась — исход чтения. */
    private suspend fun snapshotThen(packageId: Uuid, then: (PackageSnapshotNetworkDTO) -> Delivery): Delivery =
        when (val read = snapshotRead(packageId)) {
            is Read.Snapshot -> then(read.snapshot)
            is Read.Failed -> read.delivery
        }

    /**
     * Что у сервера сейчас по этой пачке — словами, которые словарь знает: промах дочитывается.
     * Пачки нет — доступа к ней нет; связи нет — проход останавливается; иначе — повтор позже.
     */
    private suspend fun snapshotRead(packageId: Uuid): Read = when (val read = transport.packageSnapshot(packageId)) {
        is ApiResult.Success -> when (val resolution = vocabulary.resolve { read.value.requireKnownIn(it) }) {
            is VocabularyResolver.Resolution.Resolved -> Read.Snapshot(read.value)
            is VocabularyResolver.Resolution.Unresolved ->
                Read.Failed(Delivery.Retry("словарь не знает ${resolution.miss.message}"), stop = resolution.failure != null)
        }
        is ApiResult.Failure -> when (val failure = read.failure) {
            ApiFailure.NotFound -> Read.Failed(Delivery.AccessLost)
            ApiFailure.Unauthorized, ApiFailure.RegistrationRefused -> Read.Failed(Delivery.Retry("нет пропуска"), stop = true)
            is ApiFailure.TooManyRequests -> Read.Failed(Delivery.Retry("429"), stop = true)
            ApiFailure.Unavailable -> Read.Failed(Delivery.Retry("связи нет"), stop = true)
            else -> Read.Failed(Delivery.Retry("снимок не прочитан: $failure"))
        }
    }

    private sealed interface Read {
        data class Snapshot(val snapshot: PackageSnapshotNetworkDTO) : Read
        data class Failed(val delivery: Delivery, val stop: Boolean = false) : Read
    }

    /** Две секунды после первой неудачи, удвоение с каждой следующей, не дольше пяти минут. */
    private fun backoff(attempts: Int): Duration =
        (INITIAL_BACKOFF * (1 shl minOf(attempts - 1, MAX_BACKOFF_STEPS).coerceAtLeast(0))).coerceAtMost(MAX_BACKOFF)

    /** Чем кончился шаг по одной операции. */
    private sealed interface Step {
        /** Исход установлен и записывается хранилищем. */
        data class Settled(val delivery: Delivery, val retryAfter: Duration? = null, val stop: Boolean = false) : Step

        /** Подготовка закрыла операцию сама — хранилище уже записало исход. */
        data class Closed(val delivery: Delivery) : Step

        /** Ответ записан, применить его пока нечем: операция ждёт с ответом в руках. */
        data class Deferred(val reason: String, val stop: Boolean = false) : Step

        data object Unauthorized : Step

        data object Skipped : Step
    }

    /** Состояние одного прохода: что закрыто, что пропущено, какие пачки уже прочитаны. */
    private inner class Drain {
        private var settled = 0
        private val skipped = ArrayList<StoredSyncOperation.Unreadable>()
        private var retryAt: Instant? = null
        private val reprepared = HashMap<Uuid, Int>()
        val skippedIds = HashSet<Uuid>()

        /** Пачки, чьё серверное состояние в этом проходе уже лежит в базе. */
        val freshPackages = HashSet<Uuid>()

        fun skip(entry: StoredSyncOperation.Unreadable) {
            skipped += entry
            skippedIds += entry.id
        }

        private fun retryNotBefore(at: Instant) {
            retryAt = if (retryAt == null || at.isBefore(retryAt)) at else retryAt
        }

        private fun later(operation: SyncOperation, wait: Duration? = null): Instant =
            clock.instant().plus((wait ?: backoff(operation.attempts + 1)).toJavaDuration()).also(::retryNotBefore)

        /** Записывает шаг; `true` — проход надо остановить. */
        suspend fun record(operation: SyncOperation, packageId: Uuid?, step: Step): Boolean {
            when (step) {
                is Step.Settled -> {
                    val delivery = when (val delivery = step.delivery) {
                        // Срок повтора живёт в базе: следующий проход, процесс или второй
                        // `drain` его увидят, а операция раньше него готовой не будет.
                        is Delivery.Retry -> delivery.copy(notBefore = later(operation, step.retryAfter))
                        is Delivery.Stale -> {
                            val rounds = (reprepared[operation.id] ?: 0) + 1
                            reprepared[operation.id] = rounds
                            if (rounds >= MAX_REPREPARE_ROUNDS) delivery.copy(notBefore = later(operation)) else delivery
                        }
                        else -> delivery
                    }
                    storage.settle(operation.id, delivery, clock.instant())
                    if (delivery is Delivery.Applied || delivery is Delivery.Refused || delivery is Delivery.AccessLost) settled++
                    return step.stop
                }
                is Step.Closed -> {
                    settled++
                    return false
                }
                is Step.Deferred -> {
                    storage.defer(operation.id, step.reason, clock.instant(), notBefore = later(operation))
                    return step.stop
                }
                Step.Unauthorized -> return true
                Step.Skipped -> {
                    // Взять не удалось — кто-то закрыл или взял её между чтением и взятием.
                    skippedIds += operation.id
                    return false
                }
            }
        }

        fun report() = Report(settled, skipped, retryAt)
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
        /** Столько раз подряд одна операция переподготавливается сразу; дальше — по задержке. */
        const val MAX_REPREPARE_ROUNDS = 3
        val INITIAL_BACKOFF: Duration = 2.seconds
        val MAX_BACKOFF: Duration = 5.minutes
        const val MAX_BACKOFF_STEPS = 8
    }
}
