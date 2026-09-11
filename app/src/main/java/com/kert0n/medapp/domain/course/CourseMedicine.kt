package com.kert0n.medapp.domain.course

import com.kert0n.medapp.domain.pack.Availability
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.value.Dose
import com.kert0n.medapp.domain.value.Doses
import com.kert0n.medapp.domain.value.doses
import com.kert0n.medapp.domain.value.Quantity
import java.util.Objects
import kotlin.uuid.Uuid

/**
 * Препарат курса: пачки, которые человек, выбрав их источниками, объявил одним лекарством. Форму
 * и единицу задаёт первая пачка; порядок пачек — порядок расходования; каждой выделено целое
 * число доз, потому что доза берётся из одной пачки и между пачками не делится (PLAN D5).
 * Взаимозаменяемость приложение не выводит (C2), поэтому вопросы обеспечения задаются препарату
 * целиком. Разовую дозу знает курс и передаёт аргументом; [Availability] без числа по пачке
 * означает «неизвестно», и такая пачка ничего не обеспечивает, но выделение сохраняет.
 *
 * Состав адресуется идентификаторами, а не пачками: препарат хранит именно их, и держать внутри
 * себя чужие сущности ему незачем. Наружу этим не пользуются — публичная сторона у лечения одна,
 * и это курс: он принимает пачку и спрашивает препарат уже её идентификатором.
 */
