package data.dao

import com.carspotter.data.dao.auth_credential.IAuthCredentialDAO
import com.carspotter.features.car_model.ICarModelDAO
import com.carspotter.features.post.IPostDAO
import com.carspotter.features.user.IUserDAO
import com.carspotter.features.post.dto.CreatePostDTO
import com.carspotter.data.model.*
import com.carspotter.features.auth.AuthTable
import com.carspotter.features.car_model.CarModelTable
import com.carspotter.features.post.PostTable
import com.carspotter.features.user.UserTable
import com.carspotter.di.daoModule
import com.carspotter.features.auth.AuthCredential
import com.carspotter.features.auth.AuthProvider
import com.carspotter.features.car_model.CarModel
import com.carspotter.features.user.User
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
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostDaoTest: KoinTest {

    private val userDao: IUserDAO by inject()
    private val postDao: IPostDAO by inject()
    private val carModelDao: ICarModelDAO by inject()
    private val authCredentialDao: IAuthCredentialDAO by inject()

    private var credentialId1: UUID = UUID.randomUUID()
    private var credentialId2: UUID = UUID.randomUUID()
    private var userId1: UUID = UUID.randomUUID()
    private var userId2: UUID = UUID.randomUUID()
    private var carModelId1: UUID = UUID.randomUUID()
    private var carModelId2: UUID = UUID.randomUUID()

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
            carModelId2 = carModelDao.createCarModel(
                CarModel(
                    brand = "Audi",
                    model = "A4",
                    startYear = 2022,
                    endYear = 2024,
                )
            )
        }
    }

    @BeforeEach
    fun clearDatabase() {
        transaction {
            PostTable.deleteAll()
        }
    }

    @Test
    fun `getFriendPosts returns correct posts`() = runBlocking {
        val postId1 = postDao.createPost(
            CreatePostDTO(
                userId = userId1,
                carModelId = carModelId1,
                imagePath = "path",
                latitude = 40.0,
                longitude = 40.0
            )
        )
        val postId2 = postDao.createPost(
            CreatePostDTO(
                userId = userId2,
                carModelId = carModelId2,
                imagePath = "path",
                latitude = 40.0,
                longitude = 40.0

            )
        )

        val result = postDao.getFriendPosts(listOf(userId1, userId2), after = null, limit = 10)

        Assertions.assertEquals(2, result.size)
        Assertions.assertTrue(result.any { it.id == postId1 })
        Assertions.assertTrue(result.any { it.id == postId2 })
    }

    @Test
    fun `getNearbyPosts returns only nearby posts`() = runBlocking {
        val lat = 40.0
        val lon = -74.0

        postDao.createPost(
            CreatePostDTO(
                userId = userId1,
                carModelId = carModelId1,
                imagePath = "path",
                latitude = lat,
                longitude = lon,
            )
        )
        postDao.createPost(
            CreatePostDTO(
                userId = userId2,
                carModelId = carModelId2,
                imagePath = "path",
                latitude = lat + 0.001,
                longitude = lon + 0.001,
            )
        )
        postDao.createPost(
            CreatePostDTO(
                userId = userId2,
                carModelId = carModelId2,
                imagePath = "path",
                latitude = lat + 2.0,
                longitude = lon + 2.0,
            )
        )

        val result = postDao.getNearbyPosts(
            excludedIds = listOf(userId1),
            lat = lat,
            lon = lon,
            radiusKm = 5,
            after = null,
            limit = 10
        )

        Assertions.assertEquals(1, result.size)
        Assertions.assertTrue(result.all { it.latitude in (lat - 0.05)..(lat + 0.05) })
    }

    @Test
    fun `getGlobalPosts returns non-excluded user posts`() = runBlocking {
        val postId1 = postDao.createPost(
            CreatePostDTO(
                userId = userId1,
                carModelId = carModelId1,
                imagePath = "path",
                latitude = 40.0,
                longitude = 40.0,
            )
        )
        val postId2 = postDao.createPost(
            CreatePostDTO(
                userId = userId2,
                carModelId = carModelId2,
                imagePath = "path",
                latitude = 40.0,
                longitude = 40.0,
            )
        )

        val result = postDao.getGlobalPosts(excludedIds = listOf(userId1), after = null, limit = 10)

        Assertions.assertEquals(1, result.size)
        Assertions.assertEquals(postId2, result[0].id)
    }

    @Test
    fun `create and get post by ID`() = runBlocking {
        val postID = postDao.createPost(
            CreatePostDTO(
                userId = userId1,
                imagePath = "path/to/image1",
                description = "Description1",
                latitude = 40.0,
                longitude = 40.0,
                carModelId = carModelId1
            )
        )

        val post = postDao.getPostById(postID)

        Assertions.assertNotNull(post)
        Assertions.assertEquals(postID, post?.id)
        Assertions.assertEquals(userId1, post?.userId)
        Assertions.assertEquals("path/to/image1", post?.imagePath)
        Assertions.assertEquals("Description1", post?.description)
        Assertions.assertEquals(carModelId1, post?.carModelId)
    }

    @Test
    fun `get all posts`() = runBlocking {
        postDao.createPost(
            CreatePostDTO(
                userId = userId1,
                imagePath = "path/to/image1",
                description = "Description1",
                latitude = 40.0,
                longitude = 40.0,
                carModelId = carModelId1
            )
        )
        postDao.createPost(
            CreatePostDTO(
                userId = userId2,
                imagePath = "path/to/image2",
                description = "Description2",
                latitude = 40.0,
                longitude = 40.0,
                carModelId = carModelId2
            )
        )

        val posts = postDao.getAllPosts()

        Assertions.assertEquals(2, posts.size)
        Assertions.assertTrue(posts.any { it.userId == userId1 && it.imagePath == "path/to/image1" && it.description == "Description1" && it.carModelId == carModelId1 })
        Assertions.assertTrue(posts.any { it.userId == userId2 && it.imagePath == "path/to/image2" && it.description == "Description2" && it.carModelId == carModelId2 })
    }

    @Test
    fun `get current day posts`() = runBlocking {
        val userTimeZone = ZoneId.of("UTC")
        val startOfDay = ZonedDateTime.now(userTimeZone).toLocalDate().atStartOfDay(userTimeZone).toInstant()
        val endOfDay = ZonedDateTime.now(userTimeZone).toLocalDate().plusDays(1).atStartOfDay(userTimeZone).toInstant()

        postDao.createPost(
            CreatePostDTO(
                userId = userId1,
                imagePath = "path/to/image1",
                description = "Description1",
                latitude = 40.0,
                longitude = 40.0,
                carModelId = carModelId1
            )
        )
        postDao.createPost(
            CreatePostDTO(
                userId = userId1,
                imagePath = "path/to/image2",
                description = "Description2",
                latitude = 40.0,
                longitude = 40.0,
                carModelId = carModelId2
            )
        )

        val currentDayPosts = postDao.getCurrentDayPostsForUser(userId1, startOfDay, endOfDay)

        Assertions.assertNotNull(currentDayPosts)
        Assertions.assertTrue(currentDayPosts.isNotEmpty(), "There should be posts returned for the current day.")

        currentDayPosts.forEach { post ->
            Assertions.assertTrue(
                post.createdAt >= startOfDay && post.createdAt < endOfDay,
                "Post createdAt should be within the current day range."
            )
        }
    }


    @Test
    fun `edit post description`() = runBlocking {
        val postId = postDao.createPost(
            CreatePostDTO(
                userId = userId1,
                imagePath = "path/to/image1",
                description = "Description1",
                latitude = 40.0,
                longitude = 40.0,
                carModelId = carModelId1
            )
        )

        val postDescription = postDao.getPostById(postId)?.description
        Assertions.assertEquals("Description1", postDescription)

        postDao.editPost(postId, "New description")

        val newDescription = postDao.getPostById(postId)?.description
        Assertions.assertEquals("New description", newDescription)
    }

    @Test
    fun `delete post`() = runBlocking {
        val postId = postDao.createPost(
            CreatePostDTO(
                userId = userId1,
                imagePath = "path/to/image1",
                description = "Description1",
                latitude = 40.0,
                longitude = 40.0,
                carModelId = carModelId1
            )
        )
        postDao.deletePost(postId)
        val deletedPost = postDao.getPostById(postId)

        assertNull(deletedPost)
    }

    @AfterAll
    fun tearDown() {
        transaction {
            SchemaUtils.drop(PostTable, UserTable, CarModelTable, AuthTable)
        }
        stopKoin()
    }
}