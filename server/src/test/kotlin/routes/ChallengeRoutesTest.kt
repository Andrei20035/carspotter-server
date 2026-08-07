package routes

import com.revio.server.core.storage.IStorageService
import com.revio.server.core.storage.LocalImageStorageService
import com.revio.server.features.auth.JwtService
import com.revio.server.features.auth.RefreshTokenGenerator
import com.revio.server.features.auth.session.AuthSessionDAO
import com.revio.server.features.auth.session.SessionScope
import com.revio.server.features.auth.session.SessionService
import com.revio.server.features.challenge.ChallengeStatus
import com.revio.server.features.challenge.dto.ChallengeProgressDetailDTO
import com.revio.server.features.challenge.dto.CurrentChallengeDTO
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.koin.dsl.module
import org.koin.ktor.ext.getKoin
import testutils.ChallengeTestSeed
import testutils.CommentTestSeed
import testutils.TestDatabaseFactory
import testutils.TestEnv
import testutils.setTestEnv
import testutils.stopKoinSafely
import testutils.testChallengeModule
import java.nio.file.Files
import java.time.Instant
import java.util.UUID

/**
 * HTTP contract of the read-only, user-facing challenge routes (plan §5's "Utilizator" table):
 * GET /challenges/current and GET /challenges/{id}/progress. Real DB/DAO/service stack via
 * [testutils.testChallengeModule] — the point here is the route's response shape and status
 * mapping, not re-deriving grant/revoke logic (dao.ChallengeProgressDaoTest.kt).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChallengeRoutesTest {

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

    /**
     * [testChallengeModule] (shared across every challenge route test file) doesn't bind
     * [IStorageService] — no challenge route needed it before `/{id}/progress` started resolving
     * contribution thumbnails (Pas 7). Loading it here, local to this file via
     * [org.koin.ktor.ext.getKoin]/`loadModules`, keeps that shared helper unchanged for every
     * other test file that doesn't need it.
     */
    private fun Application.withStorage() {
        testChallengeModule()
        val uploadsDir = Files.createTempDirectory("challenge-route-test-uploads")
        getKoin().loadModules(listOf(module { single<IStorageService> { LocalImageStorageService(uploadsDir, "http://localhost:8080") } }))
    }

    private fun userTest(block: suspend ApplicationTestBuilder.(HttpClient, String, UUID) -> Unit) = testApplication {
        application { withStorage() }
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

    // ---------- GET /challenges/current ----------

    @Test
    fun `GET challenges current returns null challenge and null progress when nothing is scheduled`() = userTest { client, token, _ ->
        val response = client.get("/api/challenges/current") { header(HttpHeaders.Authorization, "Bearer $token") }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<CurrentChallengeDTO>()
        assertNull(body.challenge)
        assertNull(body.progress)
    }

    @Test
    fun `GET challenges current returns the active challenge and the caller's zero progress`() = userTest { client, token, _ ->
        val familyId = ChallengeTestSeed.seedFamily()
        val now = Instant.now()
        ChallengeTestSeed.seedChallenge(
            familyId = familyId, startsAt = now.minusSeconds(3600), endsAt = now.plusSeconds(3600),
            status = ChallengeStatus.SCHEDULED, title = "Weekend Golf Hunt", requiredPosts = 5, rewardPoints = 300,
        )

        val response = client.get("/api/challenges/current") { header(HttpHeaders.Authorization, "Bearer $token") }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<CurrentChallengeDTO>()
        assertEquals("Weekend Golf Hunt", body.challenge?.title)
        assertEquals("volkswagen", body.challenge?.targetFamilyBrand)
        assertEquals(0, body.progress?.contributionCount)
        assertEquals("NONE", body.progress?.rewardState)
    }

    @Test
    fun `GET challenges current returns the caller's real progress, not just zero`() = userTest { client, token, userId ->
        val familyId = ChallengeTestSeed.seedFamily()
        val modelId = ChallengeTestSeed.seedModel("volkswagen", "golf r", familyId)
        val now = Instant.now()
        val challengeId = ChallengeTestSeed.seedChallenge(
            familyId = familyId, startsAt = now.minusSeconds(3600), endsAt = now.plusSeconds(3600), requiredPosts = 5,
        )
        val postId = ChallengeTestSeed.seedCameraPost(userId, modelId)
        com.revio.server.features.challenge.ChallengeProgressDAO().evaluatePostContribution(challengeId, userId, postId, modelId, now)

        val response = client.get("/api/challenges/current") { header(HttpHeaders.Authorization, "Bearer $token") }

        val body = response.body<CurrentChallengeDTO>()
        assertEquals(1, body.progress?.contributionCount)
    }

    @Test
    fun `GET challenges current returns the soonest upcoming challenge when none is active yet`() = userTest { client, token, _ ->
        val familyId = ChallengeTestSeed.seedFamily()
        val now = Instant.now()
        ChallengeTestSeed.seedChallenge(
            familyId = familyId, startsAt = now.plusSeconds(3600), endsAt = now.plusSeconds(7200),
            status = ChallengeStatus.SCHEDULED, title = "Next weekend",
        )

        val response = client.get("/api/challenges/current") { header(HttpHeaders.Authorization, "Bearer $token") }

        val body = response.body<CurrentChallengeDTO>()
        assertEquals("Next weekend", body.challenge?.title)
    }

    @Test
    fun `GET challenges current without a token is unauthorized`() = testApplication {
        application { testChallengeModule() }
        val client = createClient { install(ContentNegotiation) { json(json) } }

        val response = client.get("/api/challenges/current")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ---------- GET /challenges/{id}/progress ----------

    @Test
    fun `GET challenges id progress returns 404 for an unknown challenge`() = userTest { client, token, _ ->
        val response = client.get("/api/challenges/${UUID.randomUUID()}/progress") { header(HttpHeaders.Authorization, "Bearer $token") }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET challenges id progress returns 400 for a malformed id`() = userTest { client, token, _ ->
        val response = client.get("/api/challenges/not-a-uuid/progress") { header(HttpHeaders.Authorization, "Bearer $token") }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET challenges id progress returns progress and contributing posts for an existing challenge`() = userTest { client, token, userId ->
        val familyId = ChallengeTestSeed.seedFamily()
        val modelId = ChallengeTestSeed.seedModel("volkswagen", "golf r", familyId)
        val now = Instant.now()
        val challengeId = ChallengeTestSeed.seedChallenge(
            familyId = familyId, startsAt = now.minusSeconds(3600), endsAt = now.plusSeconds(3600), requiredPosts = 5,
        )
        val postId = ChallengeTestSeed.seedCameraPost(userId, modelId)
        com.revio.server.features.challenge.ChallengeProgressDAO().evaluatePostContribution(challengeId, userId, postId, modelId, now)

        val response = client.get("/api/challenges/$challengeId/progress") { header(HttpHeaders.Authorization, "Bearer $token") }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<ChallengeProgressDetailDTO>()
        assertEquals(1, body.progress.contributionCount)
        assertEquals(listOf(postId), body.contributions.map { it.postId })
    }

    @Test
    fun `GET challenges id progress includes imageUrl, carBrand and carModel for each contribution`() = userTest { client, token, userId ->
        val familyId = ChallengeTestSeed.seedFamily()
        val modelId = ChallengeTestSeed.seedModel("volkswagen", "golf r", familyId)
        val now = Instant.now()
        val challengeId = ChallengeTestSeed.seedChallenge(
            familyId = familyId, startsAt = now.minusSeconds(3600), endsAt = now.plusSeconds(3600), requiredPosts = 5,
        )
        val postId = ChallengeTestSeed.seedCameraPost(userId, modelId)
        com.revio.server.features.challenge.ChallengeProgressDAO().evaluatePostContribution(challengeId, userId, postId, modelId, now)

        val response = client.get("/api/challenges/$challengeId/progress") { header(HttpHeaders.Authorization, "Bearer $token") }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<ChallengeProgressDetailDTO>()
        val contribution = body.contributions.single()
        assertEquals("volkswagen", contribution.carBrand)
        assertEquals("golf r", contribution.carModel)
        assertNotNull(contribution.imageUrl)
        assertTrue(contribution.imageUrl!!.contains("posts/test.jpg"), "resolved URL must be built from the post's storage key")
    }

    @Test
    fun `GET challenges id progress response deserializes with a client that doesn't know imageUrl, carBrand or carModel`() = userTest { client, token, userId ->
        // Backward compatibility: a client built before Pas 7 has a DTO without the 3 new fields.
        // ignoreUnknownKeys mirrors how kotlinx.serialization is actually configured on both ends
        // (see this file's own `json` instance and ChallengeApi's Retrofit converter).
        val familyId = ChallengeTestSeed.seedFamily()
        val modelId = ChallengeTestSeed.seedModel("volkswagen", "golf r", familyId)
        val now = Instant.now()
        val challengeId = ChallengeTestSeed.seedChallenge(
            familyId = familyId, startsAt = now.minusSeconds(3600), endsAt = now.plusSeconds(3600), requiredPosts = 5,
        )
        val postId = ChallengeTestSeed.seedCameraPost(userId, modelId)
        com.revio.server.features.challenge.ChallengeProgressDAO().evaluatePostContribution(challengeId, userId, postId, modelId, now)

        val response = client.get("/api/challenges/$challengeId/progress") { header(HttpHeaders.Authorization, "Bearer $token") }
        val raw = response.bodyAsText()

        val legacy = json.decodeFromString(LegacyChallengeProgressDetailDTO.serializer(), raw)
        assertEquals(1, legacy.progress.contributionCount)
        assertEquals(listOf(postId.toString()), legacy.contributions.map { it.postId })
    }

    @Test
    fun `GET challenges id progress works for a challenge that is not the currently active one`() = userTest { client, token, _ ->
        // "not limited to the currently active one" per the route's own KDoc — a past, ENDED
        // challenge must still be readable.
        val familyId = ChallengeTestSeed.seedFamily()
        val now = Instant.now()
        val pastChallengeId = ChallengeTestSeed.seedChallenge(
            familyId = familyId, startsAt = now.minusSeconds(7200), endsAt = now.minusSeconds(3600),
            status = ChallengeStatus.SCHEDULED, title = "Last weekend",
        )

        val response = client.get("/api/challenges/$pastChallengeId/progress") { header(HttpHeaders.Authorization, "Bearer $token") }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<ChallengeProgressDetailDTO>()
        assertEquals(0, body.progress.contributionCount)
        assertEquals(emptyList<UUID>(), body.contributions.map { it.postId })
    }

    @Test
    fun `GET challenges id progress without a token is unauthorized`() = testApplication {
        application { testChallengeModule() }
        val client = createClient { install(ContentNegotiation) { json(json) } }

        val response = client.get("/api/challenges/${UUID.randomUUID()}/progress")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}

// ---------- Pre-Pas-7 wire shape, for the backward-compatibility test above ----------

@Serializable
private data class LegacyChallengeContributionDTO(
    val postId: String,
    val createdAt: String,
)

@Serializable
private data class LegacyChallengeProgressDTO(
    val contributionCount: Int,
    val rewardState: String,
)

@Serializable
private data class LegacyChallengeProgressDetailDTO(
    val progress: LegacyChallengeProgressDTO,
    val contributions: List<LegacyChallengeContributionDTO>,
)
