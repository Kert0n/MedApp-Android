package com.kert0n.medapp.domain.medkit

import com.kert0n.medapp.fixture.HOME_KIT

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedKitTest {

    @Test
    fun describingKeepsPublicationAndClearsLocation() {
        val original = kit(publication = KitPublication.PUBLISHED, participants = 3)
        val edited = original.describe(name = "Дачная", location = null)
        assertEquals("Дачная", edited.name)
        assertNull(edited.location)
        assertEquals(original.id, edited.id)
        assertEquals(original.publication, edited.publication)
        assertEquals(original.participantCount, edited.participantCount)
        assertEquals(original.createdAt, edited.createdAt)
        assertEquals("Домашняя", original.name)
    }

    @Test(expected = IllegalArgumentException::class)
    fun describingRejectsBlankName() {
        kit().describe(" ", null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun describingRejectsBlankLocation() {
        kit().describe("Домашняя", " ")
    }

    @Test
    fun publishingKitDoesNotHandOutInvitationsYet() {
        // Половина пачек уже на сервере, половина ещё нет: приглашённый увидел бы половину.
        val publishing = kit(publication = KitPublication.PUBLISHING, participants = 1)
        assertFalse(publishing.acceptsInvitations)
    }

    @Test
    fun kitLeftByEveryoneElseStaysOnTheServer() {
        // Там лежат мои пачки, и локальной она уже не станет: PUBLISHED не выводится из числа
        // участников, а хранится отдельно.
        val alone = kit(publication = KitPublication.PUBLISHED, participants = 1)
        assertFalse(alone.isShared)
        assertTrue(alone.acceptsInvitations)
    }

    @Test
    fun sharedKitIsTheOneWithOtherParticipants() {
        assertTrue(kit(publication = KitPublication.PUBLISHED, participants = 2).isShared)
    }

    @Test(expected = IllegalArgumentException::class)
    fun localKitCannotHaveOtherParticipants() {
        kit(publication = KitPublication.LOCAL, participants = 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankNameIsRejected() {
        kit(name = "   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyLocationIsNotAWayToSayThereIsNone() {
        kit(location = "")
    }

    @Test
    fun absentLocationIsNull() {
        assertTrue(kit(location = null).location == null)
    }

    @Test
    fun nameFillingTheLimitFits() {
        kit(name = "я".repeat(MedKit.NAME_MAX_LENGTH))
    }

    @Test(expected = IllegalArgumentException::class)
    fun nameOverTheLimitIsRejected() {
        kit(name = "я".repeat(MedKit.NAME_MAX_LENGTH + 1))
    }

    private fun kit(
        name: String = "Домашняя",
        location: String? = "верхняя полка",
        publication: KitPublication = KitPublication.LOCAL,
        participants: Long = 1
    ) = MedKit(
        id = HOME_KIT,
        name = name,
        location = location,
        publication = publication,
        participantCount = participants,
        createdAt = Instant.EPOCH
    )
}
