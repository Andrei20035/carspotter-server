package routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.carspotter.config.configureSerialization
import com.carspotter.features.user_car.dto.UserCarDTO
import com.carspotter.features.user.dto.UserDTO
import com.carspotter.features.user_car.dto.UserCarRequest
import com.carspotter.features.user_car.dto.UserCarUpdateRequest
import com.carspotter.features.user_car.IUserCarService
import com.carspotter.features.user_car.UserCarCreationException
import com.carspotter.features.user_car.userCarRoutes
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.*
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.context.GlobalContext.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import java.time.Instant
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserCarRoutesTest : KoinTest {

    private lateinit var userCarService: IUserCarService

    @BeforeAll
    fun setup() {
        userCarService = mockk()
    }

    @BeforeEach
    fun setupKoin() {
        startKoin {
            modules(
                module {
                    single { userCarService }
                }
            )
        }
    }

    private fun Application.configureTestApplication() {
        System.setProperty("JWT_SECRET", "test-secret-key")

        configureSerialization()

        install(Authentication) {
            jwt("jwt") {
                realm = "Test Server"
                verifier(
                    JWT
                        .require(Algorithm.HMAC256("test-secret-key"))
                        .build()
                )
                validate { credential ->
                    val credentialIdString = credential.payload.getClaim("userId").asString()
                    if (credentialIdString != null) {
                        try {
                            UUID.fromString(credentialIdString)
                            JWTPrincipal(credential.payload)
                        } catch (e: IllegalArgumentException) {
                            null
                        }
                    } else {
                        null
                    }
                }
                challenge { _, _ ->
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Missing or invalid JWT token"))
                }
            }
        }

        routing {
            userCarRoutes()
        }
    }

    @AfterEach
    fun tearDownKoin() {
        stopKoin()
        clearAllMocks()
    }

    @Test
    fun `POST user-cars returns 401 when JWT missing or invalid`() = testApplication {
        application {
            configureTestApplication()
        }

        val userId = UUID.randomUUID()
        val carModelId = UUID.randomUUID()

        val request = UserCarRequest(userId = userId, carModelId = carModelId)

        val response = client.post("/user-cars") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
            header(HttpHeaders.Authorization, "Bearer invalid-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Missing or invalid JWT token"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `POST user-cars returns 400 when failed to create user car`() = testApplication {

        val userId = UUID.randomUUID()
        val carModelId = UUID.randomUUID()

        coEvery { userCarService.createUserCar(any()) } throws UserCarCreationException("Invalid userId or carModelId")

        application {
            configureTestApplication()
        }

        val request = UserCarRequest(userId = userId, carModelId = carModelId)

        val token = createTestToken(userId = userId)

        val response = client.post("/user-cars") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Invalid userId or carModelId"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { userCarService.createUserCar(any()) }
    }

    @Test
    fun `POST user-cars returns 500 when unexpected error occurs`() = testApplication {
        val userId = UUID.randomUUID()
        val carModelId = UUID.randomUUID()

        coEvery { userCarService.createUserCar(any()) } throws RuntimeException("Failed to create user car")

        application {
            configureTestApplication()
        }

        val request = UserCarRequest(userId = userId, carModelId = carModelId)

        val token = createTestToken(userId = userId)

        val response = client.post("/user-cars") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.InternalServerError, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Failed to create user car"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { userCarService.createUserCar(any()) }
    }

    @Test
    fun `POST user-cars returns 201 when user car created successfully`() = testApplication {
        val userId = UUID.randomUUID()
        val carModelId = UUID.randomUUID()
        val returned = UUID.randomUUID()

        coEvery { userCarService.createUserCar(any()) } returns returned

        application {
            configureTestApplication()
        }

        val request = UserCarRequest(userId = userId, carModelId = carModelId)

        val token = createTestToken(userId = userId)

        val response = client.post("/user-cars") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.Created, response.status)

        val expectedJson = Json.parseToJsonElement("""{"message":"User car created successfully"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { userCarService.createUserCar(any()) }
    }

    @Test
    fun `GET user-cars-userCarId returns 401 when JWT missing or invalid`() = testApplication {
        application {
            configureTestApplication()
        }

        val response = client.get("/user-cars/1") {
            header(HttpHeaders.Authorization, "Bearer invalid-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Missing or invalid JWT token"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `GET user-cars-userCarId returns 400 for invalid userCarId`() = testApplication {
        application {
            configureTestApplication()
        }
        val userId = UUID.randomUUID()


        val token = createTestToken(userId = userId)

        val response = client.get("/user-cars/invalid") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Invalid or missing user car ID"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `GET user-cars-userCarId returns 404 when user car not found`() = testApplication {
        val userId = UUID.randomUUID()
        val userCarId = UUID.randomUUID()

        coEvery { userCarService.getUserCarById(userCarId) } returns null

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)

        val response = client.get("/user-cars/$userCarId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"User car not found"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { userCarService.getUserCarById(userCarId) }
    }

    @Test
    fun `GET user-cars-userCarId returns 200 with user car details when found`() = testApplication {
        val userId = UUID.randomUUID()
        val userCarId = UUID.randomUUID()
        val carModelId = UUID.randomUUID()

        val userCar = UserCarDTO(
            id = userCarId,
            userId = userId,
            carModelId = carModelId,
            imagePath = "path/to/image.jpg",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        coEvery { userCarService.getUserCarById(userCarId) } returns userCar

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)

        val response = client.get("/user-cars/$userCarId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.encodeToJsonElement(userCar)
        val actualJson = Json.parseToJsonElement(response.bodyAsText())

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { userCarService.getUserCarById(userCarId) }
    }

    @Test
    fun `GET user-cars-by-user-userId returns 401 when JWT missing or invalid`() = testApplication {
        application {
            configureTestApplication()
        }

        val response = client.get("/user-cars/by-user/1") {
            header(HttpHeaders.Authorization, "Bearer invalid-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Missing or invalid JWT token"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `GET user-cars-by-user-userId returns 400 for invalid userId`() = testApplication {
        application {
            configureTestApplication()
        }

        val userId = UUID.randomUUID()


        val token = createTestToken(userId = userId)

        val response = client.get("/user-cars/by-user/invalid") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Invalid user ID"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `GET user-cars-by-user-userId returns 404 when user car not found`() = testApplication {
        val userId = UUID.randomUUID()
        val targetUserId = UUID.randomUUID()

        coEvery { userCarService.getUserCarByUserId(targetUserId) } returns null

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)

        val response = client.get("/user-cars/by-user/$targetUserId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"User car not found"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { userCarService.getUserCarByUserId(targetUserId) }
    }

    @Test
    fun `GET user-cars-by-user-userId returns 200 with user car details when found`() = testApplication {
        val userId = UUID.randomUUID()
        val userCarId = UUID.randomUUID()
        val carModelId = UUID.randomUUID()
        val targetUserId = UUID.randomUUID()

        val userCar = UserCarDTO(
            id = userCarId,
            userId = targetUserId,
            carModelId = carModelId,
            imagePath = "path/to/image.jpg",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        coEvery { userCarService.getUserCarByUserId(targetUserId) } returns userCar

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)

        val response = client.get("/user-cars/by-user/$targetUserId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.encodeToJsonElement(userCar)
        val actualJson = Json.parseToJsonElement(response.bodyAsText())

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { userCarService.getUserCarByUserId(targetUserId) }
    }

    @Test
    fun `GET user-cars-userCarId-user returns 401 when JWT missing or invalid`() = testApplication {
        application {
            configureTestApplication()
        }

        val response = client.get("/user-cars/1/user") {
            header(HttpHeaders.Authorization, "Bearer invalid-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Missing or invalid JWT token"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `GET user-cars-userCarId-user returns 400 for invalid userCarId`() = testApplication {
        application {
            configureTestApplication()
        }

        val userId = UUID.randomUUID()

        val token = createTestToken(userId = userId)

        val response = client.get("/user-cars/invalid/user") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Invalid or missing user car ID"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `GET user-cars-userCarId-user returns 200 with user details`() = testApplication {
        val userId = UUID.randomUUID()
        val userCarId = UUID.randomUUID()
        val userId2 = UUID.randomUUID()

        val user = UserDTO(
            id = userId2,
            fullName = "John Doe",
            phoneNumber = "0712453678",
            username = "johndoe",
            birthDate = LocalDate.of(1990, 1, 1),
            country = "USA"
        )

        coEvery { userCarService.getUserByUserCarId(userCarId) } returns user

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)

        val response = client.get("/user-cars/$userCarId/user") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.encodeToJsonElement(user)
        val actualJson = Json.parseToJsonElement(response.bodyAsText())

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { userCarService.getUserByUserCarId(userCarId) }
    }

    @Test
    fun `PUT user-cars returns 401 when JWT missing or invalid`() = testApplication {
        application {
            configureTestApplication()
        }

        val carModelId = UUID.randomUUID()

        val request = UserCarUpdateRequest(carModelId = carModelId, imagePath = "path/to/image.jpg")

        val response = client.put("/user-cars") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
            header(HttpHeaders.Authorization, "Bearer invalid-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Missing or invalid JWT token"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `PUT user-cars returns 404 when user car not found`() = testApplication {
        val userId = UUID.randomUUID()
        val userCarId = UUID.randomUUID()
        val carModelId = UUID.randomUUID()
        val targetUserId = UUID.randomUUID()

        coEvery { userCarService.updateUserCar(userId, "path/to/image.jpg", carModelId) } returns 0

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)
        val request = UserCarUpdateRequest(carModelId = carModelId, imagePath = "path/to/image.jpg")

        val response = client.put("/user-cars") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"User car not found"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { userCarService.updateUserCar(userId, "path/to/image.jpg", carModelId) }
    }

    @Test
    fun `PUT user-cars returns 200 when user car updated successfully`() = testApplication {
        val userId = UUID.randomUUID()
        val userCarId = UUID.randomUUID()
        val carModelId = UUID.randomUUID()
        val targetUserId = UUID.randomUUID()

        coEvery { userCarService.updateUserCar(userId, "path/to/image.jpg", carModelId) } returns 1

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)
        val request = UserCarUpdateRequest(carModelId = carModelId, imagePath = "path/to/image.jpg")

        val response = client.put("/user-cars") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.parseToJsonElement("""{"message":"User car updated successfully"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { userCarService.updateUserCar(userId, "path/to/image.jpg", carModelId) }
    }

    @Test
    fun `DELETE user-cars returns 401 when JWT missing or invalid`() = testApplication {
        application {
            configureTestApplication()
        }

        val response = client.delete("/user-cars") {
            header(HttpHeaders.Authorization, "Bearer invalid-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Missing or invalid JWT token"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `DELETE user-cars returns 404 when user car not found`() = testApplication {
        val userId = UUID.randomUUID()
        val userCarId = UUID.randomUUID()
        val carModelId = UUID.randomUUID()
        val targetUserId = UUID.randomUUID()

        coEvery { userCarService.deleteUserCar(userId) } returns 0

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)

        val response = client.delete("/user-cars") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"User car not found"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { userCarService.deleteUserCar(userId) }
    }

    @Test
    fun `DELETE user-cars returns 200 when user car deleted successfully`() = testApplication {
        val userId = UUID.randomUUID()
        val userCarId = UUID.randomUUID()
        val carModelId = UUID.randomUUID()
        val targetUserId = UUID.randomUUID()

        coEvery { userCarService.deleteUserCar(userId) } returns 1

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)

        val response = client.delete("/user-cars") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.parseToJsonElement("""{"message":"User car deleted successfully"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { userCarService.deleteUserCar(userId) }
    }

    @Test
    fun `GET user-cars returns 200 with list of all user cars`() = testApplication {
        val userId = UUID.randomUUID()
        val userCarId = UUID.randomUUID()
        val carModelId = UUID.randomUUID()
        val userId2 = UUID.randomUUID()
        val userCarId2 = UUID.randomUUID()
        val carModelId2 = UUID.randomUUID()


        val userCars = listOf(
            UserCarDTO(
                id = userCarId,
                userId = userId,
                carModelId = carModelId,
                imagePath = "path/to/image1.jpg",
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            ),
            UserCarDTO(
                id = userCarId2,
                userId = userId2,
                carModelId = carModelId2,
                imagePath = "path/to/image2.jpg",
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )

        coEvery { userCarService.getAllUserCars() } returns userCars

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)

        val response = client.get("/user-cars") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.encodeToJsonElement(userCars)
        val actualJson = Json.parseToJsonElement(response.bodyAsText())

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { userCarService.getAllUserCars() }
    }

    private fun createTestToken(userId: UUID): String {
        return JWT.create()
            .withClaim("userId", userId.toString())
            .withExpiresAt(Date(System.currentTimeMillis() + 60000))
            .sign(Algorithm.HMAC256("test-secret-key"))

    }
}
