package com.kert0n.medapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Локальная форма и намерение PATCH — разные вещи. В форме `null` значит «сведений нет», на
 * проводе очистка текста передаётся как `""`, а `null` там значит «не трогать» (PLAN D3, H2).
 */
class PackageEditTest {

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
        val patch = editOf(onServer).wirePatchFrom(onServer)
        assertNull(patch.edit)
        assertFalse(patch.formIdClearUnsupported)
    }

    @Test
    fun onlyTheChangedFieldTravels() {
        val patch = editOf(onServer).copy(name = "Парацетамол-Дарница").wirePatchFrom(onServer)
        val edit = requireNotNull(patch.edit)
        assertEquals("Парацетамол-Дарница", edit.name)
        assertNull(edit.category)
        assertNull(edit.description)
    }

    @Test
    fun clearedTextTravelsAsAnEmptyString() {
        val patch = editOf(onServer).copy(description = null).wirePatchFrom(onServer)
        assertEquals("", requireNotNull(patch.edit).description)
    }

    @Test
    fun clearingTheFormOfAServerPackIsReportedInsteadOfSentAsNull() {
        // `null` на проводе значит «не менять», а `""` не является UUID: молча выдать
        // неудалённую серверную форму за очищенную нельзя.
        val patch = editOf(onServer).copy(formId = null).wirePatchFrom(onServer)
        assertTrue(patch.formIdClearUnsupported)
        assertNull(patch.edit?.formId)
    }

    @Test
    fun clearingTheFormOfAPackNotYetOnTheServerIsFine() {
        val local = onServer.copy(version = null)
        val patch = editOf(local).copy(formId = null).wirePatchFrom(local)
        assertFalse(patch.formIdClearUnsupported)
    }

    @Test
    fun changingTheFormToAnotherOneTravels() {
        val patch = editOf(onServer).copy(formId = CAPSULE_FORM).wirePatchFrom(onServer)
        assertEquals(CAPSULE_FORM, requireNotNull(patch.edit).formId)
        assertFalse(patch.formIdClearUnsupported)
    }

    @Test
    fun localOnlyFieldsNeverReachTheWire() {
        // Срок годности, заметка, цена и даты остаются только на устройстве (PLAN C0, E5).
        val patch = editOf(onServer)
            .copy(note = "в машине", price = Money(java.math.BigDecimal("120.00")))
            .wirePatchFrom(onServer)
        assertNull(patch.edit)
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
