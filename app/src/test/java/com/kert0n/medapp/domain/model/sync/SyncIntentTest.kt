package com.kert0n.medapp.domain.model.sync

import com.kert0n.medapp.domain.model.pack.PackageSharedFacts
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.INTAKE
import com.kert0n.medapp.fixture.OTHER_INTAKE
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.SHARED_KIT
import com.kert0n.medapp.fixture.TABLET_FORM
import com.kert0n.medapp.fixture.millilitres
import com.kert0n.medapp.fixture.tablets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Намерение синхронизации — величина, а вид намерения — тип (PLAN E2, D7).
 *
 * Проверяется не «оно компилируется», а то, что неверное состояние **невыразимо**: у расхода нет
 * способа не назвать приём, у описания нет способа потерять исходное состояние, а нулевая бронь
 * не притворяется снятием.
 */
class SyncIntentTest {

    private val paracetamol = PackageSharedFacts(name = "Парацетамол", formId = TABLET_FORM)

    @Test
    fun anotherAmountIsAnotherIntent() {
        // Величина: подготовленный запрос неизменен, «поправить намерение на месте» не бывает.
        assertNotEquals(
            CorrectStockIntent(PACK, tablets("20")),
            CorrectStockIntent(PACK, tablets("19"))
        )
        assertEquals(
            CorrectStockIntent(PACK, tablets("20")),
            CorrectStockIntent(PACK, tablets("20.000000"))
        )
    }

    @Test
    fun consumeAlwaysNamesItsIntake() {
        // Намерение приёма не поглощает следующий факт: у каждого подтверждения свой id.
        val first = ConsumeIntent(PACK, tablets("2"), INTAKE)
        val second = ConsumeIntent(PACK, tablets("2"), OTHER_INTAKE)
        assertNotEquals(first, second)
    }

    @Test
    fun claimAfterIsMeasuredByTheSameUnitAsTheConsumption() {
        assertThrows(IllegalArgumentException::class.java) {
            ConsumeIntent(PACK, tablets("2"), INTAKE, claimAfter = millilitres("10"))
        }
    }

    @Test
    fun threeMeaningsOfClaimAfterAreDistinguishable() {
        // null — внеплановый расход; положительное — новая абсолютная бронь; ноль — курсовой
        // расход без блока брони, снятие уезжает зависимым намерением (PLAN E2).
        assertEquals(null, ConsumeIntent(PACK, tablets("2"), INTAKE).claimAfter)
        assertEquals(
            tablets("8"),
            ConsumeIntent(PACK, tablets("2"), INTAKE, claimAfter = tablets("8")).claimAfter
        )
        assertEquals(
            tablets("0"),
            ConsumeIntent(PACK, tablets("2"), INTAKE, claimAfter = tablets("0")).claimAfter
        )
    }

    @Test
    fun consumingNothingIsNotAnIntake() {
        assertThrows(IllegalArgumentException::class.java) {
            ConsumeIntent(PACK, tablets("0"), INTAKE)
        }
    }

    @Test
    fun packageIsCreatedWithAPositiveStock() {
        // Пачка, которой нет, не заводится — и в доменном сценарии, и в POST-DTO.
        assertThrows(IllegalArgumentException::class.java) {
            CreatePackageIntent(PACK, HOME_KIT, tablets("0"), paracetamol)
        }
    }

    @Test
    fun creationCarriesOnlyWhatCrossesTheBoundary() {
        // Личных сведений в намерении нет по типу: срок годности, заметка и цена остаются на
        // устройстве, и «забыть» их в маппере невозможно (PLAN C0).
        val intent = CreatePackageIntent(PACK, HOME_KIT, tablets("20"), paracetamol)
        assertEquals(paracetamol, intent.facts)
    }

    @Test
    fun describeKeepsBothStates() {
        // Одного «желаемого» не хватило бы: по нему нельзя отличить «не трогали» от «очистили».
        val after = paracetamol.copy(category = "жаропонижающие")
        val intent = DescribePackageIntent(PACK, before = paracetamol, after = after)
        assertEquals(null, intent.before.category)
        assertEquals("жаропонижающие", intent.after.category)
    }

    @Test
    fun describingNothingIsNotAnIntent() {
        assertThrows(IllegalArgumentException::class.java) {
            DescribePackageIntent(PACK, before = paracetamol, after = paracetamol)
        }
    }

    @Test
    fun zeroClaimIsNotAWayToReleaseIt() {
        // Снятие — отдельный вид намерения: на проводе у него другая операция.
        assertThrows(IllegalArgumentException::class.java) {
            SetClaimIntent(PACK, tablets("0"))
        }
        assertEquals(PACK, ReleaseClaimIntent(PACK).packageId)
    }

    @Test
    fun contentsAreNotTransferredIntoTheKitBeingDeleted() {
        assertThrows(IllegalArgumentException::class.java) {
            DeleteMedKitIntent(HOME_KIT, transferTo = HOME_KIT)
        }
        assertEquals(SHARED_KIT, DeleteMedKitIntent(HOME_KIT, SHARED_KIT).transferTo)
    }

    @Test
    fun reconciliationNamesHowFarItAnswersFor() {
        assertEquals(7L, ReconcileStockIntent(PACK, tablets("12"), 7).throughSequence)
        assertThrows(IllegalArgumentException::class.java) {
            ReconcileStockIntent(PACK, tablets("12"), -1)
        }
    }

    @Test
    fun everyIntentIsASyncIntent() {
        // Вид — тип, поэтому исчерпывающий `when` по видам возможен, а проверок сочетаний нет.
        val all: List<SyncIntent> = listOf(
            CreateMedKitIntent(HOME_KIT),
            DeleteMedKitIntent(HOME_KIT),
            LeaveMedKitIntent(SHARED_KIT),
            CreatePackageIntent(PACK, HOME_KIT, tablets("20"), paracetamol),
            DescribePackageIntent(PACK, paracetamol, paracetamol.copy(country = "Украина")),
            CorrectStockIntent(PACK, tablets("19")),
            MovePackageIntent(PACK, SHARED_KIT),
            DeletePackageIntent(PACK),
            ConsumeIntent(PACK, tablets("2"), INTAKE),
            SetClaimIntent(PACK, tablets("10")),
            ReleaseClaimIntent(PACK),
            ReconcileStockIntent(PACK, tablets("12"), 7)
        )
        assertEquals(12, all.size)
    }
}
