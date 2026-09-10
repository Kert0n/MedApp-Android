package com.kert0n.medapp.domain.calc.availability

import com.kert0n.medapp.domain.model.course.CourseBrief
import com.kert0n.medapp.domain.model.pack.Claims
import com.kert0n.medapp.domain.model.pack.ClaimOwnership
import com.kert0n.medapp.domain.model.pack.EffectiveAmount
import com.kert0n.medapp.fixture.COURSE
import com.kert0n.medapp.fixture.INTAKE
import com.kert0n.medapp.fixture.millilitres
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.tablets
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Три с половиной величины «сколько доступно» (PLAN D4). Свёртки очереди здесь нет вовсе: оценка
 * количества приходит готовой, и это её единственный вход.
 */
class AvailabilityOfTest {

    private val today: LocalDate = LocalDate.of(2027, 3, 1)

    private val course = CourseBrief(COURSE, "Курс", allocatedDoses = 7)

    @Test
    fun numbersFromPlanAreReproduced() {
        // Пример PLAN D4: на сервере 20, моя бронь 10, чужого 5, локально принято 3 и выделение
        // уменьшилось до 7. Оценка 17 уже посчитана слоем данных.
        val found = availabilityOf(
            pkg = pack(quantity = tablets("20"), claims = Claims(BigDecimal("15"), BigDecimal("10"))),
            amount = EffectiveAmount.Known(tablets("17"), confirmed = false),
            myAllocation = tablets("7"),
            claimOwnership = ClaimOwnership.AssignedTo(course)
        )
        assertEquals(tablets("17"), found.effective)
        assertEquals(tablets("5"), found.reservedByOthers)
        assertEquals(tablets("5"), found.freeForAnyone)
        assertEquals(tablets("12"), found.availableToMe)
    }

    @Test
    fun unpublishedKitStillHasAllocations() {
        // Броней сервера нет, но из двадцати таблеток пятнадцать отданы курсу: свободно пять.
        val local = availabilityOf(
            pkg = pack(quantity = tablets("20")),
            amount = EffectiveAmount.Known(tablets("20")),
            myAllocation = tablets("15"),
            claimOwnership = ClaimOwnership.AssignedTo(course)
        )
        assertEquals(tablets("0"), local.reservedByOthers)
        assertEquals(tablets("5"), local.freeForAnyone)
        assertEquals(tablets("20"), local.availableToMe)
    }

    @Test
    fun consumptionOfOneTabletMakesADifferentValue() {
        // Тот самый дефект: проекция держала внутри сущность, сравниваемую по `id`, и расход
        // 20 → 19 давал равные значения — «на экране осталось 20» (решение C1 «Состояние экрана»).
        val before = availabilityOf(pack(quantity = tablets("20")), EffectiveAmount.Known(tablets("20")))
        val after = availabilityOf(pack(quantity = tablets("19")), EffectiveAmount.Known(tablets("19")))
        assertNotEquals(before, after)
        assertNotEquals(before.hashCode(), after.hashCode())
    }

    @Test
    fun sameNumbersAreTheSameValue() {
        val one = availabilityOf(pack(quantity = tablets("20")), EffectiveAmount.Known(tablets("20")))
        val other = availabilityOf(pack(quantity = tablets("20")), EffectiveAmount.Known(tablets("20.000000")))
        assertEquals(one, other)
        assertEquals(one.hashCode(), other.hashCode())
    }

    @Test
    fun ownClaimWithoutAKnownOwnerIsNotSpentSilently() {
        // Бесхозная бронь вычитается наравне с чужой: человек сначала разбирает назначение.
        val orphan = availabilityOf(
            pkg = pack(quantity = tablets("20"), claims = Claims(BigDecimal("10"), BigDecimal("10"))),
            amount = EffectiveAmount.Known(tablets("20")),
            claimOwnership = ClaimOwnership.NoKnownOwner
        )
        assertEquals(tablets("10"), orphan.orphanClaim)
        assertEquals(tablets("10"), orphan.availableToMe)
        assertEquals(tablets("0"), orphan.reservedByOthers)
    }

    @Test
    fun claimExplainedByAnAssignmentIsNotOrphan() {
        val owned = availabilityOf(
            pkg = pack(quantity = tablets("20"), claims = Claims(BigDecimal("10"), BigDecimal("10"))),
            amount = EffectiveAmount.Known(tablets("20")),
            myAllocation = tablets("10"),
            claimOwnership = ClaimOwnership.AssignedTo(course)
        )
        assertEquals(tablets("0"), owned.orphanClaim)
        assertEquals(tablets("20"), owned.availableToMe)
        assertEquals(tablets("10"), owned.freeForAnyone)
    }

    @Test
    fun locallyReleasedClaimIsNotOrphanEither() {
        // Назначение уже разобрано человеком: вычитать её значило бы спрятать запас, который
        // вот-вот освободится.
        val releasing = availabilityOf(
            pkg = pack(quantity = tablets("20"), claims = Claims(BigDecimal("10"), BigDecimal("10"))),
            amount = EffectiveAmount.Known(tablets("20")),
            claimOwnership = ClaimOwnership.ReleasedLocally
        )
        assertEquals(tablets("0"), releasing.orphanClaim)
        assertEquals(tablets("20"), releasing.availableToMe)
    }

    @Test
    fun recountRequiredMeansNoNumbersAtAll() {
        val unsure = availabilityOf(
            pkg = pack(quantity = tablets("20")),
            amount = EffectiveAmount.NeedsRecount(tablets("20"), listOf(INTAKE))
        )
        assertNull(unsure.effective)
        assertNull(unsure.availableToMe)
        assertNull(unsure.freeForAnyone)
        assertTrue(unsure.requiresRecount)
        // Последнее наблюдение при этом названо своим именем и остаётся видимым.
        assertEquals(
            EffectiveAmount.NeedsRecount(tablets("20"), listOf(INTAKE)),
            unsure.amount
        )
    }

    @Test
    fun expiryIsAskedByDateAndOnlyMarks() {
        val expiring = availabilityOf(
            pkg = pack(quantity = tablets("20"), expiresOn = today.plusDays(2)),
            amount = EffectiveAmount.Known(tablets("20"))
        )
        assertFalse(expiring.isExpiredOn(today))
        assertTrue(expiring.expiresSoonOn(today))
        assertTrue(expiring.isExpiredOn(today.plusDays(3)))
        // Просрочка ничего не списывает: пачка остаётся источником.
        assertEquals(tablets("20"), expiring.effective)
    }

    @Test
    fun negativeClaimPictureNeverProducesANegativeNumber() {
        // Сумма броней может превышать остаток: показываем ноль, а не долг.
        val over = availabilityOf(
            pkg = pack(quantity = tablets("2"), claims = Claims(BigDecimal("30"), null)),
            amount = EffectiveAmount.Known(tablets("2"))
        )
        assertEquals(tablets("0"), over.availableToMe)
        assertEquals(tablets("0"), over.freeForAnyone)
    }

    @Test
    fun unitsAreNotMixed() {
        assertThrows(IllegalArgumentException::class.java) {
            availabilityOf(
                pkg = pack(quantity = tablets("20")),
                amount = EffectiveAmount.Known(millilitres("20"))
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            availabilityOf(
                pkg = pack(quantity = tablets("20")),
                amount = EffectiveAmount.Known(tablets("20")),
                myAllocation = millilitres("5")
            )
        }
    }
}
