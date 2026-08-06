package routes

import com.revio.server.features.auth.JwtService
import com.revio.server.features.auth.RefreshTokenGenerator
import com.revio.server.features.auth.session.AuthSessionDAO
import com.revio.server.features.auth.session.SessionScope
import com.revio.server.features.auth.session.SessionService
import com.revio.server.features.car_family.AssignCarModelsRequest
import com.revio.server.features.car_family.CarFamilyAdminDTO
import com.revio.server.features.car_family.CreateCarFamilyRequest
import com.revio.server.features.car_model.CarModelTable
import com.revio.server.features.car_model.dto.CarModelOption
import com.revio.server.features.challenge.ChallengeAdminDTO
import com.revio.server.features.challenge.ChallengeAdminPageDTO
import com.revio.server.features.challenge.CreateChallengeAdminRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import testutils.CommentTestSeed
import testutils.TestDatabaseFactory
import testutils.TestEnv
import testutils.setTestEnv
import testutils.stopKoinSafely
import testutils.testChallengeAdminModule
import java.util.UUID

/**
 * End-to-end admin workflow across both route groups added for the plan's §9-E3/E4/E5 gaps:
 * create a family, assign models to it, verify the family, create a challenge targeting it,
 * list challenges, and edit the challenge while it's still DRAFT — then confirm the same edit
 * is rejected once the challenge is no longer DRAFT. This is the flow §7's Etapa 9 rollout step
 * ("creează familia Golf și verific-o prin GET /admin/car-families") assumed was possible.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminChallengeWorkflowE2ETest {

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

    private suspend fun adminToken(): String {
        val seeded = CommentTestSeed.seedUser(username = "admin")
        val (session) = SessionService(AuthSessionDAO(), RefreshTokenGenerator()).createSession(
            credentialId = seeded.authId,
            scope = SessionScope.FULL,
            userId = seeded.userId,
            deviceId = null,
            deviceName = null,
            userAgent = null,
            ip = null,
        )
        return jwt.generateAccessToken(session, seeded.authId, seeded.email, seeded.userId, isAdmin = true)
    }

    private fun seedModel(brand: String, model: String): UUID = transaction {
        CarModelTable.insert {
            it[CarModelTable.brand] = brand
            it[CarModelTable.model] = model
        }[CarModelTable.id].value
    }

    @Test
    fun `full admin workflow - create family, assign models, create challenge, list, edit while DRAFT`() = testApplication {
        application { testChallengeAdminModule() }
        val client = createClient { install(ContentNegotiation) { json(json) } }
        val token = adminToken()

        // 1. Create family.
        val familyResponse = client.post("/api/admin/car-families") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateCarFamilyRequest.serializer(), CreateCarFamilyRequest("volkswagen", "Golf")))
        }
        assertEquals(HttpStatusCode.Created, familyResponse.status)
        val family = familyResponse.body<CarFamilyAdminDTO>()

        // 2. Assign models to it.
        val golfR = seedModel("volkswagen", "golf r")
        val golfVariant = seedModel("volkswagen", "golf variant")
        val assignResponse = client.post("/api/admin/car-families/${family.id}/models") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(AssignCarModelsRequest.serializer(), AssignCarModelsRequest(listOf(golfR, golfVariant))))
        }
        assertEquals(HttpStatusCode.OK, assignResponse.status)

        // 3. Verify the family: it's listed, and the assignment response confirms both models.
        val familiesResponse = client.get("/api/admin/car-families") { header(HttpHeaders.Authorization, "Bearer $token") }
        val families = familiesResponse.body<List<CarFamilyAdminDTO>>()
        assertTrue(families.any { it.id == family.id && it.brand == "volkswagen" && it.name == "Golf" })

        val assignedModels = assignResponse.body<List<CarModelOption>>()
        assertEquals(setOf(golfR, golfVariant), assignedModels.map { it.id }.toSet())

        // 4. Create a challenge targeting the family.
        val createRequest = CreateChallengeAdminRequest(
            title = "Weekend Golf Hunt",
            description = "Spot 5 Golfs",
            targetFamilyId = family.id,
            requiredPosts = 5,
            rewardPoints = 300,
            startsAtLocal = "2026-08-08T09:00:00",
            endsAtLocal = "2026-08-10T09:00:00",
            timezone = "Europe/Bucharest",
        )
        val createResponse = client.post("/api/admin/challenges") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateChallengeAdminRequest.serializer(), createRequest))
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val challenge = createResponse.body<ChallengeAdminDTO>()
        assertEquals("DRAFT", challenge.status)

        // 5. List challenges — the new one must appear.
        val listResponse = client.get("/api/admin/challenges") { header(HttpHeaders.Authorization, "Bearer $token") }
        assertEquals(HttpStatusCode.OK, listResponse.status)
        val page = listResponse.body<ChallengeAdminPageDTO>()
        assertTrue(page.challenges.any { it.id == challenge.id })

        // 6. Edit the challenge while it's DRAFT — every field, not just title/description.
        val editRequest = createRequest.copy(
            title = "Weekend Golf Hunt (v2)",
            requiredPosts = 3,
            rewardPoints = 150,
        )
        val editResponse = client.put("/api/admin/challenges/${challenge.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateChallengeAdminRequest.serializer(), editRequest))
        }
        assertEquals(HttpStatusCode.OK, editResponse.status)
        val edited = editResponse.body<ChallengeAdminDTO>()
        assertEquals("Weekend Golf Hunt (v2)", edited.title)
        assertEquals(3, edited.requiredPosts)
        assertEquals(150, edited.rewardPoints)

        // 7. Publish, then confirm editing a non-DRAFT challenge is rejected.
        val publishResponse = client.post("/api/admin/challenges/${challenge.id}/publish") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, publishResponse.status)

        val rejectedEditResponse = client.put("/api/admin/challenges/${challenge.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateChallengeAdminRequest.serializer(), editRequest.copy(title = "Should not apply")))
        }
        assertEquals(HttpStatusCode.Conflict, rejectedEditResponse.status)

        val finalState = client.get("/api/admin/challenges/${challenge.id}") { header(HttpHeaders.Authorization, "Bearer $token") }
            .body<ChallengeAdminDTO>()
        assertEquals("Weekend Golf Hunt (v2)", finalState.title, "The rejected edit must not have applied")
        assertEquals("SCHEDULED", finalState.status)
    }
}
