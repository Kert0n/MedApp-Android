package com.kert0n.medapp.presentation.medkit

import com.kert0n.medapp.domain.medkit.MedKit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Форма аптечки приводится к тому, что требует домен: непустое название и необязательное место
 * хранения. Разбор не бросает — неверный ввод это обычное состояние формы (PLAN H1).
 */
class MedKitFormMapperTest {

    private fun parsed(name: String, location: String = "") =
        MedKitFormPresentationDTO(name, location).toDomain()

    @Test
    fun spacesAroundTheNameAreNotPartOfIt() {
        assertEquals(MedKitDescription("Домашняя", null), parsed("  Домашняя  ").valueOrNull)
    }

    /** Пустое поле значит «не указано»: отсутствие в домене выражается только `null`. */
    @Test
    fun anEmptyLocationIsAbsenceNotAnEmptyString() {
        assertNull(parsed("Домашняя", "   ").valueOrNull?.location)
        assertEquals("Верхний ящик", parsed("Домашняя", " Верхний ящик ").valueOrNull?.location)
    }

    /**
     * Название нужно: без него аптечку не отличить от другой, и домен такую не соберёт.
     *
     * Красная проверка: пропустить пустое — конструктор `MedKit` бросит, и форма уронит
     * приложение вместо подсветки поля.
     */
    @Test
    fun aNameOfSpacesIsNotAName() {
        assertEquals(MedKitFormError.NAME_EMPTY, parsed("   ").errorOrNull)
        assertEquals(MedKitFormError.NAME_EMPTY, parsed("").errorOrNull)
    }

    @Test
    fun lengthsAreTheOnesTheDomainHolds() {
        assertEquals(
            MedKitFormError.NAME_TOO_LONG,
            parsed("я".repeat(MedKit.NAME_MAX_LENGTH + 1)).errorOrNull
        )
        assertEquals(
            MedKitFormError.LOCATION_TOO_LONG,
            parsed("Домашняя", "я".repeat(MedKit.LOCATION_MAX_LENGTH + 1)).errorOrNull
        )
        // Ровно предел — ещё годится: граница принадлежит допустимому.
        assertNull(parsed("я".repeat(MedKit.NAME_MAX_LENGTH)).errorOrNull)
    }
}
