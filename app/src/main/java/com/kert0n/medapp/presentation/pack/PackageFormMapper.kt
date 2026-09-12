package com.kert0n.medapp.presentation.pack

import com.kert0n.medapp.domain.pack.PackageFacts
import com.kert0n.medapp.domain.pack.PackageSharedFacts
import com.kert0n.medapp.domain.value.Dose
import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.domain.value.QuantityUnit
import com.kert0n.medapp.domain.value.Vocabulary
import com.kert0n.medapp.presentation.ParsedInput
import com.kert0n.medapp.presentation.value.ExpiryDatePresentationDTO
import com.kert0n.medapp.presentation.value.MoneyPresentationDTO
import com.kert0n.medapp.presentation.value.toPresentationDTO
import com.kert0n.medapp.presentation.value.QuantityPresentationDTO
import com.kert0n.medapp.presentation.value.UnitPresentationDTO
import com.kert0n.medapp.presentation.value.toDomain
import kotlin.uuid.Uuid

/**
 * Приведение формы упаковки к тому, что требует домен. Обрезка пробелов, «пустое поле значит не
 * указано» и разбор напечатанного — свойства ввода и живут здесь; домен получает готовые
 * величины (PLAN H1).
 *
 * Разбор каждого поля делает его собственный маппер — количества, срока, цены: правило записи
 * живёт там же, где и раньше, а форма упаковки только называет, какое поле не прошло.
 *
 * Пределы длин берутся у [PackageSharedFacts] и [PackageFacts]: правило живёт на своём типе, и
 * второй его копии здесь не заводится (AGENTS).
 */
fun PackageFormPresentationDTO.toDomain(vocabulary: Vocabulary): ParsedInput<NewPackage, PackageFormError> {
    val medKitId = medKitId ?: return rejected(PackageFormError.MedKitMissing)
    val chosen = unit ?: return rejected(PackageFormError.UnitMissing)
    val quantity = when (val parsed = QuantityPresentationDTO(amount, chosen).toDomain(vocabulary)) {
        is ParsedInput.Rejected -> return rejected(PackageFormError.Amount(parsed.error))
        is ParsedInput.Parsed -> parsed.value
    }
    // Заводимая пачка активна, а пустой активная не бывает: правило держит `Package`, и форма
    // спрашивает о нём саму величину, а не сравнивает с нулём от себя.
    if (quantity.isZero) return rejected(PackageFormError.AmountIsZero)
    return when (val facts = toFacts(vocabulary, quantity.unit)) {
        is ParsedInput.Rejected -> rejected(facts.error)
        is ParsedInput.Parsed -> ParsedInput.Parsed(NewPackage(medKitId, facts.value, quantity))
    }
}

/**
 * Описательные сведения без количества: их правит экран 8, где количество меняется отдельно
 * (PLAN H3). [unit] — единица самой пачки: доза-подсказка это «сколько я обычно принимаю из
 * ЭТОЙ пачки», и мерить её другой единицей нечем ([com.kert0n.medapp.domain.pack.Package]).
 */
