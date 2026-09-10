package com.kert0n.medapp.data.sync.pack

import com.kert0n.medapp.domain.model.course.CourseBrief
import com.kert0n.medapp.domain.model.pack.ClaimOwnership
import com.kert0n.medapp.fixture.COURSE
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.tablets
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Чем объяснена моя бронь: назначением, локальным снятием — или ничем. Ожидающая команда очереди
 * здесь один из источников смысла, а не сам смысл.
 */
class ClaimOwnershipProjectionTest {

    private val course = CourseBrief(COURSE, "Курс", allocatedDoses = 7)

    @Test
    fun assignmentExplainsTheClaim() {
        assertEquals(ClaimOwnership.AssignedTo(course), claimOwnership(course))
    }

    @Test
    fun pendingReleaseOutweighsTheAssignment() {
        // Курс уже отменён, снятие ещё не уехало: бронь вот-вот освободится.
        assertEquals(
            ClaimOwnership.ReleasedLocally,
            claimOwnership(course, listOf(PackageSyncCommand.ReleaseClaim(PACK)))
        )
    }

    @Test
    fun claimWithoutAnAssignmentHasNoKnownOwner() {
        assertEquals(ClaimOwnership.NoKnownOwner, claimOwnership(assignedCourse = null))
    }

    @Test
    fun pendingClaimCommandDoesNotInventAnOwner() {
        // Бронь, за которой не стоит ни один курс, — это и есть неизвестное назначение. Прятать
        // его фактом «что-то отправляется» значило бы не показать человеку то, что он разбирает.
        assertEquals(
            ClaimOwnership.NoKnownOwner,
            claimOwnership(assignedCourse = null, unclosed = listOf(PackageSyncCommand.SetClaim(PACK, tablets("10"))))
        )
    }
}
