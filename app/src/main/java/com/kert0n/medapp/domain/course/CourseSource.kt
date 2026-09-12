package com.kert0n.medapp.domain.course

import com.kert0n.medapp.domain.pack.PackageRef
import com.kert0n.medapp.domain.value.Doses

/**
 * Пачка препарата курса и сколько целых доз из неё ещё выделено — оставшееся выделение, а не
 * первоначальное: подтверждённая доза уменьшает его на одну. Величина: тождество даёт пара «курс и
 * пачка», а место в препарате — очередь расходования (PLAN D5). Пачка — ссылкой: курс, читая
 * себя, читает и её, но списать из неё через курс нечем.
 */
data class CourseSource(
    val pkg: PackageRef,
    val allocatedDoses: Doses
)
