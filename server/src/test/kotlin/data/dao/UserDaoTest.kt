package data.dao

import com.carspotter.data.dao.auth_credential.IAuthCredentialDAO
import com.carspotter.features.user.IUserDAO
import com.carspotter.features.auth.AuthCredential
import com.carspotter.features.auth.AuthProvider
import com.carspotter.features.user.User
import com.carspotter.features.auth.AuthTable
import com.carspotter.features.user.UserTable
import com.carspotter.di.daoModule
import data.testutils.SchemaSetup
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
import java.time.LocalDate
import java.util.*
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserDaoTest: KoinTest {

    private val userDao: IUserDAO by inject()
    private val authCredentialDao: IAuthCredentialDAO by inject()

    private var credentialId1: UUID = UUID.randomUUID()
    private var credentialId2: UUID = UUID.randomUUID()

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

        SchemaSetup.createAuthCredentialsTableWithConstraint(AuthTable)
        SchemaSetup.createUsersTable(UserTable)
    }

    @BeforeEach
    fun clearDatabase() {
        transaction {
            AuthTable.deleteAll()

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
            }
        }
    }

    @Test
    fun `get user by ID`() = runBlocking {
        val userID = userDao.createUser(
            User(
                authCredentialId = credentialId1,
                fullName = "Peter Parker",
                phoneNumber = "0712453678",
                birthDate = LocalDate.of(2003, 11, 8),
                username = "Socate123",
                country = "USA"
            )
        )
        val retrievedUser = userDao.getUserByID(userID)

        assertNotNull(retrievedUser)
        assertEquals(userID, retrievedUser.id)
        assertEquals(null, retrievedUser.profilePicturePath)
        assertEquals(LocalDate.of(2003, 11, 8), retrievedUser.birthDate)
        assertEquals("Socate123", retrievedUser.username)
        assertEquals("USA", retrievedUser.country)
        assertEquals(0, retrievedUser.spotScore)

    }

    @Test
    fun `get user by username should return a list of users`() = runBlocking {
        userDao.createUser(
            User(
                authCredentialId = credentialId1,
                fullName = "Peter Parker",
                phoneNumber = "0712453678",
                birthDate = LocalDate.of(2003, 11, 8),
                username = "Socate123",
                country = "USA"
            )
        )
        userDao.createUser(
            User(
                authCredentialId = credentialId2,
                fullName = "Mary Jane",
                phoneNumber = "0712453678",
                birthDate = LocalDate.of(2004, 4, 1),
                username = "Socate321",
                country = "USA"
            )
        )
        val retrievedUsers1 = userDao.getUserByUsername("Socate")

        assertTrue(retrievedUsers1.isNotEmpty())
        assertEquals(2, retrievedUsers1.size)

        val retrievedUsers2 = userDao.getUserByUsername("Socate321")

        assertTrue(retrievedUsers2.isNotEmpty())
        assertEquals(1, retrievedUsers2.size)
        assertEquals("Mary Jane", retrievedUsers2[0].fullName)
    }

    @Test
    fun `get all users`() = runBlocking {
        val userId1 = userDao.createUser(
            User(
                authCredentialId = credentialId1,
                fullName = "Peter Parker",
                phoneNumber = "0712453678",
                birthDate = LocalDate.of(2003, 11, 8),
                username = "Socate123",
                country = "USA"
            )
        )
        val userId2 = userDao.createUser(
            User(
                authCredentialId = credentialId2,
                fullName = "Mary Jane",
                phoneNumber = "0712453678",
                birthDate = LocalDate.of(2004, 4, 1),
                username = "Socate321",
                country = "USA"
            )
        )

        val users = userDao.getAllUsers()

        assertEquals(2, users.size)

        val user1 = users.find { it.id == userId1 }
        assertNotNull(user1)
        assertEquals("Socate123", user1.username)
        assertEquals(LocalDate.of(2003, 11, 8), user1.birthDate)
        assertEquals("USA", user1.country)

        // Assert details of the second user
        val user2 = users.find { it.id == userId2 }
        assertNotNull(user2)
        assertEquals("Socate321", user2.username)
        assertEquals(LocalDate.of(2004, 4, 1), user2.birthDate)
        assertEquals("USA", user2.country)
    }

    @Test
    fun `update profile picture`() = runBlocking {
        val userId = userDao.createUser(
            User(
                authCredentialId = credentialId1,
                fullName = "Peter Parker",
                phoneNumber = "0712453678",
                birthDate = LocalDate.of(2003, 11, 8),
                username = "Socate123",
                country = "USA"
            )
        )

        // Make sure user is not null before updating
        val userBeforeUpdate = userDao.getUserByID(userId)
        assertNotNull(userBeforeUpdate)
        Assertions.assertNull(userBeforeUpdate.profilePicturePath)

        // Update profile picture
        userDao.updateProfilePicture(userId, "/path/to/new/picture")

        val retrievedUser = userDao.getUserByID(userId)

        // Make sure the user is not null after updating
        assertNotNull(retrievedUser)
        assertEquals("/path/to/new/picture", retrievedUser.profilePicturePath)
    }

    @Test
    fun `delete user`() = runBlocking {
        val userId = userDao.createUser(
            User(
                authCredentialId = credentialId1,
                fullName = "Peter Parker",
                phoneNumber = "0712453678",
                birthDate = LocalDate.of(2003, 11, 8),
                username = "Socate123",
                country = "USA"
            )
        )

        val userBeforeDeletion = userDao.getUserByID(userId)
        assertNotNull(userBeforeDeletion)

        userDao.deleteUser(credentialId1)

        val userAfterDeletion = userDao.getUserByID(userId)
        assertNull(userAfterDeletion)
    }

    @AfterAll
    fun tearDown() {
        transaction {
            SchemaUtils.drop(UserTable, AuthTable)
        }
        stopKoin()
    }
}
