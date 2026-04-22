package data.repository

import com.carspotter.features.post.dto.CreatePostDTO
import com.carspotter.data.model.*
import com.carspotter.data.repository.auth_credential.IAuthCredentialRepository
import com.carspotter.features.car_model.ICarModelRepository
import com.carspotter.features.friend.IFriendRepository
import com.carspotter.features.post.IPostRepository
import com.carspotter.features.user.IUserRepository
import com.carspotter.features.auth.AuthTable
import com.carspotter.features.car_model.CarModelTable
import com.carspotter.features.friend.FriendTable
import com.carspotter.features.post.PostTable
import com.carspotter.features.user.UserTable
import com.carspotter.di.daoModule
import com.carspotter.di.repositoryModule
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.inject
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostRepositoryTest: KoinTest {

    private val userRepository: IUserRepository by inject()
    private val postRepository: IPostRepository by inject()
    private val carModelRepository: ICarModelRepository by inject()
    private val authCredentialRepository: IAuthCredentialRepository by inject()
    private val friendRepository: IFriendRepository by inject()

    private var credentialId1: UUID = UUID.randomUUID()
    private var credentialId2: UUID = UUID.randomUUID()
    private var credentialId3: UUID = UUID.randomUUID()
    private var credentialId4: UUID = UUID.randomUUID()
    private var userId1: UUID = UUID.randomUUID()
    private var userId2: UUID = UUID.randomUUID()
    private var userId3: UUID = UUID.randomUUID()
    private var userId4: UUID = UUID.randomUUID()
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
            modules(daoModule, repositoryModule)
        }

        SchemaSetup.createUsersTable(UserTable)
        SchemaSetup.createPostsTable(PostTable)
        SchemaSetup.createCarModelsTable(CarModelTable)
        SchemaSetup.createFriendsTableWithConstraint(FriendTable)
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

            credentialId3 = authCredentialRepository.createCredentials(
                AuthCredential(
                    email = "test3@test.com",
                    password = null,
                    googleId = "331122",
                    provider = AuthProvider.GOOGLE
                )
            )

            credentialId4 = authCredentialRepository.createCredentials(
                AuthCredential(
                    email = "global@test.com",
                    password = "global",
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
                    fullName = "Peter Parker",
                    phoneNumber = "0712453678",
                    birthDate = LocalDate.of(2004, 4, 1),
                    username = "Socate321",
                    country = "USA"
                )
            )

            userId3 = userRepository.createUser(
                User(
                    authCredentialId = credentialId3,
                    fullName = "Tony Stark",
                    phoneNumber = "0722123456",
                    birthDate = LocalDate.of(1980, 5, 29),
                    username = "IronMan",
                    country = "USA"
                )
            )

            userId4 = userRepository.createUser(
                User(
                    authCredentialId = credentialId4,
                    fullName = "Bruce Banner",
                    phoneNumber = "0700000000",
                    birthDate = LocalDate.of(1982, 12, 1),
                    username = "Hulk",
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
            carModelId2 = carModelRepository.createCarModel(
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
    fun `getFeedPostsForUser returns ordered posts with pagination from friends, nearby and global`() = runBlocking {

        // Make friends
        friendRepository.addFriend(userId1, userId2)

        // Create friend post (Peter’s friends)
        val friendPost = CreatePostDTO(
            userId = userId2,
            carModelId = carModelId1,
            imagePath = "path/friend.jpg",
            latitude = 40.0,
            longitude = -74.0,
            description = "Friend post",
        )
        val friendPostId = postRepository.createPost(friendPost)

        // Create nearby post (not friend, but in location)
        val nearbyPost = CreatePostDTO(
            userId = userId3,
            carModelId = carModelId2,
            imagePath = "path/nearby.jpg",
            latitude = 40.01, // ~1.1 km away
            longitude = -74.0,
            description = "Nearby post",
        )
        val nearbyPostId = postRepository.createPost(nearbyPost)

        // Create global post (not friend, not nearby)
        val globalPost = CreatePostDTO(
            userId = userId4,
            carModelId = carModelId1,
            imagePath = "path/global.jpg",
            latitude = 0.0,
            longitude = 0.0,
            description = "Global post",
        )
        val globalPostId = postRepository.createPost(globalPost)

        // Act: Call repository function to fetch feed
        val response = postRepository.getFeedPostsForUser(
            userId = userId1,
            latitude = 40.0,
            longitude = -74.0,
            radiusKm = 5,
            limit = 10,
            cursor = null
        )

        println("Total posts returned: ${response.posts.size}")
        response.posts.forEachIndexed { index, post ->
            println("Post $index: id=${post.id}, userId=${post.userId}, description='${post.description}', createdAt=${post.createdAt}")
        }

        println("Has more: ${response.hasMore}")
        println("Next cursor: ${response.nextCursor}")


        // Assert
        assertEquals(3, response.posts.size)
        assertEquals("Friend post", response.posts[0].description)
        assertEquals("Nearby post", response.posts[1].description)
        assertEquals("Global post", response.posts[2].description)
        assertTrue(response.hasMore.not())
        assertNull(response.nextCursor)
    }


    @Test
    fun `create and get post by ID`() = runBlocking {
        val postID = postRepository.createPost(
            CreatePostDTO(
                userId = userId1,
                imagePath = "path/to/image1",
                description = "Description1",
                latitude = 40.0,
                longitude = 40.0,
                carModelId = carModelId1
            )
        )

        val post = postRepository.getPostById(postID)

        Assertions.assertNotNull(post)
        Assertions.assertEquals(postID, post?.id)
        Assertions.assertEquals(userId1, post?.userId)
        Assertions.assertEquals("path/to/image1", post?.imagePath)
        Assertions.assertEquals("Description1", post?.description)
        Assertions.assertEquals(carModelId1, post?.carModelId)
    }

    @Test
    fun `get all posts`() = runBlocking {
        postRepository.createPost(
            CreatePostDTO(
                userId = userId1,
                imagePath = "path/to/image1",
                description = "Description1",
                latitude = 40.0,
                longitude = 40.0,
                carModelId = carModelId1
            )
        )
        postRepository.createPost(
            CreatePostDTO(
                userId = userId2,
                imagePath = "path/to/image2",
                description = "Description2",
                latitude = 40.0,
                longitude = 40.0,
                carModelId = carModelId2
            )
        )

        val posts = postRepository.getAllPosts()

        Assertions.assertEquals(2, posts.size)
        Assertions.assertTrue(posts.any { it.userId == userId1 && it.imagePath == "path/to/image1" && it.description == "Description1" && it.carModelId == carModelId1 })
        Assertions.assertTrue(posts.any { it.userId == userId2 && it.imagePath == "path/to/image2" && it.description == "Description2" && it.carModelId == carModelId2 })
    }

    @Test
    fun `get current day posts`() = runBlocking {
        val userTimeZone = ZoneId.of("UTC")
        val startOfDay = ZonedDateTime.now(userTimeZone).toLocalDate().atStartOfDay(userTimeZone).toInstant()
        val endOfDay = ZonedDateTime.now(userTimeZone).toLocalDate().plusDays(1).atStartOfDay(userTimeZone).toInstant()

        postRepository.createPost(
            CreatePostDTO(
                userId = userId1,
                imagePath = "path/to/image1",
                description = "Description1",
                latitude = 40.0,
                longitude = 40.0,
                carModelId = carModelId1
            )
        )
        postRepository.createPost(
            CreatePostDTO(
                userId = userId1,
                imagePath = "path/to/image2",
                description = "Description2",
                latitude = 40.0,
                longitude = 40.0,
                carModelId = carModelId2
            )
        )

        val currentDayPosts = postRepository.getCurrentDayPostsForUser(userId1, startOfDay, endOfDay)

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
        val postId = postRepository.createPost(
            CreatePostDTO(
                userId = userId1,
                imagePath = "path/to/image1",
                description = "Description1",
                latitude = 40.0,
                longitude = 40.0,
                carModelId = carModelId1
            )
        )

        val postDescription = postRepository.getPostById(postId)?.description
        Assertions.assertEquals("Description1", postDescription)

        postRepository.editPost(postId, "New description")

        val newDescription = postRepository.getPostById(postId)?.description
        Assertions.assertEquals("New description", newDescription)
    }

    @Test
    fun `delete post`() = runBlocking {
        val postId = postRepository.createPost(
            CreatePostDTO(
                userId = userId1,
                imagePath = "path/to/image1",
                description = "Description1",
                latitude = 40.0,
                longitude = 40.0,
                carModelId = carModelId1
            )
        )
        postRepository.deletePost(postId)
        val deletedPost = postRepository.getPostById(postId)

        assertNull(deletedPost)
    }

    @AfterAll
    fun tearDown() {
        transaction {
            SchemaUtils.drop(CarModelTable, FriendTable, UserTable, PostTable, AuthTable)
        }
        stopKoin()
    }

}