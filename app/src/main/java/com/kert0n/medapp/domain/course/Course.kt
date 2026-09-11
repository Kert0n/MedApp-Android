package com.kert0n.medapp.domain.course

import com.kert0n.medapp.domain.pack.Availability
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.value.Dose
import com.kert0n.medapp.domain.value.Doses
import com.kert0n.medapp.domain.value.Quantity
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Курс — лечение, которым пользуются прямо сейчас: назначение и пачки, из которых оно берётся.
 * Сущность, тождество — [id] эпизода, общее с его записью. Состояний нет: живой план всегда
 * действующий, а закончившееся лечение планом быть перестаёт — план уничтожается, остаётся
 * [CourseRecord] (PLAN D5). Имя лечения живёт там же, а не здесь: так называют лечение, а не
 * расписание.
 *
 * Все вопросы о лечении задаются курсу — обеспечение, предел ползунка, зажим при нехватке,
 * пересчёт после приёма, порядок расхода, — потому что он один владеет и дозой, и препаратом.
 * На входе пачка, на выходе её идентификатор: подставить вместо пачки форму или единицу нечем,
 * а самих пачек курс не хранит.
 */
class Course(
    val id: Uuid,
    val prescription: Prescription,
    val medicine: CourseMedicine,
    val revision: Revision = Revision.initial,
    val createdAt: Instant,
    val updatedAt: Instant
) {

    init {
        require(prescription.dose.unitId == medicine.unitId) {
            "доза измеряется единицей источников курса"
        }
    }

    /** Разовая доза: назначение врача, а не подсказка упаковки (PLAN D5, C1). */
    val dose: Dose get() = prescription.dose

    val schedule: CourseSchedule get() = prescription.schedule

    val sources: List<CourseSource> get() = medicine.sources

    val formId: Uuid? get() = medicine.formId

    val unitId: Uuid? get() = medicine.unitId

    val allocatedDosesTotal: Doses get() = medicine.allocatedTotal

    /**
     * Выделение пачки **в единицах пачки** — та самая величина, которую видит серверная бронь:
     * целевой объём равен `allocatedDoses × dose` (PLAN D5). `null` — пачка не в препарате курса.
     */
    fun allocatedOf(pkg: Package): Quantity? =
        medicine.allocatedTo(pkg.id)?.let { dose * it }

    /** Пачки действующего курса менять можно: это не изменение дозы или календаря (PLAN D5). */
    fun attach(pkg: Package, doses: Doses, at: Instant): Result<Course> =
        medicine.attach(pkg, doses)
            .map { changed(medicine = it, revision = revision.next(), updatedAt = at) }

    /**
     * Отвязка последней пачки форму и единицу не забывает: в них записаны доза и прошлые приёмы.
     * Курс просто становится необеспеченным (PLAN D5).
     */
    fun detach(pkg: Package, at: Instant): Course = changed(
        medicine = medicine.detach(pkg.id, forgetFormWhenEmpty = false),
        revision = revision.next(),
        updatedAt = at
    )

    fun reorder(from: Int, to: Int, at: Instant): Course {
        val moved = medicine.reorder(from, to)
        if (moved == medicine) return this
        return changed(medicine = moved, revision = revision.next(), updatedAt = at)
    }

    fun allocate(pkg: Package, doses: Doses, at: Instant): Course = changed(
        medicine = medicine.allocate(pkg.id, doses),
        revision = revision.next(),
        updatedAt = at
    )

    /**
     * Обеспечение курса: на сколько из оставшихся приёмов хватит пачек препарата и с какого приёма
     * не хватает (PLAN D5).
     */
    fun coverage(
        remaining: List<ScheduledOccurrence>,
        availability: Availability
    ): CourseCoverage = medicine.coverage(dose, remaining, availability)

    /**
     * Верхняя граница ползунка пачки в целых дозах: меньшее из того, что пачка даёт, и того, что
     * потребность оставляет сверх выделенного остальным (PLAN D5).
     */
    fun maxDoses(pkg: Package, required: Doses, availability: Availability): Doses =
        medicine.maxDoses(pkg.id, dose, required, availability)

    /**
     * Курс с выделениями, зажатыми под нехватку и оставшуюся потребность. Доза, расписание и даты
     * не меняются — расписание это намерение человека, и чужое действие его не переписывает (C1).
     *
     * Зажимать нечего — возвращает себя: пересчёт идёт после каждого изменения входов (D5), и
     * поднимать редакцию на каждом было бы шумом в истории пунктов.
     */
    fun clamped(required: Doses, availability: Availability, at: Instant): Course {
        val clamped = medicine.clampedTo(dose, required, availability)
        if (clamped == medicine) return this
        return changed(medicine = clamped, revision = revision.next(), updatedAt = at)
    }

    /**
     * Сколько целых доз остаётся выделено пачке после подтверждённого приёма: не больше
     * выделенного за вычетом расхода и не больше того, что в пачке осталось (PLAN D5).
     */
    fun dosesAfterIntake(pkg: Package, taken: Dose, availableAfter: Quantity): Doses =
        medicine.dosesAfterIntake(pkg.id, dose, taken, availableAfter)

    /**
     * Из каких пачек уйдут следующие [doses] доз — по одной пачке на дозу, в порядке расходования:
     * сверху вниз, каждая пачка не больше выделенного и не больше целых доз, что в ней есть.
     * `null` — доза не обеспечена: полная доза «неизвестно откуда» не записывается, и пачки вне
     * препарата не подставляются (PLAN D5).
     *
     * Раскладку по конкретным приёмам делает сценарий: какие пункты ещё не отвечены и в каком они
     * порядке — его знание, а курс отвечает, из чего они возьмутся. Спрашивать у курса список
     * приёмов значило бы тянуть в него чужой агрегат ради двух проверок.
     */
    fun spendOrder(doses: Doses, availability: Availability): List<Uuid?> {
        val fromPacks = medicine.spend(dose, doses, availability)
            .flatMap { (packageId, taken) -> List(taken.count) { packageId } }
        return List(doses.count) { fromPacks.getOrNull(it) }
    }

    /**
     * Сколько уйдёт из каждой пачки на следующие [doses] доз. Пачек, из которых не уходит ничего,
     * в ответе нет; это тот же расход, что и [spendOrder], только величинами.
     */
    fun spending(doses: Doses, availability: Availability): Map<Uuid, Quantity> =
        medicine.spend(dose, doses, availability).mapValues { (_, taken) -> dose * taken }

    private fun changed(
        medicine: CourseMedicine = this.medicine,
        revision: Revision = this.revision,
        updatedAt: Instant = this.updatedAt
    ): Course = Course(
        id = id,
        prescription = prescription,
        medicine = medicine,
        revision = revision,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    /** Тождество — [id]: курс с переставленными пачками остаётся тем же курсом. */
    override fun equals(other: Any?): Boolean =
        this === other || (other is Course && other.id == id)

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "Course(id=$id, dose=$dose)"
}
