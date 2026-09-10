package com.kert0n.medapp.data.pack

import com.kert0n.medapp.domain.pack.EffectiveAmount
import com.kert0n.medapp.domain.value.Quantity
import kotlin.uuid.Uuid

/**
 * Применяет незакрытые команды очереди к подтверждённому остатку и отдаёт домену готовую оценку
 * количества (PLAN E1).
 *
 * **Здесь решается только «какие команды и в каком порядке».** Это правило синхронизации, и E1
 * не случайно лежит в части E, а не в модели: оно про доставку, а не про лекарство. Арифметика и
 * границы величин остаются доменными — сложение, вычитание и запрет отрицательного живут в
 * [Quantity].
 *
 * | команда | проекция |
 * |---|---|
 * | расход | вычесть один раз |
 * | пересчёт | **заменить** значение; это не дельта — то же правило, что у `Package.correctTo` |
 * | ручная сверка | заменить названным человеком остатком |
 * | удаление | ноль |
 * | создание, описание, перенос, брони | количество не меняют |
 *
 * [unclosed] — команды **этой** пачки в порядке `sequence`, уже без отсечённого ручной сверкой.
 * Порядок и срез по `throughSequence` — работа запроса к базе: номером очереди владеет она, и
 * формулировать о нём правило в домене нельзя (PLAN E1, D4).
 *
 * При неустановленном исходе число не выдаётся вовсе: [unresolvedOperationIds] перебивают
 * проекцию целиком, потому что неизвестно, включён ли расход в серверный остаток (PLAN E3).
 * Отрицательный расчёт показывается нулём — нехватка это конфликт операции, а не успешный расход,
 * и разбирается он по состоянию очереди, а не подменой числа.
 */
fun effectiveAmount(
    confirmed: Quantity,
    unclosed: List<PackageSyncCommand> = emptyList(),
    unresolvedOperationIds: List<Uuid> = emptyList()
): EffectiveAmount {
    if (unresolvedOperationIds.isNotEmpty()) {
        return EffectiveAmount.NeedsRecount(confirmed, unresolvedOperationIds)
    }
    var projected = confirmed
    var touched = false
    for (command in unclosed) {
        // Исчерпывающий `when` по корню понятия, без `else`: новая команда упаковки не сможет
        // молча не попасть в проекцию — компилятор потребует ответить, меняет она количество или
        // нет.
        val next = when (command) {
            is PackageSyncCommand.Consume -> projected.minusOrZero(command.amount)
            is PackageSyncCommand.CorrectStock -> command.actual
            is PackageSyncCommand.Reconcile -> command.actual
            is PackageSyncCommand.Delete -> Quantity.zero(projected.unitId)
            is PackageSyncCommand.Create,
            is PackageSyncCommand.Describe,
            is PackageSyncCommand.Move,
            is PackageSyncCommand.SetClaim,
            is PackageSyncCommand.ReleaseClaim -> continue
        }
        projected = next
        touched = true
    }
    // `confirmed = false` только когда в число действительно вложено незакрытое изменение:
    // ожидающая правка описания подтверждённое количество неподтверждённым не делает.
    return EffectiveAmount.Known(projected, confirmed = !touched)
}
