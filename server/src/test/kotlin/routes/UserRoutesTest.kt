package com.revio.server.routes

import com.revio.server.features.auth.JwtService
import com.revio.server.features.auth.RefreshTokenGenerator
import com.revio.server.features.auth.session.AuthSessionDAO
import com.revio.server.features.auth.session.SessionScope
import com.revio.server.features.auth.session.SessionService
import com.revio.server.features.user.UserDao
import com.revio.server.features.user.dto.CreateUserRequest
import com.revio.server.features.user.dto.CreateUserResponse
import com.revio.server.features.user.dto.SelfUserDTO
import com.revio.server.features.user.dto.UpdateProfilePictureRequest
import com.revio.server.features.user.dto.UpdateUserRequest
import com.revio.server.features.user.dto.UserDTO
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    // --- GET /users/me exposes phoneNumber and birthDate; GET /users/{id} does not ---

    @Test
    fun `GET users me exposes phoneNumber and birthDate`() = userTest { client ->
        val credential = UserTestSeed.seedAuthCredential("selfinfo@example.com")
        val userId = UserTestSeed.seedUser(credential.authCredentialId, username = "selfinfo")
        UserDao().updateUserProfile(userId, phoneNumber = "+40700000000")
        val token = profileToken(credential.authCredentialId, userId, credential.email)

        val response = client.get("/api/users/me") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body: SelfUserDTO = response.body()
        assertEquals("+40700000000", body.phoneNumber)
        assertEquals(java.time.LocalDate.of(1995, 1, 1), body.birthDate)
    }

    @Test
    fun `GET users by id does not expose phoneNumber or birthDate`() = userTest { client ->
        val credential = UserTestSeed.seedAuthCredential("publicinfo@example.com")
        val userId = UserTestSeed.seedUser(credential.authCredentialId, username = "publicinfo")

        val response = client.get("/api/users/$userId")

        assertEquals(HttpStatusCode.OK, response.status)
        val rawBody = response.bodyAsText()
        assertTrue(!rawBody.contains("phoneNumber"))
        assertTrue(!rawBody.contains("birthDate"))
    }

    // --- PATCH /users/me ---

    @Test
    fun `PATCH users me returns 200 and updates fields`() = userTest { client ->
        val credential = UserTestSeed.seedAuthCredential("update@example.com")
        val userId = UserTestSeed.seedUser(credential.authCredentialId, username = "toupdate")
        val token = profileToken(credential.authCredentialId, userId, credential.email)

        val response = client.patch("/api/users/me") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(
                UpdateUserRequest(
                    fullName = "Updated Name",
                    country = "US",
                    phoneNumber = "+40711111111",
                    birthDate = java.time.LocalDate.of(1990, 6, 15),
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body: SelfUserDTO = response.body()
        assertEquals("Updated Name", body.fullName)
        assertEquals("US", body.country)
        assertEquals("+40711111111", body.phoneNumber)
        assertEquals(java.time.LocalDate.of(1990, 6, 15), body.birthDate)

        val stored = UserDao().getUserById(userId)!!
        assertEquals("Updated Name", stored.fullName)
        assertEquals("+40711111111", stored.phoneNumber)
    }

    @Test
    fun `PATCH users me returns 409 when username already taken by another user`() = userTest { client ->
        UserTestSeed.seedUser(UserTestSeed.seedAuthCredential("taken@example.com").authCredentialId, username = "takenname")
        val credential = UserTestSeed.seedAuthCredential("wantstaken@example.com")
        val userId = UserTestSeed.seedUser(credential.authCredentialId, username = "wantstaken")
        val token = profileToken(credential.authCredentialId, userId, credential.email)

        val response = client.patch("/api/users/me") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(UpdateUserRequest(username = "TakenName"))
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `PATCH users me returns 409 when phone number already taken by another user`() = userTest { client ->
        val ownerCredential = UserTestSeed.seedAuthCredential("phoneowner@example.com")
        val ownerId = UserTestSeed.seedUser(ownerCredential.authCredentialId, username = "phoneowner")
        UserDao().updateUserProfile(ownerId, phoneNumber = "+40722222222")

        val credential = UserTestSeed.seedAuthCredential("phonewants@example.com")
        val userId = UserTestSeed.seedUser(credential.authCredentialId, username = "phonewants")
        val token = profileToken(credential.authCredentialId, userId, credential.email)

        val response = client.patch("/api/users/me") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(UpdateUserRequest(phoneNumber = "+40722222222"))
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `PATCH users me returns 400 for invalid data`() = userTest { client ->
        val credential = UserTestSeed.seedAuthCredential("invalidupdate@example.com")
        val userId = UserTestSeed.seedUser(credential.authCredentialId, username = "invalidupdate")
        val token = profileToken(credential.authCredentialId, userId, credential.email)

        val response = client.patch("/api/users/me") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(UpdateUserRequest(birthDate = java.time.LocalDate.now().plusDays(1)))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PATCH users me returns 401 without JWT`() = userTest { client ->
        val response = client.patch("/api/users/me") {
            contentType(ContentType.Application.Json)
            setBody(UpdateUserRequest(fullName = "Nope"))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `PATCH users me returns 404 when user profile is missing`() = userTest { client ->
        val credential = UserTestSeed.seedAuthCredential("missingprofile@example.com")
        val token = tokenWithMissingProfile(credential.authCredentialId, UUID.randomUUID(), credential.email)

        val response = client.patch("/api/users/me") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(UpdateUserRequest(fullName = "Nope"))
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // --- PATCH /users/me: 403 restriction codes ---

    private suspend fun assertForbiddenChangeError(response: io.ktor.client.statement.HttpResponse, expectedCode: String) {
        assertEquals(HttpStatusCode.Forbidden, response.status)
        val error = Json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]!!.jsonObject
        assertEquals(expectedCode, error["code"]!!.jsonPrimitive.content)
        assertTrue(error["message"]!!.jsonPrimitive.content.isNotBlank())
    }

    @Test
    fun `PATCH users me returns 403 FULL_NAME_ALREADY_CHANGED with structured error body`() = userTest { client ->
        val credential = UserTestSeed.seedAuthCredential("fullname403@example.com")
        val userId = UserTestSeed.seedUser(credential.authCredentialId, username = "fullname403")
        UserDao().updateUserProfile(userId, fullName = "Already Changed")
        val token = profileToken(credential.authCredentialId, userId, credential.email)

        val response = client.patch("/api/users/me") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(UpdateUserRequest(fullName = "Another Name"))
        }

        assertForbiddenChangeError(response, "FULL_NAME_ALREADY_CHANGED")
    }

    @Test
    fun `PATCH users me returns 403 COUNTRY_ALREADY_CHANGED with structured error body`() = userTest { client ->
        val credential = UserTestSeed.seedAuthCredential("country403@example.com")
        val userId = UserTestSeed.seedUser(credential.authCredentialId, username = "country403")
        UserDao().updateUserProfile(userId, country = "US")
        val token = profileToken(credential.authCredentialId, userId, credential.email)

        val response = client.patch("/api/users/me") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(UpdateUserRequest(country = "FR"))
        }

        assertForbiddenChangeError(response, "COUNTRY_ALREADY_CHANGED")
    }

    @Test
    fun `PATCH users me returns 403 BIRTH_DATE_ALREADY_CHANGED with structured error body`() = userTest { client ->
        val credential = UserTestSeed.seedAuthCredential("birthdate403@example.com")
        val userId = UserTestSeed.seedUser(credential.authCredentialId, username = "birthdate403")
        UserDao().updateUserProfile(userId, birthDate = java.time.LocalDate.of(1990, 1, 1))
        val token = profileToken(credential.authCredentialId, userId, credential.email)

        val response = client.patch("/api/users/me") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(UpdateUserRequest(birthDate = java.time.LocalDate.of(1991, 2, 2)))
        }

        assertForbiddenChangeError(response, "BIRTH_DATE_ALREADY_CHANGED")
    }

    @Test
    fun `PATCH users me returns 403 USERNAME_CHANGE_TOO_SOON with structured error body`() = userTest { client ->
        val credential = UserTestSeed.seedAuthCredential("username403@example.com")
        val userId = UserTestSeed.seedUser(credential.authCredentialId, username = "username403")
        UserDao().updateUserProfile(userId, username = "username403changed")
        val token = profileToken(credential.authCredentialId, userId, credential.email)

        val response = client.patch("/api/users/me") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(UpdateUserRequest(username = "username403again"))
        }

        assertForbiddenChangeError(response, "USERNAME_CHANGE_TOO_SOON")
    }

    @Test
    fun `PATCH users me returns 403 PHONE_NUMBER_CHANGE_TOO_SOON with structured error body`() = userTest { client ->
        val credential = UserTestSeed.seedAuthCredential("phone403@example.com")
        val userId = UserTestSeed.seedUser(credential.authCredentialId, username = "phone403")
        UserDao().updateUserProfile(userId, phoneNumber = "+40700000020")
        val token = profileToken(credential.authCredentialId, userId, credential.email)

        val response = client.patch("/api/users/me") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(UpdateUserRequest(phoneNumber = "+40700000021"))
        }

        assertForbiddenChangeError(response, "PHONE_NUMBER_CHANGE_TOO_SOON")
    }

    // --- SelfUserDTO eligibility fields ---

    @Test
    fun `GET users me returns canChange true and null next-change timestamps for a fresh user`() = userTest { client ->
        val credential = UserTestSeed.seedAuthCredential("fresheligibility@example.com")
        val userId = UserTestSeed.seedUser(credential.authCredentialId, username = "fresheligibility")
        val token = profileToken(credential.authCredentialId, userId, credential.email)

        val response = client.get("/api/users/me") { bearerAuth(token) }

        assertEquals(HttpStatusCode.OK, response.status)
        val body: SelfUserDTO = response.body()
        assertTrue(body.canChangeFullName)
        assertTrue(body.canChangeCountry)
        assertTrue(body.canChangeBirthDate)
        assertTrue(body.canChangeUsername)
        assertTrue(body.canChangePhoneNumber)
        assertNull(body.nextUsernameChangeAt)
        assertNull(body.nextPhoneNumberChangeAt)
    }

    @Test
    fun `GET users me returns canChange false and a future next-change timestamp after a recent username change`() = userTest { client ->
        val credential = UserTestSeed.seedAuthCredential("lockedeligibility@example.com")
        val userId = UserTestSeed.seedUser(credential.authCredentialId, username = "lockedeligibility")
        UserDao().updateUserProfile(userId, fullName = "Locked Name", username = "lockedeligibility2")
        val token = profileToken(credential.authCredentialId, userId, credential.email)

        val response = client.get("/api/users/me") { bearerAuth(token) }

        assertEquals(HttpStatusCode.OK, response.status)
        val body: SelfUserDTO = response.body()
        assertEquals(false, body.canChangeFullName)
        assertEquals(false, body.canChangeUsername)
        assertNotNull(body.nextUsernameChangeAt)
        assertTrue(body.nextUsernameChangeAt!!.isAfter(java.time.Instant.now()))
    }

    // --- GET /users/username-available ---

    @Test
    fun `GET users username-available returns available true for a free username`() = userTest { client ->
        val credential = UserTestSeed.seedAuthCredential("checkfree@example.com")
        val userId = UserTestSeed.seedUser(credential.authCredentialId, username = "checkfree")
        val token = profileToken(credential.authCredentialId, userId, credential.email)

        val response = client.get("/api/users/username-available?username=brandnewname") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(true, body["available"]!!.jsonPrimitive.boolean)
        assertEquals("brandnewname", body["normalized"]!!.jsonPrimitive.content)
    }

    @Test
    fun `GET users username-available returns available false with TAKEN reason for a taken username`() = userTest { client ->
        UserTestSeed.seedUser(UserTestSeed.seedAuthCredential("owner@example.com").authCredentialId, username = "ownedname")
        val credential = UserTestSeed.seedAuthCredential("checker@example.com")
        val userId = UserTestSeed.seedUser(credential.authCredentialId, username = "checker")
        val token = profileToken(credential.authCredentialId, userId, credential.email)

        val response = client.get("/api/users/username-available?username=OwnedName") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(false, body["available"]!!.jsonPrimitive.boolean)
        assertEquals("TAKEN", body["reason"]!!.jsonPrimitive.content)
    }

    @Test
    fun `GET users username-available returns available false with INVALID_FORMAT reason for disallowed characters`() = userTest { client ->
        val credential = UserTestSeed.seedAuthCredential("checkerinvalid@example.com")
        val userId = UserTestSeed.seedUser(credential.authCredentialId, username = "checkerinvalid")
        val token = profileToken(credential.authCredentialId, userId, credential.email)

        val response = client.get("/api/users/username-available?username=bad-name") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(false, body["available"]!!.jsonPrimitive.boolean)
        assertEquals("INVALID_FORMAT", body["reason"]!!.jsonPrimitive.content)
    }

    @Test
    fun `GET users username-available returns available true for the caller's own current username`() = userTest { client ->
        val credential = UserTestSeed.seedAuthCredential("checkerself@example.com")
        val userId = UserTestSeed.seedUser(credential.authCredentialId, username = "checkerself")
        val token = profileToken(credential.authCredentialId, userId, credential.email)

        val response = client.get("/api/users/username-available?username=CheckerSelf") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(true, body["available"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `GET users username-available returns 401 without JWT`() = userTest { client ->
        val response = client.get("/api/users/username-available?username=anything")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
