package com.kert0n.medapp.network.server

import com.kert0n.medapp.di.MedAppHttp
import com.kert0n.medapp.network.account.AccessTokenNetworkDTO
import com.kert0n.medapp.network.account.AccessTokenUnavailable
import com.kert0n.medapp.network.account.AccountCredentials
import com.kert0n.medapp.network.account.AccountRegisteredNetworkDTO
import com.kert0n.medapp.network.account.AccountSnapshotNetworkDTO
import com.kert0n.medapp.network.medkit.InvitationNetworkDTO
import com.kert0n.medapp.network.medkit.MedKitCreatedNetworkDTO
import com.kert0n.medapp.network.medkit.MedKitNetworkDTO
import com.kert0n.medapp.network.medkit.MedKitPostNetworkDTO
import com.kert0n.medapp.network.medkit.MedKitSummaryNetworkDTO
import com.kert0n.medapp.network.medkit.MembershipPostNetworkDTO
import com.kert0n.medapp.network.pack.ClaimNetworkDTO
import com.kert0n.medapp.network.pack.ClaimPatchNetworkDTO
import com.kert0n.medapp.network.pack.ClaimPostNetworkDTO
import com.kert0n.medapp.network.pack.PackageConsumeNetworkDTO
import com.kert0n.medapp.network.pack.PackagePatchNetworkDTO
import com.kert0n.medapp.network.pack.PackagePostNetworkDTO
import com.kert0n.medapp.network.pack.PackageSnapshotNetworkDTO
import com.kert0n.medapp.network.pack.PackageSyncNetworkDTO
import com.kert0n.medapp.network.template.PackageTemplateNetworkDTO
import com.kert0n.medapp.network.value.VocabularyEntryNetworkDTO
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.basicAuth
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer

/**
 * Все 26 операций сервера MedApp (PLAN B4). У каждой объявлено, что считается успехом: статус и
 * политика тела — обязательный JSON, JSON или ноль байтов (пачка кончилась и уничтожена), либо
 * отсутствие тела у `204` (PLAN B5). Всё остальное — [ApiFailure], а не исключение.
 *
 * Изменяющая команда и чтение различаются исходом сбоя: у чтения ничего не применено, у
 * команды исход неизвестен, и её вслепую не повторяют (PLAN E3).
 */
@Singleton
class MedAppApi @Inject constructor(@MedAppHttp private val http: HttpClient) {

    // Учётная запись

    /** Повторять нельзя: повтор даст вторую учётку. */
    suspend fun register(registrationToken: String): ApiResult<AccountRegisteredNetworkDTO> =
        call(HttpMethod.Post, REGISTER_PATH, HttpStatusCode.OK, required(AccountRegisteredNetworkDTO.serializer())) {
            header(REGISTRATION_TOKEN_HEADER, registrationToken)
        }

    suspend fun token(credentials: AccountCredentials): ApiResult<AccessTokenNetworkDTO> =
        call(HttpMethod.Post, "/v1/auth/token", HttpStatusCode.OK, required(AccessTokenNetworkDTO.serializer())) {
            basicAuth(credentials.login.toString(), credentials.key)
        }

    // Снимок и словари

    suspend fun snapshot(): ApiResult<AccountSnapshotNetworkDTO> =
        call(HttpMethod.Get, "/v1/users/me", HttpStatusCode.OK, required(AccountSnapshotNetworkDTO.serializer()))

    suspend fun medKits(): ApiResult<List<MedKitSummaryNetworkDTO>> =
        call(HttpMethod.Get, "/v1/med-kits", HttpStatusCode.OK, required(ListSerializer(MedKitSummaryNetworkDTO.serializer())))

    suspend fun medKit(medKitId: Uuid): ApiResult<MedKitNetworkDTO> =
        call(HttpMethod.Get, "/v1/med-kits/$medKitId", HttpStatusCode.OK, required(MedKitNetworkDTO.serializer()))

    suspend fun quantityUnits(): ApiResult<List<VocabularyEntryNetworkDTO>> =
        call(HttpMethod.Get, "/v1/quantity-units", HttpStatusCode.OK, required(ListSerializer(VocabularyEntryNetworkDTO.serializer())))

    suspend fun formTypes(): ApiResult<List<VocabularyEntryNetworkDTO>> =
        call(HttpMethod.Get, "/v1/form-types", HttpStatusCode.OK, required(ListSerializer(VocabularyEntryNetworkDTO.serializer())))

    // Аптечки

    suspend fun createMedKit(medKit: MedKitPostNetworkDTO): ApiResult<MedKitCreatedNetworkDTO> =
        call(HttpMethod.Post, "/v1/med-kits", HttpStatusCode.Created, required(MedKitCreatedNetworkDTO.serializer())) {
            json(medKit)
        }

    /** Удаляет аптечку у всех; [transferTo] переносит содержимое в другую аптечку вызывающего. */
    suspend fun deleteMedKit(medKitId: Uuid, transferTo: Uuid? = null): ApiResult<Unit> =
        call(HttpMethod.Delete, "/v1/med-kits/$medKitId", HttpStatusCode.NoContent, none) {
            transferTo?.let { parameter("targetMedKitId", it.toString()) }
        }

