package com.kert0n.medapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Локальная база: что клиент помнит, когда сети нет.
 *
 * Это **заготовка**: ни базы (`@Database`), ни запросов (`@Dao`) здесь ещё нет, поэтому KSP не
 * подключён — Room присутствует одними аннотациями. Схема поедет вместе с ними и наверняка
 * изменится.
 *
 * Три решения, которые менять не придётся:
 *
 * - **Идентификаторы — строки.** Room не знает `UUID` без конвертера, а строка совпадает с тем,
 *   что приходит с провода.
 * - **Величины — тоже строки.** Предел сервера `numeric(19, 6)` не помещается в `REAL`, а
 *   `Double` округлил бы половину таблетки молча. Считать по ним всё равно нельзя: считает домен.
 * - **Версии лежат рядом с данными.** Уходя в офлайн, клиент запоминает не только состояние, но
 *   и то, каким оно было, — иначе вернувшись ему нечего предъявить серверу.
 */

/** Аптечка. Содержимое — в [DrugEntity], здесь только счётчик участников. */
@Entity(tableName = "med_kits")
data class MedKitEntity(
    @PrimaryKey val id: String,
    val userCount: Long
)

/** Упаковка. Уезжает вместе с аптечкой: на сервере её удаление тоже каскадное. */
@Entity(
    tableName = "drugs",
    foreignKeys = [
        ForeignKey(
            entity = MedKitEntity::class,
            parentColumns = ["id"],
            childColumns = ["medKitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("medKitId")]
)
data class DrugEntity(
    @PrimaryKey val id: String,
    val medKitId: String,
    val name: String,
    val quantity: String,
    val quantityUnitId: String,
    val formTypeId: String? = null,
    val category: String? = null,
    val manufacturer: String? = null,
    val country: String? = null,
    val description: String? = null,
    val version: Long
)

/**
 * Что заявлено на упаковку.
 *
 * Отдельной таблицей, а не колонками в [DrugEntity]: у броней своя версия, и меняются они от
 * чужих действий. Слитые в одну строку, они давали бы версию, которая скачет от того, к чему
 * пачка отношения не имеет.
 */
@Entity(
    tableName = "claims",
    foreignKeys = [
        ForeignKey(
            entity = DrugEntity::class,
            parentColumns = ["id"],
            childColumns = ["drugId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ClaimsEntity(
    @PrimaryKey val drugId: String,
    val total: String,
    /** `null`, когда своей брони нет. */
    val mine: String? = null,
    val version: Long
)

/**
 * Единицы измерения и формы выпуска в одной таблице.
 *
 * Два словаря одной формы: идентификатор и имя. Разделять их на две таблицы значило бы дважды
 * написать одно и то же; [kind] отвечает, какой именно словарь.
 */
@Entity(tableName = "vocabulary")
data class VocabularyEntryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String
) {
    companion object {
        const val QUANTITY_UNIT = "quantity_unit"
        const val FORM_TYPE = "form_type"
    }
}

/**
 * Кусочек справочника Vidal.
 *
 * Целиком он сюда не поедет: на сервере это десятки тысяч записей. Складывается то, что человек
 * искал, — чтобы повторный поиск работал и без сети.
 */
@Entity(tableName = "drug_templates")
data class DrugTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val nameLat: String? = null,
    val activeSubstance: String? = null,
    val formTypeId: String? = null,
    val category: String? = null,
    val quantityUnitId: String? = null,
    val manufacturer: String? = null,
    val country: String? = null,
    val description: String? = null
)

/**
 * Журнал приёмов — только здесь.
 *
 * На сервер он не уходит: там о человеке не хранят ничего, кроме идентификатора и хеша ключа, а
 * «когда принял таблетку» слишком персонально для таблицы. Наружу едет один итог — списание.
 */
@Entity(
    tableName = "intakes",
    indices = [Index("drugId")]
)
data class IntakeEntity(
    @PrimaryKey val id: String,
    val drugId: String,
    val quantity: String,
    val takenAtEpochMillis: Long
)

/**
 * Очередь несинхронизированного: что сделано офлайн и ещё не доехало.
 *
 * По строке на упаковку, а не по строке на действие: сервер принимает состояние пачки одним
 * запросом, и накопленное всё равно схлопывается в него. Съеденное копится дельтой, бронь
 * запоминается абсолютным значением.
 */
@Entity(
    tableName = "pending_changes",
    foreignKeys = [
        ForeignKey(
            entity = DrugEntity::class,
            parentColumns = ["id"],
            childColumns = ["drugId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PendingChangeEntity(
    @PrimaryKey val drugId: String,
    val consumed: String? = null,
    val claimAfter: String? = null,
    val drugVersion: Long? = null,
    val claimsVersion: Long? = null,
    /** Ключ идемпотентности: тот же `syncId` в маршруте синхронизации при повторной отправке. */
    val syncId: String
)
