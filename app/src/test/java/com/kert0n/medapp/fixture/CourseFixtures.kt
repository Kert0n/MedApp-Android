package com.kert0n.medapp.fixture

import com.kert0n.medapp.domain.model.course.Course
import com.kert0n.medapp.domain.model.course.CourseStatus
import java.math.BigDecimal
import java.time.Instant
import kotlin.uuid.Uuid

val COURSE: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000051")

/** Момент, от которого тесты отсчитывают правки: сравнивать нужно изменение, а не «сейчас». */
val EARLIER: Instant = Instant.EPOCH

val LATER: Instant = Instant.EPOCH.plusSeconds(60)

/** Курс: тест называет только то, что проверяет. По умолчанию — черновик с одним названием. */
fun course(
    id: Uuid = COURSE,
    title: String = "Парацетамол, пять дней",
    note: String? = null,
    doseAmount: BigDecimal? = null,
    unitId: Uuid? = null,
    formId: Uuid? = null,
    status: CourseStatus = CourseStatus.DRAFT,
    revision: Long = 0,
    createdAt: Instant = EARLIER,
    updatedAt: Instant = EARLIER
) = Course(
    id = id,
    title = title,
    note = note,
    doseAmount = doseAmount,
    unitId = unitId,
    formId = formId,
    status = status,
    revision = revision,
    createdAt = createdAt,
    updatedAt = updatedAt
)
