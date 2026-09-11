package com.kert0n.medapp.queue

import com.kert0n.medapp.network.pack.PackageSnapshotNetworkDTO

/**
 * Чем кончилась отправка операции — ровно те случаи, которые хранилище записывает по-разному.
 * Неопределённости среди них нет: обрыв — это [Retry], а не состояние.
 */
sealed interface Delivery {

    /**
     * Отправка закончена: сервер применил команду или отверг её по предусловию — [refusal]
     * называет отказ. [snapshot] — истина по пачке, прочитанная ответом или снимком следом;
     * `null` у команд, за которыми пачки на сервере больше нет, и у команд аптечки.
     */
    data class Done(val snapshot: PackageSnapshotNetworkDTO?, val refusal: String? = null) : Delivery

    /** Ответа не было: связь, сервер, ограничение частоты. Операция ждёт повтора тем же запросом. */
    data class Retry(val error: String) : Delivery

    /** Пачки или аптечки на сервере для нас больше нет: отправлять некуда. */
    data object AccessLost : Delivery
}
