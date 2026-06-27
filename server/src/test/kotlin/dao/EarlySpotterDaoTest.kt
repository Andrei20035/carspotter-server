package dao

import com.carspotter.features.user.UserDao
import com.carspotter.features.user.UserTable
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.selectAll
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import testutils.TestDatabaseFactory
import testutils.UserTestSeed

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EarlySpotterDaoTest {

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

    // --- Test 1: userii seed-uiti direct (ca userii existenti) raman non-early-spotters ---

    @Test
    fun `existing users seeded directly remain non-early-spotter`() = runTest {
        repeat(5) { i ->
            val cred = UserTestSeed.seedAuthCredential("existing$i@example.com")
            UserTestSeed.seedUser(cred.authCredentialId, username = "existing$i")
        }

        val allUsers: List<Pair<Boolean, Int?>> = transaction {
            UserTable.selectAll().map { row ->
                row[UserTable.isEarlySpotter] to row[UserTable.earlySpotterNumber]
            }
        }

        assertEquals(5, allUsers.size)
        allUsers.forEach { pair ->
            assertFalse(pair.first, "User seeded directly should not be early spotter")
            assertNull(pair.second, "User seeded directly should have null early_spotter_number")
        }
    }

    // --- Test 2: primul createUser dupa migration primeste numarul 1 ---

    @Test
    fun `first createUser after migration receives earlySpotterNumber 1`() = runTest {
        val cred = UserTestSeed.seedAuthCredential("first@example.com")
        val userId = dao.createUser(UserTestSeed.buildUser(cred.authCredentialId, username = "first"))

        val user = dao.getUserById(userId)

        assertNotNull(user)
        assertTrue(user!!.isEarlySpotter)
        assertEquals(1, user.earlySpotterNumber)
    }

    // --- Test 3: al doilea createUser primeste numarul 2 ---

    @Test
    fun `second createUser receives earlySpotterNumber 2`() = runTest {
        val cred1 = UserTestSeed.seedAuthCredential("first@example.com")
        dao.createUser(UserTestSeed.buildUser(cred1.authCredentialId, username = "first"))

        val cred2 = UserTestSeed.seedAuthCredential("second@example.com")
        val userId2 = dao.createUser(UserTestSeed.buildUser(cred2.authCredentialId, username = "second"))

        val user2 = dao.getUserById(userId2)

        assertNotNull(user2)
        assertTrue(user2!!.isEarlySpotter)
        assertEquals(2, user2.earlySpotterNumber)
    }

    // --- Test 4: primii 1000 useri noi primesc numere 1..1000 distincte ---

    @Test
    fun `first 1000 createUser calls receive numbers 1 to 1000 all distinct`() = runTest {
        val numbers = (1..1000).map { i ->
            val cred = UserTestSeed.seedAuthCredential("user$i@example.com")
            val userId = dao.createUser(UserTestSeed.buildUser(cred.authCredentialId, username = "user$i"))
            dao.getUserById(userId)!!.earlySpotterNumber
        }

        assertEquals(1000, numbers.size)
        assertEquals((1..1000).toSet(), numbers.toSet())
    }

    // --- Test 5: al 1001-lea createUser nu primeste badge ---

    @Test
    fun `user 1001 does not receive early spotter badge`() = runTest {
        repeat(1000) { i ->
            val cred = UserTestSeed.seedAuthCredential("slot$i@example.com")
            dao.createUser(UserTestSeed.buildUser(cred.authCredentialId, username = "slot$i"))
        }

        val lateCred = UserTestSeed.seedAuthCredential("late@example.com")
        val lateUserId = dao.createUser(UserTestSeed.buildUser(lateCred.authCredentialId, username = "late"))
        val lateUser = dao.getUserById(lateUserId)

        assertNotNull(lateUser)
        assertFalse(lateUser!!.isEarlySpotter)
        assertNull(lateUser.earlySpotterNumber)
    }

    // --- Test 6: N createUser concurente → numere unice, fara serialization error ---

    @Test
    fun `concurrent createUser calls produce unique early spotter numbers without errors`() {
        val n = 20
        val credentials = (1..n).map { i ->
            UserTestSeed.seedAuthCredential("concurrent$i@example.com")
        }

        val numbers = runBlocking(Dispatchers.IO) {
            credentials.mapIndexed { i, cred ->
                async {
                    dao.createUser(UserTestSeed.buildUser(cred.authCredentialId, username = "concurrent$i"))
                        .let { userId -> dao.getUserById(userId)!!.earlySpotterNumber }
                }
            }.awaitAll()
        }

        val nonNullNumbers = numbers.filterNotNull()
        assertEquals(n, nonNullNumbers.size, "All $n concurrent users should be early spotters (counter starts at 0)")
        assertEquals(nonNullNumbers.toSet().size, nonNullNumbers.size, "All assigned numbers must be unique")
        assertEquals((1..n).toSet(), nonNullNumbers.toSet())
    }

    // --- Test 7: counter nu se initializeaza din numarul de useri existenti ---

    @Test
    fun `counter starts at 0 regardless of how many existing users are in DB`() = runTest {
        val existingCount = 50
        repeat(existingCount) { i ->
            val cred = UserTestSeed.seedAuthCredential("pre$i@example.com")
            UserTestSeed.seedUser(cred.authCredentialId, username = "pre$i")
        }

        val newCred = UserTestSeed.seedAuthCredential("newcomer@example.com")
        val newUserId = dao.createUser(UserTestSeed.buildUser(newCred.authCredentialId, username = "newcomer"))
        val newUser = dao.getUserById(newUserId)

        assertNotNull(newUser)
        assertTrue(newUser!!.isEarlySpotter)
        assertEquals(
            1,
            newUser.earlySpotterNumber,
            "First new user after feature launch should get number 1, not ${existingCount + 1}"
        )
    }

    // --- Test 8: insert invalid (boolean/number inconsistenti) esueaza pe constraint DB ---

    @Test
    fun `inserting true with null number violates consistency constraint`() {
        val cred = UserTestSeed.seedAuthCredential("bad@example.com")
        assertThrows<ExposedSQLException> {
            transaction {
                UserTable.insert {
                    it[UserTable.authCredentialId] = cred.authCredentialId
                    it[UserTable.fullName] = "Bad"
                    it[UserTable.username] = "baduser"
                    it[UserTable.country] = "RO"
                    it[UserTable.birthDate] = java.time.LocalDate.of(1995, 1, 1)
                    it[UserTable.isEarlySpotter] = true
                    it[UserTable.earlySpotterNumber] = null  // inconsistent cu true
                }
            }
        }
    }

    @Test
    fun `inserting false with a number violates consistency constraint`() {
        val cred = UserTestSeed.seedAuthCredential("bad2@example.com")
        assertThrows<ExposedSQLException> {
            transaction {
                UserTable.insert {
                    it[UserTable.authCredentialId] = cred.authCredentialId
                    it[UserTable.fullName] = "Bad2"
                    it[UserTable.username] = "baduser2"
                    it[UserTable.country] = "RO"
                    it[UserTable.birthDate] = java.time.LocalDate.of(1995, 1, 1)
                    it[UserTable.isEarlySpotter] = false
                    it[UserTable.earlySpotterNumber] = 42  // inconsistent cu false
                }
            }
        }
    }

    @Test
    fun `inserting number outside 1-1000 range violates range constraint`() {
        val cred = UserTestSeed.seedAuthCredential("bad3@example.com")
        assertThrows<ExposedSQLException> {
            transaction {
                UserTable.insert {
                    it[UserTable.authCredentialId] = cred.authCredentialId
                    it[UserTable.fullName] = "Bad3"
                    it[UserTable.username] = "baduser3"
                    it[UserTable.country] = "RO"
                    it[UserTable.birthDate] = java.time.LocalDate.of(1995, 1, 1)
                    it[UserTable.isEarlySpotter] = true
                    it[UserTable.earlySpotterNumber] = 1001  // depaseste limita de 1000
                }
            }
        }
    }
}
