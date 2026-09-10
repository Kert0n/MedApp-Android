package com.kert0n.medapp.domain.calc.coverage

import com.kert0n.medapp.domain.model.course.Course
import com.kert0n.medapp.domain.model.intake.Intake
import com.kert0n.medapp.domain.model.intake.IntakeStatus
import com.kert0n.medapp.domain.model.value.Doses
import com.kert0n.medapp.domain.model.value.Quantity
import kotlin.uuid.Uuid

/**
 * Раскладывает будущие пункты курса по источникам: какому приёму из какой пачки браться.
 *
 * **Сверху вниз по стеку.** Пока в текущем источнике невыбранного выделения меньше дозы,
 * переходим к следующему, а неиспользуемый остаток остаётся в его строке — он не пропадает и
 * **не переливается** (PLAN D5). Поэтому вместимость каждого источника считается в целых дозах
 * отдельно: две пачки по одной таблетке при дозе в две дают ноль обеспеченных приёмов, а не один.
 *
 * **Источники сами не появляются.** Переход происходит только между уже подключёнными пачками:
 * похожие упаковки не подставляются, и новая подходящая пачка в стек не встаёт ни при нехватке,
 * ни при появлении. Подключает человек.
 *
 * `null` в ответе — **необеспеченный приём**: полная доза при отсутствии источника не
 * записывается, пункт получает явный признак необеспеченности, и человек либо подключает
 * источник, либо подтверждает фактически принятое меньшее количество. Списать «в минус» или
 * «неизвестно откуда» нельзя (PLAN D5).
 *
 * [upcoming] — неотвеченные пункты **этого** курса в календарном порядке. Порядок задаёт
 * вызывающий: раскладка зависит от того, какой приём наступает раньше, а не от порядка строк в
 * запросе.
 *
 * [availability] — `availableToMe` по пачкам; отсутствие ключа значит «неизвестно», и такой
 * источник обеспеченным не считается: до сверки выдавать его за обеспеченный нельзя (PLAN D5).
 */
fun assignSources(
    course: Course,
    upcoming: List<Intake>,
    availability: Map<Uuid, Quantity>
): Map<Uuid, Uuid?> {
    upcoming.forEach { intake ->
        require(intake.courseId == course.id) { "пункт ${intake.id} не принадлежит курсу" }
        require(intake.status == IntakeStatus.PLANNED) {
            "раскладываются неотвеченные пункты, а не ${intake.status}"
        }
    }
    val dose = course.dose ?: return upcoming.associate { it.id to null }

    // Вместимость источника: и намерение человека, и физика пачки, каждая в целых дозах.
    val capacity = sourceCapacity(course, dose, availability).toMutableList()

    var current = 0
    return upcoming.associate { intake ->
        while (current < capacity.size && capacity[current].second.isNone) current++
        if (current >= capacity.size) {
            intake.id to null
        } else {
            val (packageId, left) = capacity[current]
            capacity[current] = packageId to left - Doses.one
            intake.id to packageId
        }
    }
}
