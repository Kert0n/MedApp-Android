package com.kert0n.medapp.domain.model.course

import com.kert0n.medapp.domain.model.pack.Package
import com.kert0n.medapp.domain.model.pack.PackageAccess
import com.kert0n.medapp.domain.model.pack.PackageLifecycle
import com.kert0n.medapp.domain.model.value.QUANTITY_MAX_INTEGER_DIGITS
import com.kert0n.medapp.domain.model.value.QUANTITY_SCALE
import com.kert0n.medapp.domain.model.value.Quantity
import com.kert0n.medapp.domain.model.value.requireNonNegativeDecimal
import com.kert0n.medapp.domain.model.value.requireOptionalText
import com.kert0n.medapp.domain.model.value.requireText
import java.math.BigDecimal
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Курс — лечение, которое человек себе назначил: сколько принимать, по какому календарю и из
 * каких пачек.
 *
 * **Курс начинается заметкой, а не расписанием.** Черновик с одним названием — законное
 * сохраняемое состояние, а не полуфабрикат: сценарий «записал у врача → купил → внёс» начинается
 * раньше, чем известны даты и пачки (PLAN D5). Поэтому ни расписания, ни дозы, ни источников у
 * него может не быть.
 *
 * **Сущность:** переименованный курс — тот же курс, и приёмы, уже порождённые им, остаются его
 * приёмами. Тождество — [id], равенство по нему.
 *
 * [sources] — стек: **порядок есть приоритет расходования** (PLAN D5). Источники расходуются
 * сверху вниз, и переход происходит только между уже подключёнными: похожие пачки не
 * подставляются и сами в стек не встают — подключает человек.
 *
 * Курс на сервер не уезжает вовсе (PLAN C0): расписаний, приёмов и курсов там нет и не будет,
 * поэтому обвязки синхронизации у него нет по построению, а не по решению. Уезжает только
 * следствие выделения — серверная бронь на упаковку (PLAN D5, E2).
 */
