package com.kert0n.medapp

import com.kert0n.medapp.data.remote.dto.DrugPatchRequest
import com.kert0n.medapp.data.remote.dto.DrugSyncRequest
import com.kert0n.medapp.data.remote.dto.ReservationSyncRequest
import com.kert0n.medapp.data.remote.dto.UserSnapshotDTO
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Провод разбирается ровно так, как отдаёт сервер.
 *
 * Разбор идёт **строгим** `Json`: неизвестное поле роняет тест. Иначе проверка была бы
 * односторонней — переименованное поле молча приезжало бы как `null`, и расхождение с контрактом
 * обнаружилось бы в приложении, а не здесь.
 *
 * Образец ответа списан со снимка пользователя: он вкладывает в себя все остальные ответы —
 * аптечку, упаковку, картину броней.
 */
class WireContractTest {

    private val strict = Json { ignoreUnknownKeys = false }

    @Test
    fun `снимок пользователя разбирается целиком`() {
        val snapshot = strict.decodeFromString<UserSnapshotDTO>(USER_SNAPSHOT)

        val medKit = snapshot.medKits.single()
        val drug = medKit.drugs.single()

        assertEquals("0199a3d1-0000-7000-8000-000000000001", snapshot.id)
        assertEquals(2L, medKit.userCount)
        assertEquals("Aspirin", drug.drug.name)
        // Хвостовые нули на месте: величина едет строкой, и разбор её не трогает.
        assertEquals("100.000000", drug.drug.quantity)
        assertEquals(3L, drug.drug.version)
        assertNull(drug.drug.description)
        assertEquals("40.000000", drug.reservations.total)
        assertEquals("20.000000", drug.reservations.mine)
        assertEquals(5L, drug.reservations.version)
    }

    /** Своей брони нет — поле не приезжает вовсе, а не приезжает нулём. */
    @Test
    fun `отсутствующая своя бронь разбирается как её отсутствие`() {
        val snapshot = strict.decodeFromString<UserSnapshotDTO>(
            USER_SNAPSHOT.replace("\"mine\": \"20.000000\",", "")
        )

        assertNull(snapshot.medKits.single().drugs.single().reservations.mine)
    }

    /**
     * В частичной правке `null` значит «не трогать», поэтому непереданное поле не должно попасть
     * в тело: сервер отличает отсутствующее поле от присланного `null`.
     */
    @Test
    fun `непереданные поля правки не уезжают на сервер`() {
        val body = Json.encodeToString(DrugPatchRequest(quantity = "120.0", version = 3))

        assertEquals("""{"quantity":"120.0","version":3}""", body)
    }

    /** Синхронизация без брони — только съеденное и версия пачки. */
    @Test
    fun `синхронизация несёт только то, что менялось`() {
        val consumedOnly = Json.encodeToString(DrugSyncRequest(consumed = "5.0", drugVersion = 3))
        assertEquals("""{"consumed":"5.0","drugVersion":3}""", consumedOnly)

        val withClaim = Json.encodeToString(
            DrugSyncRequest(drugVersion = 3, reservation = ReservationSyncRequest(amount = "20.0", version = 5))
        )
        assertEquals("""{"drugVersion":3,"reservation":{"amount":"20.0","version":5}}""", withClaim)
    }

    private companion object {
        val USER_SNAPSHOT = """
            {
              "id": "0199a3d1-0000-7000-8000-000000000001",
              "medKits": [
                {
                  "id": "0199a3d1-0000-7000-8000-000000000002",
                  "userCount": 2,
                  "drugs": [
                    {
                      "drug": {
                        "id": "0199a3d1-0000-7000-8000-000000000003",
                        "name": "Aspirin",
                        "quantity": "100.000000",
                        "quantityUnitId": "0199a3d1-0000-7000-8000-000000000004",
                        "formTypeId": null,
                        "category": "painkiller",
                        "manufacturer": "Bayer",
                        "country": "Germany",
                        "description": null,
                        "medKitId": "0199a3d1-0000-7000-8000-000000000002",
                        "version": 3
                      },
                      "reservations": {
                        "total": "40.000000",
                        "mine": "20.000000",
                        "version": 5
                      }
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
    }
}
