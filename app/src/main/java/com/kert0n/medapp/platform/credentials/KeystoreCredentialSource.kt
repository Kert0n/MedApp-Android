package com.kert0n.medapp.platform.credentials

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kert0n.medapp.di.CredentialsStore
import com.kert0n.medapp.di.IoDispatcher
import com.kert0n.medapp.network.account.AccountCredentials
import com.kert0n.medapp.network.account.CredentialSource
import com.kert0n.medapp.network.account.StoredAccount
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.util.Base64
import javax.inject.Inject
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Учётка на устройстве (PLAN G2): логин открыто, ключ — шифротекстом с вектором инициализации в
 * DataStore, а ключ расшифровки — в AndroidKeyStore. Каталог DataStore исключён из облачной
 * копии и переноса, поэтому шифротекст без своего ключа никуда не уезжает.
 *
 * Сохранённое, которое не открывается, — это [StoredAccount.Unreadable], а не отсутствие учётки:
 * сброс хранилища не должен выглядеть приглашением зарегистрироваться заново. Повреждённый файл
 * DataStore попадает в тот же случай, и обработчика, который молча заменил бы его пустым, здесь
 * нет: пустой файл — это «учётки нет», то есть приглашение завести вторую поверх локальных данных.
 */
class KeystoreCredentialSource @Inject constructor(
    @CredentialsStore private val store: DataStore<Preferences>,
    private val key: KeystoreKey,
    @IoDispatcher private val io: CoroutineDispatcher
) : CredentialSource {

    override suspend fun read(): StoredAccount = withContext(io) {
        try {
            val saved = store.data.first()
            val login = saved[LOGIN] ?: return@withContext StoredAccount.Absent
            val iv = saved[KEY_IV]
            val ciphertext = saved[KEY_CIPHERTEXT]
            if (iv == null || ciphertext == null) return@withContext StoredAccount.Unreadable
            val plain = key.open(
                KeystoreKey.Sealed(decode(iv), decode(ciphertext)),
                associated = login.encodeToByteArray()
            )
            StoredAccount.Present(AccountCredentials(Uuid.parse(login), plain.decodeToString()))
        } catch (_: IOException) {
            StoredAccount.Unreadable
        } catch (_: GeneralSecurityException) {
            StoredAccount.Unreadable
        } catch (_: ProviderException) {
            StoredAccount.Unreadable
        } catch (_: IllegalArgumentException) {
            StoredAccount.Unreadable
        }
    }

    override suspend fun save(credentials: AccountCredentials): Unit = withContext(io) {
        val login = credentials.login.toString()
        val sealed = key.seal(credentials.key.encodeToByteArray(), associated = login.encodeToByteArray())
        store.edit {
            it[LOGIN] = login
            it[KEY_IV] = encode(sealed.iv)
            it[KEY_CIPHERTEXT] = encode(sealed.ciphertext)
        }
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)

    private companion object {
        val LOGIN = stringPreferencesKey("login")
        val KEY_IV = stringPreferencesKey("key_iv")
        val KEY_CIPHERTEXT = stringPreferencesKey("key_ciphertext")
    }
}
