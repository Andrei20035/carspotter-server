package data.service

import at.favre.lib.crypto.bcrypt.BCrypt
import com.carspotter.features.auth.AuthCredential
import com.carspotter.features.auth.AuthProvider
import com.carspotter.data.repository.auth_credential.IAuthCredentialRepository
import com.carspotter.features.auth.AuthService
import com.carspotter.features.auth.IAuthService
import com.carspotter.features.auth.AuthTable
import com.carspotter.di.daoModule
import com.carspotter.di.repositoryModule
import com.carspotter.di.serviceModule
import data.testutils.FakeGoogleTokenVerifier
import data.testutils.SchemaSetup
import data.testutils.TestDatabase
import io.mockk.coEvery
import io.mockk.mockk
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
class AuthCredentialServiceTest: KoinTest {

    private val authCredentialService: IAuthService by inject()

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

        SchemaSetup.createAuthCredentialsTableWithConstraint(AuthTable)
    }

    @BeforeEach
    fun clearDatabase() {
        transaction {
            AuthTable.deleteAll()
        }
    }

    @Test
    fun `get credentials by ID`() = runBlocking {
        val id = authCredentialService.createCredentials(
            AuthCredential(
                email = "credentials@test.com",
                password = null,
                googleId = "credentialsGID",
                provider = AuthProvider.GOOGLE
            )
        )

        val credentials = authCredentialService.getCredentialsById(id)

        assertNotNull(credentials)
        assertEquals("credentials@test.com", credentials.email)
        assertEquals("credentialsGID", credentials.googleId)
    }

    @Test
    fun `get credentials for regular login`() = runBlocking {
        val id = authCredentialService.createCredentials(
            AuthCredential(
                email = "credentials@test.com",
                password = "passwordtest",
                googleId = null,
                provider = AuthProvider.REGULAR
            )
        )

        val authCredential = authCredentialService.regularLogin("credentials@test.com", "passwordtest")

        assertNotNull(authCredential)
        assertEquals("credentials@test.com", authCredential.email)
    }

    @Test
    fun `get credentials for google login`() = runBlocking {

        val fakeVerifier = FakeGoogleTokenVerifier()
        val mockRepo = mockk<IAuthCredentialRepository>()


        val testCredential = AuthCredential(
            email = "credentials@test.com",
            password = null,
            googleId = "google123",
            provider = AuthProvider.GOOGLE
        )

        coEvery { mockRepo.getCredentialsForLogin("credentials@test.com") } returns testCredential

        val authCredentialService = AuthService(
            authCredentialRepository = mockRepo,
            googleTokenVerifier = fakeVerifier
        )

        val authCredential = authCredentialService.googleLogin("credentials@test.com", "gid1")

        assertNotNull(authCredential)
        assertEquals("credentials@test.com", authCredential.email)
    }

    @Test
    fun `update password changes the stored password`() = runBlocking {
        val id = authCredentialService.createCredentials(
            AuthCredential(
                email = "update@test.com",
                password = "oldPass",
                googleId = null,
                provider = AuthProvider.REGULAR
            )
        )

        authCredentialService.updatePassword(id, "newPass")

        val result = authCredentialService.getCredentialsById(id)
        assertNotNull(result)
        val isPasswordValid = BCrypt.verifyer().verify(
            "newPass".toCharArray(),
            result.password?.toCharArray()
        ).verified
        assertTrue(isPasswordValid)
    }

    @Test
    fun `delete credentials removes them from db`() = runBlocking {
        val id = authCredentialService.createCredentials(
            AuthCredential(
                email = "delete@test.com",
                password = null,
                googleId = "gid",
                provider = AuthProvider.GOOGLE
            )
        )

        val deleted = authCredentialService.deleteCredentials(id)

        assertEquals(1, deleted)
    }

    @AfterAll
    fun tearDown() {
        transaction {
            SchemaUtils.drop(AuthTable)
        }
        stopKoin()
    }
}