class Course(
    val id: Uuid,
    val title: String,
    val note: String? = null,            // «что купить», запись от врача
    val doseAmount: BigDecimal? = null,  // разовая доза курса
    val unitId: Uuid? = null,            // фиксируется первым источником
    val formId: Uuid? = null,            // фиксируется первым источником
    val schedule: CourseSchedule? = null,
    val sources: List<CourseSource> = emptyList(),  // ПОРЯДОК = приоритет расходования
    val status: CourseStatus = CourseStatus.DRAFT,
    val revision: Long = 0,
    val createdAt: Instant,
    val updatedAt: Instant
) {

    init {
        requireText(title, COURSE_TITLE_MAX_LENGTH, "Course.title")
        requireOptionalText(note, COURSE_NOTE_MAX_LENGTH, "Course.note")
        require(revision >= 0) { "редакция курса не бывает отрицательной" }
        doseAmount?.let { amount ->
            requireNonNegativeDecimal(
                amount = amount,
                field = "Course.doseAmount",
                maxScale = QUANTITY_SCALE,
                maxIntegerDigits = QUANTITY_MAX_INTEGER_DIGITS
            )
            // Нулевая доза — не лечение, а деление на ноль в обеспечении: `dosesIn` на ней бросает.
            require(amount.signum() > 0) { "разовая доза курса строго положительна" }
        }
        // Пара (курс, пачка) и есть тождество источника: второй раз та же пачка в стек не встаёт,
        // иначе у неё оказалось бы два выделения и два места в приоритете.
        require(sources.distinctBy { it.packageId }.size == sources.size) {
            "одна пачка — один источник курса"
        }
        // Форму и единицу фиксирует первый источник, поэтому непустой стек без них невозможен.
        require(sources.isEmpty() || (formId != null && unitId != null)) {
            "у курса с источниками форма и единица зафиксированы"
        }
        // Действующий курс — это доза и календарь; источников у него может не остаться вовсе:
        // отвязали последний, и курс просто стал необеспеченным (PLAN D5).
        require(status != CourseStatus.ACTIVE || (schedule != null && doseAmount != null && unitId != null)) {
            "действующий курс имеет расписание и разовую дозу"
        }
    }

    /**
     * Доза как величина — только когда известна и единица.
     *
     * Единицу фиксирует первый источник, а дозу человек задаёт сам, и порядок бывает любым:
     * у черновика законно «две штуки чего-то» без единицы и «пачка выбрана» без дозы.
     */
    val dose: Quantity?
        get() = if (doseAmount != null && unitId != null) Quantity(doseAmount, unitId) else null

    /** Сколько доз выделено по всему стеку: с этой суммой сравнивается оставшаяся потребность. */
    val allocatedDosesTotal: Int get() = sources.sumOf { it.allocatedDoses }

    /**
     * Выделение источника **в единицах пачки** — та самая величина, которую видит серверная
     * бронь: целевой объём брони равен `allocatedDoses × dose` (PLAN D5).
     *
     * `null`, когда пачка не источник этого курса или когда доза ещё не задана: выдумывать
     * количество из неизвестной дозы нельзя.
     */
    fun allocatedOf(packageId: Uuid): Quantity? {
        val source = sources.firstOrNull { it.packageId == packageId } ?: return null
        return dose?.times(source.allocatedDoses)
    }

    /**
     * Название и заметка правятся в любом состоянии, включая действующий курс: это не изменение
     * назначенного лечения (PLAN D5).
     *
     * [revision] при этом не растёт. Редакция отмечает изменение расписания и источников, и
     * приёмы связаны с ней через `Intake.courseRevision`; поднимать её на переименовании значило
     * бы объявлять уже материализованные пункты устаревшими из-за исправленной опечатки.
     */
    fun rename(title: String, note: String?, at: Instant): Course =
        changed(title = title, note = note, updatedAt = at)

    /**
     * Доза задаётся только у черновика.
     *
     * После активации доза, единица, форма и расписание неизменны: изменившееся лечение — это
     * отмена прежнего курса с сохранением истории и создание нового (PLAN D5). Иначе прошлые
     * приёмы остались бы записанными в дозе, которой у курса больше нет.
     */
    fun setDraftDose(amount: BigDecimal, at: Instant): Course {
        requireDraft("доза")
        return changed(doseAmount = amount, revision = revision + 1, updatedAt = at)
    }

    /**
     * Расписание задаётся только у черновика — по той же причине, что и доза.
     *
     * Редакция растёт: расписание меняет состав будущих пунктов, и приёмы связаны с ней через
     * `Intake.courseRevision`. Прошлые пункты при этом не пересоздаются (PLAN D5).
     */
    fun setDraftSchedule(schedule: CourseSchedule, at: Instant): Course {
        requireDraft("расписание")
        return changed(schedule = schedule, revision = revision + 1, updatedAt = at)
    }

    /**
     * Подключает пачку последней в стеке — самой низкой по приоритету расходования.
     *
     * **Форму и единицу курса задаёт первый источник**, дальше несовместимое отвергается: доза
     * измеряется единицей курса, и пачка в другой единице не подставится в приём, а форма
     * отвечает за «то же самое лекарство» (PLAN D5).
     *
     * Отказ — обычный ответ на действие человека, поэтому `Result` с причиной-перечислением:
     * «укажите форму» и «эта пачка другой формы» ведут к разным действиям.
     *
     * Источники действующего курса менять можно — это не изменение назначенной дозы или
     * календаря; завершённый и отменённый курс остаются историей.
     */
    fun attach(pkg: Package, doses: Int, at: Instant): Result<Course> {
        require(doses >= 0) { "выделение не бывает отрицательным: $doses" }
        val rejection = attachRejection(pkg)
        if (rejection != null) return Result.failure(CourseRejected(rejection))
        return Result.success(
            changed(
                unitId = pkg.quantity.unitId,
                formId = pkg.facts.formId,
                sources = sources + CourseSource(pkg.id, doses),
                revision = revision + 1,
                updatedAt = at
            )
        )
    }

    private fun attachRejection(pkg: Package): CourseRejection? = when {
        !isOpen -> CourseRejection.COURSE_CLOSED
        pkg.lifecycle != PackageLifecycle.ACTIVE ||
            pkg.access != PackageAccess.AVAILABLE -> CourseRejection.PACKAGE_UNUSABLE
        sources.any { it.packageId == pkg.id } -> CourseRejection.ALREADY_ATTACHED
        // `formId = null` не совместим с `formId = null`: это две пачки, про каждую из которых
        // мы ничего не знаем, а не одна и та же неизвестность.
        pkg.facts.formId == null -> CourseRejection.FORM_UNKNOWN
        formId != null && formId != pkg.facts.formId -> CourseRejection.FORM_MISMATCH
        unitId != null && unitId != pkg.quantity.unitId -> CourseRejection.UNIT_MISMATCH
        else -> null
    }

    /**
     * Отвязывает пачку от курса.
     *
     * **Отвязка последнего источника у действующего курса форму и единицу не сбрасывает** —
     * иначе доза и расписание мгновенно потеряли бы смысл, а состоявшиеся приёмы остались бы с
     * единицей, которой у курса больше нет. Курс просто становится необеспеченным. У черновика
     * сбрасывает: там ещё нечего терять (PLAN D5).
     */
    fun detach(packageId: Uuid, at: Instant): Course {
        requireOpen("отвязка источника")
        require(sources.any { it.packageId == packageId }) {
            "пачка $packageId не источник этого курса"
        }
        val left = sources.filterNot { it.packageId == packageId }
        val forgetForm = left.isEmpty() && status == CourseStatus.DRAFT
        return changed(
            unitId = if (forgetForm) null else unitId,
            formId = if (forgetForm) null else formId,
            sources = left,
            revision = revision + 1,
            updatedAt = at
        )
    }

    /**
     * Переставляет источник в стеке: порядок и есть приоритет расходования, поэтому
     * перетаскивание строки — содержательное действие, а не украшение списка.
     */
    fun reorder(from: Int, to: Int, at: Instant): Course {
        requireOpen("порядок источников")
        require(from in sources.indices && to in sources.indices) {
            "источника нет на позиции: $from → $to при ${sources.size} источниках"
        }
        if (from == to) return this
        val moved = sources.toMutableList()
        moved.add(to, moved.removeAt(from))
        return changed(sources = moved, revision = revision + 1, updatedAt = at)
    }

    /**
     * Задаёт выделение источника в целых дозах.
     *
     * Верхнюю границу здесь не считают: она зависит от свежих остатка, чужих броней и
     * оставшейся потребности, то есть от состояния вне курса. Её вычисляет сценарий через
     * `maxDoses` (PLAN D5, H1), а модель отвечает за то, что выделение целое и неотрицательное.
     */
    fun allocate(packageId: Uuid, doses: Int, at: Instant): Course {
        requireOpen("выделение")
        require(sources.any { it.packageId == packageId }) {
            "пачка $packageId не источник этого курса"
        }
        return changed(
            sources = sources.map {
                if (it.packageId == packageId) CourseSource(packageId, doses) else it
            },
            revision = revision + 1,
            updatedAt = at
        )
    }

    /**
     * Активация: с этого момента доза, единица, форма и расписание неизменны, а выделения
     * становятся бронями и занимают пачки (PLAN D5, F1).
     *
     * Требует расписания, дозы и хотя бы одного источника: у черновика броней нет и упаковку он
     * не занимает, подключённые источники — предварительный выбор.
     */
    fun activate(at: Instant): Result<Course> {
        val rejection = when {
            status != CourseStatus.DRAFT -> CourseRejection.NOT_DRAFT
            schedule == null -> CourseRejection.SCHEDULE_MISSING
            dose == null -> CourseRejection.DOSE_MISSING
            sources.isEmpty() -> CourseRejection.SOURCES_MISSING
            else -> null
        }
        if (rejection != null) return Result.failure(CourseRejected(rejection))
        return Result.success(changed(status = CourseStatus.ACTIVE, updatedAt = at))
    }

    /**
     * Календарь закончился и неотвеченных пунктов не осталось.
     *
     * Выделения обнуляются: оставшегося выделения у закончившегося курса нет, и это то же
     * событие, что снятие брони и освобождение назначений (PLAN D5). Сам стек остаётся —
     * история приёмов читается по нему; редакция не растёт, прошлые пункты не пересоздаются.
     */
    fun complete(at: Instant): Course {
        check(status == CourseStatus.ACTIVE) { "завершается действующий курс, а не $status" }
        return changed(sources = releasedSources(), status = CourseStatus.COMPLETED, updatedAt = at)
    }

    /**
     * Отмена — в том числе половина замены лечения: старый курс отменяется с сохранением
     * истории, новый создаётся отдельным черновиком (PLAN D5).
     *
     * Состоявшиеся приёмы не переписываются; будущие неотвеченные пункты отменяет сценарий, а не
     * модель курса — они отдельные записи со своим переходом.
     */
    fun cancel(at: Instant): Course {
        check(isOpen) { "отменяется незакрытый курс, а не $status" }
        return changed(sources = releasedSources(), status = CourseStatus.CANCELLED, updatedAt = at)
    }

    private fun releasedSources(): List<CourseSource> =
        sources.map { if (it.allocatedDoses == 0) it else CourseSource(it.packageId, 0) }

    /** Черновик или действующий курс: то, что ещё можно менять. */
    private val isOpen: Boolean
        get() = status == CourseStatus.DRAFT || status == CourseStatus.ACTIVE

    private fun requireOpen(action: String) {
        check(isOpen) { "$action недоступно для курса в состоянии $status" }
    }

    private fun requireDraft(what: String) {
        check(status == CourseStatus.DRAFT) {
            "$what действующего курса неизменна: замена лечения — это новый курс, состояние $status"
        }
    }

    /**
     * Единственный способ получить изменённый экземпляр, и он приватный.
     *
     * [id] и [createdAt] в списке отсутствуют: тождество и момент начала курса не меняются.
     */
    private fun changed(
        title: String = this.title,
        note: String? = this.note,
        doseAmount: BigDecimal? = this.doseAmount,
        unitId: Uuid? = this.unitId,
        formId: Uuid? = this.formId,
        schedule: CourseSchedule? = this.schedule,
        sources: List<CourseSource> = this.sources,
        status: CourseStatus = this.status,
        revision: Long = this.revision,
        updatedAt: Instant = this.updatedAt
    ): Course = Course(
        id = id,
        title = title,
        note = note,
        doseAmount = doseAmount,
        unitId = unitId,
        formId = formId,
        schedule = schedule,
        sources = sources,
        status = status,
        revision = revision,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    /** Тождество — [id]: переименованный курс остаётся тем же курсом. */
    override fun equals(other: Any?): Boolean =
        this === other || (other is Course && other.id == id)

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "Course(id=$id, title=$title, status=$status)"
}
