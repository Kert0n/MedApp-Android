package com.kert0n.medapp.queue

import com.kert0n.medapp.fixture.EARLIER
import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.network.server.ApiFailure
import com.kert0n.medapp.network.server.ApiResult
import com.kert0n.medapp.network.server.MedAppApi
import com.kert0n.medapp.network.server.medAppHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Форму ответа проверяет транспорт по тому, что ждала команда: пустое тело там, где обещан
 * снимок, и HTML вместо JSON — сбой протокола, а не «пачки нет» и не исключение (PLAN B5).
 */
class QueueHttpTransportTest {

    private val snapshotJson = """
        {"drug":{"id":"$PACK","name":"Парацетамол","quantity":"17.000000","quantityUnitId":"${TABLETS.id}",
         "medKitId":"$HOME_KIT","version":4},
         "reservations":{"total":"0.000000","version":2}}
    """

    private val request = PreparedRequest("POST", "/v1/med-kits/$HOME_KIT/drugs", body = "{}", preparedAt = EARLIER)

    private fun transport(status: HttpStatusCode, body: String, contentType: String = "application/json") =
        QueueHttpTransport(
            MedAppApi(
                medAppHttpClient(
                    MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, contentType)) },
                    "https://medapp.test",
                    retryDelay = { delayMillis(false) { 0L } }
                )
            )
        )

    @Test
    fun snapshotIsReadWhereItIsExpected() = runTest {
        val answer = transport(HttpStatusCode.Created, snapshotJson).send(request, Expected.SNAPSHOT)
        assertTrue("$answer", answer is ApiResult.Success && answer.value is QueueAnswer.Snapshot)
    }

    @Test
    fun emptyBodyWhereASnapshotIsPromisedIsAProtocolFailureNotAnEmptyPack() = runTest {
        val answer = transport(HttpStatusCode.Created, "").send(request, Expected.SNAPSHOT)
        assertTrue("$answer", answer is ApiResult.Failure && answer.failure is ApiFailure.Protocol)
    }

    @Test
    fun emptyBodyIsGoneOnlyWhereTheCommandExpectsIt() = runTest {
        assertEquals(
            ApiResult.Success(QueueAnswer.Gone),
            transport(HttpStatusCode.OK, "").send(request, Expected.SNAPSHOT_OR_GONE)
        )
        assertEquals(
            ApiResult.Success(QueueAnswer.Nothing),
            transport(HttpStatusCode.NoContent, "").send(request, Expected.NOTHING)
        )
    }

    @Test
    fun htmlOnASuccessfulStatusIsAProtocolFailureNotAnException() = runTest {
        val answer = transport(HttpStatusCode.OK, "<html>прокси</html>", contentType = "text/html")
            .send(request, Expected.SNAPSHOT)
        assertTrue("$answer", answer is ApiResult.Failure && answer.failure is ApiFailure.Protocol)
    }
}
