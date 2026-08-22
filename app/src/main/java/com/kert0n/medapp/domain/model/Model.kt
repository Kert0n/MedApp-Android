package com.kert0n.medapp.domain.model

import java.math.BigDecimal
import java.util.UUID

/*
 * Домен клиента: то, чем разговаривают экраны.
 *
 * Это **заготовка**. Формы взяты у сервера, потому что понятия общие, но клиентский домен не
 * обязан совпадать с серверным: серверу нужно правило, клиенту — что нарисовать и что отправить.
 * Разойдутся они там, где у клиента появится своё — расписание приёмов, черновики, офлайн.
 *
 * Отличие от [провода][com.kert0n.medapp.data.remote.dto] всего одно, но существенное: величины
 * здесь `BigDecimal`, а идентификаторы — `UUID`. Строка приезжает снаружи, разбирается один раз
 * на границе, и дальше по коду ходит значение, с которым можно считать.
 */

/** Разрядность величин у сервера: `numeric(19, 6)`. Шесть знаков — деление таблетки и капли. */
const val QUANTITY_SCALE: Int = 6

/**
 * Количество вместе с единицей измерения.
 *
 * Неразделимы: сложить таблетки с миллилитрами нельзя даже случайно. Сама единица — запись
 * словаря, общего на всю систему; здесь от неё только идентификатор, имя разворачивается по
 * словарю, который клиент тянет один раз.
 */
data class Quantity(
    val amount: BigDecimal,
    val unitId: UUID
) {
    val isZero: Boolean get() = amount.signum() == 0
}

/** Запись общего словаря — единица измерения или форма выпуска. */
data class VocabularyEntry(
    val id: UUID,
    val name: String
)

/**
 * Упаковка — пачка, а не «лекарство вообще».
 *
 * Пополнения не бывает: докупил — завёл вторую упаковку, опустевшую выбрасывают.
 *
 * [version] — токен предусловия: чем была пачка, когда её прочитали. Клиент его не толкует, а
 * возвращает серверу вместе с командой.
 */
data class Drug(
    val id: UUID,
    val medKitId: UUID,
    val name: String,
    val quantity: Quantity,
    val formTypeId: UUID? = null,
    val category: String? = null,
    val manufacturer: String? = null,
    val country: String? = null,
    val description: String? = null,
    val version: Long
)

/**
 * Что заявлено на упаковку.
 *
 * [mine] равен `null`, когда вызывающий не заявлял ничего. [total] может превышать остаток —
 * законное состояние: сколько из своей брони оставить, решает её владелец, а не сервер.
 */
data class Claims(
    val total: BigDecimal,
    val mine: BigDecimal? = null,
    val version: Long
)

/** Упаковка вместе с бронями на неё: то, что показывает карточка. */
data class DrugSnapshot(
    val drug: Drug,
    val claims: Claims
)

/** Аптечка с содержимым. Участники равноправны — ни владельца, ни ролей. */
data class MedKit(
    val id: UUID,
    val userCount: Long,
    val drugs: List<DrugSnapshot> = emptyList()
)

/** Снимок вызывающего: всё, что ему видно. Им клиент и синхронизируется целиком. */
data class User(
    val id: UUID,
    val medKits: List<MedKit> = emptyList()
)

/** Своя бронь на упаковку — то, что видно в списке «мои брони». */
data class Reservation(
    val drugId: UUID,
    val amount: BigDecimal
)

/** Запись справочника Vidal: образец, с которого заводят упаковку. Только для чтения. */
data class DrugTemplate(
    val id: UUID,
    val name: String,
    val nameLat: String? = null,
    val activeSubstance: String? = null,
    val formTypeId: UUID? = null,
    val category: String? = null,
    val quantityUnitId: UUID? = null,
    val manufacturer: String? = null,
    val country: String? = null,
    val description: String? = null
)

/**
 * Приём: сколько съедено и когда.
 *
 * Живёт **только на клиенте** — журнал приёмов в базу сервера не попадает, он слишком
 * персонален для таблицы. Наружу уходит лишь итог: съеденное списывается с упаковки.
 */
data class Intake(
    val id: UUID,
    val drugId: UUID,
    val quantity: BigDecimal,
    val takenAtEpochMillis: Long
)

/**
 * Незаконченное изменение: что клиент сделал офлайн и ещё не донёс до сервера.
 *
 * Форма приблизительная — точная появится вместе с очередью синхронизации. Смысл уже понятен:
 * съеденное копится дельтой, а бронь запоминается абсолютным значением, потому что дельта от
 * решения владельца ничего не значит.
 */
data class PendingChange(
    val drugId: UUID,
    val consumed: BigDecimal? = null,
    val claimAfter: BigDecimal? = null,
    val drugVersion: Long? = null,
    val claimsVersion: Long? = null
)
