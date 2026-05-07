package routes

import com.carspotter.features.car_model.dto.CarModelOption
import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import testutils.CarModelTestSeed
import testutils.TestDatabaseFactory
import testutils.stopKoinSafely
import testutils.testCarModelModule

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CarModelRoutesTest {

    @BeforeAll
    fun setup() {
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

    private fun carModelTest(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) =
        testApplication {
            application { testCarModelModule() }
            val client = createClient {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; isLenient = true })
                }
            }
            block(client)
        }

    // ---------- GET /car-models/brands ----------

    @Test
    fun `GET brands returns 200 and JSON array`() = carModelTest { client ->
        CarModelTestSeed.seedDefault()

        val resp = client.get("/api/car-models/brands")

        assertEquals(HttpStatusCode.OK, resp.status)
        val body: List<String> = resp.body()
        assertEquals(3, body.size) // bmw, audi, alfa romeo (distinct)
    }

    @Test
    fun `GET brands returns empty array not 404 when no brands exist`() = carModelTest { client ->
        // DB curată — niciun seed.

        val resp = client.get("/api/car-models/brands")

        assertEquals(HttpStatusCode.OK, resp.status)
        val body: List<String> = resp.body()
        assertTrue(body.isEmpty(), "expected empty array, not 404")
    }

    @Test
    fun `GET brands returns brands sorted alphabetically`() = carModelTest { client ->
        CarModelTestSeed.insertModel("toyota", "corolla")
        CarModelTestSeed.insertModel("audi", "a3")
        CarModelTestSeed.insertModel("bmw", "m3")
        CarModelTestSeed.insertModel("alfa romeo", "giulia")

        val resp = client.get("/api/car-models/brands")

        assertEquals(HttpStatusCode.OK, resp.status)
        val body: List<String> = resp.body()
        assertEquals(listOf("alfa romeo", "audi", "bmw", "toyota"), body)
    }

    // ---------- GET /car-models/brands/{brand}/models ----------

    @Test
    fun `GET models returns 200 and JSON array of objects with id and model`() = carModelTest { client ->
        val seeded = CarModelTestSeed.seedDefault()

        val resp = client.get("/api/car-models/brands/bmw/models")

        assertEquals(HttpStatusCode.OK, resp.status)
        val body: List<CarModelOption> = resp.body()
        assertEquals(2, body.size)
        // Verificăm structura: avem id non-null și model non-blank
        body.forEach {
            assertNotNull(it.id)
            assertTrue(it.model.isNotBlank())
        }
        // Și că id-urile corespund seed-ului
        val expectedIds = setOf(seeded.bmwM3, seeded.bmwM4)
        assertEquals(expectedIds, body.map { it.id }.toSet())
        assertEquals(listOf("m3", "m4"), body.map { it.model }) // și sortate
    }

    @Test
    fun `GET models returns empty array not 404 when brand has no models`() = carModelTest { client ->
        CarModelTestSeed.seedDefault()

        val resp = client.get("/api/car-models/brands/nonexistent/models")

        assertEquals(HttpStatusCode.OK, resp.status)
        val body: List<CarModelOption> = resp.body()
        assertTrue(body.isEmpty(), "expected empty array, not 404")
    }

    @Test
    fun `GET models normalizes uppercase brand path param`() = carModelTest { client ->
        CarModelTestSeed.seedDefault()

        // Datele în DB sunt "bmw" lowercase. Cererea vine cu "BMW".
        // Service-ul trebuie să normalizeze și să returneze rezultatele corecte.
        val resp = client.get("/api/car-models/brands/BMW/models")

        assertEquals(HttpStatusCode.OK, resp.status)
        val body: List<CarModelOption> = resp.body()
        assertEquals(2, body.size)
        assertEquals(setOf("m3", "m4"), body.map { it.model }.toSet())
    }

    @Test
    fun `GET models works when stored brand is uppercase like imported CSV`() = carModelTest { client ->
        CarModelTestSeed.insertModel("ARO", "10 Series")
        CarModelTestSeed.insertModel("ARO", "24 Series")

        val resp = client.get("/api/car-models/brands/ARO/models")

        assertEquals(HttpStatusCode.OK, resp.status)
        val body: List<CarModelOption> = resp.body()
        assertEquals(listOf("10 Series", "24 Series"), body.map { it.model })
    }

    @Test
    fun `GET models works with URL-encoded multi-word brand`() = carModelTest { client ->
        CarModelTestSeed.seedDefault()

        // "Alfa Romeo" cu spațiu URL-encoded ca %20.
        val resp = client.get("/api/car-models/brands/Alfa%20Romeo/models")

        assertEquals(HttpStatusCode.OK, resp.status)
        val body: List<CarModelOption> = resp.body()
        assertEquals(1, body.size)
        assertEquals("giulia", body.first().model)
    }

    @Test
    fun `GET models with blank brand returns 400`() = carModelTest { client ->
        // Un singur spațiu URL-encoded ca path param → după trim devine blank.
        val resp = client.get("/api/car-models/brands/%20/models")

        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }
}
