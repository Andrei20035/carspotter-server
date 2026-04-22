package data.service

import com.carspotter.features.auth.AuthCredential
import com.carspotter.features.auth.AuthProvider
import com.carspotter.features.user.User
import com.carspotter.features.auth.IAuthService
import com.carspotter.features.friend.IFriendService
import com.carspotter.features.user.IUserService
import com.carspotter.features.auth.AuthTable
import com.carspotter.features.friend.FriendTable
import com.carspotter.features.user.UserTable
import com.carspotter.di.daoModule
import com.carspotter.di.repositoryModule
import com.carspotter.di.serviceModule
import data.testutils.SchemaSetup
import data.testutils.TestDatabase
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.inject
import java.time.LocalDate
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FriendServiceTest: KoinTest {

    private val friendService: IFriendService by inject()
    private val authCredentialService: IAuthService by inject()
    private val userService: IUserService by inject()

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
            modules(daoModule, repositoryModule, serviceModule)
        }

        SchemaSetup.createAuthCredentialsTableWithConstraint(AuthTable)
        SchemaSetup.createUsersTable(UserTable)
        SchemaSetup.createFriendsTableWithConstraint(FriendTable)

        runBlocking {
            credentialId1 = authCredentialService.createCredentials(
                AuthCredential(
                    email = "test1@test.com",
                    password = null,
                    googleId = "231122",
                    provider = AuthProvider.GOOGLE
                )
            )
            credentialId2 = authCredentialService.createCredentials(
                AuthCredential(
                    email = "test2@test.com",
                    password = "test2",
                    googleId = null,
                    provider = AuthProvider.REGULAR
                )
            )
            userId1 = userService.createUser(
                User(
                    authCredentialId = credentialId1,
                    fullName = "Peter Parker",
                    phoneNumber = "0712453678",
                    birthDate = LocalDate.of(2003, 11, 8),
                    username = "Socate123",
                    country = "USA"
                )
            )
            userId2 = userService.createUser(
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
    fun `add friend`() = runBlocking {
        val friendId = friendService.addFriend(userId1, userId2)

        Assertions.assertNotNull(friendId)

        val allFriendsInDb = friendService.getAllFriendsInDb()

        Assertions.assertEquals(2, allFriendsInDb.size)
        Assertions.assertTrue(allFriendsInDb.any { it.userId == userId2 && it.friendId == userId1 })
        Assertions.assertTrue(allFriendsInDb.any { it.userId == userId1 && it.friendId == userId2 })

    }

    @Test
    fun `get all friends for a user`() = runBlocking {
        friendService.addFriend(userId1, userId2)

        val allFriends = friendService.getAllFriends(userId1)
        Assertions.assertEquals(1, allFriends.size)
        Assertions.assertEquals("Mary Jane", allFriends[0].fullName)
    }

    @Test
    fun `delete a friend for a user`() = runBlocking {
        friendService.addFriend(userId1, userId2)

        val allFriendsBeforeDelete = friendService.getAllFriends(userId1)
        Assertions.assertEquals(1, allFriendsBeforeDelete.size)
        Assertions.assertEquals("Mary Jane", allFriendsBeforeDelete[0].fullName)

        friendService.deleteFriend(userId1, userId2)

        val allFriendsAfterDelete = friendService.getAllFriends(userId1)
        Assertions.assertEquals(0, allFriendsAfterDelete.size)
    }


    @AfterAll
    fun tearDown() {
        transaction {
            SchemaUtils.drop(UserTable, FriendTable, AuthTable)
        }
        stopKoin()
    }

}