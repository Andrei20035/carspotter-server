package routes

import com.carspotter.config.configureSerialization
import com.carspotter.features.car_model.CarModel
import com.carspotter.features.car_model.ICarModelService
import com.carspotter.features.car_model.carModelRoutes
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.*
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import java.util.*
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CarModelRoutesTest : KoinTest {

    private lateinit var carModelService: ICarModelService

    @BeforeAll
    fun setup() {
        carModelService = mockk()
    }

    @BeforeEach
    fun setupKoin() {
        startKoin {
            modules(
                module {
                    single { carModelService }
                }
            )
        }
    }

    private fun Application.configureTestApplication() {
        System.setProperty("JWT_SECRET", "test-secret-key")

        configureSerialization()

        routing {
            carModelRoutes()
        }
    }

    @AfterEach
    fun tearDownKoin() {
        stopKoin()
        clearAllMocks()
    }

    @Test
    fun `GET all car models returns models when available`() = testApplication {
        application {
            configureTestApplication()
        }

        val carModelId1 = UUID.randomUUID()
        val carModelId2 = UUID.randomUUID()

        val carModels = listOf(
            CarModel(carModelId1, "BMW M3", "Sedan", 2022, 2025),
            CarModel(carModelId2, "Audi R8", "Coupe", 2020, 2022)
        )
        coEvery { carModelService.getAllCarModels() } returns carModels

        val response = client.get("/car-models")

        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.parseToJsonElement("""[{"id":"$carModelId1","brand":"BMW M3","model":"Sedan","startYear":2022, "endYear": 2025}, {"id":"$carModelId2","brand":"Audi R8","model":"Coupe","startYear":2020, "endYear":2022}]""").jsonArray
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonArray

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { carModelService.getAllCarModels() }
    }

    @Test
    fun `GET all car models returns 404 when empty`() = testApplication {
        application {
            configureTestApplication()
        }

        coEvery { carModelService.getAllCarModels() } returns emptyList()

        val response = client.get("/car-models")

        assertEquals(HttpStatusCode.NotFound, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"No car models found"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { carModelService.getAllCarModels() }
    }

    @Test
    fun `GET car model by ID returns model when found`() = testApplication {
        application {
            configureTestApplication()
        }

        val id = UUID.randomUUID()

        val model = CarModel(id, "Ferrari 488", "Coupe", 2022, 2025)
        coEvery { carModelService.getCarModelById(id) } returns model

        val response = client.get("/car-models/$id")

        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.encodeToJsonElement(model).jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { carModelService.getCarModelById(id) }
    }

    @Test
    fun `GET car model by ID returns 404 when not found`() = testApplication {
        application {
            configureTestApplication()
        }

        val id = UUID.randomUUID()

        coEvery { carModelService.getCarModelById(id) } returns null

        val response = client.get("/car-models/$id")

        assertEquals(HttpStatusCode.NotFound, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Car model with ID $id not found"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { carModelService.getCarModelById(id) }
    }

    @Test
    fun `GET car model by ID returns 400 for invalid ID`() = testApplication {
        application {
            configureTestApplication()
        }

        val response = client.get("/car-models/abc")

        assertEquals(HttpStatusCode.BadRequest, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Invalid or missing modelId"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

}