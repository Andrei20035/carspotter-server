package data.dao

import com.carspotter.data.dao.auth_credential.IAuthCredentialDAO
import com.carspotter.features.auth.AuthCredential
import com.carspotter.features.auth.AuthProvider
import com.carspotter.features.auth.AuthTable
import com.carspotter.di.daoModule
import data.testutils.SchemaSetup
import data.testutils.TestDatabase
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertTrue
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthTableDaoTest: KoinTest {

    private val authCredentialsDao: IAuthCredentialDAO by inject()

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

    }

    @BeforeEach
    fun clearDatabase() {
        transaction {
            AuthTable.deleteAll()
        }
    }

    @Test
    fun `get credentials for login`() = runBlocking {
        authCredentialsDao.createCredentials(
            AuthCredential(
                email = "test@test.com",
                password = null,
                googleId = "2311",
                provider = AuthProvider.GOOGLE
            )
        )

        val credentials = authCredentialsDao.getCredentialsForLogin("test@test.com")
        assertNotNull(credentials)
        assertEquals("test@test.com", credentials.email)
        assertEquals(null, credentials.password)
        assertEquals("2311", credentials.googleId)
        assertEquals(AuthProvider.GOOGLE, credentials.provider)
    }

    @Test
    fun `get credentials by ID`() = runBlocking {
        val credentialID = authCredentialsDao.createCredentials(
            AuthCredential(
                email = "test@test.com",
                password = null,
                googleId = "2311",
                provider = AuthProvider.GOOGLE
            )
        )

        val credentials = authCredentialsDao.getCredentialsById(credentialID)
        assertNotNull(credentials)
        assertEquals("test@test.com", credentials.email)
        assertEquals("2311", credentials.googleId)
        assertEquals(AuthProvider.GOOGLE, credentials.provider)
    }

    @Test
    fun `update password`() = runBlocking {
        val credentialID = authCredentialsDao.createCredentials(
            AuthCredential(
                email = "test@test.com",
                password = "test",
                googleId = null,
                provider = AuthProvider.REGULAR
            )
        )

        authCredentialsDao.updatePassword(credentialID, "newPassword")

        val credentials = authCredentialsDao.getCredentialsForLogin("test@test.com")
        assertNotNull(credentials)
        assertEquals("newPassword", credentials.password)
    }

    @Test
    fun `show all credentials`() = runBlocking {
        authCredentialsDao.createCredentials(
            AuthCredential(
                email = "test1@test.com",
                password = null,
                googleId = "2311",
                provider = AuthProvider.GOOGLE
            )
        )
        authCredentialsDao.createCredentials(
            AuthCredential(
                email = "test2@test.com",
                password = "test2",
                googleId = null,
                provider = AuthProvider.REGULAR
            )
        )

        val allCredentials = authCredentialsDao.getAllCredentials()
        assertEquals(2, allCredentials.size)
    }

    @Test
    fun `delete credentials`() = runBlocking {
        val credentialID = authCredentialsDao.createCredentials(
            AuthCredential(
                email = "test@test.com",
                password = null,
                googleId = "2311",
                provider = AuthProvider.GOOGLE
            )
        )

        val rowsDeleted = authCredentialsDao.deleteCredentials(credentialID)
        val allCredentials = authCredentialsDao.getAllCredentials()

        assertTrue(allCredentials.isEmpty())
        assertEquals(1, rowsDeleted)
    }

    @Test
    fun `insert a credential with REGULAR provider and googleId not null`() = runBlocking {
        try {
            authCredentialsDao.createCredentials(
                AuthCredential(
                    email = "test@test.com",
                    password = "test",
                    googleId = "1234",
                    provider = AuthProvider.REGULAR
                )
            )
        } catch(e: Exception) {
            println("Insert failed as expected: ${e.message}")
        }

        val allCredentials = authCredentialsDao.getAllCredentials()
        assertEquals(0, allCredentials.size)
    }



    @AfterAll
    fun tearDown() {
        transaction {
            SchemaUtils.drop(AuthTable)
        }
        stopKoin()
    }
}