class CourseMedicine(
    sources: List<CourseSource> = emptyList(),
    val formId: Uuid? = null,
    val unitId: Uuid? = null
) {

    /**
     * Своя копия, а не переданный список: `val` защищает ссылку, а не содержимое, и список,
     * оставшийся у вызывающего, добавил бы пачку в обход проверки уникальности и без роста
     * редакции курса.
     */
    val sources: List<CourseSource> = sources.toList()

    init {
        require(sources.distinctBy { it.packageId }.size == sources.size) {
            "одна пачка входит в курс один раз"
        }
        require(sources.isEmpty() || (formId != null && unitId != null)) {
            "форма и единица фиксируются первым источником"
        }
    }

    val isEmpty: Boolean get() = sources.isEmpty()

    val allocatedTotal: Doses
        get() = sources.fold(0.doses) { total, source -> total + source.allocatedDoses }

    internal fun allocatedTo(packageId: Uuid): Doses? =
        sources.firstOrNull { it.packageId == packageId }?.allocatedDoses

    internal fun holds(packageId: Uuid): Boolean = sources.any { it.packageId == packageId }

    /** Подключает пачку последней в расходе; отказ называет причину, ведущую к действию. */
    internal fun attach(pkg: Package, doses: Doses): Result<CourseMedicine> {
        val rejection = when {
            pkg.lifecycle != Package.Lifecycle.ACTIVE ||
                pkg.access != Package.Access.AVAILABLE -> CourseRejected.Reason.PACKAGE_UNUSABLE
            holds(pkg.id) -> CourseRejected.Reason.ALREADY_ATTACHED
            // Две пачки без формы несовместимы: это два разных незнания, а не одно и то же.
            pkg.facts.formId == null -> CourseRejected.Reason.FORM_UNKNOWN
            formId != null && formId != pkg.facts.formId -> CourseRejected.Reason.FORM_MISMATCH
            unitId != null && unitId != pkg.quantity.unitId -> CourseRejected.Reason.UNIT_MISMATCH
            else -> null
        }
        if (rejection != null) return Result.failure(CourseRejected(rejection))
        return Result.success(
            CourseMedicine(
                sources = sources + CourseSource(pkg.id, doses),
                formId = pkg.facts.formId,
                unitId = pkg.quantity.unitId
            )
        )
    }

    /**
     * Убирает пачку. Форму и единицу не забывает никогда, даже когда уходит последняя: они
     * заданы первым препаратом и с этого момента описывают само лечение, а не его текущий
     * состав. В них записана доза — число человек назвал для таблеток, и перечитывать его в
     * миллилитрах нельзя (PLAN D5).
     */
    internal fun detach(packageId: Uuid): CourseMedicine {
        requireHolds(packageId)
        return CourseMedicine(
            sources = sources.filterNot { it.packageId == packageId },
            formId = formId,
            unitId = unitId
        )
    }

    /** Переставляет пачку: место в препарате — очередь в расходе. */
    internal fun reorder(from: Int, to: Int): CourseMedicine {
        require(from in sources.indices && to in sources.indices) {
            "источника нет на позиции: $from → $to при ${sources.size} источниках"
        }
        if (from == to) return this
        val moved = sources.toMutableList()
        moved.add(to, moved.removeAt(from))
        return withSources(moved)
    }

    /** Задаёт выделение пачки в целых дозах; верхнюю границу называет [maxDoses]. */
    internal fun allocate(packageId: Uuid, doses: Doses): CourseMedicine {
        requireHolds(packageId)
        return withSources(
            sources.map { if (it.packageId == packageId) CourseSource(packageId, doses) else it }
        )
    }

    /**
     * Обеспечение [remaining] пунктов, данных в календарном порядке. Пачка покрывает не больше
     * выделенного и не больше целых доз, что в ней есть; остаток меньше дозы виден в её строке и
     * в следующую не переливается. Пачка без известного числа ничего не покрывает и поднимает
     * [CourseCoverage.requiresRecount].
     */
    internal fun coverage(
        dose: Dose,
        remaining: List<ScheduledOccurrence>,
        availability: Availability
    ): CourseCoverage {
        val capacities = capacities(dose, availability)
        val required = Doses(remaining.size)
        val supplied = capacities.fold(0.doses) { total, it -> total + it.covers }
        val covered = minOf(required, supplied)
        return CourseCoverage(
            requiredDoses = required,
            coveredDoses = covered,
            coveredUntil = remaining.getOrNull(covered.count - 1)?.at,
            firstUncoveredAt = remaining.getOrNull(covered.count)?.at,
            perSource = capacities.map {
                CourseCoverage.Source(it.packageId, it.allocated, it.covers, it.leftover)
            },
            requiresRecount = capacities.any { it.isUnknown }
        )
    }

    /**
     * Верхняя граница выделения пачки [packageId] в целых дозах: меньшее из того, что пачка даёт,
     * и того, что [required] оставляет сверх выделенного остальным. С других пачек выделение само
     * не снимается — это решение человека (C1). Пачка без известного числа сохраняет своё
     * выделение. [packageId] может ещё не быть в препарате: «сколько выделю, если подключу».
     */
    internal fun maxDoses(
        packageId: Uuid,
        dose: Dose,
        required: Doses,
        availability: Availability
    ): Doses {
        val here = allocatedTo(packageId) ?: 0.doses
        val available = availability.known(packageId) ?: return here
        val stillNeeded = required.minusOrNone(allocatedTotal - here)
        return minOf(available.dosesIn(dose), stillNeeded)
    }

    /**
     * Выделения, зажатые под нехватку и под потребность: каждой пачке — не больше целых доз, что
     * в ней есть, а избыток сверх [required] снимается с конца, потому что сверху расходуют, а
     * снизу освобождают. Выделение здесь только уменьшается, а пачка без известного числа своё
     * сохраняет. Доза и расписание курса от этого не меняются (PLAN D5, C1).
     */
    internal fun clampedTo(
        dose: Dose,
        required: Doses,
        availability: Availability
    ): CourseMedicine {
        val clamped = capacities(dose, availability)
            .map { CourseSource(it.packageId, it.allows) }
        var excess = clamped.fold(0.doses) { total, it -> total + it.allocatedDoses }
            .minusOrNone(required)
        val trimmed = clamped.toMutableList()
        for (index in trimmed.indices.reversed()) {
            if (excess.isNone) break
            val source = trimmed[index]
            val taken = minOf(source.allocatedDoses, excess)
            trimmed[index] = CourseSource(source.packageId, source.allocatedDoses - taken)
            excess -= taken
        }
        return withSources(trimmed)
    }

    /**
     * Из каких пачек и по сколько уйдут следующие [doses] доз: сверху вниз, каждая пачка — не
     * больше выделенного и не больше целых доз, что в ней есть. Пачек, из которых не уходит
     * ничего, в ответе нет; порядок ответа — порядок расходования.
     */
    internal fun spend(
        dose: Dose,
        doses: Doses,
        availability: Availability
    ): Map<Uuid, Doses> {
        var left = doses
        val spent = LinkedHashMap<Uuid, Doses>()
        for (capacity in capacities(dose, availability)) {
            if (left.isNone) break
            val taken = minOf(capacity.covers, left)
            if (taken.isNone) continue
            spent[capacity.packageId] = taken
            left -= taken
        }
        return spent
    }

    /**
     * Сколько целых доз остаётся выделено пачке после приёма [taken]: не больше выделенного за
     * вычетом расхода и не больше [availableAfter] (PLAN D5). Нулевое выделение расходом не
     * оживает: приём из невыделенной пачки брони не создаёт.
     */
    internal fun dosesAfterIntake(
        packageId: Uuid,
        dose: Dose,
        taken: Dose,
        availableAfter: Quantity
    ): Doses {
        require(availableAfter.unitId == dose.unitId) {
            "доступный остаток измеряется единицей дозы: ${availableAfter.unitId} и ${dose.unitId}"
        }
        val allocated = allocatedTo(packageId) ?: 0.doses
        if (allocated.isNone) return 0.doses
        val leftAllocated = (dose * allocated).minusOrZero(taken.quantity)
        val limited =
            if (leftAllocated.amount <= availableAfter.amount) leftAllocated else availableAfter
        return limited.dosesIn(dose)
    }

    /**
     * Что даёт каждая пачка под дозу, в порядке расходования. Одно место на все вопросы: пока
     * обеспечение, расход и зажим считали это порознь, правило «не больше выделенного и не больше
     * целых доз, что в пачке есть» было написано трижды и могло разойтись.
     */
    private fun capacities(dose: Dose, availability: Availability): List<SourceCapacity> =
        sources.map { source ->
            val available = availability.known(source.packageId)
            val whole = available?.dosesIn(dose)
            SourceCapacity(
                packageId = source.packageId,
                allocated = source.allocatedDoses,
                whole = whole,
                leftover = if (available == null || whole == null) null
                else available - dose * whole
            )
        }

    /**
     * Пачка под дозой: сколько целых доз в ней есть ([whole], `null` — неизвестно), сколько из
     * них покрывает приёмы ([covers]) и сколько выделения она позволяет держать ([allows]).
     *
     * Разница между [covers] и [allows] — это и есть правило D5 про неизвестное число: такая
     * пачка ничего не обеспечивает, но и выделение своё не теряет, потому что снижать его
     * догадкой нельзя.
     */
    private data class SourceCapacity(
        val packageId: Uuid,
        val allocated: Doses,
        val whole: Doses?,
        val leftover: Quantity?
    ) {
        val isUnknown: Boolean get() = whole == null

        val covers: Doses get() = whole?.let { minOf(allocated, it) } ?: 0.doses

        val allows: Doses get() = whole?.let { minOf(allocated, it) } ?: allocated
    }

    private fun withSources(sources: List<CourseSource>): CourseMedicine =
        CourseMedicine(sources = sources, formId = formId, unitId = unitId)

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is CourseMedicine &&
                sources == other.sources &&
                formId == other.formId &&
                unitId == other.unitId
            )

    override fun hashCode(): Int = Objects.hash(sources, formId, unitId)

    override fun toString(): String = "CourseMedicine($sources, form=$formId, unit=$unitId)"

    private fun requireHolds(packageId: Uuid) {
        require(holds(packageId)) { "пачка $packageId не источник этого курса" }
    }
}
