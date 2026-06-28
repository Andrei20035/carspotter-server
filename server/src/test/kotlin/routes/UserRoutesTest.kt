package com.carspotter.routes

import com.carspotter.features.auth.JwtService
import com.carspotter.features.auth.RefreshTokenGenerator
import com.carspotter.features.auth.session.AuthSessionDAO
import com.carspotter.features.auth.session.SessionScope
import com.carspotter.features.auth.session.SessionService
import com.carspotter.features.user.UserDao
import com.carspotter.features.user.dto.CreateUserRequest
import com.carspotter.features.user.dto.CreateUserResponse
import com.carspotter.features.user.dto.UpdateProfilePictureRequest
import com.carspotter.features.user.dto.UserDTO
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import testutils.TestDatabaseFactory
import testutils.TestEnv
import testutils.UserTestSeed
import testutils.setTestEnv
import testutils.stopKoinSafely
import testutils.testUserModule
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserRoutesTest {
    private val jwt = JwtService(
        jwtSecret = TestEnv.JWT_SECRET,
        jwtIssuer = TestEnv.JWT_ISSUER,
        jwtAudience = TestEnv.JWT_AUDIENCE,
    )

    @BeforeAll
    fun setup() {
        setTestEnv()
        TestDatabaseFactory.start()
    }

    @AfterAll
    fun tearDown() {
        TestDatabaseFactory.stop()
    }

    @BeforeEach
    fun clean() {
        TestDatabaseFactory.cleanDatabase()
        stopKoinSafely()
    }

    private fun userTest(block: suspend ApplicationTestBuilder.(io.ktor.client.HttpClient) -> Unit) =
        testApplication {
            application { testUserModule() }
            val client = createClient {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; isLenient = true })
                }
            }
            block(client)
        }

    private suspend fun accessToken(
        credentialId: UUID,
        email: String,
        scope: SessionScope,
        userId: UUID? = null,
    ): Pair<String, String> {
        val (session, refreshToken) = SessionService(AuthSessionDAO(), RefreshTokenGenerator()).createSession(
            credentialId = credentialId,
            scope = scope,
            userId = userId,
            deviceId = null,
            deviceName = null,
            userAgent = null,
            ip = null,
        )
        return jwt.generateAccessToken(session, credentialId, email, userId) to refreshToken
    }

    private suspend fun onboardingToken(credentialId: UUID, email: String): String =
        accessToken(credentialId, email, SessionScope.ONBOARDING).first

    private suspend fun profileToken(credentialId: UUID, userId: UUID, email: String): String =
        accessToken(credentialId, email, SessionScope.FULL, userId).first

    private suspend fun tokenWithMissingProfile(credentialId: UUID, userId: UUID, email: String): String {
        val (session, _) = SessionService(AuthSessionDAO(), RefreshTokenGenerator()).createSession(
            credentialId = credentialId,
            scope = SessionScope.ONBOARDING,
            userId = null,
            deviceId = null,
            deviceName = null,
            userAgent = null,
            ip = null,
        )
        return jwt.generateAccessToken(session, credentialId, email, userId)
    }

    private fun profilePictureMultipartBody(imageBytes: ByteArray?): MultiPartFormDataContent =
        MultiPartFormDataContent(
            formData {
                if (imageBytes != null) {
                    append(
                        "image",
                        imageBytes,
                        Headers.build {
                            append(HttpHeaders.ContentType, ContentType.Image.JPEG.toString())
                            append(
                                HttpHeaders.ContentDisposition,
                                ContentDisposition.File.withParameter(
                                    ContentDisposition.Parameters.Name,
                                    "image"
                                ).withParameter(
                                    ContentDisposition.Parameters.FileName,
                                    "profile.jpg"
                                ).toString()
                            )
                        }
                    )
                }
            }
        )

    @Test
    fun `POST users promotes onboarding session and returns rotated token pair`() = userTest { client ->
        val credential = UserTestSeed.seedAuthCredential("alice@example.com")
        val (token, oldRefreshToken) = accessToken(
            credential.authCredentialId,
            credential.email,
            SessionScope.ONBOARDING,
        )

        val response = client.post("/api/users") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(
                CreateUserRequest(
                    fullName = "Alice",
                    birthDate = java.time.LocalDate.of(1995, 1, 1),
                    username = "Alice_1",
                    country = "RO",
                )
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body: CreateUserResponse = response.body()
        assertNotNull(body.userId)
        assertTrue(body.accessToken.isNotBlank())
        assertTrue(body.refreshToken.isNotBlank())
        assertTrue(body.refreshToken != oldRefreshToken)

        val promotedSession = AuthSessionDAO().findByRefreshHash(
            RefreshTokenGenerator().hashOf(body.refreshToken)
        )
        assertNotNull(promotedSession)
        assertEquals(SessionScope.FULL, promotedSession!!.scope)
        assertEquals(body.userId, promotedSession.userId)
        assertEquals(2, promotedSession.version)
        assertNull(AuthSessionDAO().findByRefreshHash(RefreshTokenGenerator().hashOf(oldRefreshToken)))
    }

    @Test
    fun `POST users returns 400 for invalid username`() = userTest { client ->
        val credential = UserTestSeed.seedAuthCredential("alice@example.com")
        val token = onboardingToken(credential.authCredentialId, credential.email)

        val response = client.post("/api/users") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(
                CreateUserRequest(
                    fullName = "Alice",
                    birthDate = java.time.LocalDate.of(1995, 1, 1),
                    username = "  ",
                    country = "RO",
                )
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST users returns 409 when username already exists case-insensitive`() = userTest { client ->
        val firstCredential = UserTestSeed.seedAuthCredential("alice@example.com")
        UserTestSeed.seedUser(firstCredential.authCredentialId, username = "alice")
        val secondCredential = UserTestSeed.seedAuthCredential("bob@example.com")
        val token = onboardingToken(secondCredential.authCredentialId, secondCredential.email)

        val response = client.post("/api/users") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(
                CreateUserRequest(
                    fullName = "Bob",
                    birthDate = java.time.LocalDate.of(1995, 1, 1),
                    username = "ALICE",
                    country = "RO",
                )
            )
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `POST users returns 401 without JWT`() = userTest { client ->
        val response = client.post("/api/users") {
            contentType(ContentType.Application.Json)
            setBody(
                CreateUserRequest(
                    fullName = "Alice",
                    birthDate = java.time.LocalDate.of(1995, 1, 1),
                    username = "alice",
                    country = "RO",
                )
            )
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET users me returns 200 for authenticated user`() = userTest { client ->
        val credential = UserTestSeed.seedAuthCredential("alice@example.com")
        val userId = UserTestSeed.seedUser(credential.authCredentialId, username = "alice")
        val token = profileToken(credential.authCredentialId, userId, credential.email)

        val response = client.get("/api/users/me") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body: UserDTO = response.body()
        assertEquals(userId, body.id)
        assertNull(body.javaClass.declaredFields.find { it.name == "phoneNumber" })
    }

    @Test
    fun `GET users me returns 404 when profile is missing`() = userTest { client ->
        val credential = UserTestSeed.seedAuthCredential("alice@example.com")
        val token = tokenWithMissingProfile(credential.authCredentialId, UUID.randomUUID(), credential.email)

        val response = client.get("/api/users/me") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET users me returns 401 without JWT`() = userTest { client ->
        val response = client.get("/api/users/me")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET users by id returns 200`() = userTest { client ->
        val credential = UserTestSeed.seedAuthCredential("alice@example.com")
        val userId = UserTestSeed.seedUser(credential.authCredentialId, username = "alice")

        val response = client.get("/api/users/$userId")

        assertEquals(HttpStatusCode.OK, response.status)
        val body: UserDTO = response.body()
        assertEquals("alice", body.username)
    }

    @Test
    fun `GET users by id returns 400 for invalid UUID`() = userTest { client ->
        val response = client.get("/api/users/not-a-uuid")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET users by id returns 404 for missing user`() = userTest { client ->
        val response = client.get("/api/users/${UUID.randomUUID()}")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `PATCH users me profile-picture returns 200`() = userTest { client ->
        val credential = UserTestSeed.seedAuthCredential("alice@example.com")
        val userId = UserTestSeed.seedUser(credential.authCredentialId, username = "alice")
        val token = profileToken(credential.authCredentialId, userId, credential.email)

        val response = client.patch("/api/users/me/profile-picture") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(UpdateProfilePictureRequest("/uploads/alice.jpg"))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body: UserDTO = response.body()
        assertEquals("http://localhost:8080/uploads/alice.jpg", body.profilePicturePath)
        assertEquals("alice.jpg", UserDao().getUserById(userId)!!.profilePicturePath)
    }

    @Test
    fun `PATCH users me profile-picture accepts multipart image upload`() = userTest { client ->
        val credential = UserTestSeed.seedAuthCredential("alice@example.com")
        val userId = UserTestSeed.seedUser(credential.authCredentialId, username = "alice")
        val token = profileToken(credential.authCredentialId, userId, credential.email)

        val response = client.patch("/api/users/me/profile-picture") {
            bearerAuth(token)
            setBody(profilePictureMultipartBody("fake-profile-image".toByteArray()))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body: UserDTO = response.body()
        assertNotNull(body.profilePicturePath)
        assertTrue(body.profilePicturePath!!.startsWith("http://localhost:8080/uploads/profile-pictures/"))
        val storedPath = UserDao().getUserById(userId)!!.profilePicturePath
        assertNotNull(storedPath)
        assertTrue(storedPath!!.startsWith("profile-pictures/"))
    }

    @Test
    fun `PATCH users me profile-picture returns 400 for blank image path`() = userTest { client ->
        val credential = UserTestSeed.seedAuthCredential("alice@example.com")
        val userId = UserTestSeed.seedUser(credential.authCredentialId, username = "alice")
        val token = profileToken(credential.authCredentialId, userId, credential.email)

        val response = client.patch("/api/users/me/profile-picture") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(UpdateProfilePictureRequest("   "))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PATCH users me profile-picture returns 401 without JWT`() = userTest { client ->
        val response = client.patch("/api/users/me/profile-picture") {
            contentType(ContentType.Application.Json)
            setBody(UpdateProfilePictureRequest("/uploads/alice.jpg"))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `PATCH users me profile-picture returns 404 when user profile is missing`() = userTest { client ->
        val credential = UserTestSeed.seedAuthCredential("alice@example.com")
        val token = tokenWithMissingProfile(credential.authCredentialId, UUID.randomUUID(), credential.email)

        val response = client.patch("/api/users/me/profile-picture") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(UpdateProfilePictureRequest("/uploads/alice.jpg"))
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // --- Early Spotter flag in GET /users/me and GET /users/{id} ---

    @Test
    fun `GET users me returns isEarlySpotter true and number for early spotter user`() = userTest { client ->
        val credential = UserTestSeed.seedAuthCredential("early@example.com")
        val onboardingToken = onboardingToken(credential.authCredentialId, credential.email)

        val postResponse = client.post("/api/users") {
            bearerAuth(onboardingToken)
            contentType(ContentType.Application.Json)
            setBody(
                CreateUserRequest(
                    fullName = "Early",
                    birthDate = java.time.LocalDate.of(1995, 1, 1),
                    username = "earlyspotter",
                    country = "RO",
                )
            )
        }
        assertEquals(HttpStatusCode.Created, postResponse.status)
        val createBody: CreateUserResponse = postResponse.body()

        val meResponse = client.get("/api/users/me") {
            bearerAuth(createBody.accessToken)
        }
        assertEquals(HttpStatusCode.OK, meResponse.status)
        val meBody: UserDTO = meResponse.body()
        assertTrue(meBody.isEarlySpotter)
        assertEquals(1, meBody.earlySpotterNumber)
    }

    @Test
    fun `GET users me returns isEarlySpotter false and null number for non-early-spotter`() = userTest { client ->
        val credential = UserTestSeed.seedAuthCredential("regular@example.com")
        val userId = UserTestSeed.seedUser(credential.authCredentialId, username = "regular")
        val token = profileToken(credential.authCredentialId, userId, credential.email)

        val response = client.get("/api/users/me") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body: UserDTO = response.body()
        assertEquals(false, body.isEarlySpotter)
        assertNull(body.earlySpotterNumber)
    }

    @Test
    fun `GET users by id returns isEarlySpotter true and number for early spotter user`() = userTest { client ->
        val credential = UserTestSeed.seedAuthCredential("early2@example.com")
        val onboardingToken = onboardingToken(credential.authCredentialId, credential.email)

        val postResponse = client.post("/api/users") {
            bearerAuth(onboardingToken)
            contentType(ContentType.Application.Json)
            setBody(
                CreateUserRequest(
                    fullName = "Early2",
                    birthDate = java.time.LocalDate.of(1995, 1, 1),
                    username = "earlyspotter2",
                    country = "RO",
                )
            )
        }
        assertEquals(HttpStatusCode.Created, postResponse.status)
        val createBody: CreateUserResponse = postResponse.body()

        val byIdResponse = client.get("/api/users/${createBody.userId}")
        assertEquals(HttpStatusCode.OK, byIdResponse.status)
        val body: UserDTO = byIdResponse.body()
        assertTrue(body.isEarlySpotter)
        assertEquals(1, body.earlySpotterNumber)
    }

    @Test
    fun `GET users by id returns isEarlySpotter false and null number for non-early-spotter`() = userTest { client ->
        val credential = UserTestSeed.seedAuthCredential("regular2@example.com")
        val userId = UserTestSeed.seedUser(credential.authCredentialId, username = "regular2")

        val response = client.get("/api/users/$userId")

        assertEquals(HttpStatusCode.OK, response.status)
        val body: UserDTO = response.body()
        assertEquals(false, body.isEarlySpotter)
        assertNull(body.earlySpotterNumber)
    }

    // --- streakDays in GET /users/me and GET /users/{id} ---

    @Test
    fun `GET users me returns 0 streakDays when user has no streak activity`() = userTest { client ->
        val credential = UserTestSeed.seedAuthCredential("streak0@example.com")
        val userId = UserTestSeed.seedUser(credential.authCredentialId, username = "streakzero")
        val token = profileToken(credential.authCredentialId, userId, credential.email)

        val response = client.get("/api/users/me") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body: UserDTO = response.body()
        assertEquals(0, body.streakDays)
    }

    @Test
    fun `GET users me returns positive streakDays when streak is active`() = userTest { client ->
        val credential = UserTestSeed.seedAuthCredential("streakactive@example.com")
        val userId = UserTestSeed.seedUser(credential.authCredentialId, username = "streakactive")
        UserDao().advanceStreak(userId, java.time.LocalDate.now(), "UTC")
        val token = profileToken(credential.authCredentialId, userId, credential.email)

        val response = client.get("/api/users/me") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body: UserDTO = response.body()
        assertEquals(1, body.streakDays)
    }

    @Test
    fun `GET users by id returns 0 streakDays when streak is expired`() = userTest { client ->
        val credential = UserTestSeed.seedAuthCredential("streakexpired@example.com")
        val userId = UserTestSeed.seedUser(credential.authCredentialId, username = "streakexpired")
        UserDao().advanceStreak(userId, java.time.LocalDate.now().minusDays(2), "UTC")

        val response = client.get("/api/users/$userId")

        assertEquals(HttpStatusCode.OK, response.status)
        val body: UserDTO = response.body()
        assertEquals(0, body.streakDays)
    }
}
