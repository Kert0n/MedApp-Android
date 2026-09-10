package com.kert0n.medapp.data.pack

import com.kert0n.medapp.domain.pack.PackageSharedFacts
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.INTAKE
import com.kert0n.medapp.fixture.OTHER_INTAKE
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.SHARED_KIT
import com.kert0n.medapp.fixture.TABLET_FORM
import com.kert0n.medapp.fixture.millilitres
import com.kert0n.medapp.fixture.dose
import com.kert0n.medapp.fixture.tablets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Команда очереди по упаковке — величина, а вид команды — тип.
 *
 * Проверяется не «оно компилируется», а то, что неверное состояние **невыразимо**: у расхода нет
 * способа не назвать приём, у описания нет способа потерять исходное состояние, а нулевая бронь
 * не притворяется снятием.
 */
class PackageSyncCommandTest {

    private val paracetamol = PackageSharedFacts(name = "Парацетамол", formId = TABLET_FORM)

    @Test
    fun anotherAmountIsAnotherCommand() {
        // Величина: подготовленный запрос неизменен, «поправить команду на месте» не бывает.
        assertNotEquals(
            PackageSyncCommand.CorrectStock(PACK, tablets("20")),
            PackageSyncCommand.CorrectStock(PACK, tablets("19"))
        )
        assertEquals(
            PackageSyncCommand.CorrectStock(PACK, tablets("20")),
            PackageSyncCommand.CorrectStock(PACK, tablets("20.000000"))
        )
    }

    @Test
    fun everyCommandNamesItsPackage() {
        // Поле корня, а не разбор вариантов: строка очереди называет пачку своей колонкой.
        val commands: List<PackageSyncCommand> = listOf(
            PackageSyncCommand.Create(PACK, HOME_KIT, tablets("20"), paracetamol),
            PackageSyncCommand.Describe(PACK, paracetamol, paracetamol.copy(country = "Украина")),
            PackageSyncCommand.CorrectStock(PACK, tablets("19")),
            PackageSyncCommand.Move(PACK, SHARED_KIT),
            PackageSyncCommand.Delete(PACK),
            PackageSyncCommand.Consume(PACK, dose("2"), INTAKE),
            PackageSyncCommand.SetClaim(PACK, tablets("10")),
            PackageSyncCommand.ReleaseClaim(PACK),
            PackageSyncCommand.Reconcile(PACK, tablets("12"), throughSequence = 7)
        )
        assertEquals(9, commands.size)
        assertEquals(listOf(PACK), commands.map { it.packageId }.distinct())
    }

    @Test
    fun consumeAlwaysNamesItsIntake() {
        // Команда приёма не поглощает следующий факт: у каждого подтверждения свой id.
        assertNotEquals(
            PackageSyncCommand.Consume(PACK, dose("2"), INTAKE),
            PackageSyncCommand.Consume(PACK, dose("2"), OTHER_INTAKE)
        )
    }

    @Test
    fun claimAfterIsMeasuredByTheSameUnitAsTheConsumption() {
        assertThrows(IllegalArgumentException::class.java) {
            PackageSyncCommand.Consume(PACK, dose("2"), INTAKE, claimAfter = millilitres("10"))
        }
    }

    @Test
    fun threeMeaningsOfClaimAfterAreDistinguishable() {
        // null — внеплановый расход; положительное — новая абсолютная бронь; ноль — курсовой
        // расход без блока брони, снятие уезжает зависимой командой (PLAN E2).
        assertEquals(null, PackageSyncCommand.Consume(PACK, dose("2"), INTAKE).claimAfter)
        assertEquals(
            tablets("8"),
            PackageSyncCommand.Consume(PACK, dose("2"), INTAKE, tablets("8")).claimAfter
        )
        assertEquals(
            tablets("0"),
            PackageSyncCommand.Consume(PACK, dose("2"), INTAKE, tablets("0")).claimAfter
        )
    }

    @Test
    fun consumingNothingIsNotAnIntake() {
        assertThrows(IllegalArgumentException::class.java) {
            PackageSyncCommand.Consume(PACK, dose("0"), INTAKE)
        }
    }

    @Test
    fun packageIsCreatedWithAPositiveStock() {
        // Пачка, которой нет, не заводится — и в доменном сценарии, и в POST-DTO.
        assertThrows(IllegalArgumentException::class.java) {
            PackageSyncCommand.Create(PACK, HOME_KIT, tablets("0"), paracetamol)
        }
    }

    @Test
    fun creationCarriesOnlyWhatCrossesTheBoundary() {
        // Личных сведений в команде нет по типу: срок годности, заметка и цена остаются на
        // устройстве, и «забыть» их в маппере невозможно (PLAN C0).
        val command = PackageSyncCommand.Create(PACK, HOME_KIT, tablets("20"), paracetamol)
        assertEquals(paracetamol, command.facts)
    }

    @Test
    fun describeKeepsBothStates() {
        // Одного «желаемого» не хватило бы: по нему нельзя отличить «не трогали» от «очистили».
        val after = paracetamol.copy(category = "жаропонижающие")
        val command = PackageSyncCommand.Describe(PACK, before = paracetamol, after = after)
        assertEquals(null, command.before.category)
        assertEquals("жаропонижающие", command.after.category)
    }

    @Test
    fun describingNothingIsNotACommand() {
        assertThrows(IllegalArgumentException::class.java) {
            PackageSyncCommand.Describe(PACK, before = paracetamol, after = paracetamol)
        }
    }

    @Test
    fun zeroClaimIsNotAWayToReleaseIt() {
        // Снятие — отдельный вид: на проводе у него другая операция.
        assertThrows(IllegalArgumentException::class.java) {
            PackageSyncCommand.SetClaim(PACK, tablets("0"))
        }
        assertEquals(PACK, PackageSyncCommand.ReleaseClaim(PACK).packageId)
    }

    @Test
    fun reconciliationNamesHowFarItAnswersFor() {
        assertEquals(7L, PackageSyncCommand.Reconcile(PACK, tablets("12"), 7).throughSequence)
        assertThrows(IllegalArgumentException::class.java) {
            PackageSyncCommand.Reconcile(PACK, tablets("12"), -1)
        }
    }
}
