package com.kert0n.medapp.domain.course

import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.value.Doses

/**
 * Пачка препарата курса и сколько целых доз из неё ещё выделено — оставшееся выделение, а не
 * первоначальное: подтверждённая доза уменьшает его на одну. Величина: тождество даёт пара «курс и
 * пачка», а место в препарате — очередь расходования (PLAN D5). Пачка — объектом: курс, читая
 * себя, читает и её, и пачка та, что была в момент чтения.
 */
data class CourseSource(
    val pkg: Package,
    val allocatedDoses: Doses
)
