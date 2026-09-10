package com.kert0n.medapp.network.account

/**
 * Что сети нужно от хранилища учётки: прочитать её и сохранить выданную. Хранит её платформа
 * (`platform/credentials`), и сеть знает о хранилище только этот порт.
 */
interface CredentialSource {

    suspend fun read(): StoredAccount

    suspend fun save(credentials: AccountCredentials)
}
