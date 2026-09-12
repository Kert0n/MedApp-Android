package com.kert0n.medapp.presentation.pack

import com.kert0n.medapp.domain.value.Money
import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.fixture.TABLET_FORM
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.VOCABULARY
import com.kert0n.medapp.fixture.dose
import com.kert0n.medapp.fixture.expiry
import com.kert0n.medapp.fixture.tablets
import com.kert0n.medapp.presentation.ParsedInput
import com.kert0n.medapp.presentation.value.FormPresentationDTO
import com.kert0n.medapp.presentation.value.QuantityPresentationError
import com.kert0n.medapp.presentation.value.UnitPresentationDTO
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Форма упаковки приводится к домену здесь: обрезка пробелов, «пустое поле значит не указано» и
 * разбор напечатанного — свойства ввода (PLAN H1). Обязательных полей четыре (PLAN C1), прочие
 * есть все до одного (ТЗ 4.1.1.1) и заполняются по желанию.
 */
class PackageFormMapperTest {

    private val tablets = UnitPresentationDTO(TABLETS.id, TABLETS.name)

    private fun form(
        medKitId: Uuid? = HOME_KIT,
        name: String = "Парацетамол",
        amount: String = "20",
        unit: UnitPresentationDTO? = tablets
    ) = PackageFormPresentationDTO(medKitId = medKitId, name = name, amount = amount, unit = unit)

    private fun parsed(form: PackageFormPresentationDTO) =
        form.toDomain(VOCABULARY).let {
            (it as? ParsedInput.Parsed)?.value ?: error("ожидалась разобранная упаковка: $it")
        }

    private fun refused(form: PackageFormPresentationDTO): PackageFormError =
        (form.toDomain(VOCABULARY) as ParsedInput.Rejected).error

    /** Четырёх полей достаточно: остальное человек может не знать, и врать его не заставляют. */
    @Test
    fun fourFieldsAreEnoughToDescribeAPackage() {
        val described = parsed(form())

        assertEquals(HOME_KIT, described.medKitId)
        assertEquals("Парацетамол", described.facts.name)
        assertEquals(tablets("20"), described.amount)
        assertNull(described.facts.expiresOn)
        assertNull(described.facts.form)
        assertNull(described.facts.price)
    }

    @Test
    fun aPackageLiesInSomeOneMedKit() {
        assertEquals(PackageFormError.MedKitMissing, refused(form(medKitId = null)))
    }

    @Test
    fun anAmountWithoutAUnitMeasuresNothing() {
        assertEquals(PackageFormError.UnitMissing, refused(form(unit = null)))
    }

    @Test
    fun aNamelessPackageIsNotFound() {
        assertEquals(PackageFormError.NameEmpty, refused(form(name = "   ")))
    }

    /**
     * Заводимая пачка активна, а пустой активная не бывает (`Package`).
     *
     * Красная проверка: пропустить нулевое количество — `Package` бросит на записи, и форма
     * уронит приложение вместо отказа.
     */
    @Test
    fun anEmptyPackageIsNotWorthKeeping() {
        assertEquals(PackageFormError.AmountIsZero, refused(form(amount = "0")))
    }

    @Test
    fun anAmountThatIsNotANumberNamesItsField() {
        val error = refused(form(amount = "двадцать"))

        assertEquals(PackageFormError.Amount(QuantityPresentationError.NOT_A_DECIMAL), error)
        assertEquals(PackageFormError.Field.AMOUNT, error.field)
    }

    /** Круговая проверка: заполнено всё, что человек может знать о коробке (ТЗ 4.1.1.1). */
    @Test
    fun everyOptionalFieldReachesTheDomain() {
        val described = parsed(
            form().copy(
                form = FormPresentationDTO(TABLET_FORM.id, TABLET_FORM.name),
                category = " Жаропонижающие ",
                manufacturer = "Фармстандарт",
                country = "Россия",
                description = "Белые таблетки",
                expiresOn = "03.2027",
                defaultIntakeAmount = "2",
                note = "в машине",
                price = "249,90",
                purchasedOn = LocalDate.parse("2026-09-01"),
                openedOn = LocalDate.parse("2026-09-02")
            )
        )

        val facts = described.facts
        assertEquals(TABLET_FORM, facts.form)
        assertEquals("Жаропонижающие", facts.category)
        assertEquals("Фармстандарт", facts.manufacturer)
        assertEquals("Россия", facts.country)
        assertEquals("Белые таблетки", facts.description)
        assertEquals(expiry("2027-03-31"), facts.expiresOn)
        assertEquals(dose("2"), facts.defaultIntakeAmount)
        assertEquals("в машине", facts.note)
        assertEquals(Money(BigDecimal("249.90")), facts.price)
        assertEquals(LocalDate.parse("2026-09-01"), facts.purchasedOn)
        assertEquals(LocalDate.parse("2026-09-02"), facts.openedOn)
    }

    /** Пустое поле значит «не указано», а не пустую строку: отсутствие в домене — только `null`. */
    @Test
    fun blankOptionalFieldsMeanUnknown() {
        val facts = parsed(form().copy(category = "   ", note = "", expiresOn = " ", price = "")).facts

        assertNull(facts.category)
        assertNull(facts.note)
        assertNull(facts.expiresOn)
        assertNull(facts.price)
    }

    /** Просроченная дата — законный ввод: ТЗ 4.1.2 требует принимать её и отрабатывать. */
    @Test
    fun anExpiryInThePastIsAccepted() {
        assertEquals(expiry("2020-01-31"), parsed(form().copy(expiresOn = "01.2020")).facts.expiresOn)
    }

    /** Доза-подсказка меряется единицей пачки: своей единицы у неё нет и быть не может. */
    @Test
    fun theHintIsMeasuredInThePackageUnit() {
        val hint = parsed(form(amount = "20").copy(defaultIntakeAmount = "2")).facts.defaultIntakeAmount

        assertEquals(TABLETS, hint?.unit)
    }

    @Test
    fun aZeroHintIsNotADose() {
        assertEquals(PackageFormError.HintIsZero, refused(form().copy(defaultIntakeAmount = "0")))
    }

    @Test
    fun aTooLongNameNamesItsField() {
        val error = refused(form(name = "П".repeat(301)))

        assertEquals(PackageFormError.TooLong(PackageFormError.Field.NAME), error)
    }

    @Test
    fun aTooLongNoteNamesItsOwnField() {
        val error = refused(form().copy(note = "з".repeat(201)))

        assertEquals(PackageFormError.TooLong(PackageFormError.Field.NOTE), error)
    }

    /** Снимок словаря старее, чем тот, кто назвал единицу (PLAN D1): это отказ, а не падение. */
    @Test
    fun aUnitOutsideTheVocabularyIsNamed() {
        val unknown = UnitPresentationDTO(Uuid.random(), "склянка")

        assertEquals(
            PackageFormError.Amount(QuantityPresentationError.UNKNOWN_UNIT),
            refused(form(unit = unknown))
        )
    }

    @Test
    fun aFormOutsideTheVocabularyIsNamed() {
        val unknown = FormPresentationDTO(Uuid.random(), "порошок")

        assertEquals(
            PackageFormError.UnknownInVocabulary(PackageFormError.Field.FORM),
            refused(form().copy(form = unknown))
        )
    }
}