    suspend fun createInvitation(medKitId: Uuid): ApiResult<InvitationNetworkDTO> =
        call(HttpMethod.Post, "/v1/med-kits/$medKitId/invitations", HttpStatusCode.Created, required(InvitationNetworkDTO.serializer()))

    suspend fun joinMedKit(membership: MembershipPostNetworkDTO): ApiResult<MedKitNetworkDTO> =
        call(HttpMethod.Post, "/v1/med-kit-memberships", HttpStatusCode.Created, required(MedKitNetworkDTO.serializer())) {
            json(membership)
        }

    suspend fun leaveMedKit(medKitId: Uuid): ApiResult<Unit> =
        call(HttpMethod.Delete, "/v1/med-kit-memberships/$medKitId", HttpStatusCode.NoContent, none)

    // Упаковки

    suspend fun createPackage(medKitId: Uuid, pack: PackagePostNetworkDTO): ApiResult<PackageSnapshotNetworkDTO> =
        call(HttpMethod.Post, "/v1/med-kits/$medKitId/drugs", HttpStatusCode.Created, required(PackageSnapshotNetworkDTO.serializer())) {
            json(pack)
        }

    suspend fun packageSnapshot(packageId: Uuid): ApiResult<PackageSnapshotNetworkDTO> =
        call(HttpMethod.Get, "/v1/drugs/$packageId", HttpStatusCode.OK, required(PackageSnapshotNetworkDTO.serializer()))

    suspend fun patchPackage(packageId: Uuid, patch: PackagePatchNetworkDTO): ApiResult<PackageSnapshotNetworkDTO> =
        call(HttpMethod.Patch, "/v1/drugs/$packageId", HttpStatusCode.OK, required(PackageSnapshotNetworkDTO.serializer())) {
            json(patch)
        }

    suspend fun deletePackage(packageId: Uuid, version: ResourceVersion?): ApiResult<Unit> =
        call(HttpMethod.Delete, "/v1/drugs/$packageId", HttpStatusCode.NoContent, none) {
            version(version)
        }

    suspend fun movePackage(
        packageId: Uuid,
        targetMedKitId: Uuid,
        version: ResourceVersion?
    ): ApiResult<PackageSnapshotNetworkDTO> =
        call(HttpMethod.Put, "/v1/med-kits/$targetMedKitId/drugs/$packageId", HttpStatusCode.OK, required(PackageSnapshotNetworkDTO.serializer())) {
            version(version)
        }

    /** `null` в успехе — пачка кончилась и уничтожена: сервер ответил нулём байтов. */
    suspend fun consume(packageId: Uuid, intake: PackageConsumeNetworkDTO): ApiResult<PackageSnapshotNetworkDTO?> =
        call(HttpMethod.Post, "/v1/drugs/$packageId/intakes", HttpStatusCode.OK, optional(PackageSnapshotNetworkDTO.serializer())) {
            json(intake)
        }

    /** `null` в успехе — пачка кончилась и уничтожена; повтор безопасен с тем же [syncId]. */
    suspend fun synchronise(
        packageId: Uuid,
        syncId: Uuid,
        changes: PackageSyncNetworkDTO
    ): ApiResult<PackageSnapshotNetworkDTO?> =
        call(HttpMethod.Put, "/v1/drugs/$packageId/sync/$syncId", HttpStatusCode.OK, optional(PackageSnapshotNetworkDTO.serializer())) {
            json(changes)
        }

    // Брони

    suspend fun claims(): ApiResult<List<ClaimNetworkDTO>> =
        call(HttpMethod.Get, "/v1/reservations", HttpStatusCode.OK, required(ListSerializer(ClaimNetworkDTO.serializer())))

    suspend fun claim(packageId: Uuid): ApiResult<ClaimNetworkDTO> =
        call(HttpMethod.Get, "/v1/reservations/$packageId", HttpStatusCode.OK, required(ClaimNetworkDTO.serializer()))

    suspend fun createClaim(claim: ClaimPostNetworkDTO): ApiResult<ClaimNetworkDTO> =
        call(HttpMethod.Post, "/v1/reservations", HttpStatusCode.Created, required(ClaimNetworkDTO.serializer())) {
            json(claim)
        }

    suspend fun patchClaim(packageId: Uuid, claim: ClaimPatchNetworkDTO): ApiResult<ClaimNetworkDTO> =
        call(HttpMethod.Patch, "/v1/reservations/$packageId", HttpStatusCode.OK, required(ClaimNetworkDTO.serializer())) {
            json(claim)
        }

    suspend fun deleteClaim(packageId: Uuid, version: ResourceVersion?): ApiResult<Unit> =
        call(HttpMethod.Delete, "/v1/reservations/$packageId", HttpStatusCode.NoContent, none) {
            version(version)
        }

    // Справочник

