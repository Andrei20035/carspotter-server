package routes

import com.revio.server.features.auth.JwtService
import com.revio.server.features.auth.RefreshTokenGenerator
import com.revio.server.features.auth.session.AuthSessionDAO
import com.revio.server.features.auth.session.SessionScope
import com.revio.server.features.auth.session.SessionService
import com.revio.server.features.challenge.ChallengeParticipantTable
import com.revio.server.features.challenge.ChallengeStatus
import com.revio.server.features.challenge.RewardState
import com.revio.server.features.challenge.dto.ChallengeDTO
import com.revio.server.features.challenge.dto.MyChallengesDTO
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import testutils.ChallengeTestSeed
import testutils.CommentTestSeed
import testutils.TestDatabaseFactory
import testutils.TestEnv
import testutils.setTestEnv
import testutils.stopKoinSafely
import testutils.testChallengeModule
import java.time.Instant
import java.util.UUID

/**
 * HTTP contract of `GET /challenges/me` and `GET /challenges/{id}` (plan §7 Pas 6) — separate
 * file from ChallengeRoutesTest.kt (which already covers `/current` and `/{id}/progress`) so
 * neither file grows past a screenful of unrelated concerns. Same real DB/DAO/service stack via
 * [testutils.testChallengeModule].
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChallengeMeRoutesTest {

    private val jwt = JwtService(
        jwtSecret = TestEnv.JWT_SECRET,
        jwtIssuer = TestEnv.JWT_ISSUER,
        jwtAudience = TestEnv.JWT_AUDIENCE,
    )
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @BeforeAll
    fun setup() {
        setTestEnv()
        TestDatabaseFactory.start()
    }

    @AfterAll
    fun tearDown() = TestDatabaseFactory.stop()

    @BeforeEach
    fun clean() {
        TestDatabaseFactory.cleanDatabase()
        stopKoinSafely()
    }

    private fun userTest(block: suspend ApplicationTestBuilder.(HttpClient, String, UUID) -> Unit) = testApplication {
        application { testChallengeModule() }
        val client = createClient { install(ContentNegotiation) { json(json) } }
        val seeded = CommentTestSeed.seedUser()
        val (session) = SessionService(AuthSessionDAO(), RefreshTokenGenerator()).createSession(
            credentialId = seeded.authId,
            scope = SessionScope.FULL,
            userId = seeded.userId,
            deviceId = null,
            deviceName = null,
            userAgent = null,
            ip = null,
        )
        val token = jwt.generateAccessToken(session, seeded.authId, seeded.email, seeded.userId)
        block(client, token, seeded.userId)
    }

    private suspend fun tokenFor(userId: UUID, authId: UUID, email: String): String {
        val (session) = SessionService(AuthSessionDAO(), RefreshTokenGenerator()).createSession(
            credentialId = authId,
            scope = SessionScope.FULL,
            userId = userId,
            deviceId = null,
            deviceName = null,
            userAgent = null,
            ip = null,
        )
        return jwt.generateAccessToken(session, authId, email, userId)
    }

    /** Bare challenge_participants row — enough to make a challenge appear in the caller's history. */
    private fun seedParticipant(challengeId: UUID, userId: UUID, contributionCount: Int = 1, rewardState: RewardState = RewardState.NONE) = transaction {
        ChallengeParticipantTable.insert {
            it[ChallengeParticipantTable.challengeId] = challengeId
            it[ChallengeParticipantTable.userId] = userId
            it[ChallengeParticipantTable.contributionCount] = contributionCount
            it[ChallengeParticipantTable.rewardState] = rewardState
        }
    }

    // ---------- GET /challenges/me ----------

    @Test
    fun `GET challenges me returns 200 with summary and the first page for an authenticated user`() = userTest { client, token, userId ->
        val familyId = ChallengeTestSeed.seedFamily()
        val now = Instant.now()
        val challengeId = ChallengeTestSeed.seedChallenge(
            familyId = familyId, startsAt = now.minusSeconds(7200), endsAt = now.minusSeconds(3600), title = "Weekend Golf Hunt",
        )
        seedParticipant(challengeId, userId, contributionCount = 2, rewardState = RewardState.GRANTED)

        val response = client.get("/api/challenges/me") { header(HttpHeaders.Authorization, "Bearer $token") }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<MyChallengesDTO>()
        assertEquals(1, body.summary?.joinedCount)
        assertEquals(1, body.summary?.completedCount)
        assertEquals(1, body.challenges.size)
        assertEquals("Weekend Golf Hunt", body.challenges[0].challenge.title)
        assertEquals("ENDED", body.challenges[0].effectiveStatus)
        assertEquals(2, body.challenges[0].progress.contributionCount)
        assertEquals("GRANTED", body.challenges[0].progress.rewardState)
    }

    @Test
    fun `GET challenges me returns a null summary on a page fetched with a cursor`() = userTest { client, token, userId ->
        val familyId = ChallengeTestSeed.seedFamily()
        val now = Instant.now()
        val newer = ChallengeTestSeed.seedChallenge(familyId = familyId, startsAt = now.minusSeconds(3600), endsAt = now.minusSeconds(1800))
        val older = ChallengeTestSeed.seedChallenge(familyId = familyId, startsAt = now.minusSeconds(10 * 3600), endsAt = now.minusSeconds(9 * 3600))
        seedParticipant(newer, userId)
        seedParticipant(older, userId)

        val firstPage = client.get("/api/challenges/me?limit=1") { header(HttpHeaders.Authorization, "Bearer $token") }.body<MyChallengesDTO>()
        assertTrue(firstPage.hasMore)
        val cursorEndsAt = firstPage.nextCursorEndsAt!!
        val cursorId = firstPage.nextCursorId!!

        val secondPage = client.get("/api/challenges/me?limit=1&cursorEndsAt=$cursorEndsAt&cursorId=$cursorId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.body<MyChallengesDTO>()

        assertNull(secondPage.summary)
        assertEquals(listOf(older), secondPage.challenges.map { it.challenge.id })
    }

    @Test
    fun `GET challenges me without a token is unauthorized`() = testApplication {
        application { testChallengeModule() }
        val client = createClient { install(ContentNegotiation) { json(json) } }

        val response = client.get("/api/challenges/me")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET challenges me returns 400 when only cursorId is given`() = userTest { client, token, _ ->
        val response = client.get("/api/challenges/me?cursorId=${UUID.randomUUID()}") { header(HttpHeaders.Authorization, "Bearer $token") }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET challenges me returns 400 when only cursorEndsAt is given`() = userTest { client, token, _ ->
        val response = client.get("/api/challenges/me?cursorEndsAt=${Instant.now()}") { header(HttpHeaders.Authorization, "Bearer $token") }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET challenges me returns 400 for an invalid cursorId`() = userTest { client, token, _ ->
        val response = client.get("/api/challenges/me?cursorEndsAt=${Instant.now()}&cursorId=not-a-uuid") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET challenges me returns 400 for a malformed cursorEndsAt`() = userTest { client, token, _ ->
        val response = client.get("/api/challenges/me?cursorEndsAt=not-an-instant&cursorId=${UUID.randomUUID()}") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET challenges me returns 400 for a limit above the max`() = userTest { client, token, _ ->
        val response = client.get("/api/challenges/me?limit=51") { header(HttpHeaders.Authorization, "Bearer $token") }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET challenges me returns 200 with empty challenges and a zeroed summary for a user with no history, never 404`() = userTest { client, token, _ ->
        val response = client.get("/api/challenges/me") { header(HttpHeaders.Authorization, "Bearer $token") }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<MyChallengesDTO>()
        assertTrue(body.challenges.isEmpty())
        assertEquals(0, body.summary?.joinedCount)
        assertEquals(0, body.summary?.completedCount)
        assertEquals(0, body.summary?.pointsEarned)
        assertFalse(body.hasMore)
    }

    @Test
    fun `GET challenges me never returns another user's history`() = userTest { client, tokenA, userA ->
        val familyId = ChallengeTestSeed.seedFamily()
        val now = Instant.now()
        val challengeA = ChallengeTestSeed.seedChallenge(familyId = familyId, startsAt = now.minusSeconds(3600), endsAt = now.minusSeconds(1800))
        val challengeB = ChallengeTestSeed.seedChallenge(familyId = familyId, startsAt = now.minusSeconds(10 * 3600), endsAt = now.minusSeconds(9 * 3600))
        seedParticipant(challengeA, userA)
        val userB = CommentTestSeed.seedUser(username = "bob")
        seedParticipant(challengeB, userB.userId)
        val tokenB = tokenFor(userB.userId, userB.authId, userB.email)

        val responseA = client.get("/api/challenges/me") { header(HttpHeaders.Authorization, "Bearer $tokenA") }.body<MyChallengesDTO>()
        val responseB = client.get("/api/challenges/me") { header(HttpHeaders.Authorization, "Bearer $tokenB") }.body<MyChallengesDTO>()

        assertEquals(listOf(challengeA), responseA.challenges.map { it.challenge.id })
        assertEquals(listOf(challengeB), responseB.challenges.map { it.challenge.id })
    }

    @Test
    fun `GET challenges me orders challenges by endsAt descending`() = userTest { client, token, userId ->
        val familyId = ChallengeTestSeed.seedFamily()
        val now = Instant.now()
        val newest = ChallengeTestSeed.seedChallenge(familyId = familyId, startsAt = now.minusSeconds(3600), endsAt = now.minusSeconds(1800))
        val oldest = ChallengeTestSeed.seedChallenge(familyId = familyId, startsAt = now.minusSeconds(20 * 3600), endsAt = now.minusSeconds(19 * 3600))
        val middle = ChallengeTestSeed.seedChallenge(familyId = familyId, startsAt = now.minusSeconds(10 * 3600), endsAt = now.minusSeconds(9 * 3600))
        listOf(oldest, newest, middle).forEach { seedParticipant(it, userId) }

        val body = client.get("/api/challenges/me") { header(HttpHeaders.Authorization, "Bearer $token") }.body<MyChallengesDTO>()

        assertEquals(listOf(newest, middle, oldest), body.challenges.map { it.challenge.id })
    }

    @Test
    fun `GET challenges me resolves every distinct family correctly for a full page of 20 challenges`() = userTest { client, token, userId ->
        // Behavioral proof that the batched car-family lookup (Pas 3's getFamilies) is actually
        // wired through this route for every row on the page: if it silently fell back to
        // per-item resolution gone wrong, or to an unresolved lookup, these fields would come
        // back blank instead of matching what was seeded. The single-query property itself is
        // proven at the DAO level (dao.CarFamilyDaoTest's `findByIds resolves twenty families in
        // a single query`) — this test proves the route's wiring, not the query count.
        val now = Instant.now()
        val seeded = (1..20).map { i ->
            val familyId = ChallengeTestSeed.seedFamily(brand = "brand$i", name = "family$i")
            val challengeId = ChallengeTestSeed.seedChallenge(
                familyId = familyId, startsAt = now.minusSeconds((i + 1) * 3600L), endsAt = now.minusSeconds(i * 3600L),
            )
            seedParticipant(challengeId, userId)
            challengeId to ("brand$i" to "family$i")
        }.toMap()

        val body = client.get("/api/challenges/me?limit=20") { header(HttpHeaders.Authorization, "Bearer $token") }.body<MyChallengesDTO>()

        assertEquals(20, body.challenges.size)
        body.challenges.forEach { item ->
            val expected = seeded.getValue(item.challenge.id)
            assertEquals(expected.first, item.challenge.targetFamilyBrand, "brand for ${item.challenge.id}")
            assertEquals(expected.second, item.challenge.targetFamilyName, "name for ${item.challenge.id}")
        }
    }

    @Test
    fun `GET challenges me filter=COMPLETED returns only GRANTED participants`() = userTest { client, token, userId ->
        val familyId = ChallengeTestSeed.seedFamily()
        val now = Instant.now()
        val completed = ChallengeTestSeed.seedChallenge(familyId = familyId, startsAt = now.minusSeconds(3600), endsAt = now.minusSeconds(1800))
        val notCompleted = ChallengeTestSeed.seedChallenge(familyId = familyId, startsAt = now.minusSeconds(10 * 3600), endsAt = now.minusSeconds(9 * 3600))
        seedParticipant(completed, userId, rewardState = RewardState.GRANTED)
        seedParticipant(notCompleted, userId, rewardState = RewardState.NONE)

        val body = client.get("/api/challenges/me?filter=completed") { header(HttpHeaders.Authorization, "Bearer $token") }.body<MyChallengesDTO>()

        assertEquals(listOf(completed), body.challenges.map { it.challenge.id })
    }

    @Test
    fun `GET challenges me returns 400 for an unknown filter value`() = userTest { client, token, _ ->
        val response = client.get("/api/challenges/me?filter=bogus") { header(HttpHeaders.Authorization, "Bearer $token") }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // ---------- GET /challenges/{id} ----------

    @Test
    fun `GET challenges id returns 200 with the full config for a SCHEDULED challenge`() = userTest { client, token, _ ->
        val familyId = ChallengeTestSeed.seedFamily()
        val now = Instant.now()
        val challengeId = ChallengeTestSeed.seedChallenge(
            familyId = familyId, startsAt = now.minusSeconds(3600), endsAt = now.plusSeconds(3600),
            title = "Weekend Golf Hunt", requiredPosts = 5, rewardPoints = 300,
        )

        val response = client.get("/api/challenges/$challengeId") { header(HttpHeaders.Authorization, "Bearer $token") }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<ChallengeDTO>()
        assertEquals(challengeId, body.id)
        assertEquals("Weekend Golf Hunt", body.title)
        assertEquals("volkswagen", body.targetFamilyBrand)
        assertEquals(5, body.requiredPosts)
        assertEquals(300, body.rewardPoints)
    }

    @Test
    fun `GET challenges id returns 200 for a CANCELLED challenge`() = userTest { client, token, _ ->
        val familyId = ChallengeTestSeed.seedFamily()
        val now = Instant.now()
        val challengeId = ChallengeTestSeed.seedChallenge(
            familyId = familyId, startsAt = now.minusSeconds(3600), endsAt = now.plusSeconds(3600), status = ChallengeStatus.CANCELLED,
        )

        val response = client.get("/api/challenges/$challengeId") { header(HttpHeaders.Authorization, "Bearer $token") }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET challenges id returns 404 for a DRAFT challenge`() = userTest { client, token, _ ->
        val familyId = ChallengeTestSeed.seedFamily()
        val now = Instant.now()
        val challengeId = ChallengeTestSeed.seedChallenge(
            familyId = familyId, startsAt = now.minusSeconds(3600), endsAt = now.plusSeconds(3600), status = ChallengeStatus.DRAFT,
        )

        val response = client.get("/api/challenges/$challengeId") { header(HttpHeaders.Authorization, "Bearer $token") }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET challenges id returns 404 for a non-existent challenge`() = userTest { client, token, _ ->
        val response = client.get("/api/challenges/${UUID.randomUUID()}") { header(HttpHeaders.Authorization, "Bearer $token") }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET challenges id returns 400 for a malformed id`() = userTest { client, token, _ ->
        val response = client.get("/api/challenges/not-a-uuid") { header(HttpHeaders.Authorization, "Bearer $token") }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET challenges id without a token is unauthorized`() = testApplication {
        application { testChallengeModule() }
        val client = createClient { install(ContentNegotiation) { json(json) } }

        val response = client.get("/api/challenges/${UUID.randomUUID()}")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
