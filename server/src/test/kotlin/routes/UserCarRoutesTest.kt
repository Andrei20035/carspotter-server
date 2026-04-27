package com.carspotter.routes

import com.carspotter.features.auth.JwtService
import com.carspotter.features.user_car.dto.UserCarDTO
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
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
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
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
import testutils.UserCarTestSeed
import testutils.setTestEnv
import testutils.stopKoinSafely
import testutils.testUserCarModule
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserCarRoutesTest {
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
    fun tearDown() {
        TestDatabaseFactory.stop()
    }

    @BeforeEach
    fun clean() {
        TestDatabaseFactory.cleanDatabase()
        stopKoinSafely()
    }

    private fun userCarTest(block: suspend ApplicationTestBuilder.(io.ktor.client.HttpClient) -> Unit) =
        testApplication {
            application { testUserCarModule() }
            val client = createClient {
                install(ContentNegotiation) {
                    json(json)
                }
            }
            block(client)
        }

    private fun tokenFor(authId: UUID, userId: UUID, email: String): String =
        jwt.generateJwtToken(credentialId = authId, userId = userId, email = email)

    private fun multipartBody(metadataJson: String?, imageBytes: ByteArray?): MultiPartFormDataContent =
        MultiPartFormDataContent(
            formData {
                if (metadataJson != null) {
                    append("metadata", metadataJson)
                }
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
                                    "car.jpg"
                                ).toString()
                            )
                        }
                    )
                }
            }
        )

    @Test
    fun `GET me car without JWT returns 401`() = userCarTest { client ->
        val response = client.get("/api/me/car")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET me car returns 404 when user has no car`() = userCarTest { client ->
        val user = CommentTestSeed.seedUser()
        val token = tokenFor(user.authId, user.userId, user.email)

        val response = client.get("/api/me/car") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST me car creates car and GET me car returns it`() = userCarTest { client ->
        val user = CommentTestSeed.seedUser(username = "alice")
        val token = tokenFor(user.authId, user.userId, user.email)

        val createResponse = client.post("/api/me/car") {
            bearerAuth(token)
            setBody(multipartBody("""{"customBrand":"BMW","customModel":"M3"}""", "fake-image".toByteArray()))
        }

        assertEquals(HttpStatusCode.Created, createResponse.status)
        val created: UserCarDTO = createResponse.body()
        assertEquals(user.userId, created.userId)
        assertEquals("BMW", created.brand)
        assertEquals("M3", created.model)
        assertTrue(created.imageUrl.contains("/uploads/user-cars/"))

        val getResponse = client.get("/api/me/car") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.OK, getResponse.status)
        val fetched: UserCarDTO = getResponse.body()
        assertEquals(created.id, fetched.id)
    }

    @Test
    fun `POST me car returns 409 when user already has a car`() = userCarTest { client ->
        val user = CommentTestSeed.seedUser()
        val token = tokenFor(user.authId, user.userId, user.email)
        UserCarTestSeed.insertUserCar(user.userId)

        val response = client.post("/api/me/car") {
            bearerAuth(token)
            setBody(multipartBody("""{"customBrand":"BMW","customModel":"M4"}""", "fake-image".toByteArray()))
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `GET public user car returns 200 when car exists`() = userCarTest { client ->
        val user = CommentTestSeed.seedUser()
        UserCarTestSeed.insertUserCar(user.userId, customBrand = "Porsche", customModel = "911")

        val response = client.get("/api/users/${user.userId}/car")

        assertEquals(HttpStatusCode.OK, response.status)
        val car: UserCarDTO = response.body()
        assertEquals("Porsche", car.brand)
        assertEquals("911", car.model)
    }

    @Test
    fun `PATCH me car updates brand and model`() = userCarTest { client ->
        val user = CommentTestSeed.seedUser()
        val token = tokenFor(user.authId, user.userId, user.email)
        UserCarTestSeed.insertUserCar(user.userId, customBrand = "Audi", customModel = "A4")

        val response = client.patch("/api/me/car") {
            bearerAuth(token)
            setBody(multipartBody("""{"customBrand":"Audi","customModel":"RS4"}""", null))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val updated: UserCarDTO = response.body()
        assertEquals("Audi", updated.brand)
        assertEquals("RS4", updated.model)
    }

    @Test
    fun `DELETE me car returns 204`() = userCarTest { client ->
        val user = CommentTestSeed.seedUser()
        val token = tokenFor(user.authId, user.userId, user.email)
        UserCarTestSeed.insertUserCar(user.userId)

        val response = client.delete("/api/me/car") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
    }
}
