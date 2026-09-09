package com.kert0n.medapp.domain.model

import java.math.BigDecimal
import kotlin.uuid.Uuid

/**
 * Постоянные идентификаторы вместо случайных: упавший тест должен читаться по сообщению,
 * а не воспроизводиться заново со своим набором UUID.
 */
val TABLETS: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000001")
val MILLILITRES: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000002")

fun tablets(amount: String): Quantity = Quantity(BigDecimal(amount), TABLETS)

fun millilitres(amount: String): Quantity = Quantity(BigDecimal(amount), MILLILITRES)

val HOME_KIT: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000011")
val SHARED_KIT: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000012")
