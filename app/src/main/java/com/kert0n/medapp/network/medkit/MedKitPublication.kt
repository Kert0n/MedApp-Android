package com.kert0n.medapp.network.medkit

import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.network.pack.PackageSnapshotNetworkDTO
import com.kert0n.medapp.network.pack.toPostNetworkDTO
import com.kert0n.medapp.network.server.ApiFailure
import com.kert0n.medapp.network.server.ApiResult
import com.kert0n.medapp.network.server.MedAppApi
import javax.inject.Inject

/**
 * Публикация аптечки — одно действие при связи: на сервере появляется аптечка **целиком**, со
 * всеми пачками, либо не появляется вовсе (PLAN E5). Половины не бывает: половина — это
 * состояние, в котором правка пачки уезжает к тому, чего не существует, а приглашённый видит
 * часть аптечки. Поэтому отказ на любом шаге откатывается `DELETE` уже созданной аптечки, а в
 * очередь публикация не ставится — без связи она просто не начинается.
 *
 * Что уезжает, решает [toPostNetworkDTO]: личные сведения на провод не попадают по типу (C0).
 * Ответы сервера отдаются как есть — в домен их собирает тот, у кого на руках словарь и аптечка.
 */
class MedKitPublication @Inject constructor(private val api: MedAppApi) {

    suspend fun publish(medKit: MedKit, packages: List<Package>): Outcome {
        require(packages.all { it.medKitId == medKit.id }) { "публикуются пачки этой аптечки" }
        val published = medKit.publish()
        when (val created = api.createMedKit(MedKitPostNetworkDTO(medKit.id))) {
            is ApiResult.Failure -> return Outcome.Refused(created.failure, rolledBack = true)
            is ApiResult.Success -> Unit
        }
        val snapshots = ArrayList<PackageSnapshotNetworkDTO>(packages.size)
        for (pkg in packages) {
            when (val result = api.createPackage(medKit.id, pkg.toPostNetworkDTO())) {
                is ApiResult.Success -> snapshots += result.value
                is ApiResult.Failure -> return Outcome.Refused(
                    failure = result.failure,
                    rolledBack = api.deleteMedKit(medKit.id) is ApiResult.Success
                )
            }
        }
        return Outcome.Published(published, snapshots)
    }

    /** Чем кончилась публикация: аптечка на сервере целиком — или её там нет. */
    sealed interface Outcome {

        data class Published(
            val medKit: MedKit,
            val packages: List<PackageSnapshotNetworkDTO>
        ) : Outcome

        /**
         * Отказ и откат. [rolledBack] = `false` — откат тоже не прошёл: на сервере осталась
         * аптечка, о которой устройство не знает, и повторная публикация ответит 409 по её
         * идентификатору; это не «половина», а повод показать человеку, что связи нет.
         */
        data class Refused(val failure: ApiFailure, val rolledBack: Boolean) : Outcome
    }
}
