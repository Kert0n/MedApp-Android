package com.kert0n.medapp.domain.calc.coverage

import com.kert0n.medapp.domain.calc.schedule.ScheduledOccurrence
import com.kert0n.medapp.domain.model.course.Course
import com.kert0n.medapp.domain.model.value.Doses
import com.kert0n.medapp.domain.model.value.Quantity
import kotlin.uuid.Uuid

/**
 * Считает обеспечение курса и называет первый непокрытый приём.
 *
 * [remaining] — оставшиеся пункты **в календарном порядке**: и просроченные неотвеченные, и те,
 * что лежат за окном материализации. Готовит их вызывающий из расписания и ответов, а не из
 * шестидесятидневного кэша (PLAN H1), иначе годовой курс оказался бы «обеспечен полностью» на
 * основании двух ближайших месяцев.
 *
 * [availability] — `availableToMe` по пачкам (PLAN D4). **Отсутствие ключа значит «неизвестно»**:
 * такой источник не выдаётся за обеспеченный и поднимает [CourseCoverage.requiresRecount], но
 * своего выделения не теряет (PLAN D5).
 *
 * Расход идёт **сверху вниз** по стеку: пока в источнике невыбранного выделения меньше дозы,
 * переходим к следующему, а остаток строки не переливается. Поэтому обеспечение считается как
 * сумма подтверждённых источниками доз, а не как деление суммарного количества на дозу: две
 * пачки по одной таблетке при дозе в две таблетки дают ноль приёмов, а не один.
 */
fun coverage(
    course: Course,
    remaining: List<ScheduledOccurrence>,
    availability: Map<Uuid, Quantity>
): CourseCoverage {
    val dose = requireNotNull(course.dose) { "обеспечение без дозы курса не определено" }
    val requiredDoses = Doses(remaining.size)

    var unknown = false
    var supplied = Doses.none
    val perSource = course.sources.map { source ->
        val available = availability[source.packageId]
        if (available == null) {
            unknown = true
            return@map SourceCoverage(source.packageId, source.allocatedDoses, Doses.none, null)
        }
        val wholeDoses = available.dosesIn(dose)
        // Покрытие ограничено и намерением, и физикой: выделение могло остаться больше того,
        // что пачка теперь даёт, — тогда обеспечение честно меньше выделенного.
        val usable = minOf(source.allocatedDoses, wholeDoses)
        // Сумма по источникам, а не деление общего количества на дозу: каждый источник даёт
        // целые дозы отдельно, поэтому остатки в разных пачках в одну дозу не складываются.
        supplied += usable
        SourceCoverage(
            packageId = source.packageId,
            allocatedDoses = source.allocatedDoses,
            coveredDoses = usable,
            leftover = available - dose * wholeDoses
        )
    }

    val coveredDoses = minOf(requiredDoses, supplied)
    return CourseCoverage(
        requiredDoses = requiredDoses,
        coveredDoses = coveredDoses,
        coveredUntil = remaining.getOrNull(coveredDoses.count - 1)?.at,
        firstUncoveredAt = remaining.getOrNull(coveredDoses.count)?.at,
        perSource = perSource,
        requiresRecount = unknown
    )
}
