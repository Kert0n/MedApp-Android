package com.kert0n.medapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Параметры сборки приходят из local.properties или из окружения CI. Проверяется не
 * значение — оно у каждой сборки своё, — а то, что подстановка вообще произошла и
 * приложению есть с чем идти к серверу (G1).
 */
class BuildConfigTest {

    @Test
    fun registrationTokenIsPresent() {
        assertFalse(BuildConfig.REGISTRATION_TOKEN.isBlank())
    }

    @Test
    fun serverAddressesAreHttps() {
        assertTrue(BuildConfig.BASE_URL.startsWith("https://"))
        assertTrue(BuildConfig.CRPT_BASE_URL.startsWith("https://"))
    }
}
