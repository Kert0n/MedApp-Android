package com.kert0n.medapp.data.mapper.pack

import com.kert0n.medapp.data.remote.dto.pack.PackagePatchNetworkDTO
import com.kert0n.medapp.data.sync.pack.PackageSyncState
import com.kert0n.medapp.domain.model.value.Money

import com.kert0n.medapp.fixture.CAPSULE_FORM
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.TABLET_FORM
import com.kert0n.medapp.fixture.factsOf
import com.kert0n.medapp.fixture.pack

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Домен → намерение PATCH. В домене `null` значит «сведений нет», на проводе — «не трогать»,
 * а очистка там выражается пустой строкой (PLAN D3, H2). Весь перевод живёт здесь.
 */
class PackagePatchNetworkMapperTest {

    private fun onServer(note: String? = null) = pack(
        name = "Парацетамол",
        formId = TABLET_FORM,
        category = "жаропонижающие",
        description = "по одной при температуре",
        note = note
    )

    private val onServer = onServer()

    /** Пачка уже создана на сервере: у неё есть предусловие. */
    private val synced = PackageSyncState(packageId = PACK, version = 7)

    /** Пачка ещё только заводится: предусловия нет. */
    private val notSynced = PackageSyncState(packageId = PACK)

    @Test
    fun unchangedFormSendsNothing() {
        // PATCH теми же значениями перетёр бы чужую правку, которую мы даже не видели.
        val patch = factsOf(onServer).toPatchNetworkMapping(onServer, synced)
        assertNull(patch.dto)
        assertFalse(patch.formIdClearUnsupported)
    }

    @Test
    fun onlyTheChangedFieldTravels() {
        val renamed = factsOf(onServer).copy(name = "Парацетамол-Дарница")
        val patch = renamed.toPatchNetworkMapping(onServer, synced)
        val dto = requireNotNull(patch.dto)
        assertEquals("Парацетамол-Дарница", dto.name)
        assertNull(dto.category)
        assertNull(dto.description)
    }

    @Test
    fun clearedTextTravelsAsAnEmptyString() {
        val patch = factsOf(onServer).copy(description = null)
            .toPatchNetworkMapping(onServer, synced)
        assertEquals("", requireNotNull(patch.dto).description)
    }

    @Test
    fun clearingTheFormOfAServerPackIsReportedInsteadOfSentAsNull() {
        // `null` на проводе значит «не менять», а `""` не является UUID: молча выдать
        // неудалённую серверную форму за очищенную нельзя.
        val patch = factsOf(onServer).copy(formId = null).toPatchNetworkMapping(onServer, synced)
        assertTrue(patch.formIdClearUnsupported)
        assertNull(patch.dto?.formId)
    }

    @Test
    fun clearingTheFormOfAPackNotYetOnTheServerIsFine() {
        // Ограничение — протокольное, поэтому зависит от предусловия, а не от самой пачки.
        val patch = factsOf(onServer).copy(formId = null)
            .toPatchNetworkMapping(onServer, notSynced)
        assertFalse(patch.formIdClearUnsupported)
    }

    @Test
    fun changingTheFormToAnotherOneTravels() {
        val patch = factsOf(onServer).copy(formId = CAPSULE_FORM)
            .toPatchNetworkMapping(onServer, synced)
        assertEquals(CAPSULE_FORM, requireNotNull(patch.dto).formId)
        assertFalse(patch.formIdClearUnsupported)
    }

    @Test
    fun localOnlyFieldsNeverReachTheWire() {
        // Срок годности, заметка, цена и даты остаются только на устройстве (PLAN C0, E5).
        val patch = factsOf(onServer)
            .copy(note = "в машине", price = Money(BigDecimal("120.00")))
            .toPatchNetworkMapping(onServer, synced)
        assertNull(patch.dto)
    }

    @Test(expected = IllegalArgumentException::class)
    fun wireEditRefusesToClearTheName() {
        PackagePatchNetworkDTO(name = "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun wireEditRefusesWhitespaceThatIsNeitherValueNorClearing() {
        PackagePatchNetworkDTO(description = "   ")
    }

    @Test
    fun wireEditAcceptsEmptyStringAsClearing() {
        assertEquals("", PackagePatchNetworkDTO(description = "").description)
    }

}
