package com.kert0n.medapp.queue.medkit

import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.network.pack.PackageSnapshot
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Что публикации нужно от хранилища — и только это (PLAN E5). Публикация — сетевое действие
 * между двумя транзакциями: прочитать аптечку с содержимым, записать переключение вместе с
 * первыми подтверждёнными остатками. Реализация живёт в хранении; сеть про порт не знает.
 */
interface PublicationStorage {

    suspend fun medKit(id: Uuid): MedKit?

    /** Живые пачки аптечки — то, что уедет на сервер. */
    suspend fun contentsOf(medKitId: Uuid): List<Package>

    /**
     * Момент передачи ответственности серверу: аптечка становится опубликованной, а ответы на
     * создание её пачек — первым подтверждённым остатком и версиями — одной транзакцией.
     *
     * Пачки читает и ответ пишет один владелец: между чтением и записью человек продолжал жить,
     * и если какая-то пачка успела измениться или появиться, старый серверный снимок лёг бы
     * поверх нового местного расхода. Тогда не пишется ничего — `false`, публиковать заново;
     * повтор идемпотентен по идентификаторам.
     */
    suspend fun published(medKit: MedKit, snapshots: List<PackageSnapshot>, at: Instant): Boolean
}
