package com.kert0n.medapp.fixture

import com.kert0n.medapp.domain.calc.availability.Availability
import com.kert0n.medapp.domain.model.value.Doses
import com.kert0n.medapp.domain.model.value.Quantity
import java.math.BigDecimal
import kotlin.uuid.Uuid

/**
 * Постоянные идентификаторы вместо случайных: упавший тест должен читаться по сообщению,
 * а не воспроизводиться заново со своим набором UUID.
 */
val TABLETS: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000001")
val MILLILITRES: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000002")

val TABLET_FORM: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000041")
val CAPSULE_FORM: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000042")

fun tablets(amount: String): Quantity = Quantity(BigDecimal(amount), TABLETS)

fun millilitres(amount: String): Quantity = Quantity(BigDecimal(amount), MILLILITRES)

fun doses(count: Int): Doses = Doses(count)

/** Расклад «сколько доступно мне»: чего в нём нет, про то мы не знаем (PLAN D5). */
fun availability(vararg known: Pair<Uuid, Quantity>): Availability = Availability(mapOf(*known))
