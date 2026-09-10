package com.kert0n.medapp.domain.model.course

import kotlin.uuid.Uuid

/**
 * Курс, которому назначена упаковка, — ровно настолько, насколько о нём нужно знать со стороны
 * пачки: как называется и сколько доз из этой пачки за ним выделено.
 *
 * Величина, а не сущность: это выписка, а не сам курс. Полный [Course] здесь был бы обратной
 * зависимостью объяснения брони (`ClaimOwnership`) от всего стека источников, а строке списка
 * нужно название и число.
 */
data class CourseBrief(
    val id: Uuid,
    val title: String,
    val allocatedDoses: Int
) {
    init {
        require(allocatedDoses >= 0) { "выделение не бывает отрицательным" }
    }
}
