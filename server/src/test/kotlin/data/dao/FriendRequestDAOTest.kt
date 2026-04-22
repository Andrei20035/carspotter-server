package data.dao

import com.carspotter.data.dao.auth_credential.IAuthCredentialDAO
import com.carspotter.features.friend.IFriendDAO
import com.carspotter.features.friend_request.IFriendRequestDAO
import com.carspotter.features.user.IUserDAO
import com.carspotter.features.auth.AuthCredential
import com.carspotter.features.auth.AuthProvider
import com.carspotter.features.user.User
import com.carspotter.features.auth.AuthTable
import com.carspotter.features.friend_request.FriendRequestTable
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.inject
import java.time.LocalDate
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FriendRequestDAOTest: KoinTest {

    private val userDao: IUserDAO by inject()
    private val friendDao: IFriendDAO by inject()
    private val friendRequestDao: IFriendRequestDAO by inject()
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

        SchemaSetup.createUsersTable(UserTable)
        SchemaSetup.createAuthCredentialsTableWithConstraint(AuthTable)
        SchemaSetup.createFriendsTableWithConstraint(FriendTable)
        SchemaSetup.createFriendRequestsTableWithConstraint(FriendRequestTable)

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
    fun cleanDatabase() {
        transaction {
            FriendRequestTable.deleteAll()
        }
    }

    @Test
    fun `sendFriendRequest should add a request to database`() = runBlocking {
        // Act
        val senderId = userId1
        val receiverId = userId2
        friendRequestDao.sendFriendRequest(senderId, receiverId)

        // Assert
        val friendRequests = friendRequestDao.getAllFriendReqFromDB()
        assertEquals(1, friendRequests.size)
    }

    @Test
    fun `acceptFriendRequest should create friendship and delete request`() = runBlocking {

        // Send friend request
        friendRequestDao.sendFriendRequest(userId1, userId2)

        // Verify the request was sent
        var allFriendReq = friendRequestDao.getAllFriendReqFromDB()
        assertEquals(1, allFriendReq.size)

        // Accept friend request
        friendRequestDao.acceptFriendRequest(userId1, userId2)

        // Verify the request was accepted
        allFriendReq = friendRequestDao.getAllFriendReqFromDB()
        assertEquals(0, allFriendReq.size)

        val friendsForUser1 = friendDao.getAllFriends(userId1)
        val friendsForUser2 = friendDao.getAllFriends(userId2)

        assertEquals("Mary Jane", friendsForUser1[0].fullName)
        assertEquals("Peter Parker", friendsForUser2[0].fullName)
    }

    @Test
    fun `declineFriendRequest should remove request but not add friendship`() = runBlocking {
        // Send friend request
        friendRequestDao.sendFriendRequest(userId1, userId2)

        // Verify the request was sent
        var allFriendReq = friendRequestDao.getAllFriendReqFromDB()
        assertEquals(1, allFriendReq.size)

        // Decline friend request
        friendRequestDao.declineFriendRequest(userId1, userId2)

        // Verify the request was declined and friendship was not added
        allFriendReq = friendRequestDao.getAllFriendReqFromDB()
        assertEquals(0, allFriendReq.size)

        val friendsForUser1 = friendDao.getAllFriends(userId1)
        val friendsForUser2 = friendDao.getAllFriends(userId2)

        assertTrue(friendsForUser1.isEmpty())
        assertTrue(friendsForUser2.isEmpty())
    }

    @Test
    fun `getAllFriendRequests should return all pending requests for a user`() = runBlocking {
        friendRequestDao.sendFriendRequest(userId1, userId2)

        val requests = friendRequestDao.getAllFriendRequests(userId2)

        assertEquals(1, requests.size, "User should have one pending friend request")
        assertEquals(userId1, requests.first().id, "Request should be from userId1")
    }

    @AfterAll
    fun tearDown() {
        transaction {
            SchemaUtils.drop(FriendRequestTable, FriendTable, UserTable, AuthTable)
        }
        stopKoin()
    }
}