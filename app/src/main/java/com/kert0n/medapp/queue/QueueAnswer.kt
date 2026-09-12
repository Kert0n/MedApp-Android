package com.kert0n.medapp.queue

import com.kert0n.medapp.network.pack.ClaimNetworkDTO
import com.kert0n.medapp.network.pack.PackageSnapshotNetworkDTO

/**
 * Успешный ответ сервера, прочитанный по форме, которую ждала команда ([Expected]). Случаи —
 * ровно те, что хранилище применяет по-разному: снимок ложится поверх подтверждённого, «пачки
 * нет» архивирует её, бронь и «ничего» требуют чтения снимка следом либо не касаются пачки.
 */
sealed interface QueueAnswer {

    data class Snapshot(val snapshot: PackageSnapshotNetworkDTO) : QueueAnswer

    /** Ноль байтов там, где команда его ждала: пачки на сервере больше нет. */
    data object Gone : QueueAnswer

    data class Claim(val claim: ClaimNetworkDTO) : QueueAnswer

    data object Nothing : QueueAnswer
}
