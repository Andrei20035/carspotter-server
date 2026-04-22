package data.dao

import com.carspotter.data.dao.auth_credential.IAuthCredentialDAO
import com.carspotter.features.car_model.ICarModelDAO
import com.carspotter.features.like.ILikeDAO
import com.carspotter.features.post.IPostDAO
import com.carspotter.features.user.IUserDAO
import com.carspotter.features.post.dto.CreatePostDTO
import com.carspotter.data.model.*
import com.carspotter.data.table.*
import com.carspotter.di.daoModule
import com.carspotter.features.auth.AuthCredential
import com.carspotter.features.auth.AuthProvider
import com.carspotter.features.auth.AuthTable
import com.carspotter.features.car_model.CarModel
import com.carspotter.features.car_model.CarModelTable
import com.carspotter.features.like.LikeTable
import com.carspotter.features.post.PostTable
import com.carspotter.features.user.User
import com.carspotter.features.user.UserTable
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
class LikeDaoTest: KoinTest {

    private val likeDao: ILikeDAO by inject()
    private val userDao: IUserDAO by inject()
    private val postDao: IPostDAO by inject()
    private val carModelDao: ICarModelDAO by inject()
    private val authCredentialDao: IAuthCredentialDAO by inject()

    private var credentialId1: UUID = UUID.randomUUID()
    private var credentialId2: UUID = UUID.randomUUID()
    private var userId1: UUID = UUID.randomUUID()
    private var userId2: UUID = UUID.randomUUID()
    private var postId1: UUID = UUID.randomUUID()
    private var carModelId1: UUID = UUID.randomUUID()

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
        SchemaSetup.createPostsTable(PostTable)
        SchemaSetup.createCarModelsTable(CarModelTable)
        SchemaSetup.createLikesTable(LikeTable)
        SchemaSetup.createAuthCredentialsTableWithConstraint(AuthTable)

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
            carModelId1 = carModelDao.createCarModel(
                CarModel(
                    brand = "BMW",
                    model = "M3",
                    startYear = 2020,
                    endYear = 2023
                )
            )
            postId1 = postDao.createPost(
                CreatePostDTO(
                    userId = userId1,
                    imagePath = "path/to/image1",
                    description = "Description1",
                    carModelId = carModelId1,
                    latitude = 40.0,
                    longitude = 40.0
                )
            )
        }
    }

    @BeforeEach
    fun cleanDatabase() {
        transaction {
            LikeTable.deleteAll()
        }
    }

    @Test
    fun `like post`() = runBlocking {
        likeDao.likePost(userId1, postId1)

        val usersWhoLike = likeDao.getLikesForPost(postId1)

        assertEquals(1, usersWhoLike.size)
        assertTrue(usersWhoLike.any { it.id == userId1 })
    }

    @Test
    fun `unlikePost should remove the like from the post`() = runBlocking {
        likeDao.likePost(userId1, postId1)
        var usersWhoLike = likeDao.getLikesForPost(postId1)
        assertEquals(1, usersWhoLike.size)

        likeDao.unlikePost(userId1, postId1)
        usersWhoLike = likeDao.getLikesForPost(postId1)
        assertTrue(usersWhoLike.isEmpty())
    }

    @Test
    fun `getLikesForPost should return all users who liked a post`() = runBlocking {
        likeDao.likePost(userId1, postId1)
        likeDao.likePost(userId2, postId1)

        val usersWhoLike = likeDao.getLikesForPost(postId1)

        assertEquals(2, usersWhoLike.size)
        assertTrue(usersWhoLike.any { it.id == userId1 })
        assertTrue(usersWhoLike.any { it.id == userId2 })
    }

    @AfterAll
    fun tearDown() {
        transaction {
            SchemaUtils.drop(LikeTable, PostTable, UserTable, CarModelTable, AuthTable)
        }
        stopKoin()
    }
}
