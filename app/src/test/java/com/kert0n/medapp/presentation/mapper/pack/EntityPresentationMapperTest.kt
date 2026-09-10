package com.kert0n.medapp.presentation.mapper.pack

import com.kert0n.medapp.domain.model.medkit.KitPublication
import com.kert0n.medapp.domain.model.medkit.MedKit
import com.kert0n.medapp.domain.model.pack.Claims
import com.kert0n.medapp.domain.model.pack.Package
import com.kert0n.medapp.domain.model.pack.PackageLifecycle
import com.kert0n.medapp.domain.model.value.Money
import com.kert0n.medapp.presentation.dto.medkit.MedKitPresentationDTO
import com.kert0n.medapp.presentation.dto.pack.PackagePresentationDTO
import com.kert0n.medapp.presentation.mapper.medkit.toPresentationDTO

import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.factsOf
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.tablets

import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EntityPresentationMapperTest {

    private data class PackageListState(val packages: List<PackagePresentationDTO>)
    private data class MedKitListState(val medKits: List<MedKitPresentationDTO>)

    @Test
    fun stateFlowReceivesConsumptionDescriptionAndArchivingOfTheSamePackage() = runTest {
        val original = pack(quantity = tablets("20"))
        val updates = MutableSharedFlow<List<Package>>()
        val state = updates.map { packages ->
            PackageListState(packages.map { it.toPresentationDTO() })
        }.stateIn(backgroundScope, SharingStarted.Eagerly, PackageListState(emptyList()))
        runCurrent()

        updates.emit(listOf(original))
        runCurrent()
        assertEquals("20", state.value.packages.single().quantity.amount)

        val consumed = original.consume(tablets("1"))
        assertEquals(original, consumed) // Доменное тождество не меняем ради интерфейса.
        updates.emit(listOf(consumed))
        runCurrent()
        assertEquals("19", state.value.packages.single().quantity.amount)

        val edited = consumed.describe(factsOf(consumed).copy(note = "В поездку"))
        updates.emit(listOf(edited))
        runCurrent()
        assertEquals("В поездку", state.value.packages.single().note)

        updates.emit(listOf(edited.consume(tablets("19"))))
        runCurrent()
        assertEquals("0", state.value.packages.single().quantity.amount)
        assertEquals(PackageLifecycle.ARCHIVED, state.value.packages.single().lifecycle)
    }

    @Test
    fun stateFlowReceivesRenamingAndLocationClearingOfTheSameKit() = runTest {
        val original = MedKit(HOME_KIT, "Домашняя", "Шкаф", KitPublication.LOCAL, 1, Instant.EPOCH)
        val updates = MutableSharedFlow<List<MedKit>>()
        val state = updates.map { kits ->
            MedKitListState(kits.map { it.toPresentationDTO() })
        }.stateIn(backgroundScope, SharingStarted.Eagerly, MedKitListState(emptyList()))
        runCurrent()

        updates.emit(listOf(original))
        runCurrent()
        assertEquals("Домашняя", state.value.medKits.single().name)

        val edited = original.describe("Дачная", null)
        assertEquals(original, edited)
        updates.emit(listOf(edited))
        runCurrent()
        assertEquals("Дачная", state.value.medKits.single().name)
        assertNull(state.value.medKits.single().location)
    }

    @Test
    fun numericScaleDoesNotChangePresentationState() {
        val first = pack(
            quantity = tablets("20"), defaultIntakeAmount = tablets("1"),
            price = Money(BigDecimal("150")),
            claims = Claims(BigDecimal("5"), BigDecimal("2"))
        )
        val same = pack(
            quantity = tablets("20.000000"), defaultIntakeAmount = tablets("1.000000"),
            price = Money(BigDecimal("150.00")),
            claims = Claims(BigDecimal("5.000000"), BigDecimal("2.000000"))
        )
        assertEquals(first.toPresentationDTO(), same.toPresentationDTO())
        assertEquals(first.toPresentationDTO().hashCode(), same.toPresentationDTO().hashCode())
    }

    @Test
    fun claimsChangeIsVisibleEvenWithTheSamePackageIdentityAndStock() {
        val before = pack(claims = Claims(BigDecimal("5"), null))
        val after = pack(claims = Claims(BigDecimal("8"), null))
        assertEquals(before, after)
        assertNotEquals(before.toPresentationDTO(), after.toPresentationDTO())
    }
}
