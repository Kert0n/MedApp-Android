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
     * обязательное `addedAt` домена не бывает пустым (PLAN E4, F1). Меньшая версия большую не
     * откатывает (PLAN E1): запоздалый снимок ложится, только если он не старее того, что есть.
     * `false` — снимок старее и не применён.
     */
    /**
     * Разрешённый снимок целиком: серверная часть и картина броней вместе, потому что порознь с
     * провода они не приходят. Запоздалый снимок не перекрывает свежий — ни состояние, ни брони.
     */
    @Transaction
    suspend fun applySnapshot(pack: PackageStorageEntity, claims: ClaimsStorageEntity?, observedAt: Instant): Boolean {
        if (!applyServerSnapshot(pack, observedAt)) return false
        claims?.let { upsertClaims(it) }
        return true
    }

    @Transaction
    suspend fun applyServerSnapshot(pack: PackageStorageEntity, observedAt: Instant): Boolean {
        val known = serverVersionOf(pack.id)
        val incoming = pack.version
        if (known != null && incoming != null && incoming < known) return false
        upsertServerPart(pack)
        insertDetailsIfMissing(observedPackageDetails(pack.id, observedAt))
        return true
    }

    @Query("SELECT version FROM packages WHERE id = :id")
    suspend fun serverVersionOf(id: Uuid): Long?


    /**
     * Один запрос отвечает на поиск, фильтр и сортировку сразу, и просроченные идут первыми при
     * любой сортировке: порядок нажатий на экране результат не меняет (PLAN H4).
     *
     * `HasFree` сюда не приходит — он не выражается запросом (см. `PackageQuery.Filter`), и
     * репозиторий накладывает его поверх выборки.
     *
     * Чтение, а не поток: свободное складывается ещё и из очереди с выделениями, и брать их
     * порознь нельзя. Поток строит репозиторий — из уведомлений об изменении таблиц.
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
    suspend fun query(
        medKitId: Uuid?,
        text: String,
        filter: String,
        today: LocalDate,
        until: LocalDate?,
        category: String?,
        formId: Uuid?,
        sort: String,
        includeArchived: Boolean
    ): List<PackageStorageRow>

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

    /**
     * Выделения активных курсов по названным пачкам. Активность видна по назначению: черновик
     * пачку не занимает, и его выделения в расчёт свободного не входят (PLAN D5, F1).
     */
    @Query(
        """
        SELECT s.package_id AS package_id, s.allocated_doses AS allocated_doses,
               c.dose_amount AS dose_amount, c.unit_id AS unit_id
        FROM course_sources s
        JOIN active_package_assignments a
          ON a.package_id = s.package_id AND a.course_id = s.course_id
        JOIN courses c ON c.id = s.course_id
        WHERE s.package_id IN (:packageIds)
          AND c.dose_amount IS NOT NULL AND c.unit_id IS NOT NULL
        """
    )
    suspend fun allocationsOf(packageIds: List<Uuid>): List<PackageAllocationRow>

    @Query("DELETE FROM packages WHERE id = :id")
    suspend fun delete(id: Uuid)
}
