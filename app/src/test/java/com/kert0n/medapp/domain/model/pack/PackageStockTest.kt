package com.kert0n.medapp.domain.model.pack

import com.kert0n.medapp.domain.model.course.CourseBrief
import com.kert0n.medapp.domain.model.sync.ConsumeIntent
import com.kert0n.medapp.domain.model.sync.CorrectStockIntent
import com.kert0n.medapp.domain.model.sync.DeletePackageIntent
import com.kert0n.medapp.domain.model.sync.ReconcileStockIntent
import com.kert0n.medapp.domain.model.sync.ReleaseClaimIntent
import com.kert0n.medapp.domain.model.sync.SyncIntent
import com.kert0n.medapp.fixture.COURSE
import com.kert0n.medapp.fixture.INTAKE
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.millilitres
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.tablets
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Три с половиной величины «сколько доступно» и проекция очереди на остаток (PLAN D4, E1). */
class PackageStockTest {

    private val today: LocalDate = LocalDate.of(2027, 3, 1)

    private fun stock(
        quantity: String = "20",
        claims: Claims? = null,
        allocation: String = "0",
        course: CourseBrief? = null,
        pending: List<SyncIntent> = emptyList(),
        unresolved: List<Uuid> = emptyList(),
        expiresOn: LocalDate? = null
    ) = PackageStock(
        pkg = pack(quantity = tablets(quantity), claims = claims, expiresOn = expiresOn),
        today = today,
        pendingIntents = pending,
        unresolvedOperationIds = unresolved,
        myAllocation = tablets(allocation),
        course = course
    )

    @Test
    fun numbersFromPlanAreReproduced() {
        // Пример PLAN D4: на сервере 20, моя бронь 10, чужого 5, локально принято 3 и выделение
        // уменьшилось до 7.
        val projected = stock(
            quantity = "20",
            claims = Claims(BigDecimal("15"), BigDecimal("10")),
            allocation = "7",
            course = CourseBrief(COURSE, "Курс", 7),
            pending = listOf(ConsumeIntent(PACK, tablets("3"), INTAKE, claimAfter = tablets("7")))
        )
        assertEquals(tablets("17"), projected.effective)
        assertEquals(tablets("5"), projected.reservedByOthers)
        assertEquals(tablets("5"), projected.freeForAnyone)
        assertEquals(tablets("12"), projected.availableToMe)
    }

    @Test
    fun unpublishedKitStillHasAllocations() {
        // Броней сервера нет, но из двадцати таблеток пятнадцать отданы курсу: свободно пять.
        val local = stock(quantity = "20", allocation = "15", course = CourseBrief(COURSE, "Курс", 5))
        assertEquals(tablets("0"), local.reservedByOthers)
        assertEquals(tablets("5"), local.freeForAnyone)
        assertEquals(tablets("20"), local.availableToMe)
    }

    @Test
    fun consumptionIsSubtractedOnce() {
        val once = stock(pending = listOf(ConsumeIntent(PACK, tablets("3"), INTAKE)))
        assertEquals(tablets("17"), once.effective)
        assertEquals(StockViewState.Known(tablets("17"), pending = true), once.stock)
    }

    @Test
    fun correctionReplacesTheValueInsteadOfSubtracting() {
        // Пересчёт — это не дельта: он может оказаться и больше прежнего.
        val corrected = stock(
            pending = listOf(
                ConsumeIntent(PACK, tablets("3"), INTAKE),
                CorrectStockIntent(PACK, tablets("30"))
            )
        )
        assertEquals(tablets("30"), corrected.effective)
    }

    @Test
    fun reconciliationNamesTheStockAndLaterIntentsApplyOnTop() {
        val reconciled = stock(
            pending = listOf(
                ConsumeIntent(PACK, tablets("3"), INTAKE),
                ReconcileStockIntent(PACK, tablets("12"), throughSequence = 5),
                ConsumeIntent(PACK, tablets("2"), INTAKE)
            )
        )
        assertEquals(tablets("10"), reconciled.effective)
    }

