package com.kert0n.medapp.data.sync.pack

import com.kert0n.medapp.domain.model.pack.EffectiveAmount
import com.kert0n.medapp.domain.model.pack.PackageSharedFacts
import com.kert0n.medapp.fixture.INTAKE
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.TABLET_FORM
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.tablets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проекция незакрытых команд на подтверждённый остаток (PLAN E1) — правило синхронизации, и
 * живёт оно в слое данных: оно про доставку, а не про лекарство.
 */
class EffectiveAmountProjectionTest {

    private val paracetamol = PackageSharedFacts(name = "Парацетамол", formId = TABLET_FORM)

    @Test
    fun confirmedAmountPassesThroughUntouched() {
        // В локальной аптечке исходящих команд нет вовсе (PLAN E1): число подтверждено.
        assertEquals(
            EffectiveAmount.Known(tablets("20"), confirmed = true),
            effectiveAmount(tablets("20"))
        )
    }

    @Test
    fun consumptionIsSubtractedOnce() {
        assertEquals(
            EffectiveAmount.Known(tablets("17"), confirmed = false),
            effectiveAmount(tablets("20"), listOf(PackageSyncCommand.Consume(PACK, tablets("3"), INTAKE)))
        )
    }

    @Test
    fun recountReplacesTheValueInsteadOfSubtracting() {
        // Пересчёт — не дельта: он может оказаться и больше прежнего.
        val projected = effectiveAmount(
            confirmed = tablets("20"),
            unclosed = listOf(
                PackageSyncCommand.Consume(PACK, tablets("3"), INTAKE),
                PackageSyncCommand.CorrectStock(PACK, tablets("30"))
            )
        )
        assertEquals(tablets("30"), projected.quantityOrNull)
    }

    @Test
    fun projectedRecountAgreesWithWhatThePackWouldConfirm() {
        // Одно правило на два пути: слой данных проецирует то же, что домен потом подтвердит
        // переходом `correctTo`. Разойдись они — экран показывал бы одно, база другое.
        val stored = pack(quantity = tablets("20"))
        val recounted = stored.correctTo(tablets("30"))
        val projected = effectiveAmount(stored.quantity, listOf(PackageSyncCommand.CorrectStock(PACK, tablets("30"))))
        assertEquals(recounted.quantity, projected.quantityOrNull)
    }

    @Test
    fun reconciliationNamesTheStockAndLaterCommandsApplyOnTop() {
        val projected = effectiveAmount(
            confirmed = tablets("20"),
            unclosed = listOf(
                PackageSyncCommand.Consume(PACK, tablets("3"), INTAKE),
                PackageSyncCommand.Reconcile(PACK, tablets("12"), throughSequence = 5),
                PackageSyncCommand.Consume(PACK, tablets("2"), INTAKE)
            )
        )
        assertEquals(tablets("10"), projected.quantityOrNull)
    }

    @Test
    fun deletionProjectsZero() {
        assertEquals(
            tablets("0"),
            effectiveAmount(tablets("20"), listOf(PackageSyncCommand.Delete(PACK))).quantityOrNull
        )
    }

    @Test
    fun descriptiveCommandDoesNotMakeAConfirmedNumberUnconfirmed() {
        // Прежний признак считал любую незакрытую команду: подтверждённое число помечалось
        // неподтверждённым, хотя его никто не менял.
        val projected = effectiveAmount(
            confirmed = tablets("20"),
            unclosed = listOf(
                PackageSyncCommand.Describe(PACK, paracetamol, paracetamol.copy(country = "Украина")),
                PackageSyncCommand.ReleaseClaim(PACK)
            )
        )
        assertEquals(EffectiveAmount.Known(tablets("20"), confirmed = true), projected)
    }

    @Test
    fun negativeProjectionIsShownAsZeroAndNotAsASuccessfulConsumption() {
        // Ошибку не превращаем в успешный расход: нехватка — конфликт операции, и разбирается он
        // по состоянию очереди.
        assertEquals(
            tablets("0"),
            effectiveAmount(tablets("2"), listOf(PackageSyncCommand.Consume(PACK, tablets("5"), INTAKE))).quantityOrNull
        )
    }

    @Test
    fun unresolvedOperationOverridesTheWholeProjection() {
        // Пока неизвестно, включён ли расход в серверный остаток, любое число было бы догадкой.
        val projected = effectiveAmount(
            confirmed = tablets("20"),
            unclosed = listOf(PackageSyncCommand.Consume(PACK, tablets("3"), INTAKE)),
            unresolvedOperationIds = listOf(INTAKE)
        )
        assertEquals(EffectiveAmount.NeedsRecount(tablets("20"), listOf(INTAKE)), projected)
        assertTrue(projected.quantityOrNull == null)
    }
}
