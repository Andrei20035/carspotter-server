package data.repository

import com.carspotter.features.auth.AuthCredential
import com.carspotter.features.auth.AuthProvider
import com.carspotter.data.repository.auth_credential.IAuthCredentialRepository
import com.carspotter.features.auth.AuthTable
import com.carspotter.di.daoModule
import com.carspotter.di.repositoryModule
import data.testutils.SchemaSetup
import data.testutils.TestDatabase
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.context.GlobalContext.stopKoin
import org.koin.test.KoinTest
import org.koin.test.inject

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthCredentialRepositoryTest: KoinTest {

    private val authCredentialRepository: IAuthCredentialRepository by inject()

    @BeforeAll
    fun setup() {
        Database.connect(
            url = TestDatabase.postgresContainer.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = TestDatabase.postgresContainer.username,
            password = TestDatabase.postgresContainer.password
        )

        startKoin {
            modules(daoModule, repositoryModule)
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
    fun `create and get credentials for login`() = runBlocking {
        val id = authCredentialRepository.createCredentials(
            AuthCredential(
                email = "repo@test.com",
                password = null,
                googleId = "gID123",
                provider = AuthProvider.GOOGLE
            )
        )

        val result = authCredentialRepository.getCredentialsForLogin("repo@test.com")

        assertNotNull(result)
        assertEquals(id, result.id)
        assertEquals(null, result.password)
        assertEquals("repo@test.com", result.email)
        assertEquals("gID123", result.googleId)
    }

    @Test
    fun `get credentials by ID returns correct DTO`() = runBlocking {
        val id = authCredentialRepository.createCredentials(
            AuthCredential(
                email = "dto@test.com",
                password = null,
                googleId = "dtoGID",
                provider = AuthProvider.GOOGLE
            )
        )

        val credentials = authCredentialRepository.getCredentialsById(id)

        assertNotNull(credentials)
        assertEquals("dto@test.com", credentials.email)
        assertEquals("dtoGID", credentials.googleId)
    }

    @Test
    fun `update password changes the stored password`() = runBlocking {
        val id = authCredentialRepository.createCredentials(
            AuthCredential(
                email = "update@test.com",
                password = "oldPass",
                googleId = null,
                provider = AuthProvider.REGULAR
            )
        )

        authCredentialRepository.updatePassword(id, "newPass")

        val result = authCredentialRepository.getCredentialsForLogin("update@test.com")
        assertNotNull(result)
        assertEquals("newPass", result.password)
    }

    @Test
    fun `get all credentials returns full list`() = runBlocking {
        authCredentialRepository.createCredentials(
            AuthCredential(
                email = "all1@test.com",
                password = null,
                googleId = "gid1",
                provider = AuthProvider.GOOGLE)
        )
        authCredentialRepository.createCredentials(
            AuthCredential(
                email = "all2@test.com",
                password = "passtest",
                googleId = null,
                provider = AuthProvider.REGULAR
            )
        )

        val all = authCredentialRepository.getAllCredentials()
        assertEquals(2, all.size)
    }

    @Test
    fun `delete credentials removes them from db`() = runBlocking {
        val id = authCredentialRepository.createCredentials(
            AuthCredential(
                email = "delete@test.com",
                password = null,
                googleId = "gid",
                provider = AuthProvider.GOOGLE
            )
        )

        val deleted = authCredentialRepository.deleteCredentials(id)
        val all = authCredentialRepository.getAllCredentials()

        assertEquals(1, deleted)
        assertTrue(all.none { it.email == "delete@test.com" })
    }

    @AfterAll
    fun tearDown() {
        transaction {
            SchemaUtils.drop(AuthTable)
        }
        stopKoin()
    }
}