    @Test
    fun deletionProjectsZero() {
        assertEquals(tablets("0"), stock(pending = listOf(DeletePackageIntent(PACK))).effective)
    }

    @Test
    fun negativeProjectionIsShownAsZeroAndNotAsASuccessfulConsumption() {
        // Ошибку не превращаем в успешный расход: конфликт разбирается по состоянию очереди.
        val over = stock(quantity = "2", pending = listOf(ConsumeIntent(PACK, tablets("5"), INTAKE)))
        assertEquals(tablets("0"), over.effective)
    }

    @Test
    fun unresolvedOperationOverridesTheWholeProjection() {
        // Пока неизвестно, включён ли расход в серверный остаток, любое число было бы догадкой.
        val unsure = stock(
            pending = listOf(ConsumeIntent(PACK, tablets("3"), INTAKE)),
            unresolved = listOf(INTAKE)
        )
        assertNull(unsure.effective)
        assertNull(unsure.availableToMe)
        assertNull(unsure.freeForAnyone)
        assertEquals(StockViewState.NeedsRecount(tablets("20"), listOf(INTAKE)), unsure.stock)
    }

    @Test
    fun descriptiveIntentsDoNotMoveTheQuantity() {
        val described = stock(pending = listOf(ReleaseClaimIntent(PACK)))
        assertEquals(tablets("20"), described.effective)
        assertTrue((described.stock as StockViewState.Known).pending)
    }

    @Test
    fun ownClaimWithoutALocalOwnerIsNotSpentSilently() {
        // Обнаруженная своя бронь без курса вычитается наравне с чужой: человек сначала
        // разбирает неизвестное назначение.
        val orphan = stock(quantity = "20", claims = Claims(BigDecimal("10"), BigDecimal("10")))
        assertEquals(tablets("10"), orphan.orphanClaim)
        assertEquals(tablets("10"), orphan.availableToMe)
        assertEquals(tablets("0"), orphan.reservedByOthers)
    }

    @Test
    fun claimWithALocalOwnerIsNotOrphan() {
        val owned = stock(
            claims = Claims(BigDecimal("10"), BigDecimal("10")),
            allocation = "10",
            course = CourseBrief(COURSE, "Курс", 5)
        )
        assertEquals(tablets("0"), owned.orphanClaim)
        assertEquals(tablets("20"), owned.availableToMe)
        assertEquals(tablets("10"), owned.freeForAnyone)
    }

    @Test
    fun expectedReleaseMakesTheClaimNotOrphanEither() {
        val releasing = stock(
            claims = Claims(BigDecimal("10"), BigDecimal("10")),
            pending = listOf(ReleaseClaimIntent(PACK))
        )
        assertEquals(tablets("0"), releasing.orphanClaim)
    }

    @Test
    fun expiryMarksAndDoesNotZero() {
        val expired = stock(expiresOn = today.minusDays(1))
        assertTrue(expired.isExpired)
        assertEquals(tablets("20"), expired.effective)
        val soon = stock(expiresOn = today.plusDays(2))
        assertTrue(soon.expiresSoon)
        assertFalse(soon.isExpired)
        val later = stock(expiresOn = today.plusDays(30))
        assertFalse(later.expiresSoon)
    }

    @Test
    fun intentsOfAnotherPackageAreNotAcceptedAtAll() {
        assertThrows(IllegalArgumentException::class.java) {
            stock(pending = listOf(ConsumeIntent(OTHER_PACK, tablets("1"), INTAKE)))
        }
    }

    @Test
    fun allocationIsMeasuredByThePackageUnit() {
        assertThrows(IllegalArgumentException::class.java) {
            PackageStock(
                pkg = pack(quantity = tablets("20")),
                today = today,
                myAllocation = millilitres("5")
            )
        }
    }
}
