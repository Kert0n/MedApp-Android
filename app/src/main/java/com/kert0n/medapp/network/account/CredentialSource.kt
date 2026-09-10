package com.kert0n.medapp.network.account

/**
 * Что сети нужно от хранилища учётки: прочитать её и сохранить выданную. Хранит её платформа
 * (`platform/credentials`), и сеть знает о хранилище только этот порт.
 */
interface CredentialSource {

    suspend fun read(): StoredAccount

    /** Записать выданную учётку. Исход, а не исключение: ключ выдан один раз (PLAN G2). */
    suspend fun save(credentials: AccountCredentials): CredentialsSaved
}
