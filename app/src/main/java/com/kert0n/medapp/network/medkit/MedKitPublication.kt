package com.kert0n.medapp.network.medkit

import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.network.pack.PackageNetworkDTO
import com.kert0n.medapp.network.pack.PackagePatchNetworkDTO
import com.kert0n.medapp.network.pack.PackagePostNetworkDTO
import com.kert0n.medapp.network.pack.PackageSnapshotNetworkDTO
import com.kert0n.medapp.network.pack.toPostNetworkDTO
import com.kert0n.medapp.network.server.ApiFailure
import com.kert0n.medapp.network.server.ApiResult
import com.kert0n.medapp.network.server.MedAppApi
import java.math.BigDecimal
import javax.inject.Inject
import kotlin.uuid.Uuid

/**
 * Публикация аптечки — передача ответственности за её состояние серверу (PLAN E5). До
 * локального переключения в `PUBLISHED` местная аптечка приглашений не выдаёт, и у серверной
 * копии ровно один писатель — это устройство: публикуется **текущее** местное состояние, и что
 * человек делал между попытками, попадает на сервер той попыткой, которая дойдёт до конца.
 *
 * Тождество попытки — идентификаторы: аптечка на сервере уже есть (409, потерянный ответ) —
 * читается и досоздаётся недостающее; пачка уже есть — читается и правится до местной; серверная
 * пачка, которой местно больше нет, — удаляется. Обрыв не откатывается — повтор доводит;
 * откатывается только отказ (400, 403): после него на сервере либо целиком, либо ничего.
 *
 * Что уезжает, решает [toPostNetworkDTO]: личные сведения на провод не попадают по типу (C0).
 * Ответы сервера отдаются как есть — в домен их собирает тот, у кого на руках словарь и аптечка.
 */
class MedKitPublication @Inject constructor(private val api: MedAppApi) {

    suspend fun publish(medKit: MedKit, packages: List<Package>): Outcome {
        require(packages.all { it.medKit.id == medKit.id }) { "публикуются пачки этой аптечки" }
        // Публикуется местная аптечка: у общей писателей уже несколько, и «текущее местное
        // состояние» перестало быть истиной. Переключает её хранение, здесь только провод (E5).
        check(!medKit.answersToServer) { "аптечка уже на сервере" }
        val onServer: Map<Uuid, PackageSnapshotNetworkDTO> = when (val created = api.createMedKit(MedKitPostNetworkDTO(medKit.id))) {
            is ApiResult.Success -> emptyMap()
            is ApiResult.Failure -> when (created.failure) {
                // Уже есть — наша незавершённая попытка; ответ потерян — возможно, тоже. Читаем.
                ApiFailure.Conflict, ApiFailure.OutcomeUnknown -> when (val read = api.medKit(medKit.id)) {
                    is ApiResult.Success -> read.value.packages.associateBy { it.pack.id }
                    // 404 — нашей аптечки там нет; иначе неизвестно, и повтор прочитает снова.
                    is ApiResult.Failure ->
                        return Outcome.Refused(created.failure, rolledBack = read.failure == ApiFailure.NotFound)
                }
                else -> return Outcome.Refused(created.failure, rolledBack = true)
            }
        }
        val snapshots = ArrayList<PackageSnapshotNetworkDTO>(packages.size)
        for (pkg in packages) {
            val known = onServer[pkg.id]
            val result = if (known == null) create(medKit.id, pkg) else reconcile(pkg, known)
            when (result) {
                is ApiResult.Success -> snapshots += result.value
                is ApiResult.Failure -> return refused(medKit.id, result.failure)
            }
        }
        // Что местно уже выброшено или перенесено, а серверу досталось прошлой попыткой.
        val local = packages.mapTo(HashSet(), Package::id)
        for (extra in onServer.values.filter { it.pack.id !in local }) {
            when (val deleted = api.deletePackage(extra.pack.id, extra.pack.version)) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure ->
                    if (deleted.failure != ApiFailure.NotFound) return refused(medKit.id, deleted.failure)
            }
        }
        return Outcome.Published(snapshots)
    }

    /** Завести пачку; уже есть — прочитать и довести до местной. */
    private suspend fun create(medKitId: Uuid, pkg: Package): ApiResult<PackageSnapshotNetworkDTO> =
        when (val created = api.createPackage(medKitId, pkg.toPostNetworkDTO())) {
            is ApiResult.Success -> created
            is ApiResult.Failure -> when (created.failure) {
                ApiFailure.Conflict, ApiFailure.OutcomeUnknown -> when (val read = api.packageSnapshot(pkg.id)) {
                    is ApiResult.Success -> reconcile(pkg, read.value)
                    is ApiResult.Failure -> if (read.failure == ApiFailure.NotFound) created else read
                }
                else -> created
            }
        }

    /** Серверная пачка прошлой попытки — до текущего местного состояния, по её версии. */
    private suspend fun reconcile(pkg: Package, known: PackageSnapshotNetworkDTO): ApiResult<PackageSnapshotNetworkDTO> {
        val patch = pkg.toPostNetworkDTO().differenceFrom(known.pack)?.copy(version = known.pack.version)
            ?: return ApiResult.Success(known)
        return api.patchPackage(pkg.id, patch)
    }

    /**
     * Отказ откатывается: аптечка удаляется целиком, и на сервере не остаётся половины. Не
     * прошёл и откат — так и говорится: повторная публикация доведёт либо её, либо откат.
     */
    private suspend fun refused(medKitId: Uuid, failure: ApiFailure): Outcome.Refused = when (failure) {
        is ApiFailure.Invalid, ApiFailure.RegistrationRefused, ApiFailure.PreconditionRequired ->
            Outcome.Refused(failure, rolledBack = api.deleteMedKit(medKitId) is ApiResult.Success)
        else -> Outcome.Refused(failure, rolledBack = false)
    }

    /**
     * Чем местная пачка отличается от серверной — как намерение PATCH: неизменённое не едет,
     * очищенный текст — `""`. `null` — ничем.
     */
    private fun PackagePostNetworkDTO.differenceFrom(known: PackageNetworkDTO): PackagePatchNetworkDTO? {
        val patch = PackagePatchNetworkDTO(
            name = name.takeIf { it != known.name },
            amount = amount.takeIf { BigDecimal(it).compareTo(BigDecimal(known.amount)) != 0 },
            unitId = unitId.takeIf { it != known.unitId },
            formId = formId?.takeIf { it != known.formId },
            category = cleared(known.category, category),
            manufacturer = cleared(known.manufacturer, manufacturer),
            country = cleared(known.country, country),
            description = cleared(known.description, description)
        )
        return patch.takeIf { !it.isEmpty }
    }

    private fun cleared(known: String?, local: String?): String? = when {
        local == known -> null
        local == null -> ""
        else -> local
    }

    /** Чем кончилась публикация: аптечка на сервере целиком — или её там нет. */
    sealed interface Outcome {

        data class Published(val packages: List<PackageSnapshotNetworkDTO>) : Outcome

        /**
         * Не опубликована. [rolledBack] = `true` — на сервере ничего нет; `false` — на сервере
         * осталось начатое (обрыв, отказ, за которым не прошёл откат), и повторная публикация
         * продолжит с того же места по идентификаторам.
         */
        data class Refused(val failure: ApiFailure, val rolledBack: Boolean) : Outcome
    }
}
