package com.kert0n.medapp.network.server

import com.kert0n.medapp.network.account.AccessTokens
import com.kert0n.medapp.network.account.AccountCredentials
import com.kert0n.medapp.network.account.CredentialSource
import com.kert0n.medapp.network.account.StoredAccount
import com.kert0n.medapp.network.medkit.MedKitPostNetworkDTO
import com.kert0n.medapp.network.medkit.MembershipPostNetworkDTO
import com.kert0n.medapp.network.pack.ClaimPatchNetworkDTO
import com.kert0n.medapp.network.pack.ClaimPostNetworkDTO
import com.kert0n.medapp.network.pack.PackageConsumeNetworkDTO
import com.kert0n.medapp.network.pack.PackagePatchNetworkDTO
import com.kert0n.medapp.network.pack.PackagePostNetworkDTO
import com.kert0n.medapp.network.pack.PackageSyncNetworkDTO
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.content.TextContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * У каждой из 26 операций объявлен свой успех — статус и политика тела, — а всё остальное
 * становится решением, а не исключением (PLAN B4, B5).
 */
class MedAppApiTest {

    private val kit = Uuid.parse("00000000-0000-4000-8000-000000000021")
    private val otherKit = Uuid.parse("00000000-0000-4000-8000-000000000022")
    private val pack = Uuid.parse("00000000-0000-4000-8000-000000000011")
    private val unit = Uuid.parse("00000000-0000-4000-8000-000000000031")
    private val sync = Uuid.parse("00000000-0000-4000-8000-000000000051")
    private val template = Uuid.parse("00000000-0000-4000-8000-000000000061")

    private val drug = """
        {"drug":{"id":"$pack","name":"Аспирин","quantity":"100.000000","quantityUnitId":"$unit",
         "medKitId":"$kit","version":3},"reservations":{"total":"0.000000","version":5}}
    """
    private val medKit = """{"id":"$kit","userCount":1,"drugs":[$drug]}"""
    private val claim = """{"drugId":"$pack","amount":"20.000000"}"""
    private val card = """{"id":"$template","name":"Аспирин"}"""

    /** Ответ сервера на «МЕТОД путь». */
    private val routes = mapOf(
        "POST /v1/auth/register" to (HttpStatusCode.OK to """{"login":"$kit","key":"k"}"""),
        "POST /v1/auth/token" to (HttpStatusCode.OK to """{"accessToken":"t"}"""),
        "GET /v1/users/me" to (HttpStatusCode.OK to """{"id":"$kit","medKits":[$medKit]}"""),
        "GET /v1/med-kits" to (HttpStatusCode.OK to """[{"id":"$kit","userCount":1,"drugIds":["$pack"]}]"""),
        "GET /v1/med-kits/$kit" to (HttpStatusCode.OK to medKit),
        "GET /v1/quantity-units" to (HttpStatusCode.OK to """[{"id":"$unit","name":"таб"}]"""),
        "GET /v1/form-types" to (HttpStatusCode.OK to "[]"),
        "POST /v1/med-kits" to (HttpStatusCode.Created to """{"id":"$kit"}"""),
        "DELETE /v1/med-kits/$kit" to (HttpStatusCode.NoContent to ""),
        "POST /v1/med-kits/$kit/invitations" to (HttpStatusCode.Created to """{"key":"invite"}"""),
        "POST /v1/med-kit-memberships" to (HttpStatusCode.Created to medKit),
        "DELETE /v1/med-kit-memberships/$kit" to (HttpStatusCode.NoContent to ""),
        "POST /v1/med-kits/$kit/drugs" to (HttpStatusCode.Created to drug),
        "GET /v1/drugs/$pack" to (HttpStatusCode.OK to drug),
        "PATCH /v1/drugs/$pack" to (HttpStatusCode.OK to drug),
        "DELETE /v1/drugs/$pack" to (HttpStatusCode.NoContent to ""),
        "PUT /v1/med-kits/$otherKit/drugs/$pack" to (HttpStatusCode.OK to drug),
        "POST /v1/drugs/$pack/intakes" to (HttpStatusCode.OK to drug),
        "PUT /v1/drugs/$pack/sync/$sync" to (HttpStatusCode.OK to ""),
        "GET /v1/reservations" to (HttpStatusCode.OK to "[$claim]"),
        "GET /v1/reservations/$pack" to (HttpStatusCode.OK to claim),
        "POST /v1/reservations" to (HttpStatusCode.Created to claim),
        "PATCH /v1/reservations/$pack" to (HttpStatusCode.OK to claim),
        "DELETE /v1/reservations/$pack" to (HttpStatusCode.NoContent to ""),
        "GET /v1/drug-templates" to (HttpStatusCode.OK to "[$card]"),
        "GET /v1/drug-templates/$template" to (HttpStatusCode.OK to card)
    )

