package com.kert0n.medapp.feature.course

import com.kert0n.medapp.domain.course.Course
import com.kert0n.medapp.domain.course.CourseCompletion
import com.kert0n.medapp.domain.pack.PackageRef
import com.kert0n.medapp.queue.QueueService
import com.kert0n.medapp.queue.QueuedCommand
import com.kert0n.medapp.queue.pack.PackageSyncCommand
import com.kert0n.medapp.storage.course.CourseStorageRepository
import java.time.Instant
import javax.inject.Inject
import kotlin.uuid.Uuid

/**
 * Конец эпизода в базе: запись закрывается вместе с удалением плана, брони снимаются со всех
 * источников (PLAN D5, F5). Зовётся внутри транзакции сценария, который двинул прогресс до
 * конца, — подтверждение приёма сегодня, доза мимо плана и правка числа доз потом; что конец
 * наступил, решает [CourseCompletion], здесь только запись.
 */
class CourseClosing @Inject constructor(
    private val courses: CourseStorageRepository,
    private val queue: QueueService
) {

    /**
     * [except] — пачка, чьё снятие брони уже уехало зависимым от расхода: второй раз его не
     * ставят.
     */
    suspend fun close(course: Course, closing: CourseCompletion.Closing, at: Instant, except: PackageRef? = null) {
        courses.close(closing.record, closing.cancelled)
        for (source in course.sources) {
            if (source.pkg == except) continue
            val released = QueuedCommand(Uuid.random(), PackageSyncCommand.ReleaseClaim(source.pkg.id))
            queue.change(source.pkg.medKit, listOf(released), at) { true }
        }
    }
}
