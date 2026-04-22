package data.service

import com.carspotter.features.post.dto.CreatePostDTO
import com.carspotter.data.model.*
import com.carspotter.features.auth.IAuthService
import com.carspotter.features.car_model.ICarModelService
import com.carspotter.features.post.IPostService
import com.carspotter.features.user.IUserService
import com.carspotter.features.auth.AuthTable
import com.carspotter.features.car_model.CarModelTable
import com.carspotter.features.post.PostTable
import com.carspotter.features.user.UserTable
import com.carspotter.di.daoModule
import com.carspotter.di.repositoryModule
import com.carspotter.di.serviceModule
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
class PostServiceTest: KoinTest {

    private val userService: IUserService by inject()
    private val postService: IPostService by inject()
    private val carModelService: ICarModelService by inject()
    private val authCredentialService: IAuthService by inject()

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
            modules(daoModule, repositoryModule, serviceModule)
        }

        SchemaSetup.createUsersTable(UserTable)
        SchemaSetup.createPostsTable(PostTable)
        SchemaSetup.createCarModelsTable(CarModelTable)
        SchemaSetup.createAuthCredentialsTableWithConstraint(AuthTable)

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

            carModelId1 = carModelService.createCarModel(
                CarModel(
                    brand = "BMW",
                    model = "M3",
                    startYear = 2020,
                    endYear = 2023
                )
            )
            carModelId2 = carModelService.createCarModel(
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
        println("Clearing database")
        transaction {
            PostTable.deleteAll()
        }
    }

    @Test
    fun `create and get post by ID`() = runBlocking {
        val postID = postService.createPost(
            CreatePostDTO(
                userId = userId1,
                imagePath = "path/to/image1",
                description = "Description1",
                carModelId = carModelId1,
                latitude = 40.0,
                longitude = 40.0
            )
        )

        val post = postService.getPostById(postID)

        Assertions.assertNotNull(post)
        Assertions.assertEquals(postID, post?.id)
        Assertions.assertEquals(userId1, post?.userId)
        Assertions.assertEquals("path/to/image1", post?.imagePath)
        Assertions.assertEquals("Description1", post?.description)
        Assertions.assertEquals(carModelId1, post?.carModelId)
    }

    @Test
    fun `get all posts`() = runBlocking {
        postService.createPost(
            CreatePostDTO(
                userId = userId1,
                imagePath = "path/to/image1",
                description = "Description1",
                carModelId = carModelId1,
                latitude = 40.0,
                longitude = 40.0
            )
        )
        postService.createPost(
            CreatePostDTO(
                userId = userId2,
                imagePath = "path/to/image2",
                description = "Description2",
                carModelId = carModelId2,
                latitude = 40.0,
                longitude = 40.0
            )
        )

        val posts = postService.getAllPosts()

        Assertions.assertEquals(2, posts.size)
        Assertions.assertTrue(posts.any { it.userId == userId1 && it.imagePath == "path/to/image1" && it.description == "Description1" && it.carModelId == carModelId1 })
        Assertions.assertTrue(posts.any { it.userId == userId2 && it.imagePath == "path/to/image2" && it.description == "Description2" && it.carModelId == carModelId2 })
    }

    @Test
    fun `get current day posts`() = runBlocking {
        println("Starting test: get current day posts")
        postService.createPost(
            CreatePostDTO(
                userId = userId1,
                imagePath = "path/to/image1",
                description = "Description1",
                carModelId = carModelId1,
                latitude = 40.0,
                longitude = 40.0
            )
        )
        postService.createPost(
            CreatePostDTO(
                userId = userId1,
                imagePath = "path/to/image2",
                description = "Description2",
                carModelId = carModelId2,
                latitude = 40.0,
                longitude = 40.0
            )
        )

        // Assuming you have a way to get user's time zone, let's use UTC for the example.
        val userTimeZone = ZoneId.of("UTC") // Replace with actual user's time zone if available.

        // Fetch posts for the current day for the user
        val currentDayPosts = postService.getCurrentDayPostsForUser(userId1, userTimeZone)

        // Assertions
        Assertions.assertNotNull(currentDayPosts)
        Assertions.assertTrue(currentDayPosts.isNotEmpty(), "There should be posts returned for the current day.")

        // Verify the createdAt timestamp falls within today's range
        val now = ZonedDateTime.now(userTimeZone).toLocalDate().atStartOfDay(userTimeZone).toInstant()
        currentDayPosts.forEach { post ->
            // Verify that createdAt is on the current day
            Assertions.assertTrue(post.createdAt!! >= now, "Post createdAt should be after the start of today.")
        }
    }


    @Test
    fun `edit post description`() = runBlocking {
        println("Starting test: edit post description")
        val postId = postService.createPost(
            CreatePostDTO(
                userId = userId1,
                imagePath = "path/to/image1",
                description = "Description1",
                carModelId = carModelId1,
                latitude = 40.0,
                longitude = 40.0
            )
        )

        val postDescription = postService.getPostById(postId)?.description
        Assertions.assertEquals("Description1", postDescription)

        postService.editPost(postId, "New description")

        val newDescription = postService.getPostById(postId)?.description
        Assertions.assertEquals("New description", newDescription)
    }

    @Test
    fun `delete post`() = runBlocking {
        println("Starting test: delete post")
        val postId = postService.createPost(
            CreatePostDTO(
                userId = userId1,
                imagePath = "path/to/image1",
                description = "Description1",
                carModelId = carModelId1,
                latitude = 40.0,
                longitude = 40.0
            )
        )
        postService.deletePost(postId)
        val deletedPost = postService.getPostById(postId)

        assertNull(deletedPost)
    }

    @AfterAll
    fun tearDown() {
        println("Tearing down test")
        transaction {
            SchemaUtils.drop(CarModelTable, UserTable, PostTable, AuthTable)
        }
        stopKoin()
    }
}