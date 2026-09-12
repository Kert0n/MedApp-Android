package com.kert0n.medapp.presentation.value

import com.kert0n.medapp.domain.value.QuantityUnit
import kotlin.uuid.Uuid

/**
 * Единица словаря глазами экрана: номер и имя, с равенством по содержимому.
 *
 * Домен различает единицы по тождеству — переименованная единица та же самая, и количества в
 * ней складываются (PLAN D1). Экран показывает **имя**, и переименование для него — новость:
 * состояние с доменной единицей внутри осталось бы равным прежнему, и `StateFlow` не перерисовал
 * бы ни одной строки. Поэтому у представления своя величина.
 */
data class UnitPresentationDTO(val id: Uuid, val name: String)

fun QuantityUnit.toPresentationDTO(): UnitPresentationDTO = UnitPresentationDTO(id, name)
