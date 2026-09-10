package com.kert0n.medapp.domain.calc.allocation

import com.kert0n.medapp.domain.calc.availability.Availability
import com.kert0n.medapp.domain.model.course.PlannedCourse
import com.kert0n.medapp.domain.model.course.CourseSource
import com.kert0n.medapp.domain.model.value.Doses
import com.kert0n.medapp.domain.model.value.Quantity
import kotlin.uuid.Uuid

/**
 * Пересчёт обеспечения при нехватке: каждому источнику
 * `allocatedDoses = min(previousAllocatedDoses, availableToMe.dosesIn(dose))`, затем суммарное
 * выделение ограничивается оставшейся потребностью с конца стека (PLAN D5).
 *
 * **Зажимает выделение, не трогая расписания.** Доза, даты и времена действующего курса при
 * нехватке не меняются: расписание — намерение человека, и чужое действие не должно его
 * переписывать. Поэтому функция возвращает **только источники** — тронуть календарь ей нечем, и
 * «сокращаем курс до максимально возможного срока» из исходных требований здесь невыразимо
 * (PLAN C1).
 *
 * Вызывается после изменения любого входа расчёта: применения снимка, подтверждения, пропуска или
 * неответа приёма, пересчёта либо утилизации запаса, изменения источников, активации и отмены
 * курса, переноса или утраты доступа, разрешения неопределённой операции (PLAN D5, F5).
 *
 * Пачка без известного числа выделение сохраняет: при требуемой сверке числового предела нет,
 * снижать выделение догадкой нельзя, а автоматическую замену брони до сверки не отправляют.
 * Обеспечение при этом помечено требующим проверки — это делает `coverage`.
 *
 * Автоматического **увеличения** нет: подросший остаток выделение не поднимает, потому что
 * выделение — решение человека, а не следствие поставки.
 */
fun clampAllocations(
    course: PlannedCourse,
    requiredDoses: Doses,
    availability: Availability
): List<CourseSource> {
    val dose = course.dose
    val clamped = course.sources.map { source ->
        val available = availability.known(source.packageId) ?: return@map source
        val fits = available.dosesIn(dose)
        if (fits >= source.allocatedDoses) source
        else CourseSource(source.packageId, fits)
    }
    return trimToRequired(clamped, requiredDoses)
}
