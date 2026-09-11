package com.kert0n.medapp.queue

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Подготовленный запрос заморожен до первой отправки: повтор уходит с теми же параметрами,
 * какими он был собран (PLAN E2, E3).
 */
class PreparedRequestTest {

    private fun request(query: Map<String, String>) = PreparedRequest(
        method = "PUT",
        path = "/v1/drugs/00000000-0000-4000-8000-000000000011/sync/00000000-0000-4000-8000-000000000051",
        query = query,
        preparedAt = Instant.parse("2026-09-10T09:00:00Z")
    )

    @Test
    fun changingTheSourceMapDoesNotChangeThePreparedRequest() {
        val parameters = mutableMapOf("quantity" to "3")
        val prepared = request(parameters)

        parameters["quantity"] = "7"
        parameters["version"] = "9"

        assertEquals(mapOf("quantity" to "3"), prepared.query)
    }

    @Test
    fun twoRequestsWithTheSameParametersAreTheSameRequest() {
        assertEquals(request(mapOf("quantity" to "3")), request(mutableMapOf("quantity" to "3")))
        assertEquals(
            request(mapOf("quantity" to "3")).hashCode(),
            request(mutableMapOf("quantity" to "3")).hashCode()
        )
    }
}
