package com.kert0n.medapp.queue.medkit

import com.kert0n.medapp.domain.Unavailability
import com.kert0n.medapp.network.account.asUnavailability
import com.kert0n.medapp.network.medkit.MedKitPublication
import com.kert0n.medapp.network.pack.PackageSnapshot
import com.kert0n.medapp.queue.PackageSnapshotResolver
import com.kert0n.medapp.queue.Transactions
import java.time.Clock
import javax.inject.Inject
import kotlin.uuid.Uuid

/**
 * Человек публикует аптечку — передаёт ответственность за её состояние серверу (PLAN E5). Одно
 * действие при связи, в очередь не ставится: читает аптечку с содержимым, ведёт публикацию
 * ([MedKitPublication] — она сама доводит начатое и откатывает отказ), разрешает ответы в домен
 * и пишет переключение вместе с первыми подтверждёнными остатками одной транзакцией.
 *
 * Сеть никогда не выполняется внутри транзакции (F5), поэтому транзакций две, и владелец обеих —
 * здесь: что пачки не менялись между ними, проверяет запись переключения.
 */
class MedKitPublishing @Inject constructor(
    private val storage: PublicationStorage,
    private val publication: MedKitPublication,
    private val resolver: PackageSnapshotResolver,
    private val transactions: Transactions,
    private val clock: Clock
) {

    suspend fun publish(medKitId: Uuid): Outcome {
        val read = transactions.run { storage.medKit(medKitId)?.let { it to storage.contentsOf(medKitId) } }
            ?: return Outcome.MedKitGone
        val (medKit, contents) = read
        if (medKit.answersToServer) return Outcome.AlreadyPublished
        return when (val result = publication.publish(medKit, contents)) {
            is MedKitPublication.Outcome.Refused ->
                Outcome.Refused(result.failure.asUnavailability(), result.rolledBack)
            is MedKitPublication.Outcome.Published -> {
                val at = clock.instant()
                val snapshots = ArrayList<PackageSnapshot>(result.packages.size)
                for (dto in result.packages) {
                    when (val resolution = resolver.resolve(dto, at)) {
                        is PackageSnapshotResolver.Resolution.Resolved -> snapshots += resolution.snapshot
                        // Аптечка на сервере уже есть; не прочитался словарь — повтор дочитает и
                        // доведёт публикацию по идентификаторам.
                        is PackageSnapshotResolver.Resolution.Unresolved -> return Outcome.Refused(
                            if (resolution.stop) Unavailability.NO_CONNECTION else Unavailability.SERVER_SILENT,
                            rolledBack = false
                        )
                    }
                }
                if (storage.published(result.medKit, snapshots, at)) Outcome.Published else Outcome.ChangedMeanwhile
            }
        }
    }

    /**
     * Чем кончилось. Опубликована; аптечки нет; уже на сервере; пачки менялись, пока шла
     * публикация, — повторить; сервер недоступен или отказал — [Refused.rolledBack] говорит,
     * осталось ли там начатое, которое повтор доведёт.
     */
    sealed interface Outcome {
        data object Published : Outcome
        data object MedKitGone : Outcome
        data object AlreadyPublished : Outcome
        data object ChangedMeanwhile : Outcome
        data class Refused(val reason: Unavailability, val rolledBack: Boolean) : Outcome
    }
}
