package com.kert0n.medapp.domain.model.course

import com.kert0n.medapp.domain.model.pack.Package
import com.kert0n.medapp.domain.model.pack.PackageAccess
import com.kert0n.medapp.domain.model.pack.PackageLifecycle
import com.kert0n.medapp.domain.model.value.Doses
import kotlin.uuid.Uuid

/**
 * Стек источников курса: **порядок и есть приоритет расходования** (PLAN D5).
 *
 * Отдельная величина, а не список внутри курса: правила стека — совместимость формы и единицы,
 * запрет повтора пачки, перестановка, выделение — одни и те же у черновика и у назначенного
 * курса. Пока они жили в самом курсе, каждый его вид переписывал бы их у себя.
 *
 * **Форму и единицу задаёт первый источник**, дальше несовместимое отвергается: доза измеряется
 * единицей курса, и пачка в другой единице не подставится в приём, а форма отвечает за «то же
 * самое лекарство».
 */
data class SourceStack(
    val items: List<CourseSource> = emptyList(),
    val formId: Uuid? = null,
    val unitId: Uuid? = null
) {

    init {
        require(items.distinctBy { it.packageId }.size == items.size) {
            "одна пачка входит в курс один раз"
        }
        require(items.isEmpty() || (formId != null && unitId != null)) {
            "форма и единица фиксируются первым источником"
        }
    }

    val isEmpty: Boolean get() = items.isEmpty()

    /** Сколько доз выделено по всему стеку: с этой суммой сравнивается оставшаяся потребность. */
    val allocatedTotal: Doses
        get() = items.fold(Doses.none) { total, source -> total + source.allocatedDoses }

    fun allocatedTo(packageId: Uuid): Doses? =
        items.firstOrNull { it.packageId == packageId }?.allocatedDoses

    fun holds(packageId: Uuid): Boolean = items.any { it.packageId == packageId }

    /**
     * Подключает пачку последней — самой низкой по приоритету расходования.
     *
     * Отказ — обычный ответ на действие человека, поэтому причина названа перечислением:
     * «укажите форму» и «эта пачка другой формы» ведут к разным действиям.
     */
    fun attach(pkg: Package, doses: Doses): Result<SourceStack> {
        val rejection = when {
            pkg.lifecycle != PackageLifecycle.ACTIVE ||
                pkg.access != PackageAccess.AVAILABLE -> CourseRejection.PACKAGE_UNUSABLE
            holds(pkg.id) -> CourseRejection.ALREADY_ATTACHED
            // `formId = null` не совместим с `formId = null`: это две пачки, про каждую из
            // которых мы ничего не знаем, а не одна и та же неизвестность.
            pkg.facts.formId == null -> CourseRejection.FORM_UNKNOWN
            formId != null && formId != pkg.facts.formId -> CourseRejection.FORM_MISMATCH
            unitId != null && unitId != pkg.quantity.unitId -> CourseRejection.UNIT_MISMATCH
            else -> null
        }
        if (rejection != null) return Result.failure(CourseRejected(rejection))
        return Result.success(
            SourceStack(
                items = items + CourseSource(pkg.id, doses),
                formId = pkg.facts.formId,
                unitId = pkg.quantity.unitId
            )
        )
    }

    /**
     * Убирает источник. [forgetFormWhenEmpty] — решение владельца стека: у черновика терять ещё
     * нечего, а у назначенного курса форма и единица остаются, иначе доза и расписание мгновенно
     * потеряли бы смысл (PLAN D5).
     */
    fun detach(packageId: Uuid, forgetFormWhenEmpty: Boolean): SourceStack {
        require(holds(packageId)) { "пачка $packageId не источник этого курса" }
        val left = items.filterNot { it.packageId == packageId }
        val forget = left.isEmpty() && forgetFormWhenEmpty
        return SourceStack(
            items = left,
            formId = if (forget) null else formId,
            unitId = if (forget) null else unitId
        )
    }

    /** Перетаскивание строки — содержательное действие, а не украшение списка. */
    fun reorder(from: Int, to: Int): SourceStack {
        require(from in items.indices && to in items.indices) {
            "источника нет на позиции: $from → $to при ${items.size} источниках"
        }
        if (from == to) return this
        val moved = items.toMutableList()
        moved.add(to, moved.removeAt(from))
        return copy(items = moved)
    }

    /**
     * Задаёт выделение источника в целых дозах.
     *
     * Верхнюю границу здесь не считают: она зависит от свежих остатка, чужих броней и
     * оставшейся потребности, то есть от состояния вне курса. Её вычисляет сценарий через
     * `maxDoses` (PLAN D5, H1), а что выделение целое и неотрицательное, отвечает сам тип.
     */
    fun allocate(packageId: Uuid, doses: Doses): SourceStack {
        require(holds(packageId)) { "пачка $packageId не источник этого курса" }
        return copy(items = items.map {
            if (it.packageId == packageId) CourseSource(packageId, doses) else it
        })
    }

    /** Выделения обнуляются, сам стек остаётся: история приёмов читается по нему. */
    fun released(): SourceStack = copy(
        items = items.map {
            if (it.allocatedDoses.isNone) it else CourseSource(it.packageId, Doses.none)
        }
    )
}
