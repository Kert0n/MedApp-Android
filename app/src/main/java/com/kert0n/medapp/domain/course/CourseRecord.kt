package com.kert0n.medapp.domain.course

import com.kert0n.medapp.domain.value.requireOptionalText
import com.kert0n.medapp.domain.value.requireText
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Запись об эпизоде лечения: как человек его назвал, что было назначено, когда началось и чем
 * кончилось. Заводится в момент активации и живёт **вечно** — план, которым пользуются, к этому
 * моменту только начинается, а закончившись, уничтожается (PLAN D5).
 *
 * Отсюда два следствия. Аналитика читает записи и ничего больше: идущее и законченное лечение для
 * неё одинаковы по форме, и союза «курсы плюс архив» не нужно. И ссылка приёма
 * (`CourseIntake.courseId`) никогда не повисает: [id] — тождество **эпизода**, общее у записи и
 * плана, поэтому «что принималось по этому поводу» отвечается ею одной, без единого признака в
 * самом приёме.
 *
 * Название и заметка живут здесь, а не в плане: так называют лечение, а не расписание, и второго
 * места, где то же имя может разойтись, нет. Назначение — снимок [Prescription]: пока план жив,
 * оно то же самое, а после него остаётся только здесь.
 *
 * Сущность: тождество — [id]. Состав пачек в записи не хранится — из какой пачки фактически
 * приняли, записано в самом приёме, а очередь расходования после конца лечения ничего не значит.
 */
class CourseRecord(
    val id: Uuid,
    val title: String,
    val note: String? = null,
    val prescription: Prescription,
    val startedAt: Instant,
    val outcome: Outcome? = null,
    val closedAt: Instant? = null
) {

    init {
        requireText(title, TITLE_MAX_LENGTH, "CourseRecord.title")
        requireOptionalText(note, NOTE_MAX_LENGTH, "CourseRecord.note")
        // Исход и момент — одно событие: «закончилось неизвестно когда» и «когда-то кончилось
        // неизвестно чем» это не состояния лечения, а потерянная запись.
        require((outcome == null) == (closedAt == null)) {
            "исход лечения и момент его конца бывают только вместе"
        }
        require(closedAt == null || !closedAt.isBefore(startedAt)) {
            "лечение не кончается раньше, чем началось: $startedAt — $closedAt"
        }
    }

    /** Лечение идёт: план для него ещё существует. */
    val isOpen: Boolean get() = outcome == null

    /** Название и заметка правятся всегда: это не изменение назначенного лечения (PLAN D5). */
    fun rename(title: String, note: String?): CourseRecord =
        CourseRecord(id, title, note, prescription, startedAt, outcome, closedAt)

    /**
     * Лечение закончилось — календарём или решением человека. План после этого уничтожается, а
     * запись остаётся: состоявшиеся приёмы, их времена и количества не переписываются.
     */
    fun close(outcome: Outcome, at: Instant): CourseRecord {
        check(isOpen) { "лечение уже закончено: ${this.outcome}" }
        return CourseRecord(id, title, note, prescription, startedAt, outcome, at)
    }

    /** Тождество — [id]: переименованная запись остаётся записью того же эпизода. */
    override fun equals(other: Any?): Boolean =
        this === other || (other is CourseRecord && other.id == id)

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "CourseRecord(id=$id, title=$title, outcome=$outcome)"

    /** Чем кончилось лечение. Третьего исхода нет: идущее лечение — это отсутствие исхода. */
    enum class Outcome {

        /** Календарь закончился, неотвеченных пунктов не осталось. */
        COMPLETED,

        /** Человек отменил — в том числе как половину замены лечения (PLAN D5). */
        CANCELLED
    }

    companion object {

        /** Имя принадлежит эпизоду, поэтому его предел объявлен здесь; черновик — заготовка. */
        const val TITLE_MAX_LENGTH = 200

        /** Длиннее названия: сюда переписывают запись от врача и «что купить». */
        const val NOTE_MAX_LENGTH = 500
    }
}
