package com.kert0n.medapp.app.navigation

import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * В маршруте едут только идентификаторы, дата и режим — ни объектов, ни ключей приглашения
 * (PLAN H3, G3). Маршрут переживает смерть процесса: объект в нём к моменту восстановления был бы
 * устаревшей копией того, что лежит в базе, а ключ приглашения оказался бы в логах навигации.
 *
 * Проверяется описанием сериализации, а не глазами: новое поле маршрута попадает сюда само.
 *
 * Красная проверка: дать любому маршруту поле доменного типа или поле с «key» в имени — тест
 * краснеет с именем этого поля.
 */
class RouteTest {

    /** Что разрешено возить: примитивы, из которых собираются идентификатор, дата и режим. */
    private val allowedKinds = setOf("STRING", "INT", "LONG", "BOOLEAN", "ENUM")

    private val routes = listOf(
        serializer<Route.MedKits>(),
        serializer<Route.Plan>(),
        serializer<Route.Scanner>(),
        serializer<Route.Analytics>(),
        serializer<Route.Settings>(),
        serializer<Route.MedKitForm>()
    )

    @Test
    fun routesCarryOnlyPlainIdentifiers() {
        for (route in routes) {
            val descriptor = route.descriptor
            for (index in 0 until descriptor.elementsCount) {
                val field = descriptor.getElementName(index)
                val kind = descriptor.getElementDescriptor(index).kind.toString().substringAfterLast('.')
                assertTrue(
                    "${descriptor.serialName}.$field везёт $kind — в маршруте едут идентификаторы",
                    kind in allowedKinds
                )
            }
        }
    }

    /** Ключ приглашения в маршруте оказался бы в логах навигации (PLAN G3). */
    @Test
    fun noRouteCarriesAnInvitationKey() {
        for (route in routes) {
            val descriptor = route.descriptor
            for (index in 0 until descriptor.elementsCount) {
                val field = descriptor.getElementName(index).lowercase()
                assertTrue("${descriptor.serialName}.$field возит ключ", "key" !in field)
            }
        }
    }

    /** Пять мест нижней навигации, и каждое названо своим маршрутом. */
    @Test
    fun everyDestinationHasItsOwnRoute() {
        val routesOfTabs = Destination.entries.map { it.route }

        assertEquals(routesOfTabs.size, routesOfTabs.toSet().size)
        assertEquals(5, routesOfTabs.size)
    }
}
