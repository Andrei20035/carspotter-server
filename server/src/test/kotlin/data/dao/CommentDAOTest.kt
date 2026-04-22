package data.dao

import com.carspotter.data.dao.auth_credential.IAuthCredentialDAO
import com.carspotter.features.car_model.ICarModelDAO
import com.carspotter.features.comment.ICommentDAO
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
import com.carspotter.features.comment.CommentTable
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
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.inject
import java.time.LocalDate
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommentDAOTest: KoinTest {

    private val commentDao: ICommentDAO by inject()
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

        SchemaSetup.createCommentsTable(CommentTable)
        SchemaSetup.createUsersTable(UserTable)
        SchemaSetup.createAuthCredentialsTableWithConstraint(AuthTable)
        SchemaSetup.createPostsTable(PostTable)
        SchemaSetup.createCarModelsTable(CarModelTable)

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
                    endYear = 2022
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
            CommentTable.deleteAll()
        }
    }

    @Test
    fun `add comment and retrieve it`() = runBlocking {
        val commentId = commentDao.addComment(userId1, postId1, "Nice car!")
        val comments = commentDao.getCommentsForPost(postId1)

        assertNotNull(commentId)
        assertEquals(1, comments.size)
        assertEquals("Nice car!", comments[0].commentText)
    }

    @Test
    fun `remove comment`() = runBlocking {
        val commentId = commentDao.addComment(userId1, postId1, "Great post!")

        val rowsDeleted = commentDao.deleteComment(commentId)
        val comments = commentDao.getCommentsForPost(postId1)

        assertEquals(1, rowsDeleted)
        assertTrue(comments.isEmpty())
    }

    @Test
    fun `getCommentsForPost should return all comments for a post`() = runBlocking {

        commentDao.addComment(userId1, postId1, "Awesome car!")
        commentDao.addComment(userId2, postId1, "Wow, great spot!")

        val comments = commentDao.getCommentsForPost(postId1)

        assertEquals(2, comments.size)
        assertTrue(comments.any { it.commentText == "Awesome car!" })
        assertTrue(comments.any { it.commentText == "Wow, great spot!" })
    }

    @Test
    fun `get comment by Id`() = runBlocking {
        val commId1 = commentDao.addComment(userId1, postId1, "Awesome car!")
        val commId2 = commentDao.addComment(userId2, postId1, "Wow, great spot!")

        val comments = commentDao.getCommentsForPost(postId1)

        assertEquals(2, comments.size)

        val comm1 = commentDao.getCommentById(commId1)
        val comm2 = commentDao.getCommentById(commId2)

        assertNotNull(comm1)
        assertNotNull(comm2)

        assertEquals("Awesome car!", comm1!!.commentText)
        assertEquals("Wow, great spot!", comm2!!.commentText)

    }

    @AfterAll
    fun tearDown() {
        transaction {
            SchemaUtils.drop(CommentTable, PostTable, CarModelTable, UserTable, AuthTable)
        }
        stopKoin()
    }
}