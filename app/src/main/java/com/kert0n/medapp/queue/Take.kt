package com.kert0n.medapp.queue

/**
 * Чем кончилось взятие операции в отправку: запрос собран и она в отправке — либо подготовка
 * закрыла её сама, не тревожа сервер (отказ по единице, желаемое уже так), и работнику остаётся
 * только счесть её закрытой.
 */
sealed interface Take {

    data class Sending(val operation: SyncOperation) : Take

    data class Closed(val delivery: Delivery) : Take
}
