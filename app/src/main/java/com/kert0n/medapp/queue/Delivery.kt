package com.kert0n.medapp.queue

import com.kert0n.medapp.network.pack.PackageSnapshotNetworkDTO

/**
 * Чем кончилась отправка операции — ровно те случаи, которые хранилище записывает по-разному.
 * Неопределённости среди них нет: обрыв — это [Retry], а не состояние; «устарело» — известный
 * исход, после которого запрос готовится заново ([Stale]), и от [Retry] он отличается тем, что
 * повторяется **новый** запрос по полученному состоянию, а не тот же.
 */
sealed interface Delivery {

    /** Сервер сделал, что просили. [state] — истина по пачке: снимок, «пачки нет», ничего у аптечки. */
    data class Applied(val state: PackageState) : Delivery

    /**
     * Версия устарела, и команда хочет заново: снимок применяется, запрос сбрасывается, операция
     * снова ждёт и готовится по свежему состоянию под тем же номером (PLAN E3).
     */
    data class Stale(val snapshot: PackageSnapshotNetworkDTO) : Delivery

    /** Сервер делать не будет. [state] — истина, прочитанная следом, где её было чем прочитать. */
    data class Refused(val reason: RefusalReason, val state: PackageState) : Delivery

    /** Ответа не было: связь, сервер, ограничение частоты, ответ не по форме. Повтор тем же запросом. */
    data class Retry(val error: String) : Delivery

    /** Пачки или аптечки на сервере для нас больше нет: отправлять некуда. */
    data object AccessLost : Delivery
}
