package com.kert0n.medapp.data.mapper

import com.kert0n.medapp.data.remote.dto.PackageWireEdit
import com.kert0n.medapp.data.remote.dto.PackageWireFields
import com.kert0n.medapp.domain.model.CAPSULE_FORM
import com.kert0n.medapp.domain.model.Money
import com.kert0n.medapp.domain.model.PACKAGE_DESCRIPTION_MAX_LENGTH
import com.kert0n.medapp.domain.model.TABLET_FORM
import com.kert0n.medapp.domain.model.editOf
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
class PackageWireMapperTest {

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
        val patch = editOf(onServer).toWirePatch(onServer)
        assertNull(patch.edit)
        assertFalse(patch.formIdClearUnsupported)
    }

    @Test
    fun onlyTheChangedFieldTravels() {
        val patch = editOf(onServer).copy(name = "Парацетамол-Дарница").toWirePatch(onServer)
        val edit = requireNotNull(patch.edit)
        assertEquals("Парацетамол-Дарница", edit.name)
        assertNull(edit.category)
        assertNull(edit.description)
    }

    @Test
    fun clearedTextTravelsAsAnEmptyString() {
        val patch = editOf(onServer).copy(description = null).toWirePatch(onServer)
        assertEquals("", requireNotNull(patch.edit).description)
    }

    @Test
    fun clearingTheFormOfAServerPackIsReportedInsteadOfSentAsNull() {
        // `null` на проводе значит «не менять», а `""` не является UUID: молча выдать
        // неудалённую серверную форму за очищенную нельзя.
        val patch = editOf(onServer).copy(formId = null).toWirePatch(onServer)
        assertTrue(patch.formIdClearUnsupported)
        assertNull(patch.edit?.formId)
    }

    @Test
    fun clearingTheFormOfAPackNotYetOnTheServerIsFine() {
        val local = onServer.copy(version = null)
        val patch = editOf(local).copy(formId = null).toWirePatch(local)
        assertFalse(patch.formIdClearUnsupported)
    }

    @Test
    fun changingTheFormToAnotherOneTravels() {
        val patch = editOf(onServer).copy(formId = CAPSULE_FORM).toWirePatch(onServer)
        assertEquals(CAPSULE_FORM, requireNotNull(patch.edit).formId)
        assertFalse(patch.formIdClearUnsupported)
    }

    @Test
    fun localOnlyFieldsNeverReachTheWire() {
        // Срок годности, заметка, цена и даты остаются только на устройстве (PLAN C0, E5).
        val patch = editOf(onServer)
            .copy(note = "в машине", price = Money(BigDecimal("120.00")))
            .toWirePatch(onServer)
        assertNull(patch.edit)
    }

    @Test
    fun creationCarriesTheServerHalfOnly() {
        val fields = onServer.copy(note = "в машине").toWireFields()
        assertEquals("Парацетамол", fields.name)
        assertEquals(tablets("20"), fields.quantity)
        assertEquals(TABLET_FORM, fields.formId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun wireEditRefusesToClearTheName() {
        PackageWireEdit(name = "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun wireEditRefusesWhitespaceThatIsNeitherValueNorClearing() {
        PackageWireEdit(description = "   ")
    }

    @Test
    fun wireEditAcceptsEmptyStringAsClearing() {
        assertEquals("", PackageWireEdit(description = "").description)
    }

    @Test(expected = IllegalArgumentException::class)
    fun wireFieldsRefuseAnOverlongDescription() {
        PackageWireFields(
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
