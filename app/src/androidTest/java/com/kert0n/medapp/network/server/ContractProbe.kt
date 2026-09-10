package com.kert0n.medapp.network.server

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
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
import com.kert0n.medapp.network.pack.PackageSnapshotNetworkDTO
import com.kert0n.medapp.network.pack.PackageSyncNetworkDTO
import io.ktor.client.engine.okhttp.OkHttp
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

/**
 * Проба контракта против боевого сервера (PLAN PR 5, AGENTS «Связь с сервером»): тот же клиент,
 * что у приложения, два пробных пользователя из `local.properties` и синтетические аптечки,
 * которые прогон удаляет за собой. Проверяются не только успехи, но и отказы: 409 на повтор
 * создания, 428 и 412 на предусловия, 404 на чужое, пустые тела. Ключей и пропусков в
 * сообщениях нет — их прячут `toString` сетевых форм.
 *
 * Включается только `-Pprobe`, иначе пропускается. Пропуск выдаётся один раз на пользователя за
 * прогон: сервер считает выдачи с адреса.
 */
class ContractProbe {

    private class Fixed(private val account: AccountCredentials) : CredentialSource {
        override suspend fun read(): StoredAccount = StoredAccount.Present(account)
        override suspend fun save(credentials: AccountCredentials) =
            error("проба учёток не заводит: они заведены один раз и лежат в local.properties")
    }

