import com.carspotter.features.car_model.ICarModelDAO
import com.carspotter.features.car_model.CarModel
import com.carspotter.features.car_model.CarModelTable
import com.carspotter.di.daoModule
import data.testutils.TestDatabase
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.inject

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CarModelDAOTest: KoinTest {

    private val carModelDao: ICarModelDAO by inject()

    @BeforeAll
    fun setupDatabase() {
        TestDatabase.start()

        Database.connect(
            url = TestDatabase.postgresContainer.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = TestDatabase.postgresContainer.username,
            password = TestDatabase.postgresContainer.password
        )

        startKoin {
            modules(daoModule)
        }

        transaction {
            SchemaUtils.create(CarModelTable)
        }
    }

    @BeforeEach
    fun cleanDatabase() {
        transaction {
            CarModelTable.deleteAll()
        }
    }

    @Test
    fun `get car models for brand - returns models for specific brand`() = runBlocking {

        carModelDao.createCarModel(CarModel(brand = "Toyota", model = "Camry", startYear = 2021, endYear = 2022))
        carModelDao.createCarModel(CarModel(brand = "Toyota", model = "Corolla", startYear = 2022, endYear = 2024))
        carModelDao.createCarModel(CarModel(brand = "Toyota", model = "Prius", startYear = 2018, endYear = 2022))
        carModelDao.createCarModel(CarModel(brand = "Honda", model = "Civic", startYear = 2014, endYear = 2019))
        carModelDao.createCarModel(CarModel(brand = "Honda", model = "Accord", startYear = 2024, endYear = 2025))

        val toyotaModels = carModelDao.getCarModelsForBrand("Toyota")
        val hondaModels = carModelDao.getCarModelsForBrand("Honda")

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
        val carModelId = carModelDao.createCarModel(
            CarModel(
                brand = "Tesla",
                model = "Model S",
                startYear = 2022,
                endYear = 2024
            )
        )

        val result = carModelDao.getCarModelId("Tesla", "Model S")

        assertEquals(carModelId, result)
    }

    @Test
    fun `getCarModelId returns null when brand and model do not exist`() = runBlocking {
        val result = carModelDao.getCarModelId("NonExistentBrand", "NonExistentModel")

        assertNull(result)
    }

    @Test
    fun `create and retrieve a car model`() = runBlocking {
        val carModelId = carModelDao.createCarModel(
            CarModel(
                brand = "Tesla",
                model = "Model S",
                startYear = 2022,
                endYear = 2024
            )
        )
        val retrievedCarModel = carModelDao.getCarModel(carModelId)

        assertNotNull(retrievedCarModel)
        Assertions.assertEquals("Tesla", retrievedCarModel.brand)
        Assertions.assertEquals("Model S", retrievedCarModel.model)
    }

    @Test
    fun `get all car models`() = runBlocking {
        carModelDao.createCarModel(
            CarModel(
                brand = "BMW",
                model = "M3",
                startYear = 2020,
                endYear = 2025
            )
        )
        carModelDao.createCarModel(
            CarModel(
                brand = "Audi",
                model = "A4",
                startYear = 2016,
                endYear = 2020
            )
        )

        val carModels = carModelDao.getAllCarModels()

        Assertions.assertEquals(2, carModels.size)
        Assertions.assertTrue(carModels.any { it.brand == "BMW" && it.model == "M3"})
        Assertions.assertTrue(carModels.any { it.brand == "Audi" && it.model == "A4" })
    }



    @Test
    fun `delete a car model`() = runBlocking {
        val carModelId = carModelDao.createCarModel(
            CarModel(
                brand = "Mercedes",
                model = "C-Class",
                startYear = 2019,
                endYear = 2023
            )
        )

        carModelDao.deleteCarModel(carModelId)
        val deletedCarModel = carModelDao.getCarModel(carModelId)

        assertNull(deletedCarModel)
    }

    @AfterAll
    fun tearDown() {
        transaction {
            SchemaUtils.drop(CarModelTable)
        }
        stopKoin()
    }
}
