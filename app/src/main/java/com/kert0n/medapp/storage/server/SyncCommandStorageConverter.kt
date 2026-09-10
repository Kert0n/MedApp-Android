package com.kert0n.medapp.storage.server

import com.kert0n.medapp.domain.pack.PackageSharedFacts
import com.kert0n.medapp.domain.value.Dose
import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.network.medkit.MedKitSyncCommand
import com.kert0n.medapp.network.pack.PackageSyncCommand
import com.kert0n.medapp.network.server.SyncCommand
import com.kert0n.medapp.storage.value.storedQuantity
import com.kert0n.medapp.storage.value.toStorageAmount
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Вид команды хранится дискриминатором колонки, а её поля — объектом рядом. Разбор написан
 * руками, потому что доменные величины не носят сериализации: как `Quantity` становится
 * колонкой, знает слой хранения, а не сама величина (PLAN F1, AGENTS).
 *
 * У общего маркера `SyncCommand` исчерпывающего `when` нет — цена деления команд по понятиям
 * (E2). Вместо него набор закрыт круговым тестом по всем двенадцати видам.
 */
object SyncCommandStorageConverter {

    /**
     * Версия формата payload. Незавершённые операции переживают обновление приложения:
     * неизвестная версия переводит операцию в `CONFLICT`, а не роняет процесс (PLAN F4).
     */
    const val PAYLOAD_VERSION = 1

    fun kindOf(command: SyncCommand): String = when (command) {
        is PackageSyncCommand -> when (command) {
            is PackageSyncCommand.Create -> PACKAGE_CREATE
            is PackageSyncCommand.Describe -> PACKAGE_DESCRIBE
            is PackageSyncCommand.CorrectStock -> PACKAGE_CORRECT_STOCK
            is PackageSyncCommand.Move -> PACKAGE_MOVE
            is PackageSyncCommand.Delete -> PACKAGE_DELETE
            is PackageSyncCommand.Consume -> PACKAGE_CONSUME
            is PackageSyncCommand.SetClaim -> PACKAGE_SET_CLAIM
            is PackageSyncCommand.ReleaseClaim -> PACKAGE_RELEASE_CLAIM
            is PackageSyncCommand.Reconcile -> PACKAGE_RECONCILE
        }
        is MedKitSyncCommand -> when (command) {
            is MedKitSyncCommand.Create -> MEDKIT_CREATE
            is MedKitSyncCommand.Delete -> MEDKIT_DELETE
            is MedKitSyncCommand.Leave -> MEDKIT_LEAVE
        }
        else -> unknownRoot(command)
    }

    /** Какой пачки касается команда; `null` у команд аптечки — порядок по пачке строит запрос. */
    fun packageIdOf(command: SyncCommand): Uuid? = (command as? PackageSyncCommand)?.packageId

    fun medKitIdOf(command: SyncCommand): Uuid? = when (command) {
        is MedKitSyncCommand -> command.medKitId
        is PackageSyncCommand.Create -> command.medKitId
        is PackageSyncCommand.Move -> command.targetMedKitId
        else -> null
    }

    fun payloadOf(command: SyncCommand): String = json.encodeToString(
        JsonObject.serializer(),
        when (command) {
            is PackageSyncCommand -> packagePayload(command)
            is MedKitSyncCommand -> medKitPayload(command)
            else -> unknownRoot(command)
        }
    )

    /**
     * Команда из строки. `null` означает «прочитать нечем»: неизвестный вид или чужая версия
     * payload переводят операцию в `CONFLICT`, а не роняют разбор очереди.
     */
    fun commandOf(kind: String, payload: String, payloadVersion: Int): SyncCommand? {
        if (payloadVersion != PAYLOAD_VERSION) return null
        val fields = runCatching { json.parseToJsonElement(payload) as JsonObject }.getOrNull()
            ?: return null
        return runCatching { read(kind, fields) }.getOrNull()
    }

    private fun read(kind: String, fields: JsonObject): SyncCommand? = when (kind) {
        PACKAGE_CREATE -> PackageSyncCommand.Create(
            packageId = fields.uuid("packageId"),
            medKitId = fields.uuid("medKitId"),
            quantity = fields.quantity("quantity"),
            facts = fields.facts()
        )
        PACKAGE_DESCRIBE -> PackageSyncCommand.Describe(
            packageId = fields.uuid("packageId"),
            before = (fields["before"] as JsonObject).facts(),
            after = (fields["after"] as JsonObject).facts()
        )
        PACKAGE_CORRECT_STOCK -> PackageSyncCommand.CorrectStock(
            packageId = fields.uuid("packageId"),
            actual = fields.quantity("actual")
        )
        PACKAGE_MOVE -> PackageSyncCommand.Move(
            packageId = fields.uuid("packageId"),
            targetMedKitId = fields.uuid("targetMedKitId")
        )
        PACKAGE_DELETE -> PackageSyncCommand.Delete(packageId = fields.uuid("packageId"))
        PACKAGE_CONSUME -> PackageSyncCommand.Consume(
            packageId = fields.uuid("packageId"),
            amount = Dose(fields.quantity("amount")),
            intakeId = fields.uuid("intakeId"),
            claimAfter = if (fields.containsKey("claimAfter")) fields.quantity("claimAfter") else null
        )
        PACKAGE_SET_CLAIM -> PackageSyncCommand.SetClaim(
            packageId = fields.uuid("packageId"),
            amount = fields.quantity("amount")
        )
        PACKAGE_RELEASE_CLAIM -> PackageSyncCommand.ReleaseClaim(
            packageId = fields.uuid("packageId")
        )
        PACKAGE_RECONCILE -> PackageSyncCommand.Reconcile(
            packageId = fields.uuid("packageId"),
            actual = fields.quantity("actual"),
            throughSequence = fields.text("throughSequence").toLong()
        )
        MEDKIT_CREATE -> MedKitSyncCommand.Create(medKitId = fields.uuid("medKitId"))
        MEDKIT_DELETE -> MedKitSyncCommand.Delete(
            medKitId = fields.uuid("medKitId"),
            transferTo = fields.optionalUuid("transferTo")
        )
        MEDKIT_LEAVE -> MedKitSyncCommand.Leave(medKitId = fields.uuid("medKitId"))
        else -> null
    }

