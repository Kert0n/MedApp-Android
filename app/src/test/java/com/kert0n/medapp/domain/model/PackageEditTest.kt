package com.kert0n.medapp.domain.model

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `PackageEdit` — доменная величина: `null` значит «сведений нет». Противоположный смысл `null`
 * на проводе проверяется в `data/mapper`, и это разные тесты в разных слоях не случайно.
 */
class PackageEditTest {

    @Test
    fun editCarriesEverythingDescriptive() {
        val edit = PackageEdit(
            name = "Парацетамол",
            formId = TABLET_FORM,
            category = "жаропонижающие",
            manufacturer = "Дарница",
            country = "Украина",
            description = "по одной при температуре",
            expiresOn = LocalDate.of(2027, 3, 31),
            defaultIntakeAmount = tablets("1"),
            note = "в машине",
            price = Money(BigDecimal("120.00")),
            purchasedOn = LocalDate.of(2026, 1, 10),
            openedOn = LocalDate.of(2026, 1, 12)
        )
        assertEquals("Парацетамол", edit.name)
        assertEquals(Money(BigDecimal("120.00")), edit.price)
    }

    @Test
    fun absenceOfInformationIsNull() {
        val empty = editOf(pack())
        assertNull(empty.category)
        assertNull(empty.expiresOn)
        assertNull(empty.price)
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankNameIsRejected() {
        editOf(pack()).copy(name = "   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyStringIsNotAWayToSayThereIsNone() {
        // На проводе `""` означает очистку, в домене — ничего: два смысла в одном месте
        // разошлись бы при первом же маппинге.
        editOf(pack()).copy(category = "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun overlongDescriptionIsRejected() {
        editOf(pack()).copy(description = "я".repeat(PACKAGE_DESCRIPTION_MAX_LENGTH + 1))
    }
}
