package com.kert0n.medapp.domain.model.pack

import com.kert0n.medapp.domain.model.value.Quantity
import kotlin.uuid.Uuid

/**
 * Что мы вправе сказать про остаток пачки: число — или «неизвестно, нужна сверка».
 *
 * **Ничего не хранит.** Хранимое поле разъехалось бы с очередью и с курсами при первом же
 * неустановленном исходе (PLAN D4), поэтому это результат расчёта, а не колонка.
 *
 * Два случая, а не два поля с признаком: пока «остаток» и «требуется сверка» жили рядом, любой
 * экран мог напечатать число, которого мы не знаем. Здесь у [NeedsRecount] числа `effective`
 * просто нет — есть последнее наблюдение, и оно названо своим именем.
 *
 * Виды лежат внутри, а не соседними файлами: это два исхода одного вопроса, как `Mapped` и
 * `Rejected` у приведения ввода, а не каталог видов события (PLAN H1, D7).
 */
sealed interface StockViewState {

    /** Остаток известен. [pending] — часть его ещё не подтверждена сервером. */
    data class Known(val effective: Quantity, val pending: Boolean) : StockViewState

    /**
     * Исход операции не установлен: включён ли расход в серверный остаток, неизвестно.
     *
     * Повторять расход нельзя — списали бы дважды, — и выдумывать число тоже: до сверки
     * показывается последнее наблюдение и требование проверки (PLAN E3).
     */
    data class NeedsRecount(
        val lastObserved: Quantity,
        val unresolvedOperationIds: List<Uuid>
    ) : StockViewState {
        init {
            require(unresolvedOperationIds.isNotEmpty()) {
                "сверка требуется из-за названных операций, а не сама по себе"
            }
        }
    }
}
