package com.kert0n.medapp.storage.pack

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import java.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow

@Dao
interface PackageDao {

    @Transaction
    @Query("SELECT * FROM packages WHERE id = :id")
    fun observe(id: Uuid): Flow<PackageStorageRow?>

    @Transaction
    @Query("SELECT * FROM packages WHERE med_kit_id = :medKitId ORDER BY name")
    fun observeOfMedKit(medKitId: Uuid): Flow<List<PackageStorageRow>>

    @Transaction
    @Query("SELECT * FROM packages WHERE id = :id")
    suspend fun find(id: Uuid): PackageStorageRow?

    /**
     * Пачка целиком: обе её строки пишутся одной транзакцией, потому что упаковка без личных
     * сведений — не половина пачки, а несуществующее состояние (PLAN F1, F5).
     */
    @Transaction
    suspend fun save(pack: PackageStorageEntity, details: PackageDetailsStorageEntity) {
        require(pack.id == details.packageId) { "строки одной пачки называют один идентификатор" }
        upsertServerPart(pack)
        upsertDetails(details)
    }

    /**
     * Снимок переписывает серверную строку целиком и не касается личных сведений: они лежат в
     * другой таблице. Недостающая строка деталей создаётся моментом первого наблюдения —
     * обязательное `addedAt` домена не бывает пустым (PLAN E4, F1).
     */
    @Transaction
    suspend fun applyServerSnapshot(pack: PackageStorageEntity, observedAt: Instant) {
        upsertServerPart(pack)
        insertDetailsIfMissing(observedPackageDetails(pack.id, observedAt))
    }

    @Upsert
    suspend fun upsertServerPart(pack: PackageStorageEntity)

    @Upsert
    suspend fun upsertDetails(details: PackageDetailsStorageEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDetailsIfMissing(details: PackageDetailsStorageEntity)

    @Query("DELETE FROM packages WHERE id = :id")
    suspend fun delete(id: Uuid)
}
