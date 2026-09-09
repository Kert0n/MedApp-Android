# PLAN.md — MedApp Android

## Контекст

Клиент MedApp начинался как шаблон: пустой Compose-проект, наброски моделей и DTO, контрактный
тест. Сервер, наоборот, готов и заморожен контрактом. Он приватен по замыслу — хранит о человеке
только идентификатор и хеш ключа, **не имеет ни одной колонки с датой** и не ведёт журнала приёмов.

Всё, что делает приложение органайзером лекарств — курсы, расписание, напоминания, сроки годности,
аналитика, — живёт на устройстве и обязано уживаться с общим складом, который может измениться под
руками у другого участника. Этот документ описывает, как именно.

Короткая выжимка правил — в [AGENTS.md](AGENTS.md). Наличие класса, экрана или метода здесь
**не означает**, что он реализован; состояние отслеживает матрица требований (J4).

## Что где лежит

| часть | о чём | когда открывать |
|---|---|---|
| [B. Серверный контракт](#часть-b-серверный-контракт) | 26 операций, версии, форма величин, границы контракта | задача трогает провод |
| [C. Продуктовые решения](#часть-c-продуктовые-решения) | что и почему изменено относительно исходных требований | сомневаетесь, почему сделано так |
| [D. Модель](#часть-d-модель) | сигнатуры: значения, аптечка, упаковка, курс, приём, движение, уведомления | пишете домен |
| [E. Синхронизация](#часть-e-синхронизация) | смысл остатка, очередь, установление исхода, публикация | трогаете сеть или очередь |
| [F. Хранение](#часть-f-хранение) | таблицы, ограничения, конвертеры, миграции | трогаете Room |
| [G. Безопасность](#часть-g-безопасность) | токен, ключ, хранилище | трогаете учётные данные |
| [H. Приложение](#часть-h-приложение) | слои, сборка, 28 экранов, поиск, сканер, аналитика | пишете интерфейс |
| [I. План работ](#часть-i-план-работ) | 19 PR, коммиты внутри каждого, граф зависимостей | берёте задачу |
| [J. Проверка](#часть-j-проверка) | уровни тестов, сквозные сценарии, матрица требований | сдаёте работу |

---

# ЧАСТЬ B. Серверный контракт

## B1. Авторизация

```
POST /v1/auth/register   X-Registration-Token: <из конфигурации сборки>   → {login, key}
POST /v1/auth/token      Authorization: Basic base64(login:key)           → {accessToken}
всё остальное            Authorization: Bearer <accessToken>
```

`login` — UUID. `key` — 43 символа URL-safe base64, показывается **один раз** и больше не выдаётся.
JWT несёт `sub`, `iat`, `exp`; в проде живёт **10 минут**, refresh-токена нет — истёк, берём новый
по Basic. Троттлинг выдачи: 20 попыток за 5 минут на адрес, отсюда 429.

## B2. Форма величин на проводе

**Количества — десятичные строки**, не JSON-числа: `numeric(19,6)` не помещается в `Double`, и
число на проводе теряло бы разряды.

| | шаблон |
|---|---|
| неотрицательное (остаток, сумма броней) | `^\d{1,13}(\.\d{1,6})?$` |
| строго положительное (приём, бронь, начальный остаток) | `^(?!0+(\.0+)?$)\d{1,13}(\.\d{1,6})?$` |

Не более 13 разрядов до точки и 6 после. Знака и экспоненты нет. **Ответы приходят ровно с шестью
знаками** (`"100.000000"`), запросы — в любом допустимом виде. Значит `"1"`, `"1.0"` и `"1.000000"`
— одно число, и сравнивать их строками нельзя.

## B3. Версии и предусловия

**У упаковки две независимые версии:**

- `drug.version` — состояние пачки (поля, количество, принадлежность аптечке);
- `reservations.version` — картина броней на ней.

Поэтому `ETag` не применяется: одному ресурсу пришлось бы отдать два тега. Версия передаётся явно —
в теле (`DrugPatchRequest.version`, `IntakeRequest.version`, `ReservationPatchRequest.version`) или
в query (`?version=` у `DELETE` и `PUT .../drugs/{drugId}`).

| код | значение |
|---|---|
| **428** | команда не назвала версию, на которой действует |
| **412** | названная версия не текущая |
| **409** | у `sync` — версия не текущая **или** тот же `syncId` использован для другого содержимого; у создания — идентификатор уже занят; у `POST /v1/reservations` — бронь на эту пачку уже есть |

**У аптечки версии нет.** Участие — самостоятельная строка, а не элемент версионируемого списка;
выход и удаление предусловия не требуют.

## B4. Все 26 операций

Столбец «повтор» отвечает на вопрос: безопасно ли послать то же самое ещё раз, не зная исхода
первой попытки.

### Учётные записи

| операция | запрос | успех | ошибки | повтор |
|---|---|---|---|---|
| `register` | `POST /v1/auth/register`, заголовок `X-Registration-Token`, тела нет | `200 RegisterResponse{login, key}` | 403 неверный токен, 429 | **нет** — даст вторую учётку |
| `token` | `POST /v1/auth/token`, Basic | `200 TokenResponse{accessToken}` | 401, 429 | да |

### Снимок и словари

| операция | запрос | успех | ошибки | повтор |
|---|---|---|---|---|
| `getSnapshot` | `GET /v1/users/me` | `200 UserSnapshotDTO{id, medKits[]}` | 401 | да |
| `listMedKits` | `GET /v1/med-kits` | `200 [MedKitSummaryDTO{id, userCount, drugIds}]` | 401 | да |
| `getMedKit` | `GET /v1/med-kits/{medKitId}` | `200 MedKitDTO{id, userCount, drugs[]}` | 404, 401 | да |
| `listQuantityUnits` | `GET /v1/quantity-units` | `200 [VocabularyEntryDTO{id, name}]` | 401 | да |
| `listFormTypes` | `GET /v1/form-types` | `200 [VocabularyEntryDTO]` | 401 | да |

`UserSnapshotDTO` = `{id, medKits: [MedKitDTO]}`, `MedKitDTO` = `{id, userCount, drugs: [DrugSnapshotDTO]}`,
`DrugSnapshotDTO` = `{drug: DrugDTO, reservations: ReservationsDTO}`. Один запрос — всё видимое.

### Аптечки

| операция | запрос | успех | ошибки | повтор |
|---|---|---|---|---|
| `createMedKit` | `POST /v1/med-kits` + `MedKitCreateRequest{id}` | `201 MedKitCreatedDTO{id}` | 400, **409 занят**, 401 | **да**, 409 = «уже создана» |
| `deleteMedKit` | `DELETE /v1/med-kits/{medKitId}[?targetMedKitId=]` | `204` | 404, 401 | да, 404 = «уже удалена» |
| `createInvitation` | `POST /v1/med-kits/{medKitId}/invitations` | `201 InvitationDTO{key}` | 404, 401 | да, выдаст новый ключ |
| `joinMedKit` | `POST /v1/med-kit-memberships` + `{key}` | `201 MedKitDTO` **с содержимым** | 404 ключ негоден, 409 уже участник, 401 | да, 409 = «уже вступили» |
| `leaveMedKit` | `DELETE /v1/med-kit-memberships/{medKitId}` | `204` | 404, 401 | да |

`deleteMedKit` удаляет аптечку **у всех**, вместе с пачками и бронями на них; `targetMedKitId`
переносит содержимое в другую аптечку вызывающего вместо уничтожения. `leaveMedKit` убирает только
вызывающего и его брони; аптечка и остальные остаются.

### Упаковки

| операция | запрос | успех | ошибки | повтор |
|---|---|---|---|---|
| `createDrug` | `POST /v1/med-kits/{medKitId}/drugs` + `DrugCreateRequest{id, name, quantity, quantityUnitId, formTypeId?, category?, manufacturer?, country?, description?}` | `201 DrugSnapshotDTO` | 400, 404, **409 занят**, 401 | **да**, 409 = «уже создана» |
| `getDrug` | `GET /v1/drugs/{drugId}` | `200 DrugSnapshotDTO` | 404, 401 | да |
| `patchDrug` | `PATCH /v1/drugs/{drugId}` + `DrugPatchRequest{…, version}` | `200 DrugSnapshotDTO` | 400, 404, 428, 412, 401 | да, с **исходной** версией |
| `deleteDrug` | `DELETE /v1/drugs/{drugId}?version=` | `204` | 404, 428, 412, 401 | да |
| `moveDrug` | `PUT /v1/med-kits/{targetMedKitId}/drugs/{drugId}?version=` | `200 DrugSnapshotDTO` | 404, 428, 412, 401 | да, с исходной версией |
| `recordIntake` | `POST /v1/drugs/{drugId}/intakes` + `IntakeRequest{quantity, version}` | `200 DrugSnapshotDTO` **или 200 с пустым телом**, если пачка кончилась и уничтожена | 400 больше остатка, 404, 428, 412, 401 | **только с исходной версией** |
| `synchronise` | `PUT /v1/drugs/{drugId}/sync/{syncId}` + `DrugSyncRequest{consumed?, drugVersion?, reservation?}` | `200 DrugSnapshotDTO` или пустое тело | 404, **409**, 401 | да, тем же `syncId` |

`DrugPatchRequest`: **`null` значит «не трогать»**. Очистка необязательного поля — пустая строка
(минимальная длина у них нулевая). `quantity` в нём — это **пересчёт**, а не пополнение: «пересчитал
пачку и увидел другое число»; брони при этом не трогаются.

`DrugSyncRequest`: `consumed` — **дельта** («столько израсходовано офлайн»), `reservation.amount` —
**абсолютная** величина («столько заявлено после офлайн-сессии»). `drugVersion` обязателен, когда
есть `consumed`. `reservation.version` можно опустить — тогда сервер возьмёт текущую картину.
Отсутствие блока `reservation` значит «бронь не менялась», а не «снять».

### Брони

| операция | запрос | успех | ошибки | повтор |
|---|---|---|---|---|
| `listReservations` | `GET /v1/reservations` | `200 [ReservationDTO{drugId, amount}]` | 401 | да |
| `getReservation` | `GET /v1/reservations/{drugId}` | `200 ReservationDTO` | 404, 401 | да |
| `createReservation` | `POST /v1/reservations` + `{drugId, amount, version}` | `201 ReservationDTO` | 400, 404, **409 бронь уже есть**, 428, 412, 401 | да, 409 = «уже создана» |
| `patchReservation` | `PATCH /v1/reservations/{drugId}` + `{amount, version}` | `200 ReservationDTO` | 400, 404, 428, 412, 401 | да, с исходной версией |
| `deleteReservation` | `DELETE /v1/reservations/{drugId}?version=` | `204` | 404, 428, 412, 401 | да |

Первое заявление на пачку — `POST`, последующие — `PATCH`. Клиент не гадает: 409 на `POST` значит
«переходи на `PATCH`», 404 на `PATCH` значит «переходи на `POST`».

Бронь **может превышать остаток**: сколько своей брони держать — решение владельца, а не сервера.

### Справочник

| операция | запрос | успех | ошибки |
|---|---|---|---|
| `searchDrugTemplates` | `GET /v1/drug-templates?query=<1..200>&limit=<1..50>` | `200 [DrugTemplateDTO]` | 400, 401 |
| `getDrugTemplate` | `GET /v1/drug-templates/{templateId}` | `200 DrugTemplateDTO` | 404, 401 |

`DrugTemplateDTO{id, name, nameLat?, activeSubstance?, formTypeId?, category?, quantityUnitId?,
manufacturer?, country?, description?}`. **Количества и срока годности в нём нет** — их неоткуда
взять для конкретной пачки. Поиск идёт по названию, латинскому названию, действующему веществу и
производителю.

## B5. Ошибки

`application/problem+json`. `type` всегда `about:blank`, машиночитаемого кода нет — **код ответа и
есть код ошибки**. Единственное расширение — `errors: [{field, reason}]` при 400. Серверный `detail`
как единственный пользовательский текст не показывается: он на английском и описывает нарушенное
ограничение, а не то, что человеку делать.

### Ожидания по операциям — почему не «важна только семья кода»

Общего правила «`2xx` — успех» недостаточно: `200`, `201` и `204` значат разное, и пустой ответ на
создании сделал бы публикацию незавершимой. У каждой операции в `MedAppApi` объявлен набор
допустимых статусов и требование к телу; всё прочее — ошибка протокола.

**Известное отклонение.** `recordIntake` и `synchronise` в коде возвращают обнуляемый результат без
`@ResponseStatus(NO_CONTENT)`, поэтому при опустошении пачки приходит **`200` с пустым телом**, хотя
снимок контракта показывает `204` (это вывод springdoc, а не поведение). Для этих двух операций
успех с пустым телом означает **«пачка израсходована и уничтожена»**. Для всех остальных пустое
тело — ошибка протокола.

## B6. Границы контракта, с которыми клиент живёт

| граница | следствие |
|---|---|
| **Сводный `GET /v1/med-kits` не несёт количеств, версий и броней** — только `id`, `userCount`, `drugIds` | **Дешёвой сверки не существует.** Сосед принял 5 таблеток — состав и `userCount` не изменились, расхождения не видно. Синхронизация **всегда** читает полный `GET /v1/users/me` |
| `PATCH /v1/reservations/{drugId}` возвращает `{drugId, amount}` — **без новой версии картины броней** | После любой команды над бронью нужен `GET` упаковки, чтобы узнать версию для следующей команды |
| Снять бронь вместе с приёмом одним запросом нельзя: `reservation.amount` строго положителен, а отсутствие блока значит «не менять» | Снятие — отдельный `DELETE /v1/reservations/{drugId}`. Так заканчивается каждый курс |
| Пересчёт остатка **до нуля** невыразим: `quantity` строго положителен | Выражается `DELETE /v1/drugs/{drugId}`; локально пишется честное движение `CORRECTION` или `DISPOSAL`, а не выдуманный приём |
| `null` в `PATCH` значит «не трогать» | Очистка необязательного поля — пустая строка. В клиенте `""` и `null` значат одно: «не заполнено» |
| Журнал идемпотентности `sync` — **Caffeine в памяти процесса**, 24 часа, теряется при перезапуске, не разделяется между экземплярами | Повтор безопасен **не благодаря журналу**, а благодаря замороженному предусловию (E3) |
| Ответ `createInvitation` не несёт `expiresAt`; ключ лежит в кэше и может быть вытеснен раньше срока | Показывается **оценка** оставшегося времени от момента создания, помеченная как оценка, плюс кнопка «обновить код» |
| Неизвестный ключ, истёкший ключ и выход пригласившего дают одинаковый 404 | Единственный текст: **«Приглашение недействительно. Попросите новый код»** |
| Дельта-эндпойнта, курсора и `updatedAt` нет | Каждая синхронизация — полный снимок |
| Приглашение одно на оба способа: и QR, и текстовый код несут один `key` с одним сроком | Обещать QR 15 минут, а тексту час, нельзя. Показывается один срок |

Четыре из них — кандидаты в issue сервера: **отсутствие дельты**, **версия в ответе `PATCH
/reservations`**, **атомарное снятие брони вместе с приёмом**, **`expiresAt` у приглашения**. Ни
один не блокирует работу, каждый стоит клиенту лишнего запроса или менее точного текста.

---
---
---

# ЧАСТЬ C. Продуктовые решения

## C1. Таблица решений

Что изменилось относительно исходных требований и почему. Строка меняется только явным решением, а
не по ходу реализации.

| решение | было | стало | почему |
|---|---|---|---|
| **Курс** | привязан к одной упаковке | самостоятельная сущность со **стеком источников** | лечение распределено по пачкам и аптечкам; человек хочет допить начатую и перейти к следующей |
| **Нехватка** | «сокращаем курс до максимально возможного срока» | **сокращаем обеспечение**, расписание не трогаем | расписание — намерение человека; чужое действие не должно его переписывать. Показываем «нужно 28, обеспечено 9, не хватает с 11 сентября» |
| **Ползунок** | автоматически снимает с других источников | зажимается верхней границей; перераспределяет человек | автоматика молча переписывала бы уже принятое решение. Хочешь больше во втором — сначала уменьши первый |
| **Выделение** | произвольное количество | **целое число доз** | доза 2, выделено по 1 в двух пачках: сумма равна потребности, покрытие ноль, ползунки зажаты — тупик |
| **Доза** | могла делиться между пачками | берётся **из одной упаковки** | выбранная простая модель. Выбор источника для конкретной дозы («по субботам из дачной») — заложенное расширение, не реализуется сейчас |
| **Общие сведения** | название аптечки и срок годности общие | **локальные у каждого** | приватность: сервер их не хранит и не будет |
| **Обязательные поля упаковки** | название, форма, количество, срок годности | название, количество, **единица**, аптечка | форма и срок годности часто неизвестны в момент заведения; требовать их — заставлять врать |
| **Приглашение** | QR 15 минут, текст 1 час | один ключ, один срок, показывается как оценка | сервер выдаёт один ключ на оба способа |
| **Текстовый код** | — | **автоматически копируется в буфер** | сохранено как было в требованиях |
| **Начало курса** | сразу расписание | **сохраняемый черновик с заметкой** | сценарий «записал у врача → купил → внёс»: до покупки расписания ещё нет |
| **Идентификаторы** | придумывал сервер | **придумывает клиент** | повтор создания перестал давать дубли |
| **Пачка и курсы** | — | **одна пачка — один незавершённый курс** | бизнес-правило: параллельное лечение одним препаратом согласуется вне приложения |
| **Просроченная пачка** | — | остаётся до ручного удаления, помечается, предупреждает при приёме | сохранено как было в требованиях |
| **Удаление упаковки** | «удалить из базы» | **архивирование** | иначе история приёмов теряет опору |

## C2. Что доступно без сети

| действие | локальная аптечка | общая, без сети |
|---|---|---|
| просмотр, поиск, фильтр, сортировка | да | по кэшу |
| добавление и правка упаковок | да | да, операцией в очередь |
| приём разовый и по курсу | да | да: факт локально, расход в очередь |
| пересчёт остатка, утилизация | да | да, операцией |
| создание и правка курса, перераспределение источников | да | да; брони уедут при связи |
| план на дату, уведомления, аналитика | да | да |
| перенос упаковки между аптечками, удаление аптечки, выход | да (локально) | **требует связи** |
| публикация, приглашение, вступление, справочник | — | **требует связи** |

**Первый запуск требует сети.** Без регистрации нет токена, без токена — ни словарей, ни
справочника. До успешной регистрации показывается экран первичной настройки с повтором, а не
пустой список, притворяющийся работающим приложением.

---
---
---

# ЧАСТЬ D. Модель

Три представления, и они не смешиваются. `*Dto` — форма провода, `*Entity` — форма строки в базе,
домен — ни то ни другое. `ReservationsDTO` и `Claims` совпадают по форме, но это разные вещи с
разным временем жизни, и одинаковое имя провоцировало бы подставить одно вместо другого.

## D1. Значения

```kotlin
package com.kert0n.medapp.domain.model

const val QUANTITY_SCALE = 6
const val QUANTITY_MAX_INTEGER_DIGITS = 13
const val QUANTITY_MAX_INPUT_LENGTH = 21   // 13 + точка + 6, плюс запас на ведущий ноль

/**
 * Количество вместе с единицей: величины в разных единицах не складываются даже случайно.
 *
 * Равенство — по числовому значению, а не по умолчанию data-класса: BigDecimal.equals
 * различает 1 и 1.000000 по масштабу, а сервер отвечает всегда шестью знаками.
 */
data class Quantity(val amount: BigDecimal, val unitId: Uuid) {
    init {
        require(amount.signum() >= 0) { "количество не бывает отрицательным" }
        require(amount.scale() <= QUANTITY_SCALE)
        require(amount.precision() - amount.scale() <= QUANTITY_MAX_INTEGER_DIGITS)
    }

    operator fun plus(other: Quantity): Quantity          // требует совпадения единиц

    /** Бросает при нехватке: приём 5 из остатка 3 не должен выглядеть успешным. */
    operator fun minus(other: Quantity): Quantity

    /** Для отображения доступности, где отрицательное просто не показывается. */
    fun minusOrZero(other: Quantity): Quantity

    operator fun times(count: Int): Quantity

    fun covers(dose: Quantity): Boolean
    /** Сколько целых доз помещается. Нулевая доза — ошибка; результат зажат Int.MAX_VALUE. */
    fun dosesIn(dose: Quantity): Int

    val isZero: Boolean

    /** Ровно то, что уходит на провод: без экспоненты, без знака. */
    fun toWire(): String = amount.toPlainString()

    override fun equals(other: Any?): Boolean   // unitId + compareTo == 0
    override fun hashCode(): Int                // по stripTrailingZeros

    companion object {
        /**
         * Запятая → точка. Отвергает: экспоненту, знак, пустое, > QUANTITY_MAX_INPUT_LENGTH,
         * больше шести знаков после точки, больше тринадцати до неё.
         */
        fun parse(input: String, unitId: Uuid): Result<Quantity>
        fun zero(unitId: Uuid): Quantity
    }
}

/** Цена всей пачки. Хранится и считается десятичной строкой — тем же правилом, что количества. */
data class Money(val amount: BigDecimal, val currencyCode: String = "RUB") {
    init { require(amount.signum() >= 0); require(amount.scale() <= 2) }
}

data class QuantityUnit(val id: Uuid, val name: String)
data class DosageForm(val id: Uuid, val name: String)
```

**`Comparable` у `Quantity` нет.** Числовое сравнение количеств нигде не нужно; нужны доменные
вопросы — хватает ли на дозу (`covers`) и сколько доз помещается (`dosesIn`). `Comparable` открыл бы
дорогу сравнениям в разных единицах.

**Обёрток над идентификаторами нет.** Идентификатор — `kotlin.uuid.Uuid`, тот же тип, что на
сервере; смысл несут имена свойств (`packageId`, `medKitId`), а лишний слой разворачивался бы на
каждой границе — Room, Ktor, навигация.

**Время — `java.time` и только оно.** `minSdk` 26 даёт его без десугаринга; держать рядом
`kotlinx-datetime` значило бы конвертировать на каждой границе. `Instant`, `LocalDate`, `LocalTime`,
`DayOfWeek`, `ZoneId`.

## D2. Аптечка

```kotlin
enum class KitPublication {
    LOCAL,        // на сервере не существует
    PUBLISHING,   // группа операций публикации ещё не завершена
    PUBLISHED     // существует на сервере
}

data class MedKit(
    val id: Uuid,                      // придуман клиентом; он же серверный
    val name: String,                  // 1..200, только на устройстве
    val location: String?,             // ≤300, только на устройстве
    val publication: KitPublication,
    val participantCount: Long,        // 1 у локальной, иначе userCount с сервера
    val createdAt: Instant,
    val syncedAt: Instant?
) {
    val isShared: Boolean get() = participantCount > 1
    val acceptsInvitations: Boolean get() = publication == KitPublication.PUBLISHED
}
```

**`PUBLISHING` — не косметика.** Пока группа операций публикации не завершена целиком, часть пачек
на сервере уже есть, а часть нет; приглашение в этом состоянии выдавать нельзя — второй участник
увидел бы половину аптечки.

**`PUBLISHED` не выводится из `participantCount`.** Аптечка, из которой ушли все, кроме меня,
остаётся серверной: там лежат мои пачки, и локальной она уже не станет.

## D3. Упаковка

```kotlin
enum class PackageStatus {
    ACTIVE,
    ARCHIVED,       // израсходована, утилизирована или удалена человеком
    INACCESSIBLE    // была общей, доступ утрачен: вышли из аптечки, унесли, удалили
}

/** Что заявлено на упаковку. Приходит с сервера со своей версией. */
data class Claims(
    val total: BigDecimal,     // сумма всех броней; МОЖЕТ превышать остаток
    val mine: BigDecimal?,     // моя часть; null — я ничего не заявлял
    val version: Long
)

data class Package(
    val id: Uuid,                 // придуман клиентом; он же серверный
    val medKitId: Uuid,

    // ——— знает сервер ———
    val name: String,             // 1..300
    val quantity: Quantity,       // ПОДТВЕРЖДЁННЫЙ остаток, см. E1
    val formId: Uuid?,
    val category: String?,        // ≤200
    val manufacturer: String?,    // ≤300
    val country: String?,         // ≤100
    val description: String?,     // ≤4000

    // ——— знает только устройство ———
    val expiresOn: LocalDate?,
    val dose: Quantity?,          // разовая доза; подставляется как placeholder при приёме
    val note: String?,            // ≤200
    val price: Money?,            // цена всей пачки
    val purchasedOn: LocalDate?,
    val openedOn: LocalDate?,
    val addedAt: Instant,         // для чужой пачки — момент ПЕРВОГО НАБЛЮДЕНИЯ
    val templateId: Uuid?,        // из какой карточки справочника заполнено

    val version: Long?,           // null, пока на сервере не создана
    val claims: Claims?,          // null у неопубликованной аптечки
    val status: PackageStatus,
    val syncedAt: Instant?
) {
    /** Дата передаётся, а не берётся из часов: иначе свойство непроверяемо тестом. */
    fun isExpiredOn(date: LocalDate): Boolean = expiresOn?.isBefore(date) == true
    fun expiresWithin(date: LocalDate, days: Long): Boolean

    fun consume(amount: Quantity): Package      // до нуля → ARCHIVED
    fun correctTo(actual: Quantity): Package    // ноль → ARCHIVED
    fun describe(edit: PackageEdit): Package
    fun moveTo(medKitId: Uuid): Package
    fun archive(): Package
    fun loseAccess(): Package
}

/** Правка описательных полей. null = «не трогать», "" = «очистить». */
data class PackageEdit(
    val name: String? = null,
    val formId: Uuid? = null,
    val category: String? = null,
    val manufacturer: String? = null,
    val country: String? = null,
    val description: String? = null,
    val expiresOn: LocalDate? = null,
    val dose: Quantity? = null,
    val note: String? = null,
    val price: Money? = null,
    val purchasedOn: LocalDate? = null,
    val openedOn: LocalDate? = null
)
```

**Упаковка не удаляется локально, а архивируется.** Строка остаётся, и приёмы с движениями читаются
по ней; иначе история за прошлый месяц оборвалась бы вместе с кончившейся пачкой.

## D4. Три величины «сколько доступно»

Это место, где легче всего смешать несовместимые числа. Величин три с половиной, и каждая
отвечает на свой вопрос.

```
effective        = quantity − Σ(consumed по НЕЗАКРЫТЫМ операциям этой упаковки)
                   // сколько таблеток реально осталось по нашим сведениям

reservedByOthers = max(0, claims.total − (claims.mine ?: 0))
                   // ЕДИНСТВЕННОЕ, что берётся с сервера: своей брони мы там не спрашиваем

myAllocation     = Σ allocatedDoses × dose по источникам МОИХ АКТИВНЫХ курсов на эту упаковку
                   // локально, всегда свежее claims.mine

freeForAnyone    = max(0, effective − reservedByOthers − myAllocation)
                   // «свободно» в карточке: столько можно взять, ничего не задев

availableToMe    = max(0, effective − reservedByOthers)
                   // максимум ползунка источника: своя бронь мне не мешает
```

**Почему `freeForAnyone` не считается как `effective − claims.total`.** `claims.total` включает
`claims.mine`, а `claims.mine` — это снимок с сервера, который отстаёт от локального выделения ровно
на то, что ещё не уехало. Смешивать свежий локальный остаток со старой суммой броней нельзя.

Проверка на числах: на сервере 20, `claims.mine` 10, чужого 5, локально по курсу принято 3 и
выделение уменьшилось до 7.

```
effective        = 20 − 3 = 17
reservedByOthers = 15 − 10 = 5
myAllocation     = 7                      (локально, свежее)
freeForAnyone    = 17 − 5 − 7 = 5         ✔
availableToMe    = 17 − 5 = 12            ✔ выделение можно нарастить с 7 до 12

через claims.total: 17 − 15 = 2           ✘ занизили на три собственные таблетки
```

**У неопубликованной аптечки серверных броней нет, но выделения есть.** `reservedByOthers = 0`,
`myAllocation` считается ровно так же. Из 20 таблеток 15 отданы курсу — свободно 5, и разовый приём
на 8 предупреждает, что затронет курс. Утверждение «в локальной аптечке всё равно `quantity`» —
ошибка.

```kotlin
/** Проекция для экранов. Ничего не хранит: иначе разъедется с очередью и с курсами. */
data class PackageView(
    val pkg: Package,
    val unitName: String,
    val formName: String?,
    val unsentConsumed: Quantity,       // из sync_operations
    val myAllocation: Quantity,         // из course_sources активных курсов
    val course: CourseBrief?,           // курс, которому назначена пачка
    val today: LocalDate
) {
    val effective: Quantity
    val reservedByOthers: Quantity
    val freeForAnyone: Quantity
    val availableToMe: Quantity
    val isExpired: Boolean
    val expiresSoon: Boolean
}

data class CourseBrief(val id: Uuid, val title: String, val allocatedDoses: Int)
```

## D5. Курс

```kotlin
enum class CourseStatus {
    DRAFT,        // ещё нет расписания или источников
    ACTIVE,
    COMPLETED,    // все приёмы отвечены или пропущены
    CANCELLED
}

data class CourseSchedule(
    val start: LocalDate,
    val endInclusive: LocalDate,
    val daysOfWeek: Set<DayOfWeek>,   // непустое
    val times: List<LocalTime>,       // непустое, отсортировано, без повторов
    val zone: ZoneId                  // СВОЙ у курса, не системный
) {
    init {
        require(!endInclusive.isBefore(start))
        require(daysOfWeek.isNotEmpty())
        require(times.isNotEmpty() && times.distinct().size == times.size)
    }
    fun occurrenceCount(): Int
}

/**
 * Источник — ЗНАЧЕНИЕ внутри курса, а не сущность: идентичность даёт пара (курс, упаковка),
 * приоритет — место в списке. Отдельного CourseSourceId не существует.
 */
data class CourseSource(
    val packageId: Uuid,
    val allocatedDoses: Int          // ВЫДЕЛЕНИЕ ХРАНИТСЯ В ДОЗАХ
) {
    init { require(allocatedDoses >= 0) }
}

data class Course(
    val id: Uuid,
    val title: String,               // 1..200
    val note: String?,               // ≤500 — «что купить», запись от врача
    val doseAmount: BigDecimal?,     // разовая доза курса
    val unitId: Uuid?,               // фиксируется первым источником
    val formId: Uuid?,               // фиксируется первым источником
    val schedule: CourseSchedule?,
    val sources: List<CourseSource>, // ПОРЯДОК = приоритет расходования
    val status: CourseStatus,
    val revision: Long,              // растёт при правке расписания или дозы
    val createdAt: Instant,
    val updatedAt: Instant
) {
    val dose: Quantity?              // doseAmount + unitId
    val allocatedDosesTotal: Int
    fun allocatedOf(packageId: Uuid): Quantity?

    fun rename(title: String, note: String?): Course
    fun setSchedule(schedule: CourseSchedule): Course        // revision++
    fun setDose(amount: BigDecimal): Course                  // revision++
    fun attach(pkg: Package, doses: Int): Result<Course>     // фиксирует форму и единицу
    fun detach(packageId: Uuid): Course
    fun reorder(from: Int, to: Int): Course
    fun allocate(packageId: Uuid, doses: Int): Course        // зажимается пределом
    fun activate(): Result<Course>
    fun complete(): Course
    fun cancel(): Course
}
```

### Почему выделение в дозах

Доза — 2 таблетки. В одной пачке свободна 1 таблетка, в другой тоже 1. Если хранить выделение в
таблетках, человек выделит 1 + 1 = 2, сумма сравняется с потребностью на один приём — и ползунки
зажмутся, потому что больше выделять некуда. А покрытие при этом ноль: ни из одной пачки дозу не
взять, а доза не делится между пачками. Выхода из этого состояния нет.

В дозах: `floor(1/2) = 0` и `floor(1/2) = 0`. Выделено 0 доз, покрыто 0, не хватает 1 — честно и
без тупика. Физический остаток меньше дозы остаётся свободным для разового приёма и брони не держит.

### Черновик

`DRAFT` с одним `title` и `note` — законное сохраняемое состояние: «записал у врача, куплю завтра».
У черновика **броней нет и упаковку он не занимает**: подключённые источники — предварительный
выбор. Брони и назначение появляются при `activate()`, которое требует расписания, дозы и хотя бы
одного источника.

### Форма и единица

Фиксируются первым подключённым источником. Дальше `attach` отвергает несовместимое.

**`formId = null` не совместим с `formId = null`.** «Форма неизвестна» и «форма неизвестна» — не
одно и то же: это две пачки, про каждую из которых мы ничего не знаем. Чтобы подключить пачку к
курсу, форму надо сначала заполнить. Экран так и говорит: «Укажите форму, чтобы подключить к курсу».

**Отвязка последнего источника у `ACTIVE` курса форму и единицу не сбрасывает** — иначе доза и
расписание мгновенно потеряли бы смысл, а уже состоявшиеся приёмы остались бы с единицей, которой
у курса больше нет. Курс просто становится необеспеченным. У `DRAFT` сбрасывает: там ещё нечего
терять.

### Часовой пояс

**У курса свой `zone`.** Перелёт не сдвигает назначенное лечение молча; смена зоны курса —
осознанное действие, пересоздающее только будущие приёмы.

Правило перехода на летнее и зимнее время, одно на весь проект, живёт в `ScheduleCalculator`:

- **время не существует** (перевод вперёд) → сдвигаем **вперёд** до ближайшего существующего;
- **время существует дважды** (перевод назад) → берём **первое** вхождение.

### Обеспечение — вычисляется, не хранится

```kotlin
data class SourceCoverage(
    val packageId: Uuid,
    val allocatedDoses: Int,
    val leftover: Quantity      // остаток пачки, не покрывающий целую дозу
)

data class CourseCoverage(
    val requiredDoses: Int,          // сколько приёмов ещё впереди
    val coveredDoses: Int,           // сколько из них обеспечено
    val coveredUntil: Instant?,      // «доступный курс» — до какого момента хватит
    val firstUncoveredAt: Instant?,  // с какого приёма не хватает
    val perSource: List<SourceCoverage>
) {
    val missingDoses: Int get() = requiredDoses - coveredDoses
    val isFullyCovered: Boolean get() = missingDoses == 0
}
```

Верхняя граница ползунка, в дозах:

```
maxDoses(i) = min( availableToMe(i).dosesIn(dose),
                   requiredDoses − Σ allocatedDoses(j), j ≠ i )
```

Сумма выделений не превышает потребности, и перераспределять задним числом нечего. Хочешь больше во
втором источнике — сначала уменьши первый. **Автоматики, снимающей выделение с других источников,
нет:** она молча переписывала бы уже принятое человеком решение.

### Жизненный цикл

| событие | что происходит с выделением |
|---|---|
| **приём подтверждён** | остаток источника уменьшается, выделение уменьшается на одну дозу |
| **фактическая доза меньше плановой** | пункт выполнен; остаток уменьшается на фактическое, потребность пересчитывается, избыток выделения освобождается |
| **приём пропущен** | потребность уменьшается на одну дозу → освобождается одна доза выделения, **начиная с конца стека**. Лишняя бронь не остаётся |
| **приём не отвечен, стал `MISSED`** | то же; подтвердить задним числом можно, и тогда источники и остатки проверяются заново |
| **подтверждён из другого источника курса** | законно: предсказание — это предсказание. Из пачки вне источников — приём становится внеплановым |
| **курс `COMPLETED`** | приёмов в состоянии `PLANNED` не осталось; назначения снимаются, остатки выделений освобождаются, брони снимаются |
| **курс `CANCELLED`** | то же, плюс **отменяются уже показанные уведомления** по его приёмам |

## D6. Приём

План и факт — одна запись: запланированный приём это приём, который ещё не состоялся.

```kotlin
enum class IntakeStatus {
    PLANNED,   // в будущем
    TAKEN,     // подтверждён
    SKIPPED,   // человек отказался
    MISSED     // не ответил в срок
}

data class Intake(
    val id: Uuid,
    val courseId: Uuid?,               // null — внеплановый разовый приём
    val courseRevision: Long?,         // какой редакцией расписания порождён
    val plannedPackageId: Uuid?,       // null, когда приём НЕ ОБЕСПЕЧЕН
    val takenPackageId: Uuid?,         // null, пока не подтверждён
    val medKitId: Uuid?,               // аптечка НА МОМЕНТ СОБЫТИЯ
    val plannedAt: Instant?,
    val plannedAmount: Quantity?,
    val takenAt: Instant?,
    val takenAmount: Quantity?,
    val unitId: Uuid,                  // единица НА МОМЕНТ СОБЫТИЯ
    val respondedAt: Instant?,
    val status: IntakeStatus,
    val serverConfirmed: Boolean       // расход подтверждён сервером
) {
    init {
        require(status != IntakeStatus.TAKEN ||
                (takenAt != null && takenAmount != null && takenPackageId != null))
        require(status != IntakeStatus.PLANNED || plannedAt != null)
        require(courseId != null || status == IntakeStatus.TAKEN)  // внеплановый только состоявшийся
    }
}
```

**Два поля источника вместо одного.** Необеспеченному будущему приёму назначить пачку нечего —
свободного запаса под него нет; а у подтверждённого пачка обязательна. Одно поле пришлось бы либо
сделать обязательным (и врать про необеспеченный приём), либо необязательным (и потерять
инвариант подтверждённого).

`plannedPackageId` — **точка расширения**. Когда понадобится «по субботам из дачной пачки, в будни
из домашней», изменится способ его заполнения (правило вместо порядка стека), а модель останется.

**`medKitId` и `unitId` пишутся на момент события.** Переименование пачки, смена единицы и перенос
в другую аптечку не должны переписывать прошлые отчёты.

## D7. Движение остатка

```kotlin
enum class AdjustmentKind {
    INITIAL,        // пачка заведена
    CORRECTION,     // пересчитали и увидели другое число
    DISPOSAL,       // выбросили: просрочка, порча
    TRANSFER_IN,    // приехала из другой аптечки
    TRANSFER_OUT,   // уехала
    REMOTE_CHANGE,  // изменилось на сервере, и это не мы
    ACCESS_LOST     // пачка перестала быть видимой
}

data class StockAdjustment(
    val id: Uuid,
    val packageId: Uuid,
    val kind: AdjustmentKind,
    val delta: BigDecimal,             // знаковая
    val unitId: Uuid,                  // НА МОМЕНТ СОБЫТИЯ
    val medKitId: Uuid?,               // где произошло
    val fromMedKitId: Uuid?,           // у переносов
    val toMedKitId: Uuid?,
    val occurredAt: Instant?,          // null — момент неизвестен (чужое изменение)
    val observedAt: Instant,           // когда мы это увидели
    val operationId: Uuid?,            // какая операция очереди это породила
    val note: String?
)
```

**`REMOTE_CHANGE` — это остаток, а не всё расхождение.** Он записывается как разница между
серверным количеством и тем, что объясняют **наши собственные подтверждённые** операции и движения:

```
remoteDelta = serverQuantity − (lastKnownQuantity + Σ наши подтверждённые изменения с прошлого снимка)
```

Иначе одно наше списание попало бы в отчёт дважды — записью приёма и изменением серверного числа.

**Причина не выдумывается.** `REMOTE_CHANGE` показывается как «изменилось другим участником», без
догадок про приём: сервер не хранит истории и сказать, что это было, не может.

**Внешние ключи на упаковку у приёмов и движений есть, с `ON DELETE RESTRICT`.** Строка упаковки не
удаляется (она архивируется), а ограничение защищает историю от случайного каскада. Каскадов здесь
нет ни одного.

## D8. Уведомления

Модель, а не только описание поведения: без неё дедупликация и отмена расползутся по экранам.

```kotlin
enum class NotificationKind {
    INTAKE_DUE,        // пора принять
    INTAKE_MISSED,     // не ответили в срок
    EXPIRY_SOON,       // срок годности приближается
    EXPIRED,           // истёк
    COVERAGE_SHORT,    // обеспечение курса сократилось чужим действием
    COVERAGE_ENDING,   // обеспечения хватит ещё на N дней
    DAILY_DIGEST,      // сводный план на день
    SYNC_ATTENTION     // операция требует решения человека
}

/**
 * Устойчивый ключ. Считается из вида и того, к чему уведомление относится, — так,
 * чтобы повторный расчёт того же события дал ту же строку. Без этого ежедневная
 * проверка сообщала бы об одной просрочке каждый день.
 */
data class NotificationKey(val kind: NotificationKind, val subject: String) {
    val androidId: Int get() = /* стабильный хеш kind + subject */
    companion object {
        fun intake(intakeId: Uuid): NotificationKey
        fun expiry(packageId: Uuid, kind: NotificationKind, threshold: Long): NotificationKey
        fun coverage(courseId: Uuid, revision: Long, kind: NotificationKind): NotificationKey
        fun digest(date: LocalDate): NotificationKey
        fun sync(operationId: Uuid): NotificationKey
    }
}

/** Куда ведёт нажатие. Только идентификаторы: маршрут навигации собирается из этого. */
sealed interface NotificationTarget {
    data class Intake(val intakeId: Uuid) : NotificationTarget
    data class PackageCard(val packageId: Uuid) : NotificationTarget
    data class CourseSources(val courseId: Uuid) : NotificationTarget
    data class DayPlan(val date: LocalDate) : NotificationTarget
    data object SyncStatus : NotificationTarget
}

data class PlannedNotification(
    val key: NotificationKey,
    val fireAt: Instant,
    val target: NotificationTarget,
    val channel: NotificationChannelId,
    val exact: Boolean,                  // true только у INTAKE_DUE
    val actions: List<NotificationAction>
)

enum class NotificationAction { TAKE, SKIP, SNOOZE, OPEN }

data class NotificationSettings(
    val intakeRemindersEnabled: Boolean = true,
    val snoozeMinutes: Int = 15,
    val missedAfterMinutes: Int = 120,      // через сколько PLANNED становится MISSED
    val expiryThresholdDays: Long = 30,
    val coverageThresholdDays: Long = 3,
    val digestEnabled: Boolean = true,
    val digestAt: LocalTime = LocalTime.of(9, 0),
    val remoteChangeEnabled: Boolean = true
)
```

### Каналы

| канал | важность | что шлёт |
|---|---|---|
| `intakes` | HIGH | `INTAKE_DUE`, `INTAKE_MISSED` |
| `expiry` | DEFAULT | `EXPIRY_SOON`, `EXPIRED` |
| `coverage` | DEFAULT | `COVERAGE_SHORT`, `COVERAGE_ENDING` |
| `digest` | LOW | `DAILY_DIGEST` |
| `sync` | LOW | `SYNC_ATTENTION` |

### Как доставляются

**`INTAKE_DUE` — точные, через `AlarmManager`.** Ставится `setExactAndAllowWhileIdle` на каждый
материализованный приём в окне. Разрешение `SCHEDULE_EXACT_ALARM` запрашивается у человека
(`canScheduleExactAlarms()` → `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`); при отказе **деградируем до
неточных сигналов и говорим об этом в настройках**, а не молчим.

`USE_EXACT_ALARM` **не берём**: оно предназначено будильникам и календарям, и заявлять его здесь
значит рисковать правилами публикации.

**Всё остальное — `WorkManager`.** Одна периодическая ежедневная задача: достраивает окно
расписания, помечает неотвеченные как `MISSED`, проверяет сроки годности, пересчитывает обеспечение,
собирает сводку.

### Действия из шторки

`TAKE` выполняет **тот же сценарий, что экран подтверждения** — не отдельную упрощённую ветку.
Если сценарий требует предупреждения (просроченная пачка; расход больше свободного; курс изменился
и приёма больше нет), уведомление не подтверждает молча, а **открывает экран**.

`SNOOZE` переносит сигнал на `snoozeMinutes`, не трогая `plannedAt`: отложено напоминание, а не
приём.

### Отмена и дедупликация

- `notification_log(key, shown_at)` — показанное не показывается второй раз, пока условие не
  изменилось.
- Правка расписания (`revision++`), отмена и завершение курса **гасят уже показанные** уведомления
  по своим приёмам и снимают будильники.
- После перезагрузки (`RECEIVE_BOOT_COMPLETED`) и **смены часового пояса** (`ACTION_TIMEZONE_CHANGED`)
  будильники ставятся заново из расписания, а не восстанавливаются из памяти.

### Пороги

| уведомление | когда |
|---|---|
| `EXPIRY_SOON` | за `expiryThresholdDays` до даты, один раз на пачку и порог |
| `EXPIRED` | в день истечения, один раз |
| `COVERAGE_ENDING` | когда `coveredUntil` ближе `coverageThresholdDays`, и в день исчерпания |
| `COVERAGE_SHORT` | сразу после синхронизации, если чужое действие уменьшило `coveredDoses` |
| `INTAKE_MISSED` | через `missedAfterMinutes` после `plannedAt` без ответа |
| `DAILY_DIGEST` | в `digestAt`, если на день есть приёмы или просрочки |

---
---
---

# ЧАСТЬ E. Синхронизация

## E1. Что означает `packages.quantity`

**Одно правило на оба режима, без ветвления по типу аптечки:**

- `packages.quantity` — **подтверждённый остаток**. Для локальной аптечки подтверждение
  мгновенное, для опубликованной — по ответу сервера.
- Неотправленный расход **не вычитается из него**, а применяется только в проекции:
  `effective = quantity − Σ(consumed по незакрытым операциям)`.
- У локальной аптечки операций не бывает, поэтому `effective == quantity` автоматически.
- **Факт приёма записывается сразу** в обоих режимах. Сдвигается позже только подтверждённый
  остаток, а не запись в истории.

Это правило существует, чтобы количество нельзя было вычесть дважды: один раз в Room и второй раз
в проекции. Вычитает **только** проекция.

**Запоздавший снимок не откатывает подтверждённое.** Строка из снимка применяется, только если её
`version` не меньше сохранённой; ответ на нашу же команду, пришедший позже снимка, сравнивается так
же.

## E2. Очередь: намерение → подготовленный запрос → попытка

```kotlin
sealed interface SyncIntent {
    data class CreateMedKit(val medKitId: Uuid) : SyncIntent
    data class DeleteMedKit(val medKitId: Uuid, val transferTo: Uuid?) : SyncIntent
    data class LeaveMedKit(val medKitId: Uuid) : SyncIntent

    data class CreatePackage(val packageId: Uuid, val medKitId: Uuid,
                             val fields: PackageWireFields) : SyncIntent
    data class PatchPackage(val packageId: Uuid, val edit: PackageWireEdit) : SyncIntent
    data class MovePackage(val packageId: Uuid, val targetMedKitId: Uuid) : SyncIntent
    data class DeletePackage(val packageId: Uuid) : SyncIntent

    data class Consume(val packageId: Uuid, val amount: Quantity,
                       val intakeId: Uuid) : SyncIntent
    data class SetClaim(val packageId: Uuid, val amount: Quantity) : SyncIntent
    data class ReleaseClaim(val packageId: Uuid) : SyncIntent
}

/**
 * Материализуется при ПЕРВОЙ отправке и дальше НЕ МЕНЯЕТСЯ.
 * Здесь живут предусловия, и именно их неизменность защищает от двойного списания.
 */
data class PreparedRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val body: String?,                  // готовый JSON
    val drugVersion: Long?,
    val claimsVersion: Long?,
    val preparedAt: Instant
)

enum class SyncOperationStatus {
    PENDING,      // ждёт отправки; намерение ещё можно менять
    SENDING,
    DONE,         // сервер подтвердил
    SETTLED,      // урегулирована; применялась ли команда — НЕИЗВЕСТНО
    CONFLICT,     // более неприменима, нужно решение человека
    ACCESS_LOST   // пачки или аптечки для нас больше нет
}

data class SyncOperation(
    val id: Uuid,                       // он же syncId у Consume
    val intent: SyncIntent,
    val prepared: PreparedRequest?,     // null, пока ни разу не отправляли
    val groupId: Uuid?,                 // публикация, сохранение курса — одна группа
    val sequence: Long,                 // МОНОТОННЫЙ СЧЁТЧИК, не часы
    val dependsOn: List<Uuid>,
    val status: SyncOperationStatus,
    val attempts: Int,
    val lastError: String?,
    val payloadVersion: Int,            // формат сохранённого намерения
    val createdAt: Instant,
    val lastTriedAt: Instant?
)
```

**Намерение можно править, пока оно не ушло. Отправленный запрос — нельзя.** Версии подставляются
не при создании операции, а при первой отправке: два приёма, сделанных офлайн подряд, знают версию
второго только из ответа на первый.

**`sequence` — счётчик из последовательности базы, а не время.** Часы переводятся назад, и порядок
операций по `createdAt` может перевернуться.

**`groupId` и `dependsOn` держат составные сценарии.** Публикация аптечки — это `CreateMedKit`,
затем `CreatePackage` на каждую пачку (зависят от аптечки), затем `SetClaim` на каждый источник
активных курсов (зависят от своей пачки). Отдельных таблиц публикации не нужно.

**Кто порождает операции.** `SyncOperationFactory` смотрит на состояние аптечки-источника,
аптечки-цели и на то, завершено ли создание этой пачки на сервере. Одного признака «есть serverId»
недостаточно: перенос меняет принадлежность, а публикация целевой аптечки может быть в процессе.

## E3. Почему повтор безопасен

Списание на сервере идёт `UPDATE ... WHERE id = ? AND version = ?`; ноль затронутых строк — отказ.
Версия только растёт.

Значит **повтор с исходной, незаменённой версией не может списать дважды**: если первая попытка
применилась, версия уже другая, и повтор получит отказ по предусловию. Журнал идемпотентности
`sync` при этом приятен, но не нужен — и хорошо, потому что он в памяти процесса и теряется при
перезапуске сервера.

Опасна **не повторная отправка, а подстановка свежей версии** в расход, который уже мог примениться.
Именно она запрещена правилом A3.7, и ровно поэтому `prepared` замораживается.

```
успех        → применилось (сейчас, либо раньше — по журналу sync)        → DONE
409 / 412    → предусловие не совпало: применилось наше ЛИБО писал кто-то ещё.
               Двойного списания не произошло НИ В ОДНОМ из случаев.
               → читаем GET /v1/drugs/{id}, перестраиваем проекцию         → SETTLED
404          → пачки нет: уничтожена нашим же списанием, удалена другим,
               унесена в невидимую аптечку. Различить нечем.
               → архивируем пачку                                          → SETTLED
400          → команда более неприменима (больше остатка, битое поле)      → CONFLICT
428          → наша ошибка: не назвали версию                              → CONFLICT, чинится кодом
401          → перевыпуск токена, одна попытка, затем PENDING
5xx, сеть    → PENDING, повтор по расписанию с отсрочкой
```

### `DONE` и `SETTLED` — разные вещи

`DONE` значит «сервер подтвердил: команда применена». `SETTLED` значит «дальше действовать
безопасно», но **применялась команда или нет — неизвестно, и узнать неоткуда**.

**Собственная бронь как свидетельство не используется.** Рассуждение «бронь изменилась, значит наша
команда прошла» неверно: бронь снимает и сам сервер — например, когда пачку перенесли в аптечку,
которую мы не видим. И 404 ничего не доказывает: он одинаков для «уничтожена нами» и «удалена
другим».

**Что `SETTLED` делает со счётом:**

1. `effective` перестаёт вычитать эту операцию;
2. серверный остаток из свежего чтения становится подтверждённым;
3. запись приёма получает `serverConfirmed = false` — она видна в истории и **отдельной строкой** в
   аналитике, не смешиваясь с подтверждённым расходом.

Физические таблетки остаются настоящим инвариантом: человек пересчитывает пачку и вводит фактическое
число.

**Разрешение пересчётом.** Очередь при этом **не блокируется** — повтор безопасен, блокировать
нечего. Пересчёт — обычная `PatchPackage` со свежей версией; после подтверждения помеченные записи
считаются урегулированными сверкой. Расход и корректировка **не складываются**: корректировка
записывается разницей к уже учтённому.

## E4. Отправка и чтение

**Отправляем сразу, не копим.** Каждое изменение опубликованной аптечки в **той же транзакции**
ставит операцию в очередь, и сразу после коммита запускается отправка. Не вышло — операция ждёт.
Операции по одной упаковке идут строго последовательно: параллельные версии перебивали бы друг
друга.

**`WorkManager` тянет, а не копит.** Периодическая задача читает снимок и повторяет залежавшееся —
она не «отправляет накопленное за день».

**Чтение — всегда полный `GET /v1/users/me`.** Дельты нет, сводный маршрут изменения количества не
ловит.

Применение — одной транзакцией:

1. серверные строки упаковок и `claims` обновляются с проверкой версии;
2. **локальные таблицы, приёмы, движения и очередь не трогаются вовсе** — у синхронизации к ним нет
   DAO;
3. появившиеся пачки заводятся вместе со строкой `package_details` (пустой, с `added_at = now`);
4. исчезнувшие переводятся в `INACCESSIBLE` с движением `ACCESS_LOST`;
5. переехавшие получают `TRANSFER_IN`/`TRANSFER_OUT`;
6. необъяснённая разница пишется как `REMOTE_CHANGE`;
7. после транзакции — пересчёт обеспечения затронутых курсов и уведомления.

**Заменить остаток серверным, потеряв неотправленный расход, нельзя:** расход живёт в очереди, а не
в `quantity`, и снимок его не видит.

## E5. Публикация

Перед первой публикацией экран показывает, **что станет общим**:

> Название пачки, количество, единица, форма, категория, производитель, страна, описание.

и **что не станет**:

> Срок годности, разовая доза, заметка, цена, дата покупки, дата вскрытия. Название аптечки и
> место хранения тоже остаются только у вас.

и предупреждает: **это необратимо, переданный доступ нельзя отозвать**, аптечка исчезнет с сервера
только когда её удалят все участники.

Дальше — обычные операции одной группы. Аптечка `PUBLISHING`, пока группа не завершена целиком;
приглашение до этого не выдаётся. Приём, сделанный во время публикации, ставит свою операцию с
зависимостью от `CreatePackage` и **не теряется**.

## E6. Перенос, выход, удаление

```kotlin
sealed interface MedKitRemoval {
    /** DELETE /v1/med-kit-memberships/{id} — аптечка и остальные остаются */
    data object Leave : MedKitRemoval
    /** DELETE /v1/med-kits/{id}[?targetMedKitId=] — исчезает у всех */
    data class Delete(val transferTo: Uuid?) : MedKitRemoval
}
```

Вариантов ровно два. **«Выйти и унести всё» не существует**: унесённое содержимое означает, что
аптечки не стало — а это удаление с переносом, одна серверная операция. Выдумывать третий эндпойнт
не нужно.

При выходе из общей аптечки **курсы этого человека на её препараты аннулируются**: источники
отвязываются, брони снимаются сервером вместе с участием, курс становится необеспеченным. Сами
курсы и их история остаются.

| перенос упаковки | что происходит |
|---|---|
| локальная → локальная | одна транзакция Room, движения `TRANSFER_OUT` + `TRANSFER_IN` |
| локальная → общая | публикация **этой упаковки** в целевой аптечке (`CreatePackage`) |
| общая → общая | `PUT /v1/med-kits/{target}/drugs/{drug}?version=` |
| общая → локальная | сначала явное согласие на публикацию целевой аптечки, затем серверный перенос |

Перенос может лишить другого участника доступа: сервер сохраняет его бронь, только если он видит
целевую аптечку. **Предупреждаем до подтверждения.**

---
---
---

# ЧАСТЬ F. Хранение

## F1. Таблицы

Состав — следствие ограничений, а не самоцель; «столько-то таблиц» не является инвариантом.

| таблица | колонки | почему так |
|---|---|---|
| `med_kits` | `id` PK, `name`, `location?`, `publication`, `participant_count`, `created_at`, `synced_at` | Снимок трогает **только** `participant_count`, точечным `UPDATE`: название и место хранения серверу неизвестны |
| `packages` | `id` PK, `med_kit_id`, `name`, `quantity`, `quantity_unit_id`, `form_id?`, `category?`, `manufacturer?`, `country?`, `description?`, `version?`, `status`, `synced_at` | **Серверная часть. Только её пишет синхронизация** |
| `package_details` | `package_id` PK/FK, `expires_on?`, `dose_amount?`, `dose_unit_id?`, `note?`, `price?`, `currency?`, `purchased_on?`, `opened_on?`, `added_at`, `template_id?` | Отдельно, потому что снимок переписывает серверную строку целиком — в одной таблице срок годности стирался бы при каждом обновлении. **Строка создаётся всегда**, включая пачки из снимка: тогда `added_at` — момент первого наблюдения, и обязательное поле домена никогда не отсутствует |
| `claims` | `package_id` PK/FK, `total`, `mine?`, `version` | Отдельно: версия своя, и двигают её чужие действия |
| `quantity_units` | `id` PK, `name` | Словарь с **серверными** идентификаторами |
| `form_types` | `id` PK, `name` | То же |
| `drug_templates` | `id` PK, `name`, `name_lat?`, `active_substance?`, `form_id?`, `category?`, `quantity_unit_id?`, `manufacturer?`, `country?`, `description?`, `cached_at` | Кэш карточек справочника: повторный поиск работает без сети |
| `courses` | `id` PK, `title`, `note?`, `dose_amount?`, `unit_id?`, `form_id?`, `start?`, `end_inclusive?`, `days_mask?`, `zone?`, `status`, `revision`, `created_at`, `updated_at` | Расписание встроено колонками: отдельной жизни у него нет, отдельная таблица только добавила бы join |
| `course_times` | `course_id` FK, `minutes_of_day`; UNIQUE(пара) | Времена — список; уникальность пары не даёт завести одно время дважды |
| `course_sources` | `course_id` FK, `package_id` FK, `position`, `allocated_doses`; PK(`course_id`,`package_id`), UNIQUE(`course_id`,`position`) | Порядок = приоритет. Уникальность позиции ловит сбой перетаскивания |
| `active_package_assignments` | **`package_id` PRIMARY KEY**, `course_id` FK | Это и есть механизм «одна пачка — один незавершённый курс». Проверка «а нет ли уже» перед вставкой не годится: два экрана записали бы одновременно и оба увидели бы пусто. Строка появляется при `activate()`, исчезает при завершении, отмене и отвязке |
| `intakes` | поля `Intake`; FK на `packages` **RESTRICT**, FK на `courses` **RESTRICT** | История не удаляется каскадом |
| `stock_adjustments` | поля `StockAdjustment`; FK на `packages` **RESTRICT** | То же |
| `sync_operations` | поля `SyncOperation`; `sequence` UNIQUE, монотонен | Очередь |
| `sync_operation_dependencies` | `operation_id` FK, `depends_on_id` FK; PK(пара) | Зависимости отдельной таблицей, а не размазанными по payload |
| `notification_log` | `key` PK, `kind`, `shown_at` | Без него ежедневная проверка сообщала бы об одной просрочке каждый день |

## F2. Ограничения в схеме, а не в коде

- `packages.quantity >= 0`; `course_sources.allocated_doses >= 0`; `courses.dose_amount > 0`.
- Единица источника совпадает с единицей курса (проверяется при вставке, подкреплено тестом).
- `active_package_assignments.package_id` — первичный ключ (одна пачка — один курс).
- `UNIQUE(course_id, position)` в `course_sources`.
- Подтверждение одного пункта расписания неповторимо: `intakes.id` — первичный ключ, и подтверждение
  идёт `UPDATE ... WHERE id = ? AND status = 'PLANNED'`, а не вставкой.
- `sync_operations.sequence` — `UNIQUE`.

## F3. Как что хранится

| тип | в базе |
|---|---|
| `Uuid` | каноническая строка |
| `Quantity`, `Money` | **десятичная строка** — одно правило на обе величины, без «минимальных единиц» |
| `Instant` | epoch millis, `INTEGER` |
| `LocalDate` | ISO-8601, `TEXT` |
| `LocalTime` | минуты от начала суток, `INTEGER` |
| `ZoneId` | строка идентификатора зоны |
| `Set<DayOfWeek>` | битовая маска, `INTEGER` |
| перечисления | устойчивые строковые коды, не `ordinal` |

**`CAST(... AS REAL)` в запросах не применяется.** Сортировка по количеству идёт по паре
`(единица, десятичная строка, дополненная слева нулями)` либо в домене; складывать величины в
разных единицах нельзя вовсе.

## F4. Материализация расписания и миграции

**Расписание материализуется окном.** Строятся приёмы на ближайшие **60 дней**; ежедневная задача
достраивает следующие. Годовой курс с четырьмя приёмами в день — почти полторы тысячи строк, и
создавать их разом при сохранении незачем. Прогноз дальше окна считается формулой, а не строками.

**Миграции Room тестируются.** Экспорт схемы (`room.schemaLocation`) — необходимое, но не
достаточное: тест открывает базу предыдущей версии, применяет миграцию и проверяет данные.

**`payloadVersion` незавершённых операций версионируется** отдельно: обновление приложения не должно
ронять очередь. Неизвестная версия payload переводит операцию в `CONFLICT` с человеческим текстом,
а не роняет процесс.

---
---
---

# ЧАСТЬ G. Безопасность

## G1. Регистрационный токен

Токен **неизбежно присутствует в APK**: приложение само отправляет его при регистрации. Обфускация
этого не меняет — её разбирают.

Критерий приёмки поэтому формулируется достижимо:

> Регистрационный токен не хранится в репозитории и не пишется в логи; он приходит в сборку из
> `local.properties` / переменных окружения CI. Его доступность внутри распространяемого клиента
> принята как свойство выбранного механизма регистрации.

## G2. Персональный ключ

- Ключ AES-256-GCM порождается **в AndroidKeyStore** и не покидает хранилище:
  `setBlockModes(GCM)`, `setEncryptionPaddings(NONE)`, `setRandomizedEncryptionRequired(true)`,
  **StrongBox при наличии** — с перехватом `StrongBoxUnavailableException` и повтором без него.
- Наружу идёт только шифротекст вместе с вектором инициализации; он лежит в DataStore.
- **Логирование HTTP в debug маскирует и заголовок авторизации, и тело ответа регистрации** — ключ
  лежит именно там, и маскировать только заголовок недостаточно.
- JWT живёт **только в памяти процесса** и не сохраняется.
- `android:allowBackup="false"`; файл DataStore исключён правилами `dataExtractionRules` —
  иначе секрет уедет и в облачную копию, и в перенос на новое устройство.
- **Невозможность расшифровать** сохранённое (сброс биометрии, повреждение хранилища) — это утрата
  учётной записи. Приложение показывает состояние и **спрашивает**, а не регистрирует молча новую
  учётку поверх локальных данных.
- **Утрата ключа необратима**: `key` выдаётся один раз. Старые серверные брони снять нечем — они
  останутся у других участников, пока те не удалят аптечку. Локальные курсы, история и локальные
  аптечки при этом целы; общие придётся подключить заново по приглашению.

## G3. Прочее

- Ключ приглашения не пишется в логи и не попадает в маршруты навигации.
- Клиент «Честного знака» ходит **без заголовка авторизации** MedApp: отдельный `HttpClient`, чтобы
  токен не уехал на посторонний хост случайной настройкой.
- Экраны с ключом (`QR на весь экран`) ставят `FLAG_SECURE`.

---
---
---

# ЧАСТЬ H. Приложение

## H1. Слои и пакеты

```
ui  →  domain  ←  data (local | remote | sync)
```

Интерфейсы репозиториев — в `domain`, реализации — в `data`; **домен не импортирует `data`**.

```
com.kert0n.medapp
├─ app/        MedApp(@HiltAndroidApp), MainActivity, navigation/
├─ core/       result/, time/(Clock), text/, format/
├─ di/         NetworkModule, DatabaseModule, RepositoryModule, WorkModule, DispatcherModule
├─ domain/
│   ├─ model/      Quantity, MedKit, Package, Course, Intake, StockAdjustment, ...
│   ├─ calc/       ScheduleCalculator, AllocationLimits, CoverageCalculator, ForecastCalculator
│   ├─ repository/ интерфейсы
│   └─ usecase/    сценарии
├─ data/
│   ├─ remote/     dto/, medapp/(MedAppApi), crpt/(CrptApi)
│   ├─ local/      entity/, dao/, MedAppDatabase, converters/
│   ├─ mapper/     dto↔domain, entity↔domain
│   ├─ repository/ реализации
│   └─ sync/       SyncOperationFactory, OperationSender, SnapshotApplier, SyncWorker
├─ platform/   notifications/, scanner/, credentials/, connectivity/, clipboard/
└─ feature/    bootstrap/, medkits/, packages/, courses/, schedule/, intake/,
               sharing/, analytics/, scanner/, settings/, syncstatus/
```

**Чистые вычислители — отдельными интерфейсами**, потому что именно их и надо проверять тестом без
Android: `ScheduleCalculator`, `AllocationLimits`, `CoverageCalculator`, `ForecastCalculator`.

## H2. Сборка

`minSdk` **26** — поддерживаемый минимум Android 8.0; `compileSdk`/`targetSdk` 36; `jvmTarget` задан
явно.

| нужно | зачем |
|---|---|
| KSP | Room, Hilt |
| Hilt | внедрение; стабильнее Koin и проверяется на компиляции |
| Room | локальное хранилище, `Flow`, миграции |
| Ktor client (OkHttp engine) + kotlinx.serialization | лучше ложится на Kotlin; сервер уже на kotlinx |
| Navigation Compose | типобезопасные `@Serializable`-маршруты |
| WorkManager | периодическое чтение и ежедневная задача |
| DataStore | настройки и шифротекст ключа |
| CameraX + **ML Kit barcode** | ZXing в maintenance mode; ML Kit сам отдаёт `barcode.format` |
| ZXing **core** | **только генерация QR** для приглашения |
| Compose Material 3 | интерфейс |

**Coil не нужен** — изображений в продукте нет. **`kotlinx-datetime` не нужен** — время `java.time`.

Разрешения: `INTERNET`, `CAMERA`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`,
**`SCHEDULE_EXACT_ALARM`** (с запросом и деградацией). `allowBackup="false"`.

**Два `HttpClient`:**

| клиент | настройки |
|---|---|
| `medapp` | `Json { ignoreUnknownKeys = false; encodeDefaults = false; explicitNulls = false }`, плагин авторизации с одним перевыпуском токена, таймауты, **автоповторы только у GET** |
| `crpt` | нестрогий разбор (`ignoreUnknownKeys = true`), **без заголовка авторизации**, свой таймаут |

`encodeDefaults = false` **обязателен**: сервер различает «поля нет» (не трогать) и «поле `null`».
`ignoreUnknownKeys = false` у `medapp` — чтобы расхождение с контрактом падало в тесте, а не тихо
терялось. Ошибка разбора превращается в ошибку протокола, а не летит исключением наверх.

## H3. Экраны

Одна `MainActivity`. Фоном: `SyncWorker`, `DailyWorker`, `IntakeAlarmReceiver`,
`NotificationActionReceiver`, `BootAndTimeZoneReceiver`.

Нижняя навигация: **Аптечки · План · Сканер · Аналитика · Настройки**. «План» переключается между
расписанием на дату и списком курсов. В маршруты едут **только** идентификаторы, дата и режим — ни
объектов, ни ключей приглашения.

| # | экран | что делает |
|---|---|---|
| 1 | Первичная настройка | регистрация, загрузка словарей, повтор при отказе сети |
| 2 | **Аптечки (список)** | локальные и общие, значок общей, количество пачек, просрочки |
| 3 | **Аптечка: создание и правка** | название, место хранения |
| 4 | **Аптечка: содержимое** | список пачек, поиск, фильтр, сортировка |
| 5 | Все лекарства | то же по всем доступным аптечкам |
| 6 | **Карточка упаковки** | всё известное; свободно/занято; курс; действия |
| 7 | **Добавление упаковки** | обязательны 4 поля; подсказки справочника; переход в сканер |
| 8 | **Правка упаковки** | те же поля; количество меняется отдельным экраном |
| 9 | Пересчёт остатка | «пересчитал и увидел столько»; ноль → архивирование |
| 10 | Внеплановый приём | доза как placeholder; предупреждения |
| 11 | Перенос упаковки | выбор аптечки; предупреждение о потере доступа |
| 12 | **План на дату** | приёмы дня, состояние каждого, быстрые действия |
| 13 | Список курсов | активные, черновики, завершённые; значок нехватки |
| 14 | Карточка курса | расписание, обеспечение, источники, история |
| 15 | Редактор курса | название, заметка, доза, даты, дни недели, времена |
| 16 | **Источники курса** | перетаскиваемый стек, ползунки в приёмах, сводка обеспечения |
| 17 | Выбор источника | какие пачки можно подключить и почему нельзя остальные |
| 18 | Подтверждение приёма | пачка, количество, предупреждения |
| 19 | История приёмов | по курсу и по пачке |
| 20 | **Поделиться аптечкой** | последствия → QR и текстовый код |
| 21 | QR на весь экран | `FLAG_SECURE`, оценка срока, «обновить код» |
| 22 | **Присоединиться** | скан QR или ввод кода |
| 23 | Удаление и выход | выйти / удалить у всех / удалить с переносом |
| 24 | **Сканер** | CameraX, режимы EAN-13 и DataMatrix |
| 25 | Результат сканирования | что нашли, что подставим, чего не хватает |
| 26 | **Аналитика** | сводка, прогноз, движение |
| 27 | **Настройки** | пороги и время уведомлений, разрешения, учётная запись |
| 28 | Состояние синхронизации | очередь, что ждёт, что требует решения |

Экраны 2, 3, 4, 6, 7/8, 12, 24, 20+22, 27, 26 покрывают обязательный список целиком.

**Подтверждения опасных действий:** удаление аптечки, выход из общей, архивирование упаковки,
пересчёт до нуля, генерация приглашения, приём просроченного, приём сверх свободного остатка,
отвязка источника у активного курса, отмена курса.

### Экран источников курса

```
Курс «Нурофен, 7 дней»            доза 2 капс.  ·  4 раза в день  ·  пн–вс

≡  💊  Нурофен, 20 капс.
      Домашняя · годен до 03.2027 · свободно 15
      [−] ──────●──────── [+]   0 … 7 приёмов        [ 5 ]   = 10 капс.

≡  💊  Нурофен, 12 капс.
      Дача · срок не указан · свободно 12
      [−] ────●────────── [+]   0 … 6 приёмов        [ 4 ]   = 8 капс.

      нужно 28 приёмов · обеспечено 9 · не хватает 19, с 11 сентября
                                                      [ Подключить ещё ]
```

Порядок в списке — порядок расходования; перетаскивание меняет приоритет. Ползунок и точное поле —
**оба в приёмах**; рядом справочно показывается, сколько это в единицах пачки.

`Float` допустим **только** как координата ползунка: значение — целое число доз, и оно проходит ту
же проверку, что ручной ввод. **Во время движения ползунка сеть не вызывается** — брони уезжают при
сохранении.

### Дизайн

Material 3, зелёная палитра: `primary #1B6B4A`, `primaryContainer #A8F0C6`, `tertiary #3B6470`,
`surface #F6FBF3`, `error #BA1A1A`. Тёмная схема генерируется. **`dynamicColor` выключен** — иначе
на Android 12+ фирменной палитры не видно вовсе.

Красный — просрочка, янтарный — нехватка и бронь. **Цвет всегда дублируется текстом и значком**:
цветом одним нельзя.

**Просроченные пачки идут первыми независимо от выбранной сортировки** и заметно выделены.

Тёмная тема, TalkBack, крупный шрифт, поворот, `WindowInsets`, зоны нажатия от 48 dp. Приложение
не падает ни на каком вводе: `parse` возвращает `Result`, а не бросает.

## H4. Поиск, фильтр, сортировка

```kotlin
data class PackageQuery(
    val medKitId: Uuid?,          // null — по всем доступным аптечкам
    val text: String,
    val filter: PackageFilter?,   // ПО ОДНОМУ параметру
    val sort: PackageSort
)

sealed interface PackageFilter {
    data object Expired : PackageFilter
    data class ExpiringWithin(val days: Long) : PackageFilter
    data object OnCourse : PackageFilter
    data object HasFree : PackageFilter
    data class OfCategory(val category: String) : PackageFilter
    data class OfForm(val formId: Uuid) : PackageFilter
}

enum class PackageSort { NAME, EXPIRY, ADDED_AT, QUANTITY }
```

**Порядок нажатий результат не меняет.** Конвейер один: `аптечки → поиск → фильтр → просроченные
вперёд → сортировка`. Состояние экрана хранит три независимых поля, а не историю действий.

## H5. Справочник и сканер

**Справочник.** Дебаунс 300 мс, отмена предыдущего запроса, `limit=10`, результат кладётся в
`drug_templates`. Заполняет название, форму, категорию, производителя, страну, описание и единицу.
**Количества и срока годности там нет** — не выдумываем.

Устаревший ответ не перетирает свежий: сравнивается запрос, а не только время прихода. Офлайн —
отдаём кэш и **говорим, что это кэш**.

**«Честный знак»:**

| формат | запрос | тело |
|---|---|---|
| EAN-13 | `POST https://mobile.api.crpt.ru/mobile/check` | `{"code": "<13 цифр>", "codeType": "ean13"}` |
| DataMatrix | `POST https://mobile.api.crpt.ru/v2/mobile/check` | `{"code": "{FNC1}<код>", "codeType": "datamatrix"}` |

`{FNC1}` — **литеральная строка из шести символов** `{`, `F`, `N`, `C`, `1`, `}`, а не управляющий
байт.

Разделители переменных полей DataMatrix **не вычищаются огулом**: нормализация описывается точными
парами вход→выход и покрывается тестами на фикстурах, иначе можно срезать значащий символ.

**Формат определяет распознаватель**, а не длина строки: ML Kit отдаёт `barcode.format`.

**Уровни данных различаются, и это важно:**

- **EAN-13 — это GTIN товарной позиции.** Он одинаков у всех пачек партии; срок годности из ответа
  на него относится к товару, а не к этой коробке.
- Конкретной физической пачке соответствует только **сериализованный DataMatrix**.

Поэтому срок годности подставляется **как предложение с указанием источника**, а не как факт.
`expireDate` приходит в epoch millis и переводится в календарную дату **явно заданной зоной** —
иначе сдвинется на сутки.

Автозаполнение — предложение, не решение: непонятная единица, составное количество («20 капсул в
2 блистерах») и неоднозначная форма остаются незаполненными; **дозировка вещества не превращается в
разовую дозу**; повтор кода в потоке камеры не порождает нескольких запросов; `200` с признаком «не
найдено» — нормальный результат, а не ошибка. Отказ в камере ведёт к ручному вводу, а не в тупик.

## H6. Аналитика

**Сводка на сейчас:** действующих упаковок и аптечек, просроченных, активных курсов, курсов с
нехваткой, распределение по форме и категории, **сумма указанной цены**.

Цена — цена **всей пачки**; пропорционально остатку не амортизируется (мы не знаем, сколько стоила
одна таблетка). Пачки без цены считаются **отдельным числом** («у 12 пачек цена не указана»), а не
бесплатными.

**Прогноз на три месяца:**

```
ожидаемый остаток(дата) = effective − Σ(обеспеченные собственные будущие приёмы до этой даты)
```

Считается по формуле, а не по материализованным строкам. **Чужие брони показываются отдельно** и по
дням не раскладываются: когда сосед будет принимать, нам неизвестно.

**Движение за период до года:**

```
начало + поступления + корректировки − подтверждённый расход − утилизация + переносы = конец
```

Перенос **внутри выбранного набора аптечек расходом не считается** — иначе перекладывание пачки
выглядело бы как потребление.

**Начало доступной истории — момент вступления в аптечку.** События до него нам неизвестны, и отчёт
это прямо указывает, а не показывает ноль.

**Расход, не подтверждённый сервером** (`serverConfirmed = false`), идёт **отдельной строкой**.
Разные единицы не суммируются никогда.

---
---
---

# ЧАСТЬ I. План работ

Девятнадцать PR: **PR 0 – PR 18**. Каждый собирается, проходит тесты и **не смешивает продукт с
обновлением библиотек**. Сообщения коммитов — через файл (`git commit -F`), в стиле репозитория:
утверждение о достигнутом свойстве, а не «добавил X».

---

## PR 0 — документы

**Зависит от:** ничего.

| # | коммит | содержание |
|---|---|---|
| 1 | `Агент читает одну короткую страницу, а не весь план` | `AGENTS.md` в корне: части A1–A5 |
| 2 | `План описывает продукт целиком, до сигнатур` | `PLAN.md`: части B–J |
| 3 | `Каждое требование названо местом, где оно живёт` | матрица «требование → раздел → PR» в конце `PLAN.md` |
| 4 | `Вид приложения описан там же, где всё остальное` | палитра и состав экранов живут в H3; черновой макет из корня убран, чтобы не быть вторым источником правды |
| 5 | `README ведёт в план, а не пересказывает его` | ссылки |

**Приёмка:** агент, читающий только `AGENTS.md`, знает границу данных и семнадцать правил.

---

## PR 1 — фундамент сборки

**Зависит от:** PR 0.

| # | коммит | содержание |
|---|---|---|
| 1 | `Сборка нацелена на Android 8 и собирается текущим AGP` | `minSdk 26`, `compileSdk/targetSdk 36`, явный `jvmTarget`, `buildFeatures.buildConfig` |
| 2 | `Каталог версий называет всё, чем мы пользуемся` | `libs.versions.toml`: KSP, Hilt, Room, Ktor, Navigation, WorkManager, DataStore, CameraX, ML Kit, ZXing core, Compose BOM |
| 3 | `Внедрение зависимостей работает от Application до экрана` | `@HiltAndroidApp`, `@AndroidEntryPoint`, `DispatcherModule` |
| 4 | `Манифест объявляет ровно то, чем приложение пользуется` | разрешения, `allowBackup=false`, `dataExtractionRules` |
| 5 | `Секреты приходят из окружения, а не из репозитория` | `BuildConfig` из `local.properties`/CI, `.gitignore` |
| 6 | `Проверка идёт на каждый пуш` | CI: `testDebugUnitTest`, `lintDebug`, `assembleDebug` |

**Тесты:** приложение запускается на API 26 и 36; `BuildConfig` не пуст; лог не содержит
регистрационного токена.
**Ограничение приёмки:** доступность токена внутри APK принята (G1).

---

## PR 2 — домен: значения, аптечка, упаковка

**Зависит от:** PR 1.

| # | коммит | содержание |
|---|---|---|
| 1 | `Количество не теряет разрядов и не смешивает единицы` | `Quantity`, `QUANTITY_*`, `parse`, `toWire`, `equals`/`hashCode` |
| 2 | `Нехватка при вычитании — ошибка, а не ноль` | `minus` бросает, `minusOrZero` зажимает; `covers`, `dosesIn`, `times` |
| 3 | `Цена живёт по тому же правилу, что количество` | `Money` |
| 4 | `Словари — часть домена, а не строки из ответа` | `QuantityUnit`, `DosageForm` |
| 5 | `Аптечка знает, опубликована ли она и можно ли звать в неё` | `MedKit`, `KitPublication` |
| 6 | `Упаковка отвечает на вопрос о годности по названной дате` | `Package`, `isExpiredOn`, `expiresWithin` |
| 7 | `Кончившаяся упаковка архивируется, а не исчезает` | `consume`, `correctTo`, `archive`, `loseAccess`, `moveTo`, `describe` |
| 8 | `Claims отделены от брони на проводе` | `Claims` |

**Тесты:** `0.1 + 0.2 == 0.3`; семь знаков после точки отвергнуты; запятая принята; экспонента,
знак, четырнадцать разрядов и слишком длинный ввод отвергнуты; `1 == 1.000000` и хеши равны;
сложение разных единиц бросает; `minus` при нехватке бросает, `minusOrZero` даёт ноль; `dosesIn` при
нулевой дозе — ошибка; `consume` до нуля даёт `ARCHIVED`; `isExpiredOn` на границе суток;
`correctTo(0)` архивирует.

---

## PR 3 — домен: курс, расписание, обеспечение

**Зависит от:** PR 2.

| # | коммит | содержание |
|---|---|---|
| 1 | `Курс начинается заметкой, а не расписанием` | `Course`, `CourseStatus`, `DRAFT`, `rename` |
| 2 | `Расписание знает свой часовой пояс` | `CourseSchedule`, инварианты |
| 3 | `Переход на летнее время разрешается одним названным правилом` | `ScheduleCalculator`: несуществующее время вперёд, повторяющееся — первое вхождение |
| 4 | `Расписание строится окном, а не на год вперёд` | окно 60 дней, достройка |
| 5 | `Источник курса — значение со своим местом в стеке` | `CourseSource`, `attach`/`detach`/`reorder` |
| 6 | `Форму и единицу курса задаёт первый источник` | фиксация; `formId = null` несовместим ни с чем |
| 7 | `Выделение измеряется приёмами, и тупика больше нет` | `allocate` в дозах, `AllocationLimits.maxDoses` |
| 8 | `Обеспечение вычисляется и называет первый непокрытый приём` | `CoverageCalculator`, `CourseCoverage` |
| 9 | `Пропуск освобождает выделение, а не оставляет бронь` | пересчёт при `SKIPPED`, `MISSED`, частичной дозе |
| 10 | `Прогноз считается формулой, а не строками` | `ForecastCalculator` |

**Тесты:** окно расписания через месяц и год; **несуществующее и повторяющееся время** в дни
перехода — по названному правилу; пустая маска дней отвергнута; потребность 28 при выделении 5 и 4
даёт 9 покрытых и называет первый непокрытый; **доза 2 при доступных 1 и 1 даёт 0 доз — тупика
нет**; ползунок второго источника зажат, когда первый закрыл потребность; несовместимая форма и
единица отвергнуты; **`formId = null` не подключается**; **отвязка последнего источника у `ACTIVE`
форму не сбрасывает, у `DRAFT` сбрасывает**; пропуск освобождает дозу с конца стека; фактическая
доза меньше плановой пересчитывает потребность; `activate` без расписания отвергается.

---

## PR 4 — Room

**Зависит от:** PR 2, PR 3.

| # | коммит | содержание |
|---|---|---|
| 1 | `Локальные сведения об упаковке переживают снимок сервера` | `packages` и `package_details` порознь; строка деталей создаётся всегда |
| 2 | `Брони хранятся со своей версией` | `claims` |
| 3 | `Курс и его источники хранят порядок` | `courses`, `course_times`, `course_sources` |
| 4 | `Одна упаковка не может попасть в два незавершённых курса` | `active_package_assignments`, `package_id` PK |
| 5 | `История не удаляется вместе с упаковкой` | `intakes`, `stock_adjustments`, FK `RESTRICT` |
| 6 | `Очередь и её зависимости лежат в базе` | `sync_operations`, `sync_operation_dependencies` |
| 7 | `Показанное уведомление не показывается второй раз` | `notification_log` |
| 8 | `Величины хранятся точно, а не приблизительно` | конвертеры; без `REAL` |
| 9 | `Один запрос отвечает на поиск, фильтр и сортировку сразу` | `PackageDao.query`, просроченные вперёд |
| 10 | `Схема переживает обновление приложения` | экспорт схемы, `MigrationTestHelper`, первый тест миграции |

**Тесты:** данные переживают завершение процесса; архивирование не удаляет приёмы, движения и
операции; `RESTRICT` не даёт удалить упаковку с историей; **`active_package_assignments` отвергает
второе назначение при одновременной записи из двух корутин**; `query` во всех комбинациях, и
просроченные первыми при любой сортировке; **повторный upsert серверной части не стирает
`package_details`**; миграция N→N+1 сохраняет данные.

---

## PR 5 — сеть, авторизация, проба контракта

**Зависит от:** PR 2, PR 4.

| # | коммит | содержание |
|---|---|---|
| 1 | `DTO повторяют контракт, включая клиентский идентификатор` | все `*Dto`, `BigDecimalAsString` |
| 2 | `Клиент MedApp строг, клиент «Честного знака» — нет` | два `HttpClient`, `encodeDefaults = false` |
| 3 | `Ключ лежит в защищённом хранилище, а не в настройках` | AndroidKeyStore, AES-GCM, StrongBox с откатом |
| 4 | `Логи не содержат ни заголовка авторизации, ни тела регистрации` | маскирование |
| 5 | `Истёкший токен перевыпускается один раз, а не по кругу` | плагин авторизации |
| 6 | `У каждой операции объявлено, что считается успехом` | `MedAppApi`, ожидания статусов и тела |
| 7 | `Ошибка сервера превращается в решение, а не в исключение` | `problem+json`, `errors[]`, `ApiFailure` |
| 8 | `Словари доступны сразу после регистрации` | встроенный снимок с записанным происхождением и версией + обновление |
| 9 | `Контракт проверяется живыми запросами` | `ContractProbe` |

**`ContractProbe`:** изолированная тестовая база, синтетические данные, **два пользователя**, очистка
после прогона, **никаких секретов в отчёте**. Проверяет не только успехи, но и конфликты, пустые
тела, 404 на недоступное, 409 на повтор создания, 428 и 412.

**Тесты (MockEngine):** успех каждой из 26 операций; 400 с `errors[]`; 401 с ровно одним
перевыпуском; **409 на повторное создание с тем же `id`**; 429 с `Retry-After`; **успех с пустым
телом у `recordIntake` и `synchronise`**; пустое тело у остальных — ошибка; битый JSON не роняет
процесс; **5xx на изменяющей команде не повторяется HTTP-слоем, на GET повторяется**; маскирование
тела ответа регистрации.

---

## PR 6 — оболочка

**Зависит от:** PR 1, PR 5.

| # | коммит | содержание |
|---|---|---|
| 1 | `Приложение выглядит своим на любом Android` | тема, палитра, `dynamicColor` выключен |
| 2 | `Пустое, загрузка и ошибка выглядят одинаково везде` | общие состояния, `ErrorMessage` |
| 3 | `В маршрутах едут идентификаторы, а не объекты` | `@Serializable`-маршруты, нижняя навигация |
| 4 | `Незарегистрированное приложение честно просит сеть` | экран первичной настройки, повтор |

**Тесты:** back stack и поворот; крупный шрифт не ломает разметку; в маршрутах нет объектов и
ключей; до регистрации виден экран настройки, а не пустой список.

---

## PR 7 — локальный учёт

**Зависит от:** PR 4, PR 6.

| # | коммит | содержание |
|---|---|---|
| 1 | `Аптечка заводится и переименовывается без сети` | экраны 2 и 3 |
| 2 | `Упаковку можно завести, зная только четыре поля` | экран 7, валидация |
| 3 | `Карточка показывает, сколько свободно и чем занято` | экран 6, `PackageView` |
| 4 | `Правка не трогает количество` | экран 8 |
| 5 | `Пересчёт и утилизация оставляют след в истории` | экран 9, `StockAdjustment` |
| 6 | `Перенос между локальными аптечками — одна транзакция` | экран 11 |
| 7 | `Поиск, фильтр и сортировка не зависят от порядка нажатий` | экраны 4 и 5 |
| 8 | `Просроченные видно сразу и они не исчезают сами` | выделение, порядок |

**Тесты:** сценарий целиком без сети; две одинаково названные пачки остаются двумя; пересчёт до нуля
архивирует; **локальный приём уменьшает показанный остаток ровно один раз**; просроченная дата
вводится и обрабатывается; удаление аптечки с переносом сохраняет пачки.

---

## PR 8 — справочник

**Зависит от:** PR 5, PR 7.

| # | коммит | содержание |
|---|---|---|
| 1 | `Поиск не бомбит сервер на каждую букву` | дебаунс 300 мс, отмена |
| 2 | `Найденное остаётся доступным без сети` | `drug_templates` |
| 3 | `Карточка справочника заполняет только то, что знает` | перенос в черновик упаковки |

**Тесты:** устаревший ответ не перетирает свежий; офлайн отдаёт кэш **и говорит об этом**; срок
годности и количество не подставляются.

---

## PR 9 — курсы и источники

**Зависит от:** PR 3, PR 7.

| # | коммит | содержание |
|---|---|---|
| 1 | `Курс сохраняется, когда есть только заметка` | экраны 13, 15, `DRAFT` |
| 2 | `Расписание задаётся днями недели и временами` | редактор, валидация |
| 3 | `Источники образуют стек, который можно переставить` | экран 16, перетаскивание |
| 4 | `Ползунок измеряет приёмы и не может залезть в чужое` | пределы, целые дозы |
| 5 | `Экран говорит, чем обеспечен курс и с какого дня — нет` | сводка обеспечения |
| 6 | `Активация занимает упаковки и создаёт брони` | `activate`, `active_package_assignments` |
| 7 | `Понятно, почему пачку нельзя подключить` | экран 17 с причинами |
| 8 | `Правка расписания не трогает состоявшихся приёмов` | `revision`, пересоздание будущих |

**Тесты:** черновик с одной заметкой сохраняется и **упаковку не занимает**; активация создаёт
назначение; **два экрана одновременно назначают одну пачку разным курсам — проходит ровно один**;
правка расписания не трогает `TAKEN`; **активный курс существует без единого доступного источника**;
подключение пачки без формы отвергнуто с внятным текстом.

---

## PR 10 — приёмы и история

**Зависит от:** PR 9.

| # | коммит | содержание |
|---|---|---|
| 1 | `Разовый приём подставляет дозу, но не навязывает её` | экран 10, placeholder |
| 2 | `План на дату показывает, что уже принято` | экран 12 |
| 3 | `Подтверждение списывает ровно один раз` | `UPDATE ... WHERE status = 'PLANNED'` |
| 4 | `Приём можно взять из другого источника курса` | экран 18 |
| 5 | `Пропуск и неответ не создают расхода` | `SKIPPED`, `MISSED` |
| 6 | `Приём просроченного требует подтверждения` | предупреждения |
| 7 | `История переживает переименование и смену единицы` | экран 19, `unitId` на момент события |

**Тесты:** двойное нажатие не удваивает списание; **приём по курсу уменьшает своё выделение,
сохраняя правильное «свободно»**; приём до нуля архивирует; пропуск и `MISSED` не создают
движений; подтверждение из другого источника курса законно; из пачки вне источников — приём
внеплановый; история читается после архивирования пачки.

---

## PR 11 — уведомления

**Зависит от:** PR 10.

| # | коммит | содержание |
|---|---|---|
| 1 | `Уведомление знает, чем оно вызвано и куда ведёт` | `NotificationKey`, `NotificationTarget`, `PlannedNotification` |
| 2 | `Пять каналов, и каждый можно выключить отдельно` | каналы, `NotificationSettings` |
| 3 | `Напоминание о приёме приходит вовремя или честно говорит, что нет` | `AlarmManager`, запрос `SCHEDULE_EXACT_ALARM`, деградация |
| 4 | `«Принял» из шторки проходит тот же путь, что экран` | `NotificationActionReceiver`, `TAKE`/`SKIP`/`SNOOZE` |
| 5 | `О сроке годности сообщают один раз, а не каждый день` | `notification_log`, пороги |
| 6 | `О сокращении обеспечения сообщают, называя дату` | `COVERAGE_SHORT`, `COVERAGE_ENDING` |
| 7 | `Сводка на день собирается в назначенное время` | `DAILY_DIGEST`, `DailyWorker` |
| 8 | `После перезагрузки и перелёта расписание восстанавливается` | boot и `TIMEZONE_CHANGED` |
| 9 | `Отменённый курс не напоминает о себе` | гашение показанных |

**Тесты:** пороговые даты на фиксированном `Clock`; **курс изменён между показом уведомления и
нажатием «Принял» — открывается экран, а не тихое подтверждение**; повторный запуск задачи не
дублирует; отмена курса гасит показанные и снимает будильники; **отказ в `POST_NOTIFICATIONS` и в
точных будильниках не ломает расписание**; `SNOOZE` не двигает `plannedAt`.

---

## PR 12 — чтение общих аптечек

**Зависит от:** PR 5, PR 7.

| # | коммит | содержание |
|---|---|---|
| 1 | `Снимок применяется целиком и одной транзакцией` | `SnapshotApplier` |
| 2 | `Снимок не имеет доступа к локальным таблицам` | разделение DAO |
| 3 | `Запоздавший снимок не откатывает подтверждённое` | сверка версий |
| 4 | `Появление, исчезновение и переезд пачки видны как события` | `TRANSFER_*`, `ACCESS_LOST` |
| 5 | `Необъяснённая разница называется необъяснённой` | `REMOTE_CHANGE` как остаток |
| 6 | `Чужая бронь показана отдельно от своей` | `Claims` в проекции |

**Тесты:** **другой участник изменил только количество — расхождение обнаружено, хотя состав аптечки
и `userCount` не менялись**; снимок не стирает срок годности и заметку; **запоздавший снимок не
откатывает подтверждённое**; **своё списание не попадает в отчёт дважды**; повторное применение того
же снимка ничего не меняет; пачка, ушедшая в невидимую аптечку, становится `INACCESSIBLE`.

---

## PR 13 — очередь и установление исхода

**Зависит от:** PR 12.

| # | коммит | содержание |
|---|---|---|
| 1 | `Изменение общей аптечки ставит операцию в той же транзакции` | `SyncOperationFactory` |
| 2 | `Запрос замораживается при первой отправке и больше не меняется` | `PreparedRequest` |
| 3 | `Операции одной упаковки идут по очереди, а не наперегонки` | `sequence`, сериализация по `packageId` |
| 4 | `Составной сценарий описан зависимостями, а не порядком в коде` | `groupId`, `dependsOn` |
| 5 | `Отправляем сразу после изменения, а не копим до ночи` | `OperationSender` после коммита |
| 6 | `Подтверждённое и урегулированное — разные состояния` | `DONE` против `SETTLED` |
| 7 | `Неподтверждённый расход виден человеку` | `serverConfirmed`, экран 28 |
| 8 | `Пересчёт снимает неопределённость, не списывая второй раз` | разрешение через `PatchPackage` |
| 9 | `Обновление приложения не роняет очередь` | `payloadVersion` |
| 10 | `Периодическая задача тянет снимок и добивает залежавшееся` | `SyncWorker` |

**Тесты:** приём во время отправки уезжает следующей операцией; **повтор идёт с исходной версией и
не списывает дважды**; **409 на повторе закрывает операцию как `SETTLED`, а не как
подтверждённую**; **404 после предполагаемого опустошения не превращается автоматически в
подтверждение нашего расхода**; два офлайн-приёма получают версии по очереди; **обновление
приложения при наличии `SENDING` и `SETTLED` не теряет и не дублирует операций**; **ручная сверка
снимает неопределённость без повторного списания и без двойного расхода в аналитике**; неизвестный
`payloadVersion` даёт `CONFLICT`, а не падение.

**Ограничение приёмки:** для операции, чей исход неустановим, приложение **не утверждает**, что
расход подтверждён.

---

## PR 14 — публикация и совместное использование

**Зависит от:** PR 13.

| # | коммит | содержание |
|---|---|---|
| 1 | `Перед публикацией человек видит, что станет общим` | экран последствий |
| 2 | `Публикация — обычная группа операций, а не особый режим` | `PUBLISHING`, группа |
| 3 | `Приглашение не выдаётся из наполовину опубликованной аптечки` | `acceptsInvitations` |
| 4 | `QR и код несут один ключ и один честно оценённый срок` | экраны 20, 21, `FLAG_SECURE` |
| 5 | `Текстовый код сразу лежит в буфере обмена` | `ClipboardManager` |
| 6 | `Негодное приглашение объясняется одной фразой` | экран 22 |
| 7 | `Выйти и удалить у всех — разные действия с разными последствиями` | экран 23, `MedKitRemoval` |
| 8 | `Выход из аптечки аннулирует курсы на её препараты` | отвязка источников |

**Тесты:** **повтор создания с тем же `id` даёт 409 и не создаёт дубля**; прерванная публикация
продолжается с того же места; **приглашение не выдаётся, пока группа не завершена**; недействительное
приглашение даёт единственный текст; **приём во время публикации не теряется**; выход гасит
источники, но не удаляет курсы и историю; удаление с переносом сохраняет пачки в целевой аптечке.

---

## PR 15 — общие источники и нехватка

**Зависит от:** PR 9, PR 14.

| # | коммит | содержание |
|---|---|---|
| 1 | `Пределы ползунка считаются из свежего состояния` | `availableToMe` в общей аптечке |
| 2 | `Сохранение нескольких источников идёт уменьшениями вперёд` | порядок операций в группе |
| 3 | `После команды над бронью версия читается заново` | обход отсутствия версии в ответе `PATCH` |
| 4 | `Чужой расход сокращает обеспечение, а не курс` | пересчёт после снимка |
| 5 | `Экран исправления предлагает решения, а не констатирует беду` | что докупить, откуда добрать |

**Тесты:** после чужого расхода уменьшается обеспечение, **даты и доза курса не меняются**;
частичный результат не рапортует об успехе; **после потерянного ответа упаковку перенесли туда и
обратно, сняв бронь — клиент не делает из этого вывода о своей операции**; `POST /v1/reservations`
на существующую бронь даёт 409 и переключает на `PATCH`.

---

## PR 16 — сканер

**Зависит от:** PR 7, PR 8.

| # | коммит | содержание |
|---|---|---|
| 1 | `Камера открывается и закрывается по жизненному циклу` | CameraX, экран 24 |
| 2 | `Формат кода определяет распознаватель, а не длина строки` | ML Kit `barcode.format` |
| 3 | `Запрос в «Честный знак» собирается по точному правилу` | `{FNC1}` из шести символов, нормализация парами |
| 4 | `Сведения о товаре не выдаются за сведения о пачке` | GTIN против сериализованного кода |
| 5 | `Срок годности из ответа переводится в дату явной зоной` | `expireDate` |
| 6 | `Непонятное остаётся незаполненным` | экран 25 |
| 7 | `Отказ в камере ведёт к ручному вводу` | деградация |

**Тесты:** фикстуры ответов обоих эндпойнтов; **`{FNC1}` ровно шесть символов**; нормализация
разделителей по названным парам вход→выход; `expireDate` в заданной зоне не сдвигается на сутки;
**EAN-13 помечается как сведения о товаре**; повтор кода в потоке не порождает нескольких запросов;
`200` с «не найдено» — не ошибка.

---

## PR 17 — аналитика

**Зависит от:** PR 10, PR 12.

| # | коммит | содержание |
|---|---|---|
| 1 | `Сводка считает пачки, а не догадки` | экран 26, сводка |
| 2 | `Цена без цены не превращается в ноль` | отдельный счётчик пачек без цены |
| 3 | `Прогноз считается формулой на три месяца` | `ForecastCalculator` |
| 4 | `Движение за период сходится` | баланс |
| 5 | `Отчёт называет начало доступной истории` | момент вступления в аптечку |
| 6 | `Неподтверждённый расход виден отдельной строкой` | `serverConfirmed` |

**Тесты:** баланс сходится на сгенерированной истории; **внутренний перенос не считается расходом**;
начало истории — вступление в аптечку; **разные единицы не суммируются**; пачки без цены посчитаны
отдельно.

---

## PR 18 — настройки и приёмка

**Зависит от:** все.

| # | коммит | содержание |
|---|---|---|
| 1 | `Пороги и время уведомлений настраиваются` | экран 27 |
| 2 | `Состояние разрешений видно и исправимо из приложения` | камера, уведомления, точные будильники |
| 3 | `Учётной записью можно управлять, понимая последствия` | утрата ключа, повторная регистрация |
| 4 | `Приложение говорит на одном языке и доступно на слух` | локализация, TalkBack, `contentDescription` |
| 5 | `Сквозные сценарии проходят на живом сервере` | J2 |
| 6 | `Есть чем принимать работу` | руководство оператора, методика испытаний |
| 7 | `Release-сборка собирается и не течёт секретами` | R8, правила, финальная проверка логов |

---

## I2. Граф зависимостей PR

```
0 → 1 → 2 → 3
        ↓    ↓
        4 ←──┘
        ↓
        5 → 6 → 7 → 8
                ↓   ↓
                9 ──┴→ 10 → 11
                ↓          ↓
        5,7 →  12 → 13 → 14 → 15
                            ↓
        7,8 → 16      10,12 → 17
                            ↓
                           18
```

---
---
---

# ЧАСТЬ J. Проверка

## J1. Уровни

| уровень | где | что |
|---|---|---|
| Домен | `test/` | значения, курс, расписание, обеспечение, прогноз — без Android |
| Мапперы | `test/` | `dto↔domain`, `entity↔domain`, потери и умолчания |
| Провод | `test/` | `WireContractTest` на фикстурах: `ignoreUnknownKeys = false`, `encodeDefaults = false` |
| Сеть | `test/` | `MockEngine`: коды, тела, повторы, авторизация |
| База | `androidTest/` | DAO, транзакции, ограничения, **миграции** |
| Экран | `androidTest/` | навигация, поворот, крупный шрифт, TalkBack |
| Контракт | `androidTest/` против сервера | `ContractProbe` |
| Сквозные | вручную и `androidTest/` | J2 |

## J2. Сквозные сценарии

1. **Личный учёт без сети.** Аптечка → две пачки → пересчёт → приём → история → поиск и фильтр.
2. **Курс от заметки.** Черновик с заметкой → покупка → два источника → распределение в приёмах →
   уведомления → приёмы и пропуски → завершение → история.
3. **Нехватка.** Второй клиент расходует общую пачку → синхронизация → обеспечение сократилось,
   пришло уведомление, **даты и доза курса прежние** → экран исправления.
4. **Общая аптечка.** Публикация с экраном последствий → приглашение → второй клиент вступает →
   оба меняют → синхронизация → выход одного → удаление вторым.
5. **Просрочка.** Пачка с прошедшей датой: первой в списке, выделена, предупреждает при приёме, сама
   не удаляется.
6. **Сканирование.** EAN-13 и DataMatrix, полные и неполные данные, ненайденный код, отказ в камере.
7. **Аналитика.** Прогноз на три месяца, движение за период, внутренние переносы не расход, чужие
   изменения отдельно.
8. **Потеря связи посреди приёма.** Приём офлайн → перезапуск приложения → связь → расход уехал ровно
   один раз.

## J3. Платформа

API **26**, 33, 34, 36. Поворот и пересоздание процесса; завершение процесса системой; перезагрузка;
смена времени и часового пояса; отказ в камере, уведомлениях и точных будильниках; крупный шрифт и
TalkBack; отсутствие связи при запуске уже настроенного приложения; тёмная тема; обновление
приложения при непустой очереди.

## J4. Матрица требований

Заполняется следующим коммитом.
