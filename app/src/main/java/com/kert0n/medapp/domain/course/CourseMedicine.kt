package com.kert0n.medapp.domain.course

import com.kert0n.medapp.domain.pack.Availability
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.pack.PackageAccess
import com.kert0n.medapp.domain.pack.PackageLifecycle
import com.kert0n.medapp.domain.value.Doses
import com.kert0n.medapp.domain.value.Quantity
import kotlin.uuid.Uuid

/**
 * Препарат курса: пачки, которые человек, выбрав их источниками, объявил одним лекарством. Форму
 * и единицу задаёт первая пачка; порядок пачек — порядок расходования; каждой выделено целое
 * число доз, потому что доза берётся из одной пачки и между пачками не делится (PLAN D5).
 * Взаимозаменяемость приложение не выводит (C2), поэтому вопросы обеспечения задаются препарату
 * целиком. Разовую дозу знает курс и передаёт аргументом; [Availability] без числа по пачке
 * означает «неизвестно», и такая пачка ничего не обеспечивает, но выделение сохраняет.
 */
data class CourseMedicine(
    val sources: List<CourseSource> = emptyList(),
    val formId: Uuid? = null,
    val unitId: Uuid? = null
) {

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
        get() = sources.fold(Doses.none) { total, source -> total + source.allocatedDoses }

    fun allocatedTo(packageId: Uuid): Doses? =
        sources.firstOrNull { it.packageId == packageId }?.allocatedDoses

    fun holds(packageId: Uuid): Boolean = sources.any { it.packageId == packageId }

    /** Подключает пачку последней в расходе; отказ называет причину, ведущую к действию. */
    fun attach(pkg: Package, doses: Doses): Result<CourseMedicine> {
        val rejection = when {
            pkg.lifecycle != PackageLifecycle.ACTIVE ||
                pkg.access != PackageAccess.AVAILABLE -> CourseRejected.Reason.PACKAGE_UNUSABLE
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
     * Убирает пачку. Когда уходит последняя, [forgetFormWhenEmpty] решает, забыть ли форму и
     * единицу: черновику терять нечего, а у назначенного курса в них уже записаны доза и
     * расписание (PLAN D5).
     */
    fun detach(packageId: Uuid, forgetFormWhenEmpty: Boolean): CourseMedicine {
        requireHolds(packageId)
        val left = sources.filterNot { it.packageId == packageId }
        val forget = left.isEmpty() && forgetFormWhenEmpty
        return CourseMedicine(
            sources = left,
            formId = if (forget) null else formId,
            unitId = if (forget) null else unitId
        )
    }

    /** Переставляет пачку: место в препарате — очередь в расходе. */
    fun reorder(from: Int, to: Int): CourseMedicine {
        require(from in sources.indices && to in sources.indices) {
            "источника нет на позиции: $from → $to при ${sources.size} источниках"
        }
        if (from == to) return this
        val moved = sources.toMutableList()
        moved.add(to, moved.removeAt(from))
        return copy(sources = moved)
    }

    /** Задаёт выделение пачки в целых дозах; верхнюю границу называет [maxDoses]. */
    fun allocate(packageId: Uuid, doses: Doses): CourseMedicine {
        requireHolds(packageId)
        return copy(sources = sources.map {
            if (it.packageId == packageId) CourseSource(packageId, doses) else it
        })
    }

    /** Снимает все выделения и оставляет пачки: по ним читается история приёмов. */
    fun released(): CourseMedicine = copy(
        sources = sources.map {
            if (it.allocatedDoses.isNone) it else CourseSource(it.packageId, Doses.none)
        }
    )

    /**
     * Обеспечение [remaining] пунктов, данных в календарном порядке. Пачка покрывает не больше
     * выделенного и не больше целых доз, что в ней есть; остаток меньше дозы виден в её строке и
     * в следующую не переливается. Пачка без известного числа ничего не покрывает и поднимает
     * [CourseCoverage.requiresRecount].
     */
    fun coverage(
        dose: Quantity,
        remaining: List<ScheduledOccurrence>,
        availability: Availability
    ): CourseCoverage {
        val required = Doses(remaining.size)
        var unknown = false
        var supplied = Doses.none
        val perSource = sources.map { source ->
            val available = availability.known(source.packageId)
            if (available == null) {
                unknown = true
                return@map CourseCoverage.Source(
                    source.packageId, source.allocatedDoses, Doses.none, leftover = null
                )
            }
            val wholeDoses = available.dosesIn(dose)
            val usable = minOf(source.allocatedDoses, wholeDoses)
            supplied += usable
            CourseCoverage.Source(
                packageId = source.packageId,
                allocatedDoses = source.allocatedDoses,
                coveredDoses = usable,
                leftover = available - dose * wholeDoses
            )
        }
        val covered = minOf(required, supplied)
        return CourseCoverage(
            requiredDoses = required,
            coveredDoses = covered,
            coveredUntil = remaining.getOrNull(covered.count - 1)?.at,
            firstUncoveredAt = remaining.getOrNull(covered.count)?.at,
            perSource = perSource,
            requiresRecount = unknown
        )
    }

    /**
     * Верхняя граница выделения пачки [packageId] в целых дозах: меньшее из того, что пачка даёт,
     * и того, что [required] оставляет сверх выделенного остальным. С других пачек выделение само
     * не снимается — это решение человека (C1). Пачка без известного числа сохраняет своё
     * выделение. [packageId] может ещё не быть в препарате: «сколько выделю, если подключу».
     */
    fun maxDoses(
        packageId: Uuid,
        dose: Quantity,
        required: Doses,
        availability: Availability
    ): Doses {
        val here = allocatedTo(packageId) ?: Doses.none
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
    fun clampedTo(dose: Quantity, required: Doses, availability: Availability): CourseMedicine {
        val clamped = sources.map { source ->
            val fits = availability.dosesOf(source.packageId, dose) ?: return@map source
            if (fits >= source.allocatedDoses) source else CourseSource(source.packageId, fits)
        }
        var excess = clamped.fold(Doses.none) { total, it -> total + it.allocatedDoses }
            .minusOrNone(required)
        val trimmed = clamped.toMutableList()
        for (index in trimmed.indices.reversed()) {
            if (excess.isNone) break
            val source = trimmed[index]
            val taken = minOf(source.allocatedDoses, excess)
            trimmed[index] = CourseSource(source.packageId, source.allocatedDoses - taken)
            excess -= taken
        }
        return copy(sources = trimmed)
    }

    /**
     * Из каких пачек и по сколько уйдут следующие [doses] доз: сверху вниз, каждая пачка — не
     * больше выделенного и не больше целых доз, что в ней есть. Пачек, из которых не уходит
     * ничего, в ответе нет; порядок ответа — порядок расходования.
     */
    fun spend(dose: Quantity, doses: Doses, availability: Availability): Map<Uuid, Doses> {
        var left = doses
        val spent = LinkedHashMap<Uuid, Doses>()
        for (source in sources) {
            if (left.isNone) break
            val inPack = availability.dosesOf(source.packageId, dose) ?: Doses.none
            val taken = minOf(source.allocatedDoses, inPack, left)
            if (taken.isNone) continue
            spent[source.packageId] = taken
            left -= taken
        }
        return spent
    }

    /**
     * Сколько целых доз остаётся выделено пачке после приёма [taken]: не больше выделенного за
     * вычетом расхода и не больше [availableAfter] (PLAN D5). Нулевое выделение расходом не
     * оживает: приём из невыделенной пачки брони не создаёт.
     */
    fun dosesAfterIntake(
        packageId: Uuid,
        dose: Quantity,
        taken: Quantity,
        availableAfter: Quantity
    ): Doses {
        require(availableAfter.unitId == dose.unitId) {
            "доступный остаток измеряется единицей дозы: ${availableAfter.unitId} и ${dose.unitId}"
        }
        val allocated = allocatedTo(packageId) ?: Doses.none
        if (allocated.isNone) return Doses.none
        val leftAllocated = (dose * allocated).minusOrZero(taken)
        val limited =
            if (leftAllocated.amount <= availableAfter.amount) leftAllocated else availableAfter
        return limited.dosesIn(dose)
    }

    private fun requireHolds(packageId: Uuid) {
        require(holds(packageId)) { "пачка $packageId не источник этого курса" }
    }
}