    private val calls = AtomicInteger()
    private val requests = mutableListOf<String>()
    private val bodies = mutableMapOf<String, String>()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun api(
        tokens: AccessTokens? = null,
        answer: (String) -> Pair<HttpStatusCode, String>? = { routes[it] }
    ) = MedAppApi(
        medAppHttpClient(
            MockEngine { request ->
                calls.incrementAndGet()
                val line = "${request.method.value} ${request.url.encodedPath}"
                requests += line + request.url.encodedQuery.let { if (it.isEmpty()) "" else "?$it" }
                (request.body as? TextContent)?.let { bodies[line] = it.text }
                val (status, body) = answer(line) ?: (HttpStatusCode.NotFound to "")
                respond(body, status, json)
            },
            "https://medapp.test",
            tokens = tokens,
            retryDelay = { delayMillis(false) { 0L } }
        )
    )

    private fun always(status: HttpStatusCode, body: String = "") = api { status to body }

    private fun assertSuccess(result: ApiResult<*>) {
        assertTrue("ожидался успех: $result", result is ApiResult.Success)
    }

    @Test
    fun everyOperationSucceedsExactlyAsDeclared() = runTest {
        val api = api()
        val account = AccountCredentials(kit, "k")
        val newPack = PackagePostNetworkDTO(pack, "Аспирин", "10", unit, null, null, null, null, null)

        listOf(
            api.register("registration"),
            api.token(account),
            api.snapshot(),
            api.medKits(),
            api.medKit(kit),
            api.quantityUnits(),
            api.formTypes(),
            api.createMedKit(MedKitPostNetworkDTO(kit)),
            api.deleteMedKit(kit, transferTo = otherKit),
            api.createInvitation(kit),
            api.joinMedKit(MembershipPostNetworkDTO("invite")),
            api.leaveMedKit(kit),
            api.createPackage(kit, newPack),
            api.packageSnapshot(pack),
            api.patchPackage(pack, PackagePatchNetworkDTO(name = "Аспирин C", version = ResourceVersion(3))),
            api.deletePackage(pack, ResourceVersion(3)),
            api.movePackage(pack, otherKit, ResourceVersion(3)),
            api.consume(pack, PackageConsumeNetworkDTO("2", ResourceVersion(3))),
            api.synchronise(pack, sync, PackageSyncNetworkDTO("2", ResourceVersion(3))),
            api.claims(),
            api.claim(pack),
            api.createClaim(ClaimPostNetworkDTO(pack, "20", ResourceVersion(5))),
            api.patchClaim(pack, ClaimPatchNetworkDTO("15", ResourceVersion(5))),
            api.deleteClaim(pack, ResourceVersion(5)),
            api.searchTemplates("аспирин", 10),
            api.template(template)
        ).forEach(::assertSuccess)

        assertEquals(26, requests.size)
        assertEquals(26, requests.map { it.substringBefore('?') }.toSet().size)
    }

    @Test
    fun preconditionsAndOptionsTravelInTheQuery() = runTest {
        val api = api()
        api.deletePackage(pack, ResourceVersion(3))
        api.movePackage(pack, otherKit, ResourceVersion(4))
        api.deleteMedKit(kit, transferTo = otherKit)
        api.searchTemplates("аспирин", 10)

        assertEquals(
            listOf(
                "DELETE /v1/drugs/$pack?version=3",
                "PUT /v1/med-kits/$otherKit/drugs/$pack?version=4",
                "DELETE /v1/med-kits/$kit?targetMedKitId=$otherKit",
                "GET /v1/drug-templates?query=%D0%B0%D1%81%D0%BF%D0%B8%D1%80%D0%B8%D0%BD&limit=10"
            ),
            requests
        )
    }

    @Test
    fun commandWithoutVersionSendsNoVersionAtAll() = runTest {
        api().deletePackage(pack, version = null)

        assertEquals(listOf("DELETE /v1/drugs/$pack"), requests)
    }

    @Test
    fun bodiesAreTheContractShapes() = runTest {
        api().consume(pack, PackageConsumeNetworkDTO("2", ResourceVersion(3)))

        assertEquals(
            Json.parseToJsonElement("""{"quantity":"2","version":3}"""),
            Json.parseToJsonElement(bodies.getValue("POST /v1/drugs/$pack/intakes"))
        )
    }

    @Test
    fun registrationCarriesTheBuildToken() = runTest {
        var header: String? = null
        MedAppApi(
            medAppHttpClient(
                MockEngine { request ->
                    header = request.headers[REGISTRATION_TOKEN_HEADER]
                    respond("""{"login":"$kit","key":"k"}""", HttpStatusCode.OK, json)
                },
                "https://medapp.test"
            )
        ).register("registration-token")

        assertEquals("registration-token", header)
    }

