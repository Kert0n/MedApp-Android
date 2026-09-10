package com.kert0n.medapp.data.pack

import com.kert0n.medapp.domain.pack.EffectiveAmount
import com.kert0n.medapp.domain.pack.PackageSharedFacts
import com.kert0n.medapp.fixture.INTAKE
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.TABLET_FORM
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.dose
import com.kert0n.medapp.fixture.tablets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Незакрытые команды ложатся на подтверждённый остаток по правилу E1; домен получает только
 * количество, а признаки очереди остаются в данных.
 */
class PackageQueueStateTest {

    private val paracetamol = PackageSharedFacts(name = "Парацетамол", formId = TABLET_FORM)

    private fun consume(amount: String) = PackageSyncCommand.Consume(PACK, dose(amount), INTAKE)

    @Test
    fun confirmedAmountPassesThroughUntouched() {
        // В локальной аптечке исходящих команд нет вовсе (PLAN E1).
        val state = PackageQueueState(pack(quantity = tablets("20")))
        assertEquals(EffectiveAmount.Known(tablets("20")), state.amount)
        assertFalse(state.hasUnconfirmedChanges)
    }

    @Test
    fun consumptionIsSubtractedOnceAndLeavesTheNumberUnconfirmed() {
        val state = PackageQueueState(pack(quantity = tablets("20")), listOf(consume("3")))
        assertEquals(EffectiveAmount.Known(tablets("17")), state.amount)
        assertTrue(state.hasUnconfirmedChanges)
    }

    @Test
    fun queueMarksStayOutOfTheDomainAmount() {
        // Семнадцать подтверждённых и семнадцать после незакрытого расхода — для домена одно и то
        // же количество; разницу знает только очередь.
        val settled = PackageQueueState(pack(quantity = tablets("17")))
        val pending = PackageQueueState(pack(quantity = tablets("20")), listOf(consume("3")))
        assertEquals(settled.amount, pending.amount)
        assertNotEquals(settled.hasUnconfirmedChanges, pending.hasUnconfirmedChanges)
    }

    @Test
    fun changingTheListAfterwardsDoesNotChangeTheProjection() {
        // Свёртка считается при сборке: без своей копии добавленное позже удаление осталось бы
        // невидимым, и остаток показывал бы двадцать при уже назначенном нуле.
        val commands: MutableList<PackageSyncCommand> = mutableListOf(consume("3"))
        val state = PackageQueueState(pack(quantity = tablets("20")), commands)
        commands += PackageSyncCommand.Delete(PACK)
        assertEquals(EffectiveAmount.Known(tablets("17")), state.amount)
        assertEquals(1, state.unclosed.size)
    }

    @Test
    fun commandsOfAnotherPackAreNotFoldedIn() {
        // Чужая команда дала бы неверный остаток молча: состояние теперь знает, чьё оно.
        assertThrows(IllegalArgumentException::class.java) {
            PackageQueueState(
                pack(quantity = tablets("20")),
                listOf(PackageSyncCommand.Consume(OTHER_PACK, dose("3"), INTAKE))
            )
        }
    }

    @Test
    fun recountReplacesTheValueInsteadOfSubtracting() {
        // Пересчёт — не дельта: он может оказаться и больше прежнего.
        val state = PackageQueueState(
            pack(quantity = tablets("20")),
            unclosed = listOf(consume("3"), PackageSyncCommand.CorrectStock(PACK, tablets("30")))
        )
        assertEquals(tablets("30"), state.amount.quantityOrNull)
    }

    @Test
    fun projectedRecountAgreesWithWhatThePackWouldConfirm() {
        // Очередь проецирует то же, что пачка потом подтвердит переходом `correctTo`.
        val stored = pack(quantity = tablets("20"))
        val recount = PackageSyncCommand.CorrectStock(PACK, tablets("30"))
        val state = PackageQueueState(stored, listOf(recount))
        assertEquals(stored.correctTo(tablets("30")).quantity, state.amount.quantityOrNull)
    }

    @Test
    fun reconciliationNamesTheStockAndLaterCommandsApplyOnTop() {
        val state = PackageQueueState(
            pack(quantity = tablets("20")),
            unclosed = listOf(
                consume("3"),
                PackageSyncCommand.Reconcile(PACK, tablets("12"), throughSequence = 5),
                consume("2")
            )
        )
        assertEquals(tablets("10"), state.amount.quantityOrNull)
    }

    @Test
    fun deletionProjectsZero() {
        val state = PackageQueueState(
            pack(quantity = tablets("20")), listOf(PackageSyncCommand.Delete(PACK)))
        assertEquals(tablets("0"), state.amount.quantityOrNull)
    }

    @Test
    fun descriptiveCommandDoesNotMakeTheNumberUnconfirmed() {
        val state = PackageQueueState(
            pack(quantity = tablets("20")),
            unclosed = listOf(
                PackageSyncCommand.Describe(PACK, paracetamol, paracetamol.copy(country = "Чехия")),
                PackageSyncCommand.ReleaseClaim(PACK)
            )
        )
        assertEquals(EffectiveAmount.Known(tablets("20")), state.amount)
        assertFalse(state.hasUnconfirmedChanges)
    }

    @Test
    fun negativeProjectionIsShownAsZeroAndNotAsASuccessfulConsumption() {
        // Нехватка — конфликт операции, и разбирается она по состоянию очереди.
        val state = PackageQueueState(
            pack(quantity = tablets("2")), listOf(consume("5")))
        assertEquals(tablets("0"), state.amount.quantityOrNull)
    }

    @Test
    fun unresolvedOperationMakesTheAmountUnknown() {
        // Пока неизвестно, включён ли расход в серверный остаток, любое число было бы догадкой;
        // какая операция тому виной, знает очередь.
        val state = PackageQueueState(
            pack(quantity = tablets("20")),
            unclosed = listOf(consume("3")),
            unresolvedOperationIds = listOf(INTAKE)
        )
        assertEquals(EffectiveAmount.Unknown, state.amount)
        assertEquals(listOf(INTAKE), state.unresolvedOperationIds)
    }
}
