package com.kert0n.medapp.domain.pack

import com.kert0n.medapp.domain.value.Quantity

import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.fixture.millilitres
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.dose
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
class PackageQuantityTransitionsTest {

    @Test
    fun consumingToZeroArchivesThePack() {
        val empty = pack(quantity = tablets("2")).consume(dose("2"))
        assertTrue(empty.quantity.isZero)
        assertEquals(Package.Lifecycle.ARCHIVED, empty.lifecycle)
    }

    @Test
    fun consumingPartOfThePackKeepsItActive() {
        val left = pack(quantity = tablets("20")).consume(dose("0.5"))
        assertEquals(tablets("19.5"), left.quantity)
        assertEquals(Package.Lifecycle.ACTIVE, left.lifecycle)
    }

    @Test
    fun disposingMoreThanIsLeftDisposesOfEverything() {
        // Выбросил «пачку» из трёх таблеток, назвав пять: в минус не уходит, ушло три, пачка в
        // архиве. Правило — переход пачки, а не хранения, которое его записывает.
        val gone = pack(quantity = tablets("3")).dispose(tablets("5"))
        assertTrue(gone.quantity.isZero)
        assertEquals(Package.Lifecycle.ARCHIVED, gone.lifecycle)
        assertEquals(tablets("18"), pack(quantity = tablets("20")).dispose(tablets("2")).quantity)
    }

    @Test(expected = IllegalStateException::class)
    fun disposingFromAnArchivedPackIsRefused() {
        pack().archive().dispose(tablets("1"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun consumingMoreThanIsLeftIsRefused() {
        pack(quantity = tablets("3")).consume(dose("5"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun consumingNothingIsNotAnIntake() {
        // Проверка переехала на саму дозу: нулевого расхода не бывает вовсе (D1).
        dose(Quantity.zero(TABLETS))
    }

    @Test
    fun recountToZeroArchivesThePack() {
        val empty = pack(quantity = tablets("20")).correctTo(Quantity.zero(TABLETS))
        assertEquals(Package.Lifecycle.ARCHIVED, empty.lifecycle)
    }

    @Test
    fun recountMayFindMoreThanWasKnown() {
        // Пересчёт — замена значения, а не дельта: пачку могли докупить или ошибиться в учёте.
        val more = pack(quantity = tablets("3")).correctTo(tablets("12"))
        assertEquals(tablets("12"), more.quantity)
        assertEquals(Package.Lifecycle.ACTIVE, more.lifecycle)
    }

    @Test(expected = IllegalArgumentException::class)
    fun recountDoesNotChangeTheUnit() {
        pack(quantity = tablets("20")).correctTo(millilitres("20"))
    }

    @Test(expected = IllegalStateException::class)
    fun recountDoesNotReviveAnArchivedPack() {
        // «Удалена человеком» не отменяется числом.
        pack(lifecycle = Package.Lifecycle.ARCHIVED).correctTo(tablets("5"))
    }

    @Test(expected = IllegalStateException::class)
    fun archivedPackIsNotConsumed() {
        pack(lifecycle = Package.Lifecycle.ARCHIVED).consume(dose("1"))
    }

}
