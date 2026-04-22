package data.dao

import com.carspotter.data.dao.auth_credential.IAuthCredentialDAO
import com.carspotter.features.friend.IFriendDAO
import com.carspotter.features.user.IUserDAO
import com.carspotter.features.auth.AuthCredential
import com.carspotter.features.auth.AuthProvider
import com.carspotter.features.user.User
import com.carspotter.features.auth.AuthTable
import com.carspotter.features.friend.FriendTable
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
class FriendDAOTest: KoinTest {

    private val userDao: IUserDAO by inject()
    private val friendDao: IFriendDAO by inject()
    private val authCredentialDao: IAuthCredentialDAO by inject()


    private var credentialId1: UUID = UUID.randomUUID()
    private var credentialId2: UUID = UUID.randomUUID()
    private var userId1: UUID = UUID.randomUUID()
    private var userId2: UUID = UUID.randomUUID()

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
        SchemaSetup.createFriendsTableWithConstraint(FriendTable)

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
        }
    }

    @BeforeEach
    fun clearDatabase() {
        transaction {
            FriendTable.deleteAll()
        }
    }

    @Test
    fun `getFriendIdsForUser returns correct friend IDs`() = runBlocking {
        friendDao.addFriend(userId1, userId2)

        val friendIds1 = friendDao.getFriendIdsForUser(userId1)
        val friendIds2 = friendDao.getFriendIdsForUser(userId2)

        assertTrue(friendIds1.contains(userId2), "Friend list should contain userId2")
        assertTrue(friendIds2.contains(userId1), "Friend list should contain userId1")

        assertEquals(1, friendIds1.size)
        assertEquals(1, friendIds2.size)
    }

    @Test
    fun `add friend`() = runBlocking {
        val friendId = friendDao.addFriend(userId1, userId2)

        Assertions.assertNotNull(friendId)

        val allFriendsInDb = friendDao.getAllFriendsInDb()

        Assertions.assertEquals(2, allFriendsInDb.size)
        Assertions.assertTrue(allFriendsInDb.any { it.userId == userId2 && it.friendId == userId1 })
        Assertions.assertTrue(allFriendsInDb.any { it.userId == userId1 && it.friendId == userId2 })

    }

    @Test
    fun `get all friends for a user`() = runBlocking {
        friendDao.addFriend(userId1, userId2)

        val allFriends = friendDao.getAllFriends(userId1)
        Assertions.assertEquals(1, allFriends.size)
        Assertions.assertEquals("Mary Jane", allFriends[0].fullName)
    }

    @Test
    fun `delete a friend for a user`() = runBlocking {
        friendDao.addFriend(userId1, userId2)

        val allFriendsBeforeDelete = friendDao.getAllFriends(userId1)
        Assertions.assertEquals(1, allFriendsBeforeDelete.size)
        Assertions.assertEquals("Mary Jane", allFriendsBeforeDelete[0].fullName)

        friendDao.deleteFriend(userId1, userId2)

        val allFriendsAfterDelete = friendDao.getAllFriends(userId1)
        Assertions.assertEquals(0, allFriendsAfterDelete.size)
    }


    @AfterAll
    fun tearDown() {
        transaction {
            SchemaUtils.drop(FriendTable, UserTable, AuthTable)
        }
        stopKoin()
    }
}