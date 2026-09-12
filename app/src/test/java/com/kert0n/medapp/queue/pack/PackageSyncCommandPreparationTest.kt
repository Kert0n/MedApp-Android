package com.kert0n.medapp.queue.pack

import com.kert0n.medapp.domain.pack.PackageSharedFacts
import com.kert0n.medapp.fixture.EARLIER
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.INTAKE
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.SHARED_KIT
import com.kert0n.medapp.fixture.TABLET_FORM
import com.kert0n.medapp.fixture.dose
import com.kert0n.medapp.fixture.tablets
import com.kert0n.medapp.network.pack.PackageSyncState
import com.kert0n.medapp.network.server.ResourceVersion
import com.kert0n.medapp.queue.Expected
import java.math.BigDecimal
import com.kert0n.medapp.queue.RefusalReason
import com.kert0n.medapp.queue.Preparation
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.millilitres
import com.kert0n.medapp.domain.pack.Claims
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Команда становится запросом один раз, с предусловиями пачки на этот момент (PLAN E2). */
class PackageSyncCommandPreparationTest {

    private val sync = PackageSyncState(PACK, version = ResourceVersion(3), claimsVersion = ResourceVersion(5))

    private fun PackageSyncCommand.prepared(mine: com.kert0n.medapp.domain.value.Quantity? = null) =
        toPreparedRequest(INTAKE, sync, confirmed = tablets("20"), mine = mine, at = EARLIER)

    /** Внеплановый расход — тот же `sync` без блока брони: номер есть, повтор сервер применит один раз. */
    @Test
    fun consumeOutsideACourseIsASyncWithoutAReservationBlock() {
        val request = PackageSyncCommand.Consume(PACK, dose("3"), INTAKE).prepared()
        assertEquals("PUT", request.method)
        assertEquals("/v1/drugs/$PACK/sync/$INTAKE", request.path)
        assertTrue(request.body!!.contains("\"drugVersion\":3"))
        assertFalse(request.body!!.contains("reservation"))
        assertEquals(ResourceVersion(3), request.drugVersion)
        assertEquals(tablets("20"), request.quantityBefore)
        assertEquals(EARLIER, request.preparedAt)
    }

    /** «Подумали» по свежей пачке: чужая единица — отказ до провода, желаемое уже так — применено. */
    @Test
    fun preparationRefusesAForeignUnitAndRecognisesWhatIsAlreadySo() {
        val syrup = pack(quantity = millilitres("100"))
        assertEquals(
            Preparation.Refuse(RefusalReason.UNIT_CHANGED),
            PackageSyncCommand.Consume(PACK, dose("3"), INTAKE).prepare(INTAKE, syrup, sync, EARLIER)
        )
        // Бронь и пересчёт тоже везут голое число: сервер прочёл бы таблетки миллилитрами.
        assertEquals(
            Preparation.Refuse(RefusalReason.UNIT_CHANGED),
            PackageSyncCommand.SetClaim(PACK, tablets("6")).prepare(INTAKE, syrup, sync, EARLIER)
        )
        assertEquals(
            Preparation.Refuse(RefusalReason.UNIT_CHANGED),
            PackageSyncCommand.CorrectStock(PACK, tablets("17")).prepare(INTAKE, syrup, sync, EARLIER)
        )
        // Ноль пересчёта — удаление: ноль в любой единице ноль.
        assertTrue(PackageSyncCommand.CorrectStock(PACK, tablets("0")).prepare(INTAKE, syrup, sync, EARLIER) is Preparation.Request)
        val claimed = pack(quantity = tablets("20"), claims = Claims(BigDecimal("6"), BigDecimal("6")))
        assertEquals(Preparation.AlreadyApplied, PackageSyncCommand.SetClaim(PACK, tablets("6")).prepare(INTAKE, claimed, sync, EARLIER))
        assertTrue(PackageSyncCommand.SetClaim(PACK, tablets("7")).prepare(INTAKE, claimed, sync, EARLIER) is Preparation.Request)
        val unclaimed = pack(quantity = tablets("20"), claims = Claims(BigDecimal("2"), null))
        assertEquals(Preparation.AlreadyApplied, PackageSyncCommand.ReleaseClaim(PACK).prepare(INTAKE, unclaimed, sync, EARLIER))
        assertTrue(PackageSyncCommand.ReleaseClaim(PACK).prepare(INTAKE, claimed, sync, EARLIER) is Preparation.Request)
    }

    @Test
    fun courseConsumeIsASyncUnderTheOperationIdWithBothNumbers() {
        val request = PackageSyncCommand.Consume(PACK, dose("3"), INTAKE, claimAfter = tablets("4")).prepared()
        assertEquals("PUT", request.method)
        assertEquals("/v1/drugs/$PACK/sync/$INTAKE", request.path)
        assertTrue(request.body!!.contains("\"consumed\":\"3\""))
        assertTrue(request.body!!.contains("\"reservation\":{\"amount\":\"4\",\"version\":5}"))
    }

    @Test
    fun zeroClaimAfterSendsNoReservationBlock() {
        val request = PackageSyncCommand.Consume(PACK, dose("3"), INTAKE, claimAfter = tablets("0")).prepared()
        assertFalse(request.body!!.contains("reservation"))
    }

    @Test
    fun zeroRecountIsADelete() {
        val request = PackageSyncCommand.CorrectStock(PACK, tablets("0")).prepared()
        assertEquals("DELETE", request.method)
        assertEquals(mapOf("version" to "3"), request.query)
        assertNull(request.body)
    }

    @Test
    fun claimIsDeclaredWhenThereIsNoneAndChangedWhenThereIs() {
        val declared = PackageSyncCommand.SetClaim(PACK, tablets("6")).prepared(mine = null)
        assertEquals("POST" to "/v1/reservations", declared.method to declared.path)
        val changed = PackageSyncCommand.SetClaim(PACK, tablets("6")).prepared(mine = tablets("4"))
        assertEquals("PATCH" to "/v1/reservations/$PACK", changed.method to changed.path)
        assertEquals(tablets("4"), changed.mineBefore)
    }

    @Test
    fun theRestNameTheirPathsAndVersions() {
        assertEquals(
            "PUT /v1/med-kits/$SHARED_KIT/drugs/$PACK",
            PackageSyncCommand.Move(PACK, SHARED_KIT).prepared().let { "${it.method} ${it.path}" }
        )
        assertEquals("DELETE", PackageSyncCommand.Delete(PACK).prepared().method)
        assertEquals("DELETE /v1/reservations/$PACK", PackageSyncCommand.ReleaseClaim(PACK).prepared().let { "${it.method} ${it.path}" })
        val facts = PackageSharedFacts("Парацетамол", TABLET_FORM)
        val create = PackageSyncCommand.Create(PACK, HOME_KIT, tablets("20"), facts).prepared()
        assertEquals("POST /v1/med-kits/$HOME_KIT/drugs", "${create.method} ${create.path}")
        val describe = PackageSyncCommand.Describe(PACK, facts, facts.copy(category = "жар")).prepared()
        assertEquals("PATCH", describe.method)
        assertTrue(describe.body!!.contains("\"category\":\"жар\""))
        assertFalse(describe.body!!.contains("name"))
    }

    /** Форма ответа — по контракту операции, и «пачки нет» ждёт только расход (PLAN B4, B5). */
    @Test
    fun eachCommandNamesTheShapeOfItsAnswer() {
        assertEquals(Expected.SNAPSHOT_OR_GONE, PackageSyncCommand.Consume(PACK, dose("3"), INTAKE).expects)
        assertEquals(Expected.SNAPSHOT, PackageSyncCommand.CorrectStock(PACK, tablets("5")).expects)
        assertEquals(Expected.NOTHING, PackageSyncCommand.CorrectStock(PACK, tablets("0")).expects)
        assertEquals(Expected.CLAIM, PackageSyncCommand.SetClaim(PACK, tablets("6")).expects)
        assertEquals(Expected.NOTHING, PackageSyncCommand.Delete(PACK).expects)
        assertEquals(Expected.SNAPSHOT, PackageSyncCommand.Create(PACK, HOME_KIT, tablets("5"), PackageSharedFacts("Парацетамол", TABLET_FORM)).expects)
    }
}
