package com.kert0n.medapp.network.pack

/**
 * Шаблон количества на проводе (PLAN B2): не больше 13 разрядов до точки и 6 после, без знака и
 * экспоненты. Проверяется в сетевом слое, потому что обещание даёт запрос, а не величина.
 */
private val NETWORK_AMOUNT = Regex("""^\d{1,13}(\.\d{1,6})?$""")

internal fun requireNetworkAmount(amount: String, field: String) {
    require(NETWORK_AMOUNT.matches(amount)) { "$field: не десятичная строка контракта B2" }
}

/**
 * Пустая строка законна только в PATCH — это очистка, и потому у него своя проверка, а не
 * доменная: `requireText` пустую строку отвергает, потому что в домене она не значит ничего.
 * Пробелы не значат ни того, ни другого ни там, ни здесь.
 */
internal fun requireClearable(value: String?, maxLength: Int, field: String) {
    if (value.isNullOrEmpty()) return
    require(value.isNotBlank()) { "$field: пробелы не являются ни значением, ни очисткой" }
    require(value.length <= maxLength) { "$field: длиннее $maxLength символов" }
}
