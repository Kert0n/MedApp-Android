package com.kert0n.medapp.network.account

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Пропуск MedApp: живёт только в памяти процесса и выдаётся по учётке (PLAN B1, G2).
 *
 * Выдача одна на всех: запросы, одновременно получившие 401 со старым пропуском, ждут одну
 * выдачу и берут её результат, а не просят каждый свою — иначе лимит выдачи по адресу сгорел бы
 * на одном всплеске. `null` значит, что пропуска нет и взять его не по чему: учётки нет, она
 * нечитаема или сервер её не принял.
 */
@Singleton
class AccessTokens @Inject constructor(private val credentials: CredentialSource) {

    private val mutex = Mutex()

    @Volatile
    private var token: String? = null

    val current: String? get() = token

    /**
     * Новый пропуск вместо [stale]. Если его уже заменил другой запрос, возвращается замена, и
     * второй выдачи не происходит.
     */
    suspend fun renew(stale: String?, issue: suspend (AccountCredentials) -> String?): String? =
        mutex.withLock {
            token?.takeIf { it != stale }?.let { return@withLock it }
            val account = (credentials.read() as? StoredAccount.Present)?.credentials
                ?: return@withLock null
            issue(account).also { token = it }
        }
}
