package com.kert0n.medapp.fixture

import com.kert0n.medapp.domain.pack.Availability
import com.kert0n.medapp.domain.value.DosageForm
import com.kert0n.medapp.domain.value.Dose
import com.kert0n.medapp.domain.value.Doses
import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.domain.value.QuantityUnit
import com.kert0n.medapp.domain.value.Vocabulary
import java.math.BigDecimal
import kotlin.uuid.Uuid

/**
 * Постоянные идентификаторы вместо случайных: упавший тест должен читаться по сообщению,
 * а не воспроизводиться заново со своим набором UUID.
 */
val TABLETS_ID: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000001")
val MILLILITRES_ID: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000002")

val TABLET_FORM_ID: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000041")
val CAPSULE_FORM_ID: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000042")

/** Единицы и формы — объекты словаря: домен принимает их сами, а не номера (PLAN D1). */
val TABLETS: QuantityUnit = QuantityUnit(TABLETS_ID, "таблетка")
val MILLILITRES: QuantityUnit = QuantityUnit(MILLILITRES_ID, "мл")

val TABLET_FORM: DosageForm = DosageForm(TABLET_FORM_ID, "таблетки")
val CAPSULE_FORM: DosageForm = DosageForm(CAPSULE_FORM_ID, "капсулы")

/** Снимок словаря, которым тесты собирают строки в домен. */
val VOCABULARY: Vocabulary = Vocabulary(
    units = listOf(TABLETS, MILLILITRES),
    forms = listOf(TABLET_FORM, CAPSULE_FORM)
)

fun tablets(amount: String): Quantity = Quantity(BigDecimal(amount), TABLETS)

fun millilitres(amount: String): Quantity = Quantity(BigDecimal(amount), MILLILITRES)


/** Доза: положительное количество, которое принимают за раз. */
fun dose(amount: String): Dose = Dose(tablets(amount))

fun dose(quantity: Quantity): Dose = Dose(quantity)

/** Расклад «сколько доступно мне»: чего в нём нет, про то мы не знаем (PLAN D5). */
fun availability(vararg known: Pair<Uuid, Quantity>): Availability = Availability(mapOf(*known))
