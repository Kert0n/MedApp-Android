package com.kert0n.medapp.storage.server

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import java.time.Instant

/**
 * Что уже показано. Без журнала ежедневная проверка сообщала бы об одной и той же просрочке
 * каждый день, а после долгого простоя выдала бы залпом очередь старых напоминаний (PLAN F1, D8).
 *
 * Ключ и вид приходят строками: понятия уведомления — `NotificationKey`, `NotificationKind`,
 * `NoticeDelivery` — заводятся вместе со своим отправителем в PR 11. Журнал знает ровно одно:
 * это уже показывали или ещё нет.
 *
 * Способ доставки входит в ключ, потому что баннер в приложении и системное уведомление — два
 * разных показа одного события, и один не отменяет другой.
 */
@Entity(
    tableName = "notification_log",
    primaryKeys = ["key", "delivery"],
    indices = [Index("shown_at")]
)
class NotificationLogStorageEntity(
    val key: String,
    val delivery: String,
    val kind: String,
    @ColumnInfo(name = "shown_at") val shownAt: Instant
)
