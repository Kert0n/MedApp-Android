package com.kert0n.medapp.storage.intake

import com.kert0n.medapp.fixture.INTAKE
import com.kert0n.medapp.fixture.LATER
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.dose
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.plannedIntake
import com.kert0n.medapp.domain.intake.IntakeStatus
import com.kert0n.medapp.network.intake.IntakeAccounting
import com.kert0n.medapp.network.intake.IntakeSyncState
import com.kert0n.medapp.network.pack.PackageSyncCommand
import com.kert0n.medapp.storage.server.QueuedCommand
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Что записывается вместе с ответом на приём. Проверяются связи между частями: порознь их не
 * бывает, и хранение это отвергает, а не пишет половину (PLAN D6, F5).
 */
class IntakeOutcomeTest {

    private val operation: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000091")

    private fun confirmed() = plannedIntake().confirm(pack(), dose("2"), LATER)

    private fun outcome(sync: IntakeSyncState, command: QueuedCommand? = null) = IntakeOutcome(
        intake = confirmed(),
        expected = setOf(IntakeStatus.PLANNED),
        sync = sync,
        command = command
    )

    private fun consume() = QueuedCommand(operation, PackageSyncCommand.Consume(PACK, dose("2"), INTAKE))

    @Test
    fun aPendingSpendIsQueuedTogetherWithTheIntake() {
        val sync = IntakeSyncState(INTAKE, IntakeAccounting.PENDING, operationId = operation)
        val queued = consume()

        assertEquals(queued, outcome(sync, queued).command)
    }

    /** Иначе приём остался бы ожидающим расход, которого в очереди нет. */
    @Test
    fun aPendingSpendWithoutItsCommandIsRefused() {
        val sync = IntakeSyncState(INTAKE, IntakeAccounting.PENDING, operationId = operation)

        assertThrows(IllegalArgumentException::class.java) { outcome(sync) }
    }

    /** Приём называет ту операцию, которая ставится вместе с ним, а не какую-то другую. */
    @Test
    fun anIntakeNamingAnotherOperationIsRefused() {
        val sync = IntakeSyncState(INTAKE, IntakeAccounting.PENDING, operationId = operation)
        val other = QueuedCommand(Uuid.random(), PackageSyncCommand.Consume(PACK, dose("2"), INTAKE))

        assertThrows(IllegalArgumentException::class.java) { outcome(sync, other) }
    }

    /** Локальный расход в очередь не едет: он уже записан вместе с фактом. */
    @Test
    fun aLocallyAppliedSpendNeedsNoCommand() {
        val sync = IntakeSyncState(INTAKE, IntakeAccounting.LOCAL_APPLIED)

        assertEquals(null, outcome(sync).command)
    }
}