    companion object {
        private lateinit var owner: MedAppApi
        private lateinit var guest: MedAppApi
        private lateinit var anonymous: MedAppApi
        private lateinit var ownerAccount: AccountCredentials
        private lateinit var unit: Uuid

        /** Почему проба не идёт; `null` — идёт. Пропуск виден в отчёте у каждого теста. */
        private var skipReason: String? = null

        @BeforeClass
        @JvmStatic
        fun connect() {
            val arguments = InstrumentationRegistry.getArguments()
            val baseUrl = arguments.getString("probeBaseUrl")
            val ownerCredentials = credentials(arguments, "A")
            val guestCredentials = credentials(arguments, "B")
            if (baseUrl.isNullOrBlank()) {
                skipReason = "проба контракта включается только -Pprobe"
                return
            }
            if (ownerCredentials == null || guestCredentials == null) {
                skipReason = "пробные пользователи не заведены: scripts/register-probe-users.sh"
                return
            }
            ownerAccount = ownerCredentials
            owner = api(baseUrl, ownerCredentials)
            guest = api(baseUrl, guestCredentials)
            anonymous = api(baseUrl, account = null)
            unit = runBlocking { success(owner.quantityUnits()).first().id }
        }

        private fun credentials(arguments: Bundle, user: String): AccountCredentials? {
            val login = arguments.getString("probeLogin$user")
            val key = arguments.getString("probeKey$user")
            if (login.isNullOrBlank() || key.isNullOrBlank()) return null
            return AccountCredentials(Uuid.parse(login), key)
        }

        private fun api(baseUrl: String, account: AccountCredentials?) = MedAppApi(
            medAppHttpClient(
                OkHttp.create(),
                baseUrl,
                tokens = account?.let { AccessTokens(Fixed(it)) }
            )
        )

        /** Успех, в том числе с `null` — пачка кончилась и уничтожена; отказ — провал пробы. */
        private fun <T> success(result: ApiResult<T>): T = when (result) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> throw AssertionError("ожидался успех: $result")
        }
    }

    private val kits = mutableListOf<Uuid>()

    @Before
    fun requireProbe() {
        assumeTrue(skipReason.orEmpty(), skipReason == null)
    }

    @After
    fun removeSyntheticKits() = runBlocking {
        for (kit in kits) owner.deleteMedKit(kit)
    }

    private fun failure(result: ApiResult<*>): ApiFailure =
        (result as? ApiResult.Failure)?.failure ?: throw AssertionError("ожидался отказ: $result")

    private suspend fun newKit(): Uuid {
        val id = Uuid.random()
        success(owner.createMedKit(MedKitPostNetworkDTO(id)))
        kits += id
        return id
    }

    private fun packagePost(amount: String = "10") = PackagePostNetworkDTO(
        id = Uuid.random(),
        name = "Проба контракта",
        amount = amount,
        unitId = unit,
        formId = null,
        category = null,
        manufacturer = null,
        country = null,
        description = "синтетическая пачка пробы контракта"
    )

    private suspend fun newPackage(kit: Uuid, amount: String = "10"): PackageSnapshotNetworkDTO =
        success(owner.createPackage(kit, packagePost(amount)))

    @Test
    fun foreignRegistrationTokenIsRefusedWithoutAnAccount() = runBlocking {
        assertEquals(ApiFailure.RegistrationRefused, failure(anonymous.register("not-the-build-token")))
    }

    @Test
    fun wrongKeyIsNotAccepted() = runBlocking {
        val wrong = AccountCredentials(ownerAccount.login, "not-the-key")
        assertEquals(ApiFailure.Unauthorized, failure(anonymous.token(wrong)))
    }

    @Test
    fun whatTheAccountSeesIsReadable() = runBlocking {
        assertTrue(success(owner.quantityUnits()).isNotEmpty())
        success(owner.formTypes())
        success(owner.snapshot())
        success(owner.medKits())
        success(owner.claims())
        success(owner.searchTemplates("аспирин", 5))
        Unit
    }

    @Test
    fun creationByClientIdentifierIsNotRepeated() = runBlocking {
        val kit = newKit()
        assertEquals(ApiFailure.Conflict, failure(owner.createMedKit(MedKitPostNetworkDTO(kit))))

        val post = packagePost()
        success(owner.createPackage(kit, post))
        assertEquals(ApiFailure.Conflict, failure(owner.createPackage(kit, post)))
    }

    @Test
    fun commandsActOnlyOnTheVersionTheyName() = runBlocking {
        val pack = newPackage(newKit()).pack
        val edit = PackagePatchNetworkDTO(description = "правка пробы")

        assertEquals(ApiFailure.PreconditionRequired, failure(owner.patchPackage(pack.id, edit)))
        val stale = edit.copy(version = ResourceVersion(pack.version.number + 1))
        assertEquals(ApiFailure.PreconditionFailed, failure(owner.patchPackage(pack.id, stale)))

        val patched = success(owner.patchPackage(pack.id, edit.copy(version = pack.version))).pack
        assertEquals("правка пробы", patched.description)
        assertTrue(patched.version > pack.version)
    }

    @Test
    fun strangerCannotTellAForeignKitFromNothing() = runBlocking {
        val kit = newKit()
        val pack = newPackage(kit).pack

        assertEquals(ApiFailure.NotFound, failure(guest.packageSnapshot(pack.id)))
        assertEquals(ApiFailure.NotFound, failure(guest.medKit(kit)))
    }

    @Test
    fun invitationLetsTheSecondUserInOnce() = runBlocking {
        val kit = newKit()
        newPackage(kit)
        val invitation = success(owner.createInvitation(kit))

        val joined = success(guest.joinMedKit(MembershipPostNetworkDTO(invitation.key)))
        assertEquals(2L, joined.participantCount)
        assertEquals(1, joined.packages.size)
        assertEquals(ApiFailure.Conflict, failure(guest.joinMedKit(MembershipPostNetworkDTO(invitation.key))))
        assertEquals(Unit, success(guest.leaveMedKit(kit)))
    }

    @Test
    fun claimIsDeclaredOnceThenChangedAndRemoved() = runBlocking {
        val snapshot = newPackage(newKit())
        val packageId = snapshot.pack.id

        success(owner.createClaim(ClaimPostNetworkDTO(packageId, "3", snapshot.claims.version)))
        val declared = success(owner.packageSnapshot(packageId)).claims
        assertEquals("3.000000", declared.mine)
        assertEquals(
            ApiFailure.Conflict,
            failure(owner.createClaim(ClaimPostNetworkDTO(packageId, "3", declared.version)))
        )

        success(owner.patchClaim(packageId, ClaimPatchNetworkDTO("2", declared.version)))
        val changed = success(owner.packageSnapshot(packageId)).claims
        assertEquals("2.000000", changed.mine)
        assertEquals(Unit, success(owner.deleteClaim(packageId, changed.version)))
        assertNull(success(owner.packageSnapshot(packageId)).claims.mine)
    }

    @Test
    fun consumptionAnswersWithTheSnapshotUntilThePackageIsGone() = runBlocking {
        val pack = newPackage(newKit(), amount = "10").pack

        val left = requireNotNull(success(owner.consume(pack.id, PackageConsumeNetworkDTO("4", pack.version)))) {
            "после частичного расхода пачка остаётся"
        }.pack
        assertEquals("6.000000", left.amount)
        assertNull(success(owner.consume(pack.id, PackageConsumeNetworkDTO("6", left.version))))
    }

    @Test
    fun offlineChangesAreAppliedOnceUnderTheirSyncId() = runBlocking {
        val pack = newPackage(newKit(), amount = "10").pack
        val syncId = Uuid.random()
        val changes = PackageSyncNetworkDTO(consumed = "1", packageVersion = pack.version)

        success(owner.synchronise(pack.id, syncId, changes))
        val repeated = success(owner.synchronise(pack.id, syncId, changes))
        assertEquals("9.000000", repeated?.pack?.amount)
        assertEquals(
            ApiFailure.Conflict,
            failure(owner.synchronise(pack.id, syncId, PackageSyncNetworkDTO("2", pack.version)))
        )
    }

    @Test
    fun packageMovesToAnotherKitOfTheOwner() = runBlocking {
        val target = newKit()
        val pack = newPackage(newKit()).pack

        assertEquals(target, success(owner.movePackage(pack.id, target, pack.version)).pack.medKitId)
    }

    @Test
    fun removalAnswersWithNoContent() = runBlocking {
        val pack = newPackage(newKit()).pack

        assertEquals(Unit, success(owner.deletePackage(pack.id, pack.version)))
        assertEquals(ApiFailure.NotFound, failure(owner.packageSnapshot(pack.id)))
    }
}
