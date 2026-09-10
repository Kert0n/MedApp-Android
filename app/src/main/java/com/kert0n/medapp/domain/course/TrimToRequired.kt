package com.kert0n.medapp.domain.course

import com.kert0n.medapp.domain.value.Doses

/**
 * Ограничивает суммарное выделение оставшейся потребностью, снимая избыток **с конца стека**.
 *
 * Пропуск, неответ и отмена пункта расхода не делают, но уменьшают потребность: приёмов впереди
 * стало меньше, и одна выделенная доза больше не нужна. Оставить её выделенной значило бы держать
 * бронь под приём, которого не будет — а бронь это чужой недоступный запас (PLAN D5).
 *
 * С конца, а не пропорционально: сверху расходуют, снизу освобождают. Пропорциональное снятие
 * тронуло бы источник, из которого человек как раз принимает, и порядок расходования потерял бы
 * смысл. Верхние источники остаются нетронутыми, пока избыток снимается с нижних.
 *
 * Автоматического увеличения нет ни здесь, ни где-либо ещё: выделение — решение человека, и
 * поднять его может только он.
 */
fun trimToRequired(sources: List<CourseSource>, requiredDoses: Doses): List<CourseSource> {
    val allocated = sources.fold(Doses.none) { total, source -> total + source.allocatedDoses }
    var excess = allocated.minusOrNone(requiredDoses)
    if (excess.isNone) return sources
    val trimmed = sources.toMutableList()
    for (index in trimmed.indices.reversed()) {
        if (excess.isNone) break
        val source = trimmed[index]
        val taken = minOf(source.allocatedDoses, excess)
        if (taken.isNone) continue
        trimmed[index] = CourseSource(source.packageId, source.allocatedDoses - taken)
        excess -= taken
    }
    return trimmed
}
