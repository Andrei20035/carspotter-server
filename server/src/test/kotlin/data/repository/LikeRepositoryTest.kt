package data.repository

import com.carspotter.features.post.dto.CreatePostDTO
import com.carspotter.data.model.*
import com.carspotter.data.repository.auth_credential.IAuthCredentialRepository
import com.carspotter.features.car_model.ICarModelRepository
import com.carspotter.features.like.ILikeRepository
import com.carspotter.features.post.IPostRepository
import com.carspotter.features.user.IUserRepository
import com.carspotter.data.table.*
import com.carspotter.di.daoModule
import com.carspotter.di.repositoryModule
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
class LikeRepositoryTest: KoinTest {

    private val likeRepository: ILikeRepository by inject()
    private val userRepository: IUserRepository by inject()
    private val postRepository: IPostRepository by inject()
    private val carModelRepository: ICarModelRepository by inject()
    private val authCredentialRepository: IAuthCredentialRepository by inject()

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
            modules(daoModule, repositoryModule)
        }

        SchemaSetup.createUsersTable(UserTable)
        SchemaSetup.createPostsTable(PostTable)
        SchemaSetup.createCarModelsTable(CarModelTable)
        SchemaSetup.createLikesTable(LikeTable)
        SchemaSetup.createAuthCredentialsTableWithConstraint(AuthTable)

        runBlocking {
            credentialId1 = authCredentialRepository.createCredentials(
                AuthCredential(
                    email = "test1@test.com",
                    password = null,
                    googleId = "231122",
                    provider = AuthProvider.GOOGLE
                )
            )
            credentialId2 = authCredentialRepository.createCredentials(
                AuthCredential(
                    email = "test2@test.com",
                    password = "test2",
                    googleId = null,
                    provider = AuthProvider.REGULAR
                )
            )
            userId1 = userRepository.createUser(
                User(
                    authCredentialId = credentialId1,
                    fullName = "Peter Parker",
                    phoneNumber = "0712453678",
                    birthDate = LocalDate.of(2003, 11, 8),
                    username = "Socate123",
                    country = "USA"
                )
            )
            userId2 = userRepository.createUser(
                User(
                    authCredentialId = credentialId2,
                    fullName = "Mary Jane",
                    phoneNumber = "0712453678",
                    birthDate = LocalDate.of(2004, 4, 1),
                    username = "Socate321",
                    country = "USA"
                )
            )
            carModelId1 = carModelRepository.createCarModel(
                CarModel(
                    brand = "BMW",
                    model = "M3",
                    startYear = 2020,
                    endYear = 2023
                )
            )
            postId1 = postRepository.createPost(
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
        likeRepository.likePost(userId1, postId1)

        val usersWhoLike = likeRepository.getLikesForPost(postId1)

        assertEquals(1, usersWhoLike.size)
        assertTrue(usersWhoLike.any { it.id == userId1 })
    }

    @Test
    fun `unlikePost should remove the like from the post`() = runBlocking {
        likeRepository.likePost(userId1, postId1)
        var usersWhoLike = likeRepository.getLikesForPost(postId1)
        assertEquals(1, usersWhoLike.size)

        likeRepository.unlikePost(userId1, postId1)
        usersWhoLike = likeRepository.getLikesForPost(postId1)
        assertTrue(usersWhoLike.isEmpty())
    }

    @Test
    fun `getLikesForPost should return all users who liked a post`() = runBlocking {
        likeRepository.likePost(userId1, postId1)
        likeRepository.likePost(userId2, postId1)

        val usersWhoLike = likeRepository.getLikesForPost(postId1)

        assertEquals(2, usersWhoLike.size)
        assertTrue(usersWhoLike.any { it.id == userId1 })
        assertTrue(usersWhoLike.any { it.id == userId2 })
    }

    @AfterAll
    fun tearDown() {
        transaction {
            SchemaUtils.drop(UserTable, PostTable, CarModelTable, LikeTable, AuthTable)
        }
        stopKoin()
    }
}
