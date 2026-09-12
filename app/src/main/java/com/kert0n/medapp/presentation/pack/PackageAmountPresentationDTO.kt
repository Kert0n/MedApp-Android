package com.kert0n.medapp.presentation.pack

import com.kert0n.medapp.domain.stock.StockMovement
import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.domain.value.QuantityUnit
import com.kert0n.medapp.domain.value.Vocabulary
import com.kert0n.medapp.presentation.ParsedInput
import com.kert0n.medapp.presentation.value.QuantityPresentationDTO
import com.kert0n.medapp.presentation.value.QuantityPresentationError
import com.kert0n.medapp.presentation.value.UnitPresentationDTO
import com.kert0n.medapp.presentation.value.toDomain

/**
 * Что человек напечатал на экране пересчёта и утилизации (PLAN H3 №9): число и, для выброшенного,
 * причина с пояснением. Единицы здесь нет — она у пачки, и пересчёт её не меняет (PLAN D3).
 */
data class PackageAmountPresentationDTO(
    val amount: String = "",
    val reason: StockMovement.Disposal.Reason = StockMovement.Disposal.Reason.EXPIRED,
    val note: String = ""
)

/** Почему запись не принимается. Текст по причине берёт экран из `R.string.*`. */
sealed interface PackageAmountError {

    data class Amount(val reason: QuantityPresentationError) : PackageAmountError

    /** Утилизация нулевого количества — не событие: выбрасывать нечего. */
    data object NothingToDispose : PackageAmountError

    data object NoteTooLong : PackageAmountError
}

/**
 * Приведение к домену: число меряется единицей самой пачки, а пустое пояснение значит «не
 * сказано», а не пустую строку. Предел длины берётся у [StockMovement], которое эту заметку и
 * хранит.
 */
fun PackageAmountPresentationDTO.toDomain(
    vocabulary: Vocabulary,
    unit: QuantityUnit
): ParsedInput<CountedAmount, PackageAmountError> {
    val counted = when (
        val parsed = QuantityPresentationDTO(amount, UnitPresentationDTO(unit.id, unit.name))
            .toDomain(vocabulary)
    ) {
        is ParsedInput.Rejected -> return ParsedInput.Rejected(PackageAmountError.Amount(parsed.error))
        is ParsedInput.Parsed -> parsed.value
    }
    val note = note.trim().takeIf { it.isNotEmpty() }
    if (note != null && note.length > StockMovement.NOTE_MAX_LENGTH) {
        return ParsedInput.Rejected(PackageAmountError.NoteTooLong)
    }
    return ParsedInput.Parsed(CountedAmount(counted, note))
}

/**
 * Проверенные число и пояснение. Отдельный тип, а не пара: подставить сюда сырой ввод не
 * получится, а сценарию нужно знать, что проверка уже была.
 */
data class CountedAmount(val amount: Quantity, val note: String?)
