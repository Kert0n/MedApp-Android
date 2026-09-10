package com.kert0n.medapp.network.intake

import com.kert0n.medapp.fixture.INTAKE
import com.kert0n.medapp.fixture.PACK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Учёт расхода приёма живёт в слое данных: он существует только из-за сервера, и ни одно правило
 * о приёме его не читает (PLAN E1, решение PR 3).
 */
class IntakeSyncStateTest {

    @Test
    fun localIntakeCarriesNoOperationAtAll() {
        // В локальной аптечке исходящих операций нет вовсе.
        val local = IntakeSyncState(INTAKE, IntakeAccounting.LOCAL_APPLIED)
        assertNull(local.operationId)
        assertNull(local.reconciliationId)
    }

    @Test
    fun accountingIsNotApplicableOnlyWithoutAnyConsumption() {
        // Иначе «расхода нет» скрывало бы уехавшее списание.
        assertThrows(IllegalArgumentException::class.java) {
            IntakeSyncState(INTAKE, IntakeAccounting.NOT_APPLICABLE, operationId = PACK)
        }
    }

    @Test
    fun unsettledConsumptionNamesItsOperation() {
        assertThrows(IllegalArgumentException::class.java) {
            IntakeSyncState(INTAKE, IntakeAccounting.PENDING)
        }
        assertThrows(IllegalArgumentException::class.java) {
            IntakeSyncState(INTAKE, IntakeAccounting.NEEDS_RECOUNT)
        }
        assertEquals(
            PACK,
            IntakeSyncState(INTAKE, IntakeAccounting.PENDING, operationId = PACK).operationId
        )
    }

    @Test
    fun reconciledConsumptionNamesItsReconciliation() {
        // Исход прежнего запроса так и остаётся неизвестным, но факт учтён названной сверкой.
        assertThrows(IllegalArgumentException::class.java) {
            IntakeSyncState(INTAKE, IntakeAccounting.RECONCILED, operationId = PACK)
        }
        val reconciled = IntakeSyncState(
            intakeId = INTAKE,
            accounting = IntakeAccounting.RECONCILED,
            operationId = PACK,
            reconciliationId = INTAKE
        )
        assertEquals(INTAKE, reconciled.reconciliationId)
    }

    @Test
    fun defaultStateOfAPlannedItemIsNoConsumption() {
        assertEquals(IntakeAccounting.NOT_APPLICABLE, IntakeSyncState(INTAKE).accounting)
    }
}
