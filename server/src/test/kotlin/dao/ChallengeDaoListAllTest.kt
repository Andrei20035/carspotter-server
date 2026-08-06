package dao

import com.revio.server.features.car_family.CarFamilyTable
import com.revio.server.features.challenge.ChallengeDAO
import com.revio.server.features.challenge.ChallengeStatus
import com.revio.server.features.challenge.ChallengeTable
import com.revio.server.features.challenge.EffectiveChallengeStatus
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import testutils.TestDatabaseFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * IChallengeDAO.listAll: keyset pagination (createdAt DESC, id DESC) and the SQL-pushed-down
 * effective-status filter (plan §9-E5). The filter is pushed to SQL specifically so it composes
 * correctly with the cursor — see the DAO's KDoc for why a post-fetch Kotlin filter would be
 * wrong here (it could return fewer than `limit` rows on a page while more matching rows exist
 * beyond the cursor).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChallengeDaoListAllTest {

    private val dao = ChallengeDAO()

    @BeforeAll
    fun setup() = TestDatabaseFactory.start()

    @AfterAll
    fun tearDown() = TestDatabaseFactory.stop()

    @BeforeEach
    fun clean() = TestDatabaseFactory.cleanDatabase()

    private fun seedFamily(name: String = "Golf"): UUID = transaction {
        CarFamilyTable.insert {
            it[CarFamilyTable.brand] = "volkswagen"
            it[CarFamilyTable.name] = name
        }[CarFamilyTable.id].value
    }

    /**
     * Inserts a challenge directly (bypassing ChallengeService) with an explicit createdAt, so
     * tests can control insertion order precisely instead of relying on wall-clock timing.
     */
    private fun seedChallenge(
        familyId: UUID,
        title: String,
        status: ChallengeStatus,
        startsAt: Instant,
        endsAt: Instant,
        createdAt: Instant,
    ): UUID = transaction {
        val id = ChallengeTable.insert {
            it[ChallengeTable.title] = title
            it[ChallengeTable.targetFamilyId] = familyId
            it[ChallengeTable.requiredPosts] = 5
            it[ChallengeTable.rewardPoints] = 300
            it[ChallengeTable.startsAt] = startsAt.atOffset(java.time.ZoneOffset.UTC)
            it[ChallengeTable.endsAt] = endsAt.atOffset(java.time.ZoneOffset.UTC)
            it[ChallengeTable.adminTimezone] = "Europe/Bucharest"
            it[ChallengeTable.status] = status
        }[ChallengeTable.id].value

        // createdAt has a DB default; overwrite it so tests control ordering deterministically.
        ChallengeTable.update({ ChallengeTable.id eq id }) {
            it[ChallengeTable.createdAt] = createdAt.atOffset(java.time.ZoneOffset.UTC)
        }
        id
    }

    @Test
    fun `listAll returns challenges newest-created first`() = runTest {
        val familyId = seedFamily()
        val now = Instant.now()
        val oldest = seedChallenge(familyId, "Oldest", ChallengeStatus.DRAFT, now, now.plusSeconds(3600), now.minus(3, ChronoUnit.DAYS))
        val middle = seedChallenge(familyId, "Middle", ChallengeStatus.DRAFT, now, now.plusSeconds(3600), now.minus(2, ChronoUnit.DAYS))
        val newest = seedChallenge(familyId, "Newest", ChallengeStatus.DRAFT, now, now.plusSeconds(3600), now.minus(1, ChronoUnit.DAYS))

        val page = dao.listAll(limit = 10, cursorCreatedAt = null, cursorId = null, effectiveStatusFilter = null, now = now)

        assertEquals(listOf(newest, middle, oldest), page.map { it.id })
    }

    @Test
    fun `listAll paginates with a stable cursor across pages`() = runTest {
        val familyId = seedFamily()
        val now = Instant.now()
        val ids = (1..5).map {
            seedChallenge(familyId, "Challenge $it", ChallengeStatus.DRAFT, now, now.plusSeconds(3600), now.minus((10 - it).toLong(), ChronoUnit.DAYS))
        }
        // ids[4] was created most recently (10-5=5 days ago is the smallest offset)... compute
        // expected newest-first order directly from insertion metadata instead of assuming it.
        val expectedOrder = transaction {
            ChallengeTable.selectAll()
                .orderBy(ChallengeTable.createdAt to SortOrder.DESC, ChallengeTable.id to SortOrder.DESC)
                .map { it[ChallengeTable.id].value }
        }
        assertEquals(5, expectedOrder.size)

        val firstPage = dao.listAll(limit = 2, cursorCreatedAt = null, cursorId = null, effectiveStatusFilter = null, now = now)
        assertEquals(expectedOrder.take(2), firstPage.map { it.id })

        val last = firstPage.last()
        val secondPage = dao.listAll(limit = 2, cursorCreatedAt = last.createdAt, cursorId = last.id, effectiveStatusFilter = null, now = now)
        assertEquals(expectedOrder.drop(2).take(2), secondPage.map { it.id })

        val secondLast = secondPage.last()
        val thirdPage = dao.listAll(limit = 2, cursorCreatedAt = secondLast.createdAt, cursorId = secondLast.id, effectiveStatusFilter = null, now = now)
        assertEquals(expectedOrder.drop(4), thirdPage.map { it.id })
        assertTrue(thirdPage.size == 1)

        // No overlap and no gaps across the three pages.
        val allSeen = (firstPage + secondPage + thirdPage).map { it.id }
        assertEquals(expectedOrder, allSeen)
    }

    @Test
    fun `listAll filters by effective status, distinguishing SCHEDULED, ACTIVE and ENDED`() = runTest {
        val familyId = seedFamily()
        val now = Instant.now()

        val draft = seedChallenge(familyId, "Draft", ChallengeStatus.DRAFT, now, now.plusSeconds(3600), now)
        val cancelled = seedChallenge(familyId, "Cancelled", ChallengeStatus.CANCELLED, now, now.plusSeconds(3600), now)
        val notYetStarted = seedChallenge(
            familyId, "Not started", ChallengeStatus.SCHEDULED,
            now.plus(1, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS), now,
        )
        val active = seedChallenge(
            familyId, "Active", ChallengeStatus.SCHEDULED,
            now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS), now,
        )
        val ended = seedChallenge(
            familyId, "Ended", ChallengeStatus.SCHEDULED,
            now.minus(2, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS), now,
        )

        suspend fun idsFor(status: EffectiveChallengeStatus) =
            dao.listAll(limit = 10, cursorCreatedAt = null, cursorId = null, effectiveStatusFilter = status, now = now).map { it.id }.toSet()

        assertEquals(setOf(draft), idsFor(EffectiveChallengeStatus.DRAFT))
        assertEquals(setOf(cancelled), idsFor(EffectiveChallengeStatus.CANCELLED))
        assertEquals(setOf(notYetStarted), idsFor(EffectiveChallengeStatus.SCHEDULED))
        assertEquals(setOf(active), idsFor(EffectiveChallengeStatus.ACTIVE))
        assertEquals(setOf(ended), idsFor(EffectiveChallengeStatus.ENDED))
    }

    @Test
    fun `listAll status filter composes correctly with the cursor`() = runTest {
        val familyId = seedFamily()
        val now = Instant.now()

        // Three DRAFT and two CANCELLED, interleaved by createdAt so a naive post-fetch filter
        // would shrink a page instead of the SQL-level filter keeping it full.
        val d1 = seedChallenge(familyId, "D1", ChallengeStatus.DRAFT, now, now.plusSeconds(3600), now.minus(5, ChronoUnit.DAYS))
        seedChallenge(familyId, "C1", ChallengeStatus.CANCELLED, now, now.plusSeconds(3600), now.minus(4, ChronoUnit.DAYS))
        val d2 = seedChallenge(familyId, "D2", ChallengeStatus.DRAFT, now, now.plusSeconds(3600), now.minus(3, ChronoUnit.DAYS))
        seedChallenge(familyId, "C2", ChallengeStatus.CANCELLED, now, now.plusSeconds(3600), now.minus(2, ChronoUnit.DAYS))
        val d3 = seedChallenge(familyId, "D3", ChallengeStatus.DRAFT, now, now.plusSeconds(3600), now.minus(1, ChronoUnit.DAYS))

        val firstPage = dao.listAll(limit = 2, cursorCreatedAt = null, cursorId = null, effectiveStatusFilter = EffectiveChallengeStatus.DRAFT, now = now)
        assertEquals(2, firstPage.size, "A full page of 2 DRAFT challenges must come back, not fewer")
        assertEquals(listOf(d3, d2), firstPage.map { it.id })

        val last = firstPage.last()
        val secondPage = dao.listAll(limit = 2, cursorCreatedAt = last.createdAt, cursorId = last.id, effectiveStatusFilter = EffectiveChallengeStatus.DRAFT, now = now)
        assertEquals(listOf(d1), secondPage.map { it.id })
    }

    @Test
    fun `listAll returns empty for a nonexistent status match and respects limit`() = runTest {
        val familyId = seedFamily()
        val now = Instant.now()
        seedChallenge(familyId, "Only draft", ChallengeStatus.DRAFT, now, now.plusSeconds(3600), now)

        val page = dao.listAll(limit = 10, cursorCreatedAt = null, cursorId = null, effectiveStatusFilter = EffectiveChallengeStatus.ACTIVE, now = now)
        assertTrue(page.isEmpty())
    }
}
