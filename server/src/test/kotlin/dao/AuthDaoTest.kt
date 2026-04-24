package dao

import com.carspotter.features.auth.AuthDAO
import com.carspotter.features.auth.AuthProvider
import testutils.TestDataFactory
import testutils.TestDatabaseFactory

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthDaoTest {

    private val dao = AuthDAO()

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
    }

    @Test
    fun `createCredentials inserts and returns UUID`() = runTest {
        val cred = TestDataFactory.regularCredential(email = "alice@example.com")
        val id = dao.createCredentials(cred)
        assertNotNull(id)
    }

    @Test
    fun `getCredentialsById returns correct credential`() = runTest {
        val cred = TestDataFactory.regularCredential(email = "alice@example.com")
        val id = dao.createCredentials(cred)

        val fetched = dao.getCredentialsById(id)

        assertNotNull(fetched)
        assertEquals("alice@example.com", fetched!!.email)
        assertEquals(AuthProvider.REGULAR, fetched.provider)
        assertEquals(id, fetched.id)
    }

    @Test
    fun `getCredentialsById returns null for unknown id`() = runTest {
        val fetched = dao.getCredentialsById(UUID.randomUUID())
        assertNull(fetched)
    }

    @Test
    fun `getCredentialsForLogin returns credential by email`() = runTest {
        val cred = TestDataFactory.regularCredential(email = "alice@example.com")
        dao.createCredentials(cred)

        val fetched = dao.getCredentialsForLogin("alice@example.com")

        assertNotNull(fetched)
        assertEquals("alice@example.com", fetched!!.email)
    }

    @Test
    fun `getCredentialsForLogin returns null when email not found`() = runTest {
        val fetched = dao.getCredentialsForLogin("nobody@example.com")
        assertNull(fetched)
    }

    @Test
    fun `updatePassword modifies the password and returns 1`() = runTest {
        val cred = TestDataFactory.regularCredential(email = "alice@example.com")
        val id = dao.createCredentials(cred)
        val originalHash = dao.getCredentialsById(id)!!.password

        val updatedRows = dao.updatePassword(id, "new-hashed-password")

        assertEquals(1, updatedRows)
        val after = dao.getCredentialsById(id)
        assertNotNull(after)
        assertEquals("new-hashed-password", after!!.password)
        assertNotEquals(originalHash, after.password)
    }

    @Test
    fun `updatePassword returns 0 when id does not exist`() = runTest {
        val rows = dao.updatePassword(UUID.randomUUID(), "whatever")
        assertEquals(0, rows)
    }

    @Test
    fun `deleteCredentials removes row and returns 1`() = runTest {
        val cred = TestDataFactory.regularCredential(email = "alice@example.com")
        val id = dao.createCredentials(cred)

        val deleted = dao.deleteCredentials(id)

        assertEquals(1, deleted)
        assertNull(dao.getCredentialsById(id))
    }

    @Test
    fun `duplicate email throws a constraint violation`() = runTest {
        val cred = TestDataFactory.regularCredential(email = "dup@example.com")
        dao.createCredentials(cred)

        // Exposed va propaga SQLException pentru unique constraint.
        // Exposed împachetează de obicei într-un ExposedSQLException, care extinde SQLException.
        assertThrows(Exception::class.java) {
            runTest {
                dao.createCredentials(cred.copy())
            }
        }
    }
}