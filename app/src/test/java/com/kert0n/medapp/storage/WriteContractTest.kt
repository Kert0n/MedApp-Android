package com.kert0n.medapp.storage

import com.kert0n.medapp.queue.medkit.PublicationStorage
import com.kert0n.medapp.storage.course.CourseStorageRepository
import com.kert0n.medapp.storage.intake.IntakeStorageRepository
import com.kert0n.medapp.storage.medkit.MedKitStorageRepository
import com.kert0n.medapp.storage.pack.PackageStorageRepository
import com.kert0n.medapp.storage.stock.StockMovementStorageRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Хранению передаётся **действие**, а не прочитанный экземпляр (PLAN F5). Правило проект знает
 * давно — `CourseDraft.Activation`, `CourseCompletion.Closing`, `IntakeOutcome`,
 * `PackageAdjustment`, — и KDoc `IntakeOutcome` формулирует его прямо: «Готового нового состояния
 * пачки сюда не передают: посчитанное по прочитанному когда-то раньше, оно легло бы поверх
 * нынешнего».
 *
 * Нарушали его дважды, и оба раза молча: `discard(packageId)` брал идентификатор и ничего больше,
 * поэтому спутники конца коробки собирались вызывающими вразнобой, а `published(medKit)` писал
 * аптечку, прочитанную **до сети**, и терял сделанное человеком, пока сеть шла. Ни то ни другое
 * не видно в ревью без такой проверки — как не видно каскада, поставленного «на всякий случай»
 * (`ForeignKeysTest`).
 *
 * Поэтому каждый метод порта хранения назван здесь и назван его формой. Незнакомый метод —
 * падение: договор пополняется явно, а не молчаливым умолчанием.
 */
class WriteContractTest {

    /** Чем метод берёт то, что пишет, — и почему этого достаточно. */
    private enum class Shape {

        /** Спрашивает, не меняет. */
        READ,

        /** Значение-действие: всё, чего порознь не бывает, приходит одним типом. */
        ACTION,

        /** Заведение: спорить не с чем, вещи ещё не было. */
        CREATION,

        /** Сущность вместе с редакцией, из которой её правили: устаревшее не запишется. */
        GUARDED,

        /** Идентификатор и названные поля: прочитанный экземпляр не передаётся вовсе. */
        NAMED_FIELDS,

        /** Пришедшее с провода: истина по нему — сервер, и спорить с ним нечем. */
        SNAPSHOT,

        /**
         * Прочитанный экземпляр без токена редакции — **долг**, а не форма. Каждый такой метод
         * назван ниже вместе с причиной и сроком; новый добавить молча нельзя.
         */
        UNGUARDED
    }

    private val contract: Map<String, Shape> = mapOf(
        // Упаковка
        "PackageStorageRepository.observe" to Shape.READ,
        "PackageStorageRepository.find" to Shape.READ,
        "PackageStorageRepository.list" to Shape.READ,
        "PackageStorageRepository.pendingOf" to Shape.READ,
        "PackageStorageRepository.contentsOf" to Shape.READ,
        "PackageStorageRepository.observeSyncState" to Shape.READ,
        "PackageStorageRepository.add" to Shape.CREATION,
        "PackageStorageRepository.describe" to Shape.NAMED_FIELDS,
        "PackageStorageRepository.saveClaims" to Shape.NAMED_FIELDS,
        "PackageStorageRepository.end" to Shape.ACTION,
        "PackageStorageRepository.adjust" to Shape.ACTION,
        "PackageStorageRepository.applySnapshot" to Shape.SNAPSHOT,
        // Лечение
        "CourseStorageRepository.observeDrafts" to Shape.READ,
        "CourseStorageRepository.observePlan" to Shape.READ,
        "CourseStorageRepository.observeRecords" to Shape.READ,
        "CourseStorageRepository.observeRecord" to Shape.READ,
        "CourseStorageRepository.findDraft" to Shape.READ,
        "CourseStorageRepository.findPlan" to Shape.READ,
        "CourseStorageRepository.findRecord" to Shape.READ,
        "CourseStorageRepository.courseHolding" to Shape.READ,
        "CourseStorageRepository.rename" to Shape.NAMED_FIELDS,
        "CourseStorageRepository.setTotalDoses" to Shape.GUARDED,
        "CourseStorageRepository.updateSources" to Shape.GUARDED,
        "CourseStorageRepository.reallocate" to Shape.ACTION,
        "CourseStorageRepository.activate" to Shape.ACTION,
        "CourseStorageRepository.close" to Shape.ACTION,
        // Черновик — сам себе правка: человек держит его на экране целиком, и записывается он
        // целиком же, а начатое лечение поверх не затирается (проверка живёт в реализации).
        "CourseStorageRepository.saveDraft" to Shape.UNGUARDED,
        // Аптечка
        "MedKitStorageRepository.observeAll" to Shape.READ,
        "MedKitStorageRepository.observe" to Shape.READ,
        "MedKitStorageRepository.observeSyncedAt" to Shape.READ,
        "MedKitStorageRepository.find" to Shape.READ,
        "MedKitStorageRepository.delete" to Shape.NAMED_FIELDS,
        "MedKitStorageRepository.applyServerParticipants" to Shape.NAMED_FIELDS,
        // Долг: заведение и правка местных сведений одним методом. Пока у него нет ни одного
        // вызывающего в продукте; экран правки придёт в PR 7 и должен принести названные поля,
        // как `rename` у записи эпизода, — иначе он затрёт то, что сделал сосед.
        "MedKitStorageRepository.save" to Shape.UNGUARDED,
        // Публикация
        "PublicationStorage.medKit" to Shape.READ,
        "PublicationStorage.contentsOf" to Shape.READ,
        "PublicationStorage.publish" to Shape.SNAPSHOT,
        // Приём
        "IntakeStorageRepository.observeOfCourse" to Shape.READ,
        "IntakeStorageRepository.ofCourse" to Shape.READ,
        "IntakeStorageRepository.find" to Shape.READ,
        "IntakeStorageRepository.syncStateOf" to Shape.READ,
        "IntakeStorageRepository.plannedBefore" to Shape.READ,
        "IntakeStorageRepository.save" to Shape.ACTION,
        "IntakeStorageRepository.record" to Shape.ACTION,
        "IntakeStorageRepository.materialise" to Shape.CREATION,
        "IntakeStorageRepository.prunePlanned" to Shape.NAMED_FIELDS,
        // История остатка — только дописывается.
        "StockMovementStorageRepository.observeOfPackage" to Shape.READ,
        "StockMovementStorageRepository.ofPackage" to Shape.READ,
        "StockMovementStorageRepository.observedBetween" to Shape.READ,
        "StockMovementStorageRepository.record" to Shape.CREATION
    )

    /**
     * Долг назван поимённо: список закрыт, и новый метод, принимающий прочитанный экземпляр без
     * редакции, в него молча не попадёт — его придётся приписать сюда руками и объяснить.
     */
    private val known: Set<String> = setOf(
        "CourseStorageRepository.saveDraft",
        "MedKitStorageRepository.save"
    )

    private val ports = listOf(
        PackageStorageRepository::class.java,
        CourseStorageRepository::class.java,
        MedKitStorageRepository::class.java,
        IntakeStorageRepository::class.java,
        StockMovementStorageRepository::class.java,
        PublicationStorage::class.java
    )

    private fun methods(): Set<String> = ports.flatMapTo(LinkedHashSet()) { port ->
        port.declaredMethods
            .filterNot { it.isSynthetic || it.isBridge }
            // Kotlin дописывает к имени хеш, когда аргумент — `value class` (`Revision`):
            // договор называет метод так, как он записан в исходнике.
            .map { "${port.simpleName}.${it.name.substringBefore('-')}" }
    }

    @Test
    fun everyPortMethodNamesItsShape() {
        val found = methods()
        assertTrue("у портов не нашлось ни одного метода — читается не то", found.isNotEmpty())
        assertEquals("методы без договора: ${found - contract.keys}", emptySet<String>(), found - contract.keys)
        assertEquals("договор называет то, чего нет: ${contract.keys - found}", emptySet<String>(), contract.keys - found)
    }

    @Test
    fun onlyTheNamedDebtTakesAReadEntityWithoutARevision() {
        val unguarded = contract.filterValues { it == Shape.UNGUARDED }.keys
        assertEquals(
            "прочитанный экземпляр без редакции принимает метод, которого нет в списке долга",
            known,
            unguarded
        )
    }
}
