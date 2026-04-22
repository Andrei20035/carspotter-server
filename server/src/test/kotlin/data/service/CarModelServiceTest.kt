package data.service

import com.carspotter.features.car_model.CarModel
import com.carspotter.features.car_model.ICarModelService
import com.carspotter.features.car_model.CarModelTable
import com.carspotter.di.daoModule
import com.carspotter.di.repositoryModule
import com.carspotter.di.serviceModule
import data.testutils.SchemaSetup
import data.testutils.TestDatabase
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.inject
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CarModelServiceTest: KoinTest {

    private val carModelService: ICarModelService by inject()

    @BeforeAll
    fun setup() {
        Database.connect(
            url = TestDatabase.postgresContainer.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = TestDatabase.postgresContainer.username,
            password = TestDatabase.postgresContainer.password
        )

        startKoin {
            modules(daoModule, repositoryModule, serviceModule)
        }

        SchemaSetup.createCarModelsTable(CarModelTable)
    }

    @BeforeEach
    fun clearDatabase() {
        transaction {
            CarModelTable.deleteAll()
        }
    }

    @Test
    fun `get car models for brand - returns models for specific brand`() = runBlocking {

        carModelService.createCarModel(CarModel(brand = "Toyota", model = "Camry", startYear = 2021, endYear = 2022))
        carModelService.createCarModel(CarModel(brand = "Toyota", model = "Corolla", startYear = 2022, endYear = 2024))
        carModelService.createCarModel(CarModel(brand = "Toyota", model = "Prius", startYear = 2018, endYear = 2022))
        carModelService.createCarModel(CarModel(brand = "Honda", model = "Civic", startYear = 2014, endYear = 2019))
        carModelService.createCarModel(CarModel(brand = "Honda", model = "Accord", startYear = 2024, endYear = 2025))

        val toyotaModels = carModelService.getCarModelsForBrand("Toyota")
        val hondaModels = carModelService.getCarModelsForBrand("Honda")

        Assertions.assertEquals(3, toyotaModels.size)
        Assertions.assertTrue(toyotaModels.contains("Camry"))
        Assertions.assertTrue(toyotaModels.contains("Corolla"))
        Assertions.assertTrue(toyotaModels.contains("Prius"))

        Assertions.assertEquals(2, hondaModels.size)
        Assertions.assertTrue(hondaModels.contains("Civic"))
        Assertions.assertTrue(hondaModels.contains("Accord"))
    }

    @Test
    fun `getCarModelId returns correct id when brand and model exist`() = runBlocking {
        val carModelId = carModelService.createCarModel(
            CarModel(
                brand = "Tesla",
                model = "Model S",
                startYear = 2022,
                endYear = 2024
            )
        )

        val result = carModelService.getCarModelId("Tesla", "Model S")

        Assertions.assertEquals(carModelId, result)
    }

    @Test
    fun `getCarModelId returns null when brand and model do not exist`() = runBlocking {
        val result = carModelService.getCarModelId("NonExistentBrand", "NonExistentModel")

        org.junit.jupiter.api.assertNull(result)
    }

    @Test
    fun `create and get car model by id`() = runBlocking {
        val id = carModelService.createCarModel(
            CarModel(
                brand = "Toyota",
                model = "GR Supra",
                startYear = 2019,
                endYear = 2024
            )
        )

        val result = carModelService.getCarModelById(id)

        assertNotNull(result)
        assertEquals("Toyota", result?.brand)
        assertEquals("GR Supra", result?.model)
    }

    @Test
    fun `get all car models returns all items`() = runBlocking {
        carModelService.createCarModel(CarModel(brand = "Lamborghini", model = "Huracan", startYear = 2018, endYear = 2022))
        carModelService.createCarModel(CarModel(brand = "Ferrari", model = "296 GTB", startYear = 2015, endYear = 2020))

        val allModels = carModelService.getAllCarModels()

        assertEquals(2, allModels.size)
        assertTrue(allModels.any { it.brand == "Lamborghini" && it.model == "Huracan" })
        assertTrue(allModels.any { it.brand == "Ferrari" && it.model == "296 GTB" })
    }

    @Test
    fun `delete car model removes it from database`() = runBlocking {
        val id = carModelService.createCarModel(
            CarModel(brand = "Porsche", model = "911 GT3", startYear = 2018, endYear = 2024)
        )

        val deletedCount = carModelService.deleteCarModel(id)
        val result = carModelService.getCarModelById(id)

        assertEquals(1, deletedCount)
        assertNull(result)
    }

    @Test
    fun `get all car brands - returns distinct brands only`() = runBlocking {
        // Insert multiple car models with duplicate brands
        carModelService.createCarModel(CarModel(brand = "Toyota", model = "Camry", startYear = 2021, endYear = 2022))
        carModelService.createCarModel(CarModel(brand = "Toyota", model = "Corolla", startYear = 2022, endYear = 2024))
        carModelService.createCarModel(CarModel(brand = "Toyota", model = "Prius", startYear = 2018, endYear = 2022))
        carModelService.createCarModel(CarModel(brand = "Honda", model = "Civic", startYear = 2014, endYear = 2019))
        carModelService.createCarModel(CarModel(brand = "Honda", model = "Accord", startYear = 2024, endYear = 2025))
        carModelService.createCarModel(CarModel(brand = "Ford", model = "Mustang", startYear = 2020, endYear = 2023))
        carModelService.createCarModel(CarModel(brand = "Ford", model = "Focus", startYear = 2015, endYear = 2018))
        carModelService.createCarModel(CarModel(brand = "BMW", model = "X5", startYear = 2019, endYear = 2024))
        carModelService.createCarModel(CarModel(brand = "BMW", model = "X3", startYear = 2017, endYear = 2021))
        carModelService.createCarModel(CarModel(brand = "Audi", model = "A4", startYear = 2018, endYear = 2022))

        // Call method to get all distinct car brands
        val brands = carModelService.getAllCarBrands()

        // Check that all brands are unique and present
        Assertions.assertEquals(5, brands.size) // Toyota, Honda, Ford, BMW, Audi
        Assertions.assertTrue(brands.contains("Toyota"))
        Assertions.assertTrue(brands.contains("Honda"))
        Assertions.assertTrue(brands.contains("Ford"))
        Assertions.assertTrue(brands.contains("BMW"))
        Assertions.assertTrue(brands.contains("Audi"))
    }


    @AfterAll
    fun tearDown() {
        transaction {
            SchemaUtils.drop(CarModelTable)
        }
        stopKoin()
    }
}