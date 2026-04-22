package data.dao

import com.carspotter.data.dao.auth_credential.IAuthCredentialDAO
import com.carspotter.features.car_model.ICarModelDAO
import com.carspotter.features.user.IUserDAO
import com.carspotter.features.user_car.IUserCarDAO
import com.carspotter.data.model.*
import com.carspotter.features.auth.AuthTable
import com.carspotter.features.car_model.CarModelTable
import com.carspotter.features.user.UserTable
import com.carspotter.features.user_car.UserCarTable
import com.carspotter.di.daoModule
import com.carspotter.features.auth.AuthCredential
import com.carspotter.features.auth.AuthProvider
import com.carspotter.features.car_model.CarModel
import com.carspotter.features.user.User
import com.carspotter.features.user_car.UserCar
import data.testutils.SchemaSetup
import data.testutils.TestDatabase
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.inject
import java.time.LocalDate
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserCarDaoTest: KoinTest {

    private val userCarDao: IUserCarDAO by inject()
    private val userDao: IUserDAO by inject()
    private val carModelDao: ICarModelDAO by inject()
    private val authCredentialDao: IAuthCredentialDAO by inject()

    private var credentialId1: UUID = UUID.randomUUID()
    private var credentialId2: UUID = UUID.randomUUID()
    private var userId1: UUID = UUID.randomUUID()
    private var userId2: UUID = UUID.randomUUID()
    private var userCarId1: UUID = UUID.randomUUID()
    private var carModelId1: UUID = UUID.randomUUID()
    private var carModelId2: UUID = UUID.randomUUID()


    @BeforeAll
    fun setupDatabase() {
        Database.connect(
            url = TestDatabase.postgresContainer.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = TestDatabase.postgresContainer.username,
            password = TestDatabase.postgresContainer.password
        )

        startKoin {
            modules(daoModule)
        }

        SchemaSetup.createUsersTable(UserTable)
        SchemaSetup.createUsersCarsTable(UserCarTable)
        SchemaSetup.createAuthCredentialsTableWithConstraint(AuthTable)
        SchemaSetup.createCarModelsTable(CarModelTable)

        runBlocking {
            credentialId1 = authCredentialDao.createCredentials(
                AuthCredential(
                    email = "test1@test.com",
                    password = null,
                    googleId = "231122",
                    provider = AuthProvider.GOOGLE
                )
            )
            credentialId2 = authCredentialDao.createCredentials(
                AuthCredential(
                    email = "test2@test.com",
                    password = "test2",
                    googleId = null,
                    provider = AuthProvider.REGULAR
                )
            )
            userId1 = userDao.createUser(
                User(
                    authCredentialId = credentialId1,
                    fullName = "Peter Parker",
                    phoneNumber = "0712453678",
                    birthDate = LocalDate.of(2003, 11, 8),
                    username = "Socate123",
                    country = "USA"
                )
            )
            userId2 = userDao.createUser(
                User(
                    authCredentialId = credentialId2,
                    fullName = "Mary Jane",
                    phoneNumber = "0712453678",
                    birthDate = LocalDate.of(2004, 4, 1),
                    username = "Socate321",
                    country = "USA"
                )
            )
            carModelId1 = carModelDao.createCarModel(
                CarModel(
                    brand = "BMW",
                    model = "M3",
                    startYear = 2020,
                    endYear = 2023
                )
            )
            carModelId2 = carModelDao.createCarModel(
                CarModel(
                    brand = "Audi",
                    model = "A4",
                    startYear = 2022,
                    endYear = 2024,
                )
            )
        }
    }

    @BeforeEach
    fun cleanDatabase() {
        transaction {
            UserCarTable.deleteAll()
        }
    }

    @Test
    fun `create and retrieve user car by ID`() = runBlocking {
        userCarId1 = userCarDao.createUserCar(
            UserCar(
                userId = userId1,
                carModelId = carModelId1,
                imagePath = "path/to/car/image"
            )
        )
        val userCar = userCarDao.getUserCarById(userCarId1)

        assertNotNull(userCar)
        assertEquals(userId1, userCar?.userId)
        assertEquals(carModelId1, userCar?.carModelId)
        assertEquals("path/to/car/image", userCar?.imagePath)
    }

    @Test
    fun `get user car by user ID`() = runBlocking {
        userCarId1 = userCarDao.createUserCar(
            UserCar(
                userId = userId1,
                carModelId = carModelId1,
                imagePath = "path/to/car/image"
            )
        )
        val userCar = userCarDao.getUserCarByUserId(userId1)

        assertNotNull(userCar)
        assertEquals(userId1, userCar?.userId)
        assertEquals(carModelId1, userCar?.carModelId)
        assertEquals("path/to/car/image", userCar?.imagePath)
    }

    @Test
    fun `get user by user car ID`() = runBlocking {
        userCarId1 = userCarDao.createUserCar(
            UserCar(
                userId = userId1,
                carModelId = carModelId1,
                imagePath = "path/to/car/image"
            )
        )

        val user = userCarDao.getUserByUserCarId(userCarId1)

        assertNotNull(user)
        assertEquals(userId1, user.id)
        assertEquals("Peter Parker", user.fullName)
    }

    @Test
    fun `update user car`() = runBlocking {
        userCarDao.createUserCar(
            UserCar(
                userId = userId1,
                carModelId = carModelId1,
                imagePath = "path/to/car/image"
            )
        )
        // Update only image path
        userCarDao.updateUserCar(userId1, "new/path/to/car/image", null)

        var userCar = userCarDao.getUserCarByUserId(userId1)

        assertNotNull(userCar)
        assertEquals("new/path/to/car/image", userCar?.imagePath)
        assertEquals(carModelId1, userCar?.carModelId)

        // Update only the carModelId
        userCarDao.updateUserCar(userId1, null, carModelId2)

        userCar = userCarDao.getUserCarByUserId(userId1)

        assertNotNull(userCar)
        assertEquals("new/path/to/car/image", userCar?.imagePath)
        assertEquals(carModelId2, userCar?.carModelId)
    }

    @Test
    fun `delete user car`() = runBlocking {
        userCarId1 = userCarDao.createUserCar(
            UserCar(
                userId = userId1,
                carModelId = carModelId1,
                imagePath = "path/to/car/image"
            )
        )
        var userCars = userCarDao.getAllUserCars()
        assertTrue(userCars.size == 1)

        // Delete the user car
        userCarDao.deleteUserCar(userId1)

        // Verify the deletion
        userCars = userCarDao.getAllUserCars()
        assertTrue(userCars.isEmpty())
    }

    @Test
    fun `get all user cars`() = runBlocking {
        userCarDao.createUserCar(
            UserCar(
                userId = userId1,
                carModelId = carModelId1,
                imagePath = "path/to/car/image1"
            )
        )
        userCarDao.createUserCar(
            UserCar(
                userId = userId2,
                carModelId = carModelId2,
                imagePath = "path/to/car/image2"
            )
        )

        val allUserCars = userCarDao.getAllUserCars()

        assertTrue(allUserCars.size == 2)
    }

    @AfterAll
    fun tearDown() {
        transaction {
            SchemaUtils.drop(UserCarTable, UserTable, CarModelTable, AuthTable)
        }
        stopKoin()
    }
}