    /** Пачка кончилась и уничтожена: сервер отвечает 200 и нулём байтов. */
    @Test
    fun emptyIntakeResponseMeansThePackageIsGone() = runTest {
        val result = always(HttpStatusCode.OK).consume(pack, PackageConsumeNetworkDTO("100"))

        assertEquals(ApiResult.Success(null), result)
    }

    @Test
    fun emptySyncResponseMeansThePackageIsGone() = runTest {
        val result = always(HttpStatusCode.OK)
            .synchronise(pack, sync, PackageSyncNetworkDTO("2", ResourceVersion(3)))

        assertEquals(ApiResult.Success(null), result)
    }

    /** JSON `null` — не «ноль байтов»: за уничтожение пачки его не принимают. */
    @Test
    fun jsonNullIsNotAnEmptyIntakeResponse() = runTest {
        val result = always(HttpStatusCode.OK, "null").consume(pack, PackageConsumeNetworkDTO("2"))

        assertEquals(ApiResult.Failure(ApiFailure.OutcomeUnknown), result)
    }

    @Test
    fun emptyBodyWhereJsonIsPromisedIsAProtocolError() = runTest {
        val result = always(HttpStatusCode.OK).snapshot()

        assertTrue((result as ApiResult.Failure).failure is ApiFailure.Protocol)
    }

    @Test
    fun noContentIsTheSuccessOfRemoval() = runTest {
        assertEquals(ApiResult.Success(Unit), always(HttpStatusCode.NoContent).leaveMedKit(kit))
    }

    @Test
    fun unexpectedSuccessStatusOfAReadIsAProtocolError() = runTest {
        val result = always(HttpStatusCode.Created, "[]").formTypes()

        assertTrue((result as ApiResult.Failure).failure is ApiFailure.Protocol)
    }

    @Test
    fun unexpectedSuccessStatusOfACommandLeavesTheOutcomeUnknown() = runTest {
        val result = always(HttpStatusCode.OK, """{"id":"$kit"}""").createMedKit(MedKitPostNetworkDTO(kit))

        assertEquals(ApiResult.Failure(ApiFailure.OutcomeUnknown), result)
    }

    @Test
    fun brokenJsonOfAReadIsAProtocolErrorNotACrash() = runTest {
        val result = always(HttpStatusCode.OK, """{"id":""").snapshot()

        assertTrue((result as ApiResult.Failure).failure is ApiFailure.Protocol)
    }

    @Test
    fun brokenJsonOfACommandLeavesTheOutcomeUnknown() = runTest {
        val result = always(HttpStatusCode.OK, "{").consume(pack, PackageConsumeNetworkDTO("2"))

        assertEquals(ApiResult.Failure(ApiFailure.OutcomeUnknown), result)
    }

    @Test
    fun serverErrorOnACommandIsNotRepeatedAndLeavesTheOutcomeUnknown() = runTest {
        val result = always(HttpStatusCode.InternalServerError).consume(pack, PackageConsumeNetworkDTO("2"))

        assertEquals(ApiResult.Failure(ApiFailure.OutcomeUnknown), result)
        assertEquals(1, calls.get())
    }

    @Test
    fun serverErrorOnAReadIsRepeatedThenUnavailable() = runTest {
        val result = always(HttpStatusCode.ServiceUnavailable).snapshot()

        assertEquals(ApiResult.Failure(ApiFailure.Unavailable), result)
        assertEquals(4, calls.get())
    }

    @Test
    fun brokenConnectionDuringACommandLeavesTheOutcomeUnknown() = runTest {
        val result = MedAppApi(
            medAppHttpClient(MockEngine { throw IOException("обрыв") }, "https://medapp.test")
        ).consume(pack, PackageConsumeNetworkDTO("2"))

        assertEquals(ApiResult.Failure(ApiFailure.OutcomeUnknown), result)
    }

    /** Пропуска не получить — запрос ещё не ушёл, значит ничего не применено. */
    @Test
    fun missingTokenIsUnavailableNotUnknown() = runTest {
        val stored = object : CredentialSource {
            override suspend fun read(): StoredAccount =
                StoredAccount.Present(AccountCredentials(kit, "k"))
            override suspend fun save(credentials: AccountCredentials) = Unit
        }
        val result = api(tokens = AccessTokens(stored)) { line ->
            if (line == "POST /v1/auth/token") HttpStatusCode.ServiceUnavailable to "" else routes[line]
        }.consume(pack, PackageConsumeNetworkDTO("2"))

        assertEquals(ApiResult.Failure(ApiFailure.Unavailable), result)
        assertEquals(listOf("POST /v1/auth/token"), requests)
    }

    @Test
    fun refusalCarriesItsStatus() = runTest {
        assertEquals(
            ApiResult.Failure(ApiFailure.Refused(404)),
            always(HttpStatusCode.NotFound).packageSnapshot(pack)
        )
    }
}
