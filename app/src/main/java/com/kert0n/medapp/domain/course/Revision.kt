package com.kert0n.medapp.domain.course

/**
 * Редакция курса: чем отличается нынешний состав расписания и источников от прежнего (PLAN D5).
 *
 * Растёт при изменении расписания, дозы и стека — и **не** растёт при переименовании: приёмы
 * связаны с редакцией через `CourseIntake.courseRevision`, и поднимать её из-за исправленной
 * опечатки значило бы объявить уже материализованные пункты устаревшими.
 *
 * Величина, а не `Long`: «не бывает отрицательной» — правило самой редакции, и записано оно здесь
 * один раз, а не в каждом виде курса. Заодно редакцию нельзя перепутать с любым другим числом,
 * которое пункт приёма о себе хранит.
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
