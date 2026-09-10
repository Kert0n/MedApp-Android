package com.kert0n.medapp.storage.server

import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.inMemoryDatabase
import com.kert0n.medapp.storage.database.MedAppDatabase
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Показанное уведомление не показывается второй раз: без журнала ежедневная проверка сообщала
 * бы об одной просрочке каждый день (PLAN F1, D8).
 */
class NotificationLogDaoTest {

    private lateinit var database: MedAppDatabase
    private val log get() = database.notificationLog()

    private val shownAt: Instant = Instant.parse("2026-09-10T06:00:00Z")
    private val expiryKey = "EXPIRY_SOURCE_3D:$PACK@2027-03-31"

    @Before
    fun openDatabase() {
        database = inMemoryDatabase()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun shown(
        key: String = expiryKey,
        delivery: String = "SYSTEM",
        kind: String = "EXPIRY_SOURCE_3D",
        at: Instant = shownAt
    ) = NotificationLogStorageEntity(key = key, delivery = delivery, kind = kind, shownAt = at)

    @Test
    fun unknownNotificationHasNotBeenShown() = runTest {
        assertFalse(log.wasShown(expiryKey, "SYSTEM"))
    }

    @Test
    fun rememberedNotificationIsNotShownAgain() = runTest {
        log.remember(shown())
        assertTrue(log.wasShown(expiryKey, "SYSTEM"))
    }

    /** Первый показ побеждает: повтор не сдвигает момент, когда человек это увидел. */
    @Test
    fun repeatedDailyCheckKeepsTheFirstMoment() = runTest {
        log.remember(shown())
        val ignored = log.remember(shown(at = shownAt.plusSeconds(86_400)))

        assertEquals(-1L, ignored)
        assertEquals(shownAt, requireNotNull(log.find(expiryKey, "SYSTEM")).shownAt)
    }

    /** Баннер и системное уведомление — два разных показа: один не отменяет другой. */
    @Test
    fun bannerAndSystemNoticeAreCountedApart() = runTest {
        log.remember(shown(delivery = "SYSTEM"))

        assertFalse(log.wasShown(expiryKey, "IN_APP_BANNER"))
        log.remember(shown(delivery = "IN_APP_BANNER"))
        assertTrue(log.wasShown(expiryKey, "IN_APP_BANNER"))
        assertEquals(2, log.ofKind("EXPIRY_SOURCE_3D").size)
    }

    /** Два этапа предупреждения о годности — разные события, и второй не считается показанным. */
    @Test
    fun threeDayAndOneDayWarningsDoNotCollide() = runTest {
        log.remember(shown(kind = "EXPIRY_SOURCE_3D"))
        assertFalse(log.wasShown("EXPIRY_SOURCE_1D:$PACK@2027-03-31", "SYSTEM"))
    }

    @Test
    fun forgettingRemovesEveryDeliveryOfOneEvent() = runTest {
        log.remember(shown(delivery = "SYSTEM"))
        log.remember(shown(delivery = "IN_APP_BANNER"))

        log.forget(expiryKey)

        assertFalse(log.wasShown(expiryKey, "SYSTEM"))
        assertFalse(log.wasShown(expiryKey, "IN_APP_BANNER"))
    }

    @Test
    fun oldEntriesCanBeDroppedWithoutTouchingRecentOnes() = runTest {
        log.remember(shown(key = "old", at = shownAt))
        log.remember(shown(key = "fresh", at = shownAt.plusSeconds(3600)))

        log.forgetShownBefore(shownAt.plusSeconds(60))

        assertFalse(log.wasShown("old", "SYSTEM"))
        assertTrue(log.wasShown("fresh", "SYSTEM"))
    }
}