    suspend fun searchTemplates(query: String, limit: Int): ApiResult<List<PackageTemplateNetworkDTO>> {
        require(query.length in 1..TEMPLATE_QUERY_MAX) { "запрос справочника — от 1 до $TEMPLATE_QUERY_MAX символов" }
        require(limit in 1..TEMPLATE_LIMIT_MAX) { "справочник отдаёт от 1 до $TEMPLATE_LIMIT_MAX карточек" }
        return call(HttpMethod.Get, "/v1/drug-templates", HttpStatusCode.OK, required(ListSerializer(PackageTemplateNetworkDTO.serializer()))) {
            parameter("query", query)
            parameter("limit", limit)
        }
    }

    suspend fun template(templateId: Uuid): ApiResult<PackageTemplateNetworkDTO> =
        call(HttpMethod.Get, "/v1/drug-templates/$templateId", HttpStatusCode.OK, required(PackageTemplateNetworkDTO.serializer()))

    // Исполнение

    private suspend fun <T> call(
        method: HttpMethod,
        path: String,
        success: HttpStatusCode,
        reader: Reader<T>,
        configure: HttpRequestBuilder.() -> Unit = {}
    ): ApiResult<T> {
        val command = method != HttpMethod.Get
        return try {
            val response = http.request(path) {
                this.method = method
                configure()
            }
            when {
                response.status == success -> reader(response, command)
                response.status.isSuccess() ->
                    broken(command, "успех ${response.status.value} вместо ${success.value}")
                else -> ApiResult.Failure(refusal(response, command, path))
            }
        } catch (_: AccessTokenUnavailable) {
            ApiResult.Failure(ApiFailure.Unavailable)
        } catch (_: IOException) {
            ApiResult.Failure(if (command) ApiFailure.OutcomeUnknown else ApiFailure.Unavailable)
        }
    }

    /** Отказ сервера — решение по коду ответа (PLAN B5); тело добавляет только `errors[]` при 400. */
    private suspend fun refusal(response: HttpResponse, command: Boolean, path: String): ApiFailure =
        when (response.status.value) {
            400 -> ApiFailure.Invalid(
                problem(response).errors.map { ApiFailure.FieldError(it.field, it.reason) }
            )
            401 -> ApiFailure.Unauthorized
            403 ->
                if (path == REGISTER_PATH) ApiFailure.RegistrationRefused
                else ApiFailure.Protocol("403 вне регистрации")
            404 -> ApiFailure.NotFound
            409 -> ApiFailure.Conflict
            412 -> ApiFailure.PreconditionFailed
            428 -> ApiFailure.PreconditionRequired
            429 -> ApiFailure.TooManyRequests(retryAfter(response))
            in 500..599 -> if (command) ApiFailure.OutcomeUnknown else ApiFailure.Unavailable
            else -> ApiFailure.Protocol("отказ ${response.status.value} вне контракта")
        }

    private suspend fun problem(response: HttpResponse): ProblemNetworkDTO = try {
        problemJson.decodeFromString(ProblemNetworkDTO.serializer(), response.bodyAsText())
    } catch (_: IllegalArgumentException) {
        ProblemNetworkDTO()
    }

    /** `Retry-After` в секундах; дату и прочее вызывающий заменяет своим backoff (PLAN B5). */
    private fun retryAfter(response: HttpResponse): Duration? =
        response.headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.seconds

    private fun <T> required(serializer: KSerializer<T>): Reader<T> = { response, command ->
        val text = response.bodyAsText()
        if (text.isEmpty()) broken(command, "пустое тело там, где контракт обещает JSON")
        else decode(serializer, text, command)
    }

    private fun <T : Any> optional(serializer: KSerializer<T>): Reader<T?> = { response, command ->
        val text = response.bodyAsText()
        if (text.isEmpty()) ApiResult.Success(null) else decode(serializer, text, command)
    }

    private val none: Reader<Unit> = { _, _ -> ApiResult.Success(Unit) }

    private fun <T> decode(serializer: KSerializer<T>, text: String, command: Boolean): ApiResult<T> =
        try {
            ApiResult.Success(medAppJson.decodeFromString(serializer, text))
        } catch (_: IllegalArgumentException) {
            broken(command, "тело ответа не по контракту")
        }

    /** Ответ не по контракту: у чтения это ошибка протокола, у команды — неизвестный исход. */
    private fun broken(command: Boolean, reason: String): ApiResult<Nothing> =
        ApiResult.Failure(if (command) ApiFailure.OutcomeUnknown else ApiFailure.Protocol(reason))

    private inline fun <reified B> HttpRequestBuilder.json(body: B) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private fun HttpRequestBuilder.version(version: ResourceVersion?) {
        version?.let { parameter("version", it.number) }
    }

    private companion object {
        const val REGISTER_PATH = "/v1/auth/register"
        const val TEMPLATE_QUERY_MAX = 200
        const val TEMPLATE_LIMIT_MAX = 50
    }
}

/** Как операция читает свой успешный ответ; второй аргумент — изменяет ли она состояние сервера. */
private typealias Reader<T> = suspend (HttpResponse, Boolean) -> ApiResult<T>
