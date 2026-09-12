package com.kert0n.medapp.queue

import com.kert0n.medapp.fixture.HOME_KIT
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.network.server.ApiFailure
import com.kert0n.medapp.network.server.ApiResult
import com.kert0n.medapp.network.server.RawResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Форму ответа проверяет разбор по тому, что ждала команда: пустое тело там, где обещан снимок,
 * и HTML вместо JSON — сбой протокола, а не «пачки нет» и не исключение (PLAN B5). Разбор
 * чистый — записанный ответ разбирается так же, как свежий.
 */
class ExpectedTest {

    private val snapshotJson = """
        {"drug":{"id":"$PACK","name":"Парацетамол","quantity":"17.000000","quantityUnitId":"${TABLETS.id}",
         "medKitId":"$HOME_KIT","version":4},
         "reservations":{"total":"0.000000","version":2}}
    """

    @Test
    fun snapshotIsReadWhereItIsExpected() {
        val answer = Expected.SNAPSHOT.read(RawResponse(201, snapshotJson))
        assertTrue("$answer", answer is ApiResult.Success && answer.value is QueueAnswer.Snapshot)
    }

    @Test
    fun emptyBodyWhereASnapshotIsPromisedIsAProtocolFailureNotAnEmptyPack() {
        val answer = Expected.SNAPSHOT.read(RawResponse(201, ""))
        assertTrue("$answer", answer is ApiResult.Failure && answer.failure is ApiFailure.Protocol)
    }

    @Test
    fun emptyBodyIsGoneOnlyWhereTheCommandExpectsIt() {
        assertEquals(ApiResult.Success(QueueAnswer.Gone), Expected.SNAPSHOT_OR_GONE.read(RawResponse(200, "")))
        assertEquals(ApiResult.Success(QueueAnswer.Nothing), Expected.NOTHING.read(RawResponse(204, "")))
    }

    @Test
    fun htmlOnASuccessfulStatusIsAProtocolFailureNotAnException() {
        val answer = Expected.SNAPSHOT.read(RawResponse(200, "<html>прокси</html>"))
        assertTrue("$answer", answer is ApiResult.Failure && answer.failure is ApiFailure.Protocol)
    }

    @Test
    fun claimIsReadWhereItIsExpected() {
        val answer = Expected.CLAIM.read(RawResponse(201, """{"drugId":"$PACK","amount":"6.000000"}"""))
        assertTrue("$answer", answer is ApiResult.Success && answer.value is QueueAnswer.Claim)
    }
}
