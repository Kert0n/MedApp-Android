package com.kert0n.medapp.domain.course

/**
 * Редакция курса — номер состава расписания, дозы и пачек, с которым связаны порождённые пункты
 * (`CourseIntake.courseRevision`). Растёт при их изменении и не растёт при переименовании:
 * опечатка пунктов не меняет.
 */
@JvmInline
value class Revision(val number: Long) : Comparable<Revision> {

    init {
        require(number >= 0) { "редакция курса не бывает отрицательной: $number" }
    }

    /** Следующая редакция: состав будущих пунктов изменился. */
    fun next(): Revision = Revision(number + 1)

    override fun compareTo(other: Revision): Int = number.compareTo(other.number)

    companion object {
        val initial: Revision = Revision(0)
    }
}
