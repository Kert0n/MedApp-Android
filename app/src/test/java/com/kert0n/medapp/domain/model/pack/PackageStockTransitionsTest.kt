package com.kert0n.medapp.domain.model.pack

import com.kert0n.medapp.domain.model.value.Quantity

import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.fixture.millilitres
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.tablets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Переходы упаковки. Кончившаяся пачка архивируется, а не исчезает: иначе история приёмов за
 * прошлый месяц оборвалась бы вместе с ней (PLAN D3).
 */
/**
 * Переходы, меняющие остаток. Кончившаяся пачка архивируется, а не удаляется: иначе история
 * приёмов за прошлый месяц оборвалась бы вместе с ней (PLAN D3).
 */
class PackageStockTransitionsTest {

    @Test
    fun consumingToZeroArchivesThePack() {
        val empty = pack(quantity = tablets("2")).consume(tablets("2"))
        assertTrue(empty.quantity.isZero)
        assertEquals(PackageLifecycle.ARCHIVED, empty.lifecycle)
    }

    @Test
    fun consumingPartOfThePackKeepsItActive() {
        val left = pack(quantity = tablets("20")).consume(tablets("0.5"))
        assertEquals(tablets("19.5"), left.quantity)
        assertEquals(PackageLifecycle.ACTIVE, left.lifecycle)
    }

    @Test(expected = IllegalArgumentException::class)
    fun consumingMoreThanIsLeftIsRefused() {
        pack(quantity = tablets("3")).consume(tablets("5"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun consumingNothingIsNotAnIntake() {
        pack().consume(Quantity.zero(TABLETS))
    }

    @Test
    fun recountToZeroArchivesThePack() {
        val empty = pack(quantity = tablets("20")).correctTo(Quantity.zero(TABLETS))
        assertEquals(PackageLifecycle.ARCHIVED, empty.lifecycle)
    }

    @Test
    fun recountMayFindMoreThanWasKnown() {
        // Пересчёт — замена значения, а не дельта: пачку могли докупить или ошибиться в учёте.
        val more = pack(quantity = tablets("3")).correctTo(tablets("12"))
        assertEquals(tablets("12"), more.quantity)
        assertEquals(PackageLifecycle.ACTIVE, more.lifecycle)
    }

    @Test(expected = IllegalArgumentException::class)
    fun recountDoesNotChangeTheUnit() {
        pack(quantity = tablets("20")).correctTo(millilitres("20"))
    }

    @Test(expected = IllegalStateException::class)
    fun recountDoesNotReviveAnArchivedPack() {
        // «Удалена человеком» не отменяется числом.
        pack(lifecycle = PackageLifecycle.ARCHIVED).correctTo(tablets("5"))
    }

    @Test(expected = IllegalStateException::class)
    fun archivedPackIsNotConsumed() {
        pack(lifecycle = PackageLifecycle.ARCHIVED).consume(tablets("1"))
    }

}
