package dao

import com.carspotter.features.user.UserCreationException
import com.carspotter.features.user.UserDao
import com.carspotter.features.user.UserTable
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import testutils.TestDatabaseFactory
import testutils.UserTestSeed
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserDaoTest {

    private val dao = UserDao()

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
    fun `createUser inserts and returns id`() = runTest {
        val credential = UserTestSeed.seedAuthCredential("alice@example.com")
        val user = UserTestSeed.buildUser(credential.authCredentialId, username = "alice")

        val userId = dao.createUser(user)

        assertNotNull(userId)
        val stored = dao.getUserById(userId)
        assertEquals("alice", stored!!.username)
    }

    @Test
    fun `getUserById returns user when it exists`() = runTest {
        val credential = UserTestSeed.seedAuthCredential("alice@example.com")
        val userId = UserTestSeed.seedUser(credential.authCredentialId, username = "alice")

        val user = dao.getUserById(userId)

        assertNotNull(user)
        assertEquals(userId, user!!.id)
        assertEquals(credential.authCredentialId, user.authCredentialId)
    }

    @Test
    fun `getUserById returns null for unknown id`() = runTest {
        assertNull(dao.getUserById(UUID.randomUUID()))
    }

    @Test
    fun `getUserByAuthCredentialId returns user when it exists`() = runTest {
        val credential = UserTestSeed.seedAuthCredential("alice@example.com")
        UserTestSeed.seedUser(credential.authCredentialId, username = "alice")

        val user = dao.getUserByAuthCredentialId(credential.authCredentialId)

        assertNotNull(user)
        assertEquals("alice", user!!.username)
    }

    @Test
    fun `getUserByAuthCredentialId returns null when profile does not exist`() = runTest {
        val credential = UserTestSeed.seedAuthCredential("alice@example.com")
        assertNull(dao.getUserByAuthCredentialId(credential.authCredentialId))
    }

    @Test
    fun `duplicate username is blocked`() = runTest {
        val firstCredential = UserTestSeed.seedAuthCredential("alice@example.com")
        val secondCredential = UserTestSeed.seedAuthCredential("bob@example.com")
        dao.createUser(UserTestSeed.buildUser(firstCredential.authCredentialId, username = "alice"))

        assertThrows(ExposedSQLException::class.java) {
            kotlinx.coroutines.runBlocking {
                dao.createUser(UserTestSeed.buildUser(secondCredential.authCredentialId, username = "alice"))
            }
        }
    }

    @Test
    fun `duplicate username is blocked case-insensitive`() = runTest {
        val firstCredential = UserTestSeed.seedAuthCredential("alice@example.com")
        val secondCredential = UserTestSeed.seedAuthCredential("bob@example.com")
        dao.createUser(UserTestSeed.buildUser(firstCredential.authCredentialId, username = "alice"))

        assertThrows(ExposedSQLException::class.java) {
            kotlinx.coroutines.runBlocking {
                dao.createUser(UserTestSeed.buildUser(secondCredential.authCredentialId, username = "ALICE"))
            }
        }
    }

    @Test
    fun `auth_credential_id is unique`() = runTest {
        val credential = UserTestSeed.seedAuthCredential("alice@example.com")
        dao.createUser(UserTestSeed.buildUser(credential.authCredentialId, username = "alice"))

        assertThrows(ExposedSQLException::class.java) {
            kotlinx.coroutines.runBlocking {
                dao.createUser(UserTestSeed.buildUser(credential.authCredentialId, username = "alice.second"))
            }
        }
    }

    @Test
    fun `usernameExistsIgnoreCase returns true only for matching usernames`() = runTest {
        val credential = UserTestSeed.seedAuthCredential("alice@example.com")
        dao.createUser(UserTestSeed.buildUser(credential.authCredentialId, username = "alice"))

        assertTrue(dao.usernameExistsIgnoreCase("ALICE"))
        assertEquals(false, dao.usernameExistsIgnoreCase("bob"))
    }

    @Test
    fun `updateProfilePicture updates row and returns 1`() = runTest {
        val credential = UserTestSeed.seedAuthCredential("alice@example.com")
        val userId = UserTestSeed.seedUser(credential.authCredentialId, username = "alice")

        val rows = dao.updateProfilePicture(userId, "/uploads/alice.jpg")

        assertEquals(1, rows)
        val stored = dao.getUserById(userId)
        assertEquals("/uploads/alice.jpg", stored!!.profilePicturePath)
    }

    @Test
    fun `updateProfilePicture returns 0 for non-existent user`() = runTest {
        val rows = dao.updateProfilePicture(UUID.randomUUID(), "/uploads/ghost.jpg")
        assertEquals(0, rows)
    }

    @Test
    fun `username blank is blocked by DB constraint`() = runTest {
        val credential = UserTestSeed.seedAuthCredential("alice@example.com")

        assertThrows(ExposedSQLException::class.java) {
            kotlinx.coroutines.runBlocking {
                dao.createUser(UserTestSeed.buildUser(credential.authCredentialId, username = "   "))
            }
        }
    }

    @Test
    fun `case-insensitive username unique index exists`() = runTest {
        val indexes = transaction {
            exec(
                """
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'users'
                  AND indexname = 'idx_users_username_lower'
                """.trimIndent()
            ) { rs ->
                buildList {
                    while (rs.next()) add(rs.getString("indexname"))
                }
            } ?: emptyList()
        }
        assertEquals(listOf("idx_users_username_lower"), indexes)
    }
}
