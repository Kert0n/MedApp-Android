package com.kert0n.medapp.queue

import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.INTAKE
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.SHARED_KIT
import com.kert0n.medapp.fixture.dose
import com.kert0n.medapp.fixture.medKit
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.tablets
import com.kert0n.medapp.queue.intake.IntakeAccounting
import com.kert0n.medapp.network.pack.PackageSnapshot
import com.kert0n.medapp.network.pack.PackageSyncState
import com.kert0n.medapp.network.server.ResourceVersion
import com.kert0n.medapp.queue.Settlement.Effect
import com.kert0n.medapp.queue.Settlement.Transition
import com.kert0n.medapp.queue.medkit.MedKitSyncCommand
import com.kert0n.medapp.queue.pack.PackageSyncCommand
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Исход доставки переводится в переход и эффекты там, где живёт доставка, — чистой функцией без
 * базы. Хранение получает список и применяет его, не толкуя (PLAN E3, F5).
 */
class SettlementTest {

    private val consume = PackageSyncCommand.Consume(PACK, dose("3"), INTAKE)
    private val leave = MedKitSyncCommand.Leave(SHARED_KIT)
    private val snapshot = PackageSnapshot(
        pack(quantity = tablets("17"), medKit = medKit(id = HOME_KIT, publication = MedKit.Publication.PUBLISHED).ref),
        PackageSyncState(PACK, ResourceVersion(4), ResourceVersion(2))
    )
    private val later: Instant = Instant.parse("2026-09-12T12:00:00Z")

    @Test
    fun appliedWithASnapshotClosesLaysItDownAndAccountsTheIntake() {
        val settlement = Delivery.Applied(PackageState.Present(snapshot)).settlement(consume)
        assertEquals(Transition.Close(SyncOperationStatus.APPLIED), settlement.transition)
        assertEquals(listOf(Effect.Account(IntakeAccounting.REMOTE_APPLIED), Effect.LayDown(snapshot)), settlement.effects)
    }

    @Test
    fun appliedWithThePackageGoneArchivesIt() {
        val settlement = Delivery.Applied(PackageState.Gone).settlement(consume)
        assertEquals(listOf(Effect.Account(IntakeAccounting.REMOTE_APPLIED), Effect.PackageGone(PACK)), settlement.effects)
    }

    @Test
    fun appliedWithNoPackageStateTouchesOnlyTheAccounting() {
        val settlement = Delivery.Applied(PackageState.None).settlement(leave)
        assertEquals(Transition.Close(SyncOperationStatus.APPLIED), settlement.transition)
        assertEquals(listOf<Effect>(Effect.Account(IntakeAccounting.REMOTE_APPLIED)), settlement.effects)
    }

    @Test
    fun staleRepreparesUnderTheSameNumberAndLaysTheSnapshotDown() {
        val settlement = Delivery.Stale(snapshot, notBefore = later).settlement(consume)
        assertEquals(Transition.Reprepare("устарело: ${ResourceVersion(4)}", later), settlement.transition)
        assertEquals(listOf<Effect>(Effect.LayDown(snapshot)), settlement.effects)
    }

    @Test
    fun refusedClosesWithTheReasonAccountsAndCascades() {
        val settlement = Delivery.Refused(RefusalReason.INSUFFICIENT, PackageState.Present(snapshot)).settlement(consume)
        assertEquals(Transition.Close(SyncOperationStatus.REFUSED, "INSUFFICIENT"), settlement.transition)
        assertEquals(
            listOf(
                Effect.Account(IntakeAccounting.REMOTE_REFUSED),
                Effect.LayDown(snapshot),
                Effect.Cascade(SyncOperationStatus.REFUSED, IntakeAccounting.REMOTE_REFUSED)
            ),
            settlement.effects
        )
    }

    @Test
    fun retryOnlyMovesTheOperationBackToWaiting() {
        val settlement = Delivery.Retry("ответ потерян", later, attempted = true, outcomeUnknown = true).settlement(consume)
        assertEquals(Transition.Retry("ответ потерян", attempted = true, outcomeUnknown = true, notBefore = later), settlement.transition)
        assertEquals(emptyList<Effect>(), settlement.effects)
    }

    @Test
    fun accessLostMarksThePackageAccountsAndCascades() {
        val settlement = Delivery.AccessLost.settlement(consume)
        assertEquals(Transition.Close(SyncOperationStatus.ACCESS_LOST), settlement.transition)
        assertEquals(
            listOf(
                Effect.Account(IntakeAccounting.REMOTE_REFUSED),
                Effect.PackageLost(PACK),
                Effect.Cascade(SyncOperationStatus.ACCESS_LOST, IntakeAccounting.REMOTE_REFUSED)
            ),
            settlement.effects
        )
    }

    @Test
    fun accessLostOfAMedKitCommandHasNoPackageToMark() {
        val settlement = Delivery.AccessLost.settlement(leave)
        assertEquals(
            listOf(
                Effect.Account(IntakeAccounting.REMOTE_REFUSED),
                Effect.Cascade(SyncOperationStatus.ACCESS_LOST, IntakeAccounting.REMOTE_REFUSED)
            ),
            settlement.effects
        )
    }

    @Test
    fun aCloseLeadsOnlyIntoAClosedState() {
        assertThrows(IllegalArgumentException::class.java) { Transition.Close(SyncOperationStatus.PENDING) }
    }
}
