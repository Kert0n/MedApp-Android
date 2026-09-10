package com.kert0n.medapp.storage.pack

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import java.time.Instant
import java.time.LocalDate
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


    /**
     * Один запрос отвечает на поиск, фильтр и сортировку сразу, и просроченные идут первыми при
     * любой сортировке: порядок нажатий на экране результат не меняет (PLAN H4).
     *
     * `HasFree` сюда не приходит — он не выражается запросом (см. `PackageQuery.Filter`), и
     * репозиторий накладывает его поверх выборки.
     */
    @Transaction
    @Query(
        """
        SELECT p.* FROM packages p
        JOIN package_details d ON d.package_id = p.id
        LEFT JOIN active_package_assignments a ON a.package_id = p.id
        WHERE (:includeArchived OR p.lifecycle = 'ACTIVE')
          AND (:medKitId IS NULL OR p.med_kit_id = :medKitId)
          AND (:text = '' OR p.name_search LIKE '%' || :text || '%')
          AND (
            :filter = 'NONE'
            OR (:filter = 'EXPIRED' AND d.expires_on IS NOT NULL AND d.expires_on < :today)
            OR (
              :filter = 'EXPIRING'
              AND d.expires_on IS NOT NULL
              AND d.expires_on >= :today
              AND d.expires_on <= :until
            )
            OR (:filter = 'ON_COURSE' AND a.package_id IS NOT NULL)
            OR (:filter = 'CATEGORY' AND p.category = :category)
            OR (:filter = 'FORM' AND p.form_id = :formId)
          )
        ORDER BY
          CASE WHEN d.expires_on IS NOT NULL AND d.expires_on < :today THEN 0 ELSE 1 END,
          CASE WHEN :sort = 'EXPIRY' THEN (d.expires_on IS NULL) END,
          CASE WHEN :sort = 'EXPIRY' THEN d.expires_on END,
          CASE WHEN :sort = 'ADDED_AT' THEN -d.added_at END,
          CASE WHEN :sort = 'QUANTITY' THEN p.quantity_unit_id END,
          CASE WHEN :sort = 'QUANTITY' THEN p.quantity_sort END,
          p.name_search
        """
    )
    fun query(
        medKitId: Uuid?,
        text: String,
        filter: String,
        today: LocalDate,
        until: LocalDate?,
        category: String?,
        formId: Uuid?,
        sort: String,
        includeArchived: Boolean
    ): Flow<List<PackageStorageRow>>

    @Upsert
    suspend fun upsertServerPart(pack: PackageStorageEntity)

    @Upsert
    suspend fun upsertDetails(details: PackageDetailsStorageEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDetailsIfMissing(details: PackageDetailsStorageEntity)

    @Upsert
    suspend fun upsertClaims(claims: ClaimsStorageEntity)

    /** Утрата доступа и снятие публикации не оставляют картины броней: её больше не существует. */
    @Query("DELETE FROM claims WHERE package_id = :packageId")
    suspend fun deleteClaims(packageId: Uuid)

    @Query("DELETE FROM packages WHERE id = :id")
    suspend fun delete(id: Uuid)
}
