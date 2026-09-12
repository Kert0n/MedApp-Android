package com.kert0n.medapp.feature.packages

import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.queue.QueueService
import com.kert0n.medapp.queue.QueuedCommand
import com.kert0n.medapp.queue.Transactions
import com.kert0n.medapp.queue.pack.PackageSyncCommand
import com.kert0n.medapp.storage.course.CourseStorageRepository
import com.kert0n.medapp.storage.pack.PackageStorageRepository
import com.kert0n.medapp.storage.stock.StockMovementStorageRepository
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlin.uuid.Uuid

/**
 * Человек выбросил коробку (ТЗ 4.1.1.3). Коробки больше нет, а история — приёмы и движения —
 * держится за запись о ней и остаётся (PLAN D3, D6, D7). Здесь встречаются пачка и курс, и
 * здесь живёт правило **источник не переживает коробку**: курс, державший её, теряет источник
 * доменным переходом, а не молча каскадом схемы.
 *
 * Местная коробка уходит сразу. Общая — командой `Delete`: её готовят по версии живой строки,
 * поэтому строка живёт до ответа сервера, а проекция остатка уже показывает ноль (PLAN E1);
 * «пачки нет» в ответе уносит строку. Брони на сервере уносит он сам.
 */
class PackageRemoval @Inject constructor(
    private val packages: PackageStorageRepository,
    private val courses: CourseStorageRepository,
    private val movements: StockMovementStorageRepository,
    private val queue: QueueService,
    private val transactions: Transactions,
    private val clock: Clock
) {

    suspend fun remove(packageId: Uuid): Outcome = transactions.run {
        val pkg = packages.find(packageId) ?: return@run Outcome.GONE
        val now = clock.instant()
        release(pkg, now)
        if (pkg.medKit.answersToServer) {
            val delete = QueuedCommand(Uuid.random(), PackageSyncCommand.Delete(pkg.id))
            queue.change(pkg.medKit, listOf(delete), now) { true }
        } else {
            packages.discard(pkg.id)
        }
        Outcome.REMOVED
    }

    /**
     * Шаг внутри чужой транзакции — аптечку разбирают по коробкам: курс теряет источник, строка
     * уходит сразу. Серверу о содержимом общей аптечки говорит одна команда аптечки, поэтому
     * команды пачки здесь не ставятся (PLAN E6).
     */
    internal suspend fun discard(pkg: Package, at: Instant) {
        release(pkg, at)
        packages.discard(pkg.id)
    }

    /**
     * Шаг внутри чужой транзакции — доступ к коробке утрачен, из аптечки вышли: последний виденный
     * остаток уходит в историю, курс теряет источник, строка уходит (PLAN D7, E6).
     */
    internal suspend fun lose(pkg: Package, at: Instant) {
        movements.record(pkg.lost(Uuid.random(), at))
        discard(pkg, at)
    }

    /** Источник не переживает коробку: курс теряет её своим переходом, с ростом редакции. */
    private suspend fun release(pkg: Package, at: Instant) {
        val courseId = courses.courseHolding(pkg.id) ?: return
        val course = courses.findPlan(courseId) ?: return
        check(courses.updateSources(course.detach(pkg.ref, at), course.revision)) {
            "курс прочитан этой же транзакцией"
        }
    }

    /** Убрали — экран уходит с карточки; коробки и так нет — закрывает молча. */
    enum class Outcome { REMOVED, GONE }
}
