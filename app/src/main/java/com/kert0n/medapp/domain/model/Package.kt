package com.kert0n.medapp.domain.model

import java.time.Instant
import java.time.LocalDate
import kotlin.uuid.Uuid

const val PACKAGE_NAME_MAX_LENGTH = 300
const val PACKAGE_CATEGORY_MAX_LENGTH = 200
const val PACKAGE_MANUFACTURER_MAX_LENGTH = 300
const val PACKAGE_COUNTRY_MAX_LENGTH = 100
const val PACKAGE_DESCRIPTION_MAX_LENGTH = 4000
const val PACKAGE_NOTE_MAX_LENGTH = 200

enum class PackageStatus {
    ACTIVE,
    ARCHIVED,       // израсходована, утилизирована или удалена человеком
    INACCESSIBLE    // была общей, доступ утрачен: вышли из аптечки, унесли, удалили
}

/**
 * Упаковка — конкретная пачка или флакон, а не «лекарство вообще». Одинаковые названия не
 * объединяют пачки: покупка другой пачки не пополняет старую (PLAN C0).
 *
 * [quantity] — **подтверждённый** остаток, последний согласованный с сервером или записанный
 * локальной командой (PLAN E1). Это не «сколько сейчас видно на экране»: проекция незакрытых
 * намерений очереди считается отдельно и здесь не хранится, иначе она разъехалась бы с очередью.
 *
 * Часть полей знает сервер, часть — только устройство (PLAN C0). Граница проведена в
 * `PackagePostNetworkDTO`: локальные поля не уезжают физически, а не по договорённости.
 *
 * **Это сущность, а не величина.** Пачка, из которой приняли таблетку, — та же самая пачка;
 * содержимое уменьшается, её переносят, она кончается, и ни одно из этих событий не делает её
 * другой пачкой. Поэтому тождество — это [id], равенство идёт по нему, и `data class` здесь был
 * бы неверен: он утверждает, что смена поля даёт другой объект.
 *
 * Из этого же следует отсутствие `copy()`. Состояние меняют только переходы ниже, а собрать
 * пачку можно двумя названными путями: [create] заводит новую, [restore] восстанавливает
 * сохранённую. Иначе правило «пересчёт не оживляет архив» соблюдалось бы по дисциплине
 * вызывающего кода, а не сущностью.
 */
class Package private constructor(
    val id: Uuid,                 // придуман клиентом; он же серверный
    val medKitId: Uuid,

    // ——— знает сервер ———
    val name: String,
    val quantity: Quantity,
    val formId: Uuid?,
    val category: String?,
    val manufacturer: String?,
    val country: String?,
    val description: String?,

    // ——— знает только устройство ———
    val expiresOn: LocalDate?,
    val defaultIntakeAmount: Quantity?, // личная подсказка для внепланового приёма, не доза курса
    val note: String?,
    val price: Money?,                  // цена всей пачки
    val purchasedOn: LocalDate?,
    val openedOn: LocalDate?,
    val addedAt: Instant,               // для чужой пачки — момент ПЕРВОГО НАБЛЮДЕНИЯ
    val templateId: Uuid?,              // из какой карточки справочника заполнено

    val version: Long?,                 // null, пока на сервере не создана
    val claims: Claims?,                // null у неопубликованной аптечки
    val status: PackageStatus,
    val syncedAt: Instant?
) {

    init {
        requireText(name, PACKAGE_NAME_MAX_LENGTH, "Package.name")
        requireOptionalText(category, PACKAGE_CATEGORY_MAX_LENGTH, "Package.category")
        requireOptionalText(manufacturer, PACKAGE_MANUFACTURER_MAX_LENGTH, "Package.manufacturer")
        requireOptionalText(country, PACKAGE_COUNTRY_MAX_LENGTH, "Package.country")
        requireOptionalText(description, PACKAGE_DESCRIPTION_MAX_LENGTH, "Package.description")
        requireOptionalText(note, PACKAGE_NOTE_MAX_LENGTH, "Package.note")
        require(version == null || version >= 0) { "версия пачки не бывает отрицательной" }
        require(status != PackageStatus.ACTIVE || !quantity.isZero) {
            "активная пачка не бывает пустой"
        }
        // Подсказка — это «сколько я обычно принимаю из ЭТОЙ пачки»: величина в чужой единице
        // не подставится в форму приёма и молча притворилась бы подходящей.
        require(defaultIntakeAmount == null || defaultIntakeAmount.unitId == quantity.unitId) {
            "доза-подсказка измеряется той же единицей, что остаток пачки"
        }
    }

    /**
     * Дата передаётся, а не берётся из часов: иначе свойство непроверяемо тестом.
     *
     * Сравнение именно `isBefore`: дата включительная, пачка «годна до 31 марта» просрочена
     * только 1 апреля. Правило записано здесь, чтобы знак не «поправили» при рефакторинге.
     */
    fun isExpiredOn(date: LocalDate): Boolean = expiresOn?.isBefore(date) == true

    /**
     * Истекает ли срок в ближайшие [days] дней, считая [date] включительно.
     *
     * Именно «не позже чем через N дней», а не «ровно за N дней»: пороги 3 и 1 день (PLAN D8)
     * проверяются фоновой задачей, а она может задержаться и перепрыгнуть точную дату. Уже
     * просроченная пачка не «истекает скоро» — у неё другое состояние и другое сообщение.
     */
    fun expiresWithin(date: LocalDate, days: Long): Boolean {
        require(days >= 0) { "окно предупреждения не бывает отрицательным" }
        val expires = expiresOn ?: return false
        if (expires.isBefore(date)) return false
        return !expires.isAfter(date.plusDays(days))
    }

    /**
     * Расход: приём, плановый или разовый.
     *
     * Нехватка бросает — через [Quantity.minus], — потому что списание в минус запрещено
     * (PLAN D5). Пачка, израсходованная до нуля, архивируется, а не удаляется: строка остаётся,
     * и приёмы с движениями продолжают читаться по ней (PLAN D3).
     */
    fun consume(amount: Quantity): Package {
        requireActive("расход")
        require(!amount.isZero) { "расход нулевого количества не является приёмом" }
        return withQuantity(quantity - amount)
    }

    /**
     * Пересчёт: «пересчитал и увидел столько».
     *
     * Замена значения, а не дельта (PLAN E1), поэтому фактический остаток может оказаться и
     * больше прежнего. Единица обязана совпадать: смена единицы — отдельный сценарий пересчёта
     * без автоматической конверсии (PLAN D3), а не побочный эффект исправления числа.
     */
    fun correctTo(actual: Quantity): Package {
        requireActive("пересчёт")
        require(actual.unitId == quantity.unitId) {
            "пересчёт не меняет единицу: это отдельный сценарий"
        }
        return withQuantity(actual)
    }

    /** Принимает сведения целиком — и серверные поля, и локальные (PLAN D3). */
    fun describe(facts: PackageFacts): Package {
        requireActive("правка описания")
        return changed(
            name = facts.name,
            formId = facts.formId,
            category = facts.category,
            manufacturer = facts.manufacturer,
            country = facts.country,
            description = facts.description,
            expiresOn = facts.expiresOn,
            defaultIntakeAmount = facts.defaultIntakeAmount,
            note = facts.note,
            price = facts.price,
            purchasedOn = facts.purchasedOn,
            openedOn = facts.openedOn
        )
    }

    /**
     * Перенос в другую аптечку.
     *
     * Меняется только принадлежность. Что делать с серверной версией и бронями при переносе
     * через границу публикации, решает сценарий переноса (PLAN E6): домен не знает, опубликована
     * ли целевая аптечка, и притворяться, что знает, здесь нельзя.
     */
    fun moveTo(medKitId: Uuid): Package {
        requireActive("перенос")
        require(medKitId != this.medKitId) { "пачка уже лежит в этой аптечке" }
        return changed(medKitId = medKitId)
    }

    /**
     * Утилизация или удаление человеком.
     *
     * Идемпотентно: повторное нажатие не должно превращаться в ошибку. Из [INACCESSIBLE]
     * [PackageStatus.INACCESSIBLE] тоже разрешено — так человек убирает из списка пачку,
     * доступ к которой потерян.
     */
    fun archive(): Package =
        if (status == PackageStatus.ARCHIVED) this else changed(status = PackageStatus.ARCHIVED)

    /**
     * Доступ утрачен: вышли из аптечки, унесли её или удалили.
     *
     * Брони снимаются: сервер снимает их каскадом по участию (PLAN D5), и держать их снимок
     * значило бы показывать чужие брони на пачке, которой у нас больше нет. [version]
     * сохраняется как последнее наблюдённое, но предусловием больше не служит — связанные
     * операции очереди снимает сценарий синхронизации.
     *
     * Из [ARCHIVED][PackageStatus.ARCHIVED] переход запрещён: он ничего не добавляет к истории,
     * а пачку из неё спрятал бы.
     */
    fun loseAccess(): Package {
        if (status == PackageStatus.INACCESSIBLE) return this
        check(status != PackageStatus.ARCHIVED) {
            "архивная пачка доступ не теряет: её история уже закрыта"
        }
        return changed(status = PackageStatus.INACCESSIBLE, claims = null)
    }

    private fun withQuantity(left: Quantity): Package = changed(
        quantity = left,
        status = if (left.isZero) PackageStatus.ARCHIVED else status
    )

    /**
     * Пересчёт не оживляет архив, а описание недоступной пачки не правится.
     *
     * `ARCHIVED` означает «израсходована, утилизирована или удалена человеком»; отменять
     * осознанное удаление новым числом нельзя. Возврат из архива, если он понадобится, будет
     * отдельным явным действием со своим экраном подтверждения.
     */
    private fun requireActive(action: String) {
        check(status == PackageStatus.ACTIVE) { "$action недоступен для пачки в состоянии $status" }
    }

    /**
     * Единственный способ получить изменённый экземпляр, и он приватный: снаружи состояние меняют
     * только переходы выше.
     *
     * [id] и [addedAt] в списке отсутствуют — тождество и момент появления пачки не меняются
     * никогда. Значения по умолчанию берутся из текущего состояния, поэтому явный `claims = null`
     * очищает брони, а непереданный аргумент их сохраняет.
     */
    private fun changed(
        medKitId: Uuid = this.medKitId,
        name: String = this.name,
        quantity: Quantity = this.quantity,
        formId: Uuid? = this.formId,
        category: String? = this.category,
        manufacturer: String? = this.manufacturer,
        country: String? = this.country,
        description: String? = this.description,
        expiresOn: LocalDate? = this.expiresOn,
        defaultIntakeAmount: Quantity? = this.defaultIntakeAmount,
        note: String? = this.note,
        price: Money? = this.price,
        purchasedOn: LocalDate? = this.purchasedOn,
        openedOn: LocalDate? = this.openedOn,
        templateId: Uuid? = this.templateId,
        version: Long? = this.version,
        claims: Claims? = this.claims,
        status: PackageStatus = this.status,
        syncedAt: Instant? = this.syncedAt
    ): Package = Package(
        id = id,
        medKitId = medKitId,
        name = name,
        quantity = quantity,
        formId = formId,
        category = category,
        manufacturer = manufacturer,
        country = country,
        description = description,
        expiresOn = expiresOn,
        defaultIntakeAmount = defaultIntakeAmount,
        note = note,
        price = price,
        purchasedOn = purchasedOn,
        openedOn = openedOn,
        addedAt = addedAt,
        templateId = templateId,
        version = version,
        claims = claims,
        status = status,
        syncedAt = syncedAt
    )

    /** Тождество — [id]. Пачка, из которой приняли таблетку, та же самая пачка. */
    override fun equals(other: Any?): Boolean =
        this === other || (other is Package && other.id == id)

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "Package(id=$id, name=$name, status=$status)"

    companion object {

        /**
         * Заведение новой пачки: её ещё не было ни на сервере, ни в базе.
         *
         * Пустой она быть не может — заводить нечего, и начальный остаток на проводе строго
         * положителен (PLAN B2). Версии и броней у неё нет по построению, а не по забывчивости
         * вызывающего: пачка появится на сервере отдельной операцией.
         */
        fun create(
            id: Uuid,
            medKitId: Uuid,
            quantity: Quantity,
            facts: PackageFacts,
            addedAt: Instant,
            templateId: Uuid? = null
        ): Package {
            require(!quantity.isZero) { "новая пачка не бывает пустой" }
            return Package(
                id = id,
                medKitId = medKitId,
                name = facts.name,
                quantity = quantity,
                formId = facts.formId,
                category = facts.category,
                manufacturer = facts.manufacturer,
                country = facts.country,
                description = facts.description,
                expiresOn = facts.expiresOn,
                defaultIntakeAmount = facts.defaultIntakeAmount,
                note = facts.note,
                price = facts.price,
                purchasedOn = facts.purchasedOn,
                openedOn = facts.openedOn,
                addedAt = addedAt,
                templateId = templateId,
                version = null,
                claims = null,
                status = PackageStatus.ACTIVE,
                syncedAt = null
            )
        }

        /**
         * Восстановление сохранённого состояния: строка базы или снимок сервера.
         *
         * Принимает любое допустимое состояние, включая архивную пачку с нулевым остатком, —
         * и именно поэтому назван отдельно от [create]. Это не бизнес-переход: он ничего не
         * решает, а только возвращает то, что уже было решено раньше.
         */
        @Suppress("LongParameterList")
        fun restore(
            id: Uuid,
            medKitId: Uuid,
            name: String,
            quantity: Quantity,
            formId: Uuid?,
            category: String?,
            manufacturer: String?,
            country: String?,
            description: String?,
            expiresOn: LocalDate?,
            defaultIntakeAmount: Quantity?,
            note: String?,
            price: Money?,
            purchasedOn: LocalDate?,
            openedOn: LocalDate?,
            addedAt: Instant,
            templateId: Uuid?,
            version: Long?,
            claims: Claims?,
            status: PackageStatus,
            syncedAt: Instant?
        ): Package = Package(
            id = id,
            medKitId = medKitId,
            name = name,
            quantity = quantity,
            formId = formId,
            category = category,
            manufacturer = manufacturer,
            country = country,
            description = description,
            expiresOn = expiresOn,
            defaultIntakeAmount = defaultIntakeAmount,
            note = note,
            price = price,
            purchasedOn = purchasedOn,
            openedOn = openedOn,
            addedAt = addedAt,
            templateId = templateId,
            version = version,
            claims = claims,
            status = status,
            syncedAt = syncedAt
        )
    }
}