fun PackageFormPresentationDTO.toFacts(
    vocabulary: Vocabulary,
    unit: QuantityUnit
): ParsedInput<PackageFacts, PackageFormError> {
    val named = name.trim()
    if (named.isEmpty()) return rejected(PackageFormError.NameEmpty)
    tooLong(named, PackageSharedFacts.NAME_MAX_LENGTH, PackageFormError.Field.NAME)?.let { return it }

    val categoryOf = optional(category, PackageSharedFacts.CATEGORY_MAX_LENGTH, PackageFormError.Field.CATEGORY)
    if (categoryOf is ParsedInput.Rejected) return rejected(categoryOf.error)
    val manufacturerOf = optional(manufacturer, PackageSharedFacts.MANUFACTURER_MAX_LENGTH, PackageFormError.Field.MANUFACTURER)
    if (manufacturerOf is ParsedInput.Rejected) return rejected(manufacturerOf.error)
    val countryOf = optional(country, PackageSharedFacts.COUNTRY_MAX_LENGTH, PackageFormError.Field.COUNTRY)
    if (countryOf is ParsedInput.Rejected) return rejected(countryOf.error)
    val descriptionOf = optional(description, PackageSharedFacts.DESCRIPTION_MAX_LENGTH, PackageFormError.Field.DESCRIPTION)
    if (descriptionOf is ParsedInput.Rejected) return rejected(descriptionOf.error)
    val noteOf = optional(note, PackageFacts.NOTE_MAX_LENGTH, PackageFormError.Field.NOTE)
    if (noteOf is ParsedInput.Rejected) return rejected(noteOf.error)

    val formOf = form?.let {
        vocabulary.form(it.id)
            ?: return rejected(PackageFormError.UnknownInVocabulary(PackageFormError.Field.FORM))
    }

    val expiry = expiresOn.trim().takeIf { it.isNotEmpty() }?.let {
        when (val parsed = ExpiryDatePresentationDTO(it).toDomain()) {
            is ParsedInput.Rejected -> return rejected(PackageFormError.Expiry(parsed.error))
            is ParsedInput.Parsed -> parsed.value
        }
    }

    val hint = defaultIntakeAmount.trim().takeIf { it.isNotEmpty() }?.let {
        val quantity = when (
            val parsed = QuantityPresentationDTO(it, UnitPresentationDTO(unit.id, unit.name)).toDomain(vocabulary)
        ) {
            is ParsedInput.Rejected -> return rejected(PackageFormError.Hint(parsed.error))
            is ParsedInput.Parsed -> parsed.value
        }
        if (quantity.isZero) return rejected(PackageFormError.HintIsZero)
        Dose(quantity)
    }

    val priceOf = price.trim().takeIf { it.isNotEmpty() }?.let {
        when (val parsed = MoneyPresentationDTO(it).toDomain()) {
            is ParsedInput.Rejected -> return rejected(PackageFormError.Price(parsed.error))
            is ParsedInput.Parsed -> parsed.value
        }
    }

    return ParsedInput.Parsed(
        PackageFacts(
            shared = PackageSharedFacts(
                name = named,
                form = formOf,
                category = categoryOf.valueOrNull,
                manufacturer = manufacturerOf.valueOrNull,
                country = countryOf.valueOrNull,
                description = descriptionOf.valueOrNull
            ),
            expiresOn = expiry,
            defaultIntakeAmount = hint,
            note = noteOf.valueOrNull,
            price = priceOf,
            purchasedOn = purchasedOn,
            openedOn = openedOn
        )
    )
}

/**
 * Упаковка, какой её описал человек: всё проверено, и завести её остаётся сценарию. Отдельный
 * тип, а не три аргумента: подставить сюда непроверенный ввод не получится.
 */
data class NewPackage(val medKitId: Uuid, val facts: PackageFacts, val amount: Quantity)

/** Необязательный текст: пустое поле — «не указано», а не пустая строка (PLAN D1). */
private fun optional(
    text: String,
    maxLength: Int,
    field: PackageFormError.Field
): ParsedInput<String?, PackageFormError> {
    val trimmed = text.trim().takeIf { it.isNotEmpty() } ?: return ParsedInput.Parsed(null)
    return tooLong(trimmed, maxLength, field) ?: ParsedInput.Parsed(trimmed)
}

private fun tooLong(
    text: String,
    maxLength: Int,
    field: PackageFormError.Field
): ParsedInput.Rejected<PackageFormError>? =
    if (text.length > maxLength) ParsedInput.Rejected(PackageFormError.TooLong(field)) else null

private fun <T> rejected(error: PackageFormError): ParsedInput<T, PackageFormError> =
    ParsedInput.Rejected(error)

/**
 * Нынешние сведения пачки — в поля формы: правка начинается с того, что записано, а не с пустых
 * полей (PLAN H3 №8). Количество и единица переносятся как есть: править их здесь нечем —
 * количество меняют пересчёт и утилизация, а единица нужна форме лишь затем, чтобы померить
 * дозу-подсказку.
 *
 * Срок годности возвращается той же записью, какой его печатают на упаковке, — и разбирается
 * обратно ею же.
 */
fun PackagePresentationDTO.toFormPresentationDTO(): PackageFormPresentationDTO =
    PackageFormPresentationDTO(
        medKitId = medKitId,
        name = name,
        amount = quantity.amount,
        unit = quantity.unit,
        form = form,
        category = category.orEmpty(),
        manufacturer = manufacturer.orEmpty(),
        country = country.orEmpty(),
        description = description.orEmpty(),
        expiresOn = expiresOn?.toPresentationDTO()?.text.orEmpty(),
        defaultIntakeAmount = defaultIntakeAmount?.amount.orEmpty(),
        note = note.orEmpty(),
        price = price?.amount.orEmpty(),
        purchasedOn = purchasedOn,
        openedOn = openedOn
    )
