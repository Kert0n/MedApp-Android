package com.kert0n.medapp.platform.credentials

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.platform.app.InstrumentationRegistry
import com.kert0n.medapp.network.account.AccountCredentials
import com.kert0n.medapp.network.account.StoredAccount
import java.io.File
import java.security.KeyStore
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

/**
 * Ключ лежит шифротекстом, ключ расшифровки — в AndroidKeyStore, а сохранённое, которое не
 * открывается, — это «нечитаема», а не «учётки нет» (PLAN G2).
 */
class KeystoreCredentialSourceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val alias = "medapp.test.${Uuid.random()}"
    private val credentials = AccountCredentials(
        login = Uuid.parse("00000000-0000-4000-8000-000000000071"),
        key = "k3y-shown-only-once-43-characters-long-abcd"
    )

    private lateinit var scope: CoroutineScope
    private lateinit var file: File
    private lateinit var store: DataStore<Preferences>
    private lateinit var source: KeystoreCredentialSource

    @Before
    fun openStore() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        file = File(context.cacheDir, "$alias.preferences_pb")
        store = PreferenceDataStoreFactory.create(scope = scope) { file }
        source = KeystoreCredentialSource(store, KeystoreKey(alias), Dispatchers.IO)
    }

    @After
    fun closeStore() {
        scope.cancel()
        file.delete()
        keyStore().deleteEntry(alias)
    }

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private suspend fun raw(name: String): String? =
        store.data.first()[stringPreferencesKey(name)]

    @Test
    fun nothingSavedMeansNoAccount() = runTest {
        assertEquals(StoredAccount.Absent, source.read())
    }

    @Test
    fun savedAccountIsReadBack() = runTest {
        source.save(credentials)

        assertEquals(StoredAccount.Present(credentials), source.read())
    }

    @Test
    fun keyIsStoredOnlyAsCiphertext() = runTest {
        source.save(credentials)

        val stored = store.data.first().asMap().values.joinToString()
        assertFalse(stored.contains(credentials.key))
    }

    @Test
    fun everySaveGetsItsOwnInitialisationVector() = runTest {
        source.save(credentials)
        val first = raw("key_iv")
        source.save(credentials)

        assertNotEquals(first, raw("key_iv"))
    }

    /** Сброс хранилища — утрата учётки, о которой спрашивают, а не повод регистрироваться заново. */
    @Test
    fun lostKeystoreKeyMakesTheAccountUnreadableNotAbsent() = runTest {
        source.save(credentials)
        keyStore().deleteEntry(alias)

        assertEquals(StoredAccount.Unreadable, source.read())
    }

    @Test
    fun tamperedCiphertextIsUnreadable() = runTest {
        source.save(credentials)
        store.edit { it[stringPreferencesKey("key_ciphertext")] = "AAAAAAAAAAAAAAAAAAAAAA==" }

        assertEquals(StoredAccount.Unreadable, source.read())
    }

    /** Шифротекст привязан к своему логину: подставленный рядом чужой логин его не откроет. */
    @Test
    fun ciphertextDoesNotOpenUnderAnotherLogin() = runTest {
        source.save(credentials)
        store.edit { it[stringPreferencesKey("login")] = Uuid.random().toString() }

        assertEquals(StoredAccount.Unreadable, source.read())
    }
}