    private fun packagePayload(command: PackageSyncCommand): JsonObject = buildJsonObject {
        put("packageId", JsonPrimitive(command.packageId.toString()))
        when (command) {
            is PackageSyncCommand.Create -> {
                put("medKitId", JsonPrimitive(command.medKitId.toString()))
                putQuantity("quantity", command.quantity)
                put("name", JsonPrimitive(command.facts.name))
                putFacts(command.facts)
            }
            is PackageSyncCommand.Describe -> {
                put("before", factsObject(command.before))
                put("after", factsObject(command.after))
            }
            is PackageSyncCommand.CorrectStock -> putQuantity("actual", command.actual)
            is PackageSyncCommand.Move ->
                put("targetMedKitId", JsonPrimitive(command.targetMedKitId.toString()))
            is PackageSyncCommand.Delete -> Unit
            is PackageSyncCommand.Consume -> {
                putQuantity("amount", command.amount.quantity)
                put("intakeId", JsonPrimitive(command.intakeId.toString()))
                command.claimAfter?.let { putQuantity("claimAfter", it) }
            }
            is PackageSyncCommand.SetClaim -> putQuantity("amount", command.amount)
            is PackageSyncCommand.ReleaseClaim -> Unit
            is PackageSyncCommand.Reconcile -> {
                putQuantity("actual", command.actual)
                put("throughSequence", JsonPrimitive(command.throughSequence.toString()))
            }
        }
    }

    private fun medKitPayload(command: MedKitSyncCommand): JsonObject = buildJsonObject {
        put("medKitId", JsonPrimitive(command.medKitId.toString()))
        if (command is MedKitSyncCommand.Delete) {
            command.transferTo?.let { put("transferTo", JsonPrimitive(it.toString())) }
        }
    }

    /**
     * Корней команд два, и оба перечислены выше. Третий означает, что маркер надели на новое
     * понятие и забыли про хранение — исчерпывающего `when` у маркера нет (PLAN E2).
     */
    private fun unknownRoot(command: SyncCommand): Nothing =
        error("команда неизвестного корня: ${command::class.simpleName}")

    private const val PACKAGE_CREATE = "PACKAGE_CREATE"
    private const val PACKAGE_DESCRIBE = "PACKAGE_DESCRIBE"
    private const val PACKAGE_CORRECT_STOCK = "PACKAGE_CORRECT_STOCK"
    private const val PACKAGE_MOVE = "PACKAGE_MOVE"
    private const val PACKAGE_DELETE = "PACKAGE_DELETE"
    private const val PACKAGE_CONSUME = "PACKAGE_CONSUME"
    private const val PACKAGE_SET_CLAIM = "PACKAGE_SET_CLAIM"
    private const val PACKAGE_RELEASE_CLAIM = "PACKAGE_RELEASE_CLAIM"
    private const val PACKAGE_RECONCILE = "PACKAGE_RECONCILE"
    private const val MEDKIT_CREATE = "MEDKIT_CREATE"
    private const val MEDKIT_DELETE = "MEDKIT_DELETE"
    private const val MEDKIT_LEAVE = "MEDKIT_LEAVE"

    private val json = Json

    private fun kotlinx.serialization.json.JsonObjectBuilder.putQuantity(
        name: String,
        quantity: Quantity
    ) {
        put(name, JsonPrimitive(quantity.toStorageAmount()))
        put("${name}UnitId", JsonPrimitive(quantity.unitId.toString()))
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putFacts(facts: PackageSharedFacts) {
        facts.formId?.let { put("formId", JsonPrimitive(it.toString())) }
        facts.category?.let { put("category", JsonPrimitive(it)) }
        facts.manufacturer?.let { put("manufacturer", JsonPrimitive(it)) }
        facts.country?.let { put("country", JsonPrimitive(it)) }
        facts.description?.let { put("description", JsonPrimitive(it)) }
    }

    private fun factsObject(facts: PackageSharedFacts): JsonObject = buildJsonObject {
        put("name", JsonPrimitive(facts.name))
        putFacts(facts)
    }

    private fun JsonObject.text(name: String): String =
        requireNotNull(this[name]).jsonPrimitive.content

    private fun JsonObject.optionalText(name: String): String? = this[name]?.jsonPrimitive?.content

    private fun JsonObject.uuid(name: String): Uuid = Uuid.parse(text(name))

    private fun JsonObject.optionalUuid(name: String): Uuid? = optionalText(name)?.let(Uuid::parse)

    private fun JsonObject.quantity(name: String): Quantity =
        storedQuantity(text(name), uuid("${name}UnitId"))

    private fun JsonObject.facts(): PackageSharedFacts = PackageSharedFacts(
        name = text("name"),
        formId = optionalUuid("formId"),
        category = optionalText("category"),
        manufacturer = optionalText("manufacturer"),
        country = optionalText("country"),
        description = optionalText("description")
    )
}
