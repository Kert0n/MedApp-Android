package com.kert0n.medapp.data.mapper

import com.kert0n.medapp.data.remote.dto.PackagePatchNetworkDTO
import com.kert0n.medapp.data.remote.dto.PackagePostNetworkDTO
import com.kert0n.medapp.domain.model.CAPSULE_FORM
import com.kert0n.medapp.domain.model.Money
import com.kert0n.medapp.domain.model.PACKAGE_DESCRIPTION_MAX_LENGTH
import com.kert0n.medapp.domain.model.TABLET_FORM
import com.kert0n.medapp.domain.model.factsOf
import com.kert0n.medapp.domain.model.pack
import com.kert0n.medapp.domain.model.tablets
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Перевод домена в форму провода. В домене `null` значит «сведений нет», на проводе — «не
 * трогать», а очистка там выражается пустой строкой (PLAN D3, H2). Весь этот перевод живёт здесь,
 * и здесь же проверяется.
 */
class PackageNetworkMapperTest {

    private val onServer = pack(
        name = "Парацетамол",
        formId = TABLET_FORM,
        category = "жаропонижающие",
        description = "по одной при температуре",
        version = 7
    )

    @Test
    fun unchangedFormSendsNothing() {
        // PATCH теми же значениями перетёр бы чужую правку, которую мы даже не видели.
        val patch = factsOf(onServer).toPatchNetworkMapping(onServer)
        assertNull(patch.dto)
        assertFalse(patch.formIdClearUnsupported)
    }

    @Test
    fun onlyTheChangedFieldTravels() {
        val renamed = factsOf(onServer).copy(name = "Парацетамол-Дарница")
        val patch = renamed.toPatchNetworkMapping(onServer)
        val dto = requireNotNull(patch.dto)
        assertEquals("Парацетамол-Дарница", dto.name)
        assertNull(dto.category)
        assertNull(dto.description)
    }

    @Test
    fun clearedTextTravelsAsAnEmptyString() {
        val patch = factsOf(onServer).copy(description = null).toPatchNetworkMapping(onServer)
        assertEquals("", requireNotNull(patch.dto).description)
    }

    @Test
    fun clearingTheFormOfAServerPackIsReportedInsteadOfSentAsNull() {
        // `null` на проводе значит «не менять», а `""` не является UUID: молча выдать
        // неудалённую серверную форму за очищенную нельзя.
        val patch = factsOf(onServer).copy(formId = null).toPatchNetworkMapping(onServer)
        assertTrue(patch.formIdClearUnsupported)
        assertNull(patch.dto?.formId)
    }

    @Test
    fun clearingTheFormOfAPackNotYetOnTheServerIsFine() {
        val local = onServer.copy(version = null)
        val patch = factsOf(local).copy(formId = null).toPatchNetworkMapping(local)
        assertFalse(patch.formIdClearUnsupported)
    }

    @Test
    fun changingTheFormToAnotherOneTravels() {
        val patch = factsOf(onServer).copy(formId = CAPSULE_FORM).toPatchNetworkMapping(onServer)
        assertEquals(CAPSULE_FORM, requireNotNull(patch.dto).formId)
        assertFalse(patch.formIdClearUnsupported)
    }

    @Test
    fun localOnlyFieldsNeverReachTheWire() {
        // Срок годности, заметка, цена и даты остаются только на устройстве (PLAN C0, E5).
        val patch = factsOf(onServer)
            .copy(note = "в машине", price = Money(BigDecimal("120.00")))
            .toPatchNetworkMapping(onServer)
        assertNull(patch.dto)
    }

    @Test
    fun creationCarriesTheServerHalfOnly() {
        val fields = onServer.copy(note = "в машине").toPostNetworkDTO()
        assertEquals("Парацетамол", fields.name)
        assertEquals(tablets("20"), fields.quantity)
        assertEquals(TABLET_FORM, fields.formId)
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

    @Test(expected = IllegalArgumentException::class)
    fun wireFieldsRefuseAnOverlongDescription() {
        PackagePostNetworkDTO(
            name = "Парацетамол",
            quantity = tablets("20"),
            formId = null,
            category = null,
            manufacturer = null,
            country = null,
            description = "я".repeat(PACKAGE_DESCRIPTION_MAX_LENGTH + 1)
        )
    }
}
