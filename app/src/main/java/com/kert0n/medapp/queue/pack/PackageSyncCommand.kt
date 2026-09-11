package com.kert0n.medapp.queue.pack

import com.kert0n.medapp.domain.pack.PackageSharedFacts
import com.kert0n.medapp.domain.value.Dose
import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.queue.SyncCommand
import kotlin.uuid.Uuid

/**
 * Что предстоит доставить серверу по одной упаковке: завести, описать, пересчитать, перенести,
 * удалить, списать, заявить или снять бронь. Команда описывает доставку, а не лечение,
 * поэтому живёт в данных; порядок применения задаёт `sequence` очереди. Виды вложены в корень —
 * это варианты одной команды (C1); общий маркер `SyncCommand` появится с `SyncOperation` (PR 4).
 */
sealed interface PackageSyncCommand : SyncCommand {

    val packageId: Uuid

    /**
     * Остаток после этой команды (PLAN E1); `null` — количество команда не меняет. Пересчёт
     * заменяет число, а не вычитает; удаление даёт ноль; расход больше остатка даёт ноль,
     * потому что нехватка — отказ сервера, и разбирается она снимком, а не числом.
     */
    fun appliedTo(amount: Quantity): Quantity? = when (this) {
        is Consume -> amount.minusOrZero(this.amount.quantity)
        is CorrectStock -> this.actual
        is Delete -> Quantity.zero(amount.unit)
        is Create, is Describe, is Move, is SetClaim, is ReleaseClaim -> null
    }

    /**
     * Завести упаковку на сервере.
     *
     * Начальный остаток строго положителен — и в доменном сценарии, и в POST-DTO: пачка, которой
     * нет, не заводится (PLAN E2). Личные срок, заметка и цена в команде отсутствуют по типу:
     * уезжает [PackageSharedFacts], остальное остаётся на устройстве (PLAN C0).
     */
    data class Create(
        override val packageId: Uuid,
        val medKitId: Uuid,
        val quantity: Quantity,
        val facts: PackageSharedFacts
    ) : PackageSyncCommand {
        init {
            require(!quantity.isZero) { "пачка заводится с положительным остатком" }
        }
    }

    /**
     * Изменить описание препарата на сервере.
     *
     * Хранит **и исходное, и желаемое** состояние: неизменённые поля не отправляются, очистка
     * текста становится `""`, а ограничение очистки формы не теряется (PLAN E2, D3). Одного
     * «желаемого» не хватило бы — по нему нельзя отличить «поле не трогали» от «поле очистили».
     *
     * Правку, не выходящую за границу публикации, отправлять незачем: `before == after` — это
     * команда, которой нечего делать на проводе.
     */
    data class Describe(
        override val packageId: Uuid,
        val before: PackageSharedFacts,
        val after: PackageSharedFacts
    ) : PackageSyncCommand {
        init {
            require(before != after) { "описание не изменилось: отправлять нечего" }
        }
    }

    /**
     * Пересчитали и увидели столько.
     *
     * **Абсолютное значение, а не дельта** (PLAN E1): проекция заменяет остаток, а не вычитает.
     * Ноль допустим и становится `DELETE` при подготовке запроса — это форма провода, а не смысл команды (B6).
     */
    data class CorrectStock(
        override val packageId: Uuid,
        val actual: Quantity
    ) : PackageSyncCommand

    /** Перенести упаковку в другую аптечку. */
    data class Move(
        override val packageId: Uuid,
        val targetMedKitId: Uuid
    ) : PackageSyncCommand

    /**
     * Удалить упаковку на сервере.
     *
     * Локально пачка при этом **архивируется**, а не исчезает: строка остаётся, и приёмы с
     * движениями продолжают читаться по ней (PLAN D3). В проекции остатка это ноль (PLAN E1).
     */
    data class Delete(override val packageId: Uuid) : PackageSyncCommand

    /**
     * Списать фактически принятое.
     *
     * [intakeId] отдельным полем: **команда приёма не поглощает следующий факт**, у каждого
     * подтверждения свой идентификатор, и повтор отправки не превращается во второе списание
     * (PLAN E2).
     *
     * [claimAfter] — новая **абсолютная** бронь после расхода, и три её значения означают три
     * разных действия (PLAN E2):
     * - `null` — внеплановый расход, брони не касается;
     * - положительное — курсовой расход с новым объёмом брони;
     * - ноль — курсовой расход без блока брони, а снятие уезжает зависимым [ReleaseClaim].
     *
     * Величина брони не всегда уменьшается на физический расход: при частичной или увеличенной
     * дозе она пересчитывается по правилам D5, поэтому здесь два независимых числа, а не одно.
     */
    data class Consume(
        override val packageId: Uuid,
        val amount: Dose,
        val intakeId: Uuid,
        val claimAfter: Quantity? = null
    ) : PackageSyncCommand {
        init {
            require(claimAfter == null || claimAfter.unit == amount.unit) {
                "бронь измеряется той же единицей, что расход"
            }
        }
    }

    /**
     * Заявить бронь на упаковку — серверное представление невыбранного выделения источника курса
     * (PLAN D5).
     *
     * Величина **абсолютная**: сервер хранит одну бронь на пару «человек и упаковка», и целевой
     * объём равен `allocatedDoses × dose`. Ноль здесь не пишут: снятие — это [ReleaseClaim], и на
     * проводе у него другая операция.
     */
    data class SetClaim(
        override val packageId: Uuid,
        val amount: Quantity
    ) : PackageSyncCommand {
        init {
            require(!amount.isZero) { "нулевая бронь — это снятие брони, у него свой вид" }
        }
    }

    /**
     * Снять свою бронь с упаковки: источник исчерпан, курс завершён или отменён (PLAN D5).
     *
     * Если пачка уже уничтожена, каскад снял бронь до нас, и зависимое снятие закрывается по
     * проверенному отсутствию, а не считается неудачей (PLAN E2).
     */
    data class ReleaseClaim(override val packageId: Uuid) : PackageSyncCommand
}
