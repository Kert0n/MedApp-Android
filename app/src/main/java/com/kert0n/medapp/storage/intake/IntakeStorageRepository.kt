package com.kert0n.medapp.storage.intake

import com.kert0n.medapp.domain.intake.CourseIntake
import com.kert0n.medapp.domain.intake.Intake
import com.kert0n.medapp.network.intake.IntakeSyncState
import java.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow

/**
 * Хранение приёмов. Учёт расхода едет рядом отдельным значением: правила о приёме его не
 * читают, и в доменный тип он не входит (PLAN D6).
 */
interface IntakeStorageRepository {

    fun observeOfCourse(courseId: Uuid): Flow<List<Intake>>

    suspend fun find(id: Uuid): Intake?

    suspend fun syncStateOf(id: Uuid): IntakeSyncState?

    /**
     * Запись приёма как есть — например, при приведении к серверному снимку (PLAN E4). Ответ
     * человека идёт через [record]: там условный переход статуса, а здесь его нет.
     */
    suspend fun save(recorded: RecordedIntake)

    /**
     * Материализация окна: повторный проход не заводит второй такой же пункт — тождество даёт
     * курс и исходные дата и время (PLAN F4). Возвращает число заведённых пунктов.
     */
    suspend fun materialise(planned: List<CourseIntake>): Int

    suspend fun plannedBefore(until: Instant): List<CourseIntake>

    /**
     * Ответ на приём целиком: условный переход статуса, локальный остаток либо команда расхода,
     * движение, пересчитанные выделения курса и учёт — одной транзакцией (PLAN F5).
     *
     * `false` означает, что приём уже записан: условный переход не нашёл ожидаемого статуса либо
     * внеплановый приём с тем же тождеством уже заведён, и повтор ничего не списал.
     */
    suspend fun record(outcome: IntakeOutcome): Boolean
}
