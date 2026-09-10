package com.kert0n.medapp.platform.credentials

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import java.security.KeyStoreException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Ключ AES-256-GCM в AndroidKeyStore (PLAN G2). Материал ключа хранилище не покидает: наружу
 * выходят только шифротекст и вектор инициализации. StrongBox берётся, когда он есть, иначе —
 * обычное аппаратное хранилище. Открывать запечатанное без ключа нельзя: отсутствующий ключ не
 * создаётся заново при чтении, иначе утрата выглядела бы как чужой шифротекст.
 *
 * `associated` привязывает шифротекст к тому, чей он: подставленный рядом чужой логин не откроется.
 */
class KeystoreKey(private val alias: String) {

    class Sealed(val iv: ByteArray, val ciphertext: ByteArray)

    fun seal(plain: ByteArray, associated: ByteArray): Sealed {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, existingKey() ?: generate())
        cipher.updateAAD(associated)
        return Sealed(iv = cipher.iv, ciphertext = cipher.doFinal(plain))
    }

    /** @throws java.security.GeneralSecurityException ключа нет, он недействителен или шифротекст подменён */
    fun open(sealed: Sealed, associated: ByteArray): ByteArray {
        val key = existingKey() ?: throw KeyStoreException("ключа $alias в хранилище нет")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed.iv))
        cipher.updateAAD(associated)
        return cipher.doFinal(sealed.ciphertext)
    }

    private fun existingKey(): SecretKey? =
        (keyStore().getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey

    private fun generate(): SecretKey = try {
        generate(strongBox = true)
    } catch (_: StrongBoxUnavailableException) {
        generate(strongBox = false)
    }

    private fun generate(strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .setRandomizedEncryptionRequired(true)
            .setIsStrongBoxBacked(strongBox)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            .apply { init(spec) }
            .generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
    }
}
