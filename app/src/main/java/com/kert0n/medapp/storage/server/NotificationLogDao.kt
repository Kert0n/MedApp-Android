package com.kert0n.medapp.storage.server

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.time.Instant

@Dao
interface NotificationLogDao {

    /**
     * Первый показ побеждает: повторная запись того же ключа ничего не меняет, и момент
     * остаётся тем, когда человек это увидел (PLAN D8).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun remember(shown: NotificationLogStorageEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM notification_log WHERE key = :key AND delivery = :delivery)")
    suspend fun wasShown(key: String, delivery: String): Boolean

    @Query("SELECT * FROM notification_log WHERE key = :key AND delivery = :delivery")
    suspend fun find(key: String, delivery: String): NotificationLogStorageEntity?

    @Query("SELECT * FROM notification_log WHERE kind = :kind ORDER BY shown_at")
    suspend fun ofKind(kind: String): List<NotificationLogStorageEntity>

    /** Показанное уведомление снимается вместе со своим поводом: курс отменён, приём отвечен. */
    @Query("DELETE FROM notification_log WHERE key = :key")
    suspend fun forget(key: String)

    @Query("DELETE FROM notification_log WHERE shown_at < :before")
    suspend fun forgetShownBefore(before: Instant)
}
