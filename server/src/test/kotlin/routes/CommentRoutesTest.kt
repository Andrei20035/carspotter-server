package routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.carspotter.config.configureSerialization
import com.carspotter.features.comment.dto.CommentRequest
import com.carspotter.features.comment.dto.toDTO
import com.carspotter.features.comment.Comment
import com.carspotter.features.comment.ICommentService
import com.carspotter.features.post.IPostService
import com.carspotter.features.comment.commentRoutes
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.*
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.context.GlobalContext.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import java.util.*
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommentRoutesTest : KoinTest {

    private lateinit var commentService: ICommentService
    private lateinit var postService: IPostService

    @BeforeAll
    fun setup() {
        commentService = mockk()
        postService = mockk()
    }

    @BeforeEach
    fun setupKoin() {
        startKoin {
            modules(
                module {
                    single { commentService }
                    single { postService }
                }
            )
        }
    }

    private fun Application.configureTestApplication() {
        System.setProperty("JWT_SECRET", "test-secret-key")

        configureSerialization()

        install(Authentication) {
            jwt("jwt") {
                realm = "Test Server"
                verifier(
                    JWT
                        .require(Algorithm.HMAC256("test-secret-key"))
                        .build()
                )
                validate { credential ->
                    val credentialIdString = credential.payload.getClaim("userId").asString()
                    if (credentialIdString != null) {
                        try {
                            UUID.fromString(credentialIdString)
                            JWTPrincipal(credential.payload)
                        } catch (e: IllegalArgumentException) {
                            null
                        }
                    } else {
                        null
                    }
                }
                challenge { _, _ ->
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Missing or invalid JWT token"))
                }
            }
        }

        routing {
            commentRoutes()
        }
    }

    @AfterEach
    fun tearDownKoin() {
        stopKoin()
        clearAllMocks()
    }

    @Test
    fun `GET comments returns 400 for invalid post ID`() = testApplication {
        application {
            configureTestApplication()
        }

        val response = client.get("/comments/abc")

        assertEquals(HttpStatusCode.BadRequest, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Invalid or missing postId"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `GET comments returns 204 for empty comment list`() = testApplication {
        val postId = UUID.randomUUID()

        coEvery { commentService.getCommentsForPost(postId) } returns emptyList()

        application {
            configureTestApplication()
        }


        val response = client.get("/comments/${postId}")

        assertEquals(HttpStatusCode.NoContent, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"No comments found for this post"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `GET comments returns 200 for non-empty comment list`() = testApplication {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val id3 = UUID.randomUUID()
        val id4 = UUID.randomUUID()


        val commentList = listOf(
            Comment(id = id1, postId = id1, userId = id4, commentText = "Nice car!").toDTO(),
            Comment(id = id2, postId = id3, userId = id4, commentText = "Socate").toDTO()

        )

        coEvery { commentService.getCommentsForPost(id1) } returns commentList

        application {
            configureTestApplication()
        }

        val response = client.get("/comments/${id1}")

        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.encodeToJsonElement(commentList)
        val actualJson = Json.parseToJsonElement(response.bodyAsText())

        assertEquals(expectedJson, actualJson)

    }

    @Test
    fun `POST comment returns 401 when JWT missing or invalid`() = testApplication {
        application {
            configureTestApplication()
        }

        val postId = UUID.randomUUID()

        val request = CommentRequest(postId = postId, commentText = "Hello")

        val response = client.post("/comments") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer invalid-token")
            setBody(Json.encodeToString(request))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Missing or invalid JWT token"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `POST comment returns 400 when comment text is blank`() = testApplication {
        val postId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        application {
            configureTestApplication()
        }

        val request = CommentRequest(postId = postId, commentText = "")

        val token = createTestToken(userId)

        val response = client.post("/comments") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Comment text cannot be blank"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(actualJson, expectedJson)
    }

    @Test
    fun `POST comment returns 201 when comment is created successfully`() = testApplication {
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        val commentId = UUID.randomUUID()

        coEvery { commentService.addComment(userId, postId, "Hello!")} returns commentId

        application {
            configureTestApplication()
        }

        val request = CommentRequest(postId = postId, commentText = "Hello!")

        val token = createTestToken(userId)

        val response = client.post("/comments") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.Created, response.status)

        val expectedJson = Json.parseToJsonElement("""{"message":"Comment created successfully"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(actualJson, expectedJson)

        coVerify(exactly = 1) { commentService.addComment(userId, postId, "Hello!")}
    }

    @Test
    fun `POST comment returns 500 when comment creation fails`() = testApplication {
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()


        coEvery { commentService.addComment(userId, postId, "Hello!") } throws RuntimeException("DB Error")

        application {
            configureTestApplication()
        }

        val request = CommentRequest(postId = postId, commentText = "Hello!")

        val token = createTestToken(userId)
        val response = client.post("/comments") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.InternalServerError, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Failed to create comment"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { commentService.addComment(userId, postId, "Hello!") }
    }

    @Test
    fun `DELETE comment returns 400 when commentId is missing or invalid`() = testApplication {
        application {
            configureTestApplication()
        }

        val userId = UUID.randomUUID()

        val token = createTestToken(userId)

        val response = client.delete("/comments/invalid-id") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        val expectedJson = Json.parseToJsonElement("""{"error":"Invalid or missing commentId"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `DELETE comment returns 401 when JWT missing or invalid`() = testApplication {
        application {
            configureTestApplication()
        }

        val response = client.delete("/comments/1") {
            header(HttpHeaders.Authorization, "Bearer invalid-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Missing or invalid JWT token"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `DELETE comment returns 404 if comment not found`() = testApplication {
        val commentId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        coEvery { commentService.getCommentById(commentId) } returns null

        val token = createTestToken(userId)

        application {
            configureTestApplication()
        }

        val response = client.delete("/comments/${commentId}") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        val expectedJson = Json.parseToJsonElement("""{"error":"Comment not found"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { commentService.getCommentById(commentId) }
    }

    @Test
    fun `DELETE comment returns 403 when user is not owner or post owner`() = testApplication {
        val commentId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        val commentOwnerId = UUID.randomUUID()
        val postOwnerId = UUID.randomUUID()
        val requestingUserId = UUID.randomUUID()

        val fakeComment = Comment(
            id = commentId,
            postId = postId,
            userId = commentOwnerId,
            commentText = "Hello"
        ).toDTO()

        coEvery { commentService.getCommentById(commentId) } returns fakeComment
        coEvery { postService.getUserIdByPost(postId) } returns postOwnerId

        val token = createTestToken(requestingUserId)

        application {
            configureTestApplication()
        }

        val response = client.delete("/comments/$commentId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        val expectedJson = Json.parseToJsonElement("""{"error":"You are not authorized to delete this comment"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { commentService.getCommentById(commentId) }
        coVerify(exactly = 1) { postService.getUserIdByPost(postId) }
    }


    @Test
    fun `DELETE comment returns 200 when successfully deleted`() = testApplication {
        val commentId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        val fakeComment = Comment(
            id = commentId,
            postId = postId,
            userId = userId,
            commentText = "Hello"
        ).toDTO()

        coEvery { commentService.getCommentById(commentId) } returns fakeComment
        coEvery { postService.getUserIdByPost(postId) } returns userId
        coEvery { commentService.deleteComment(commentId) } returns 1

        val token = createTestToken(userId = userId)

        application {
            configureTestApplication()
        }

        val response = client.delete("/comments/$commentId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.parseToJsonElement("""{"message":"Comment deleted successfully"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { commentService.getCommentById(commentId) }
        coVerify(exactly = 1) { postService.getUserIdByPost(postId) }
        coVerify(exactly = 1) { commentService.deleteComment(commentId) }
    }

    @Test
    fun `DELETE comment returns 500 when deletion fails`() = testApplication {
        val commentId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        val fakeComment = Comment(
            id = commentId,
            postId = postId,
            userId = userId,
            commentText = "Hello"
        ).toDTO()

        coEvery { commentService.getCommentById(commentId) } returns fakeComment
        coEvery { postService.getUserIdByPost(postId) } returns userId
        coEvery { commentService.deleteComment(commentId) } returns 0

        val token = createTestToken(userId)

        application { configureTestApplication() }

        val response = client.delete("/comments/$commentId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.InternalServerError, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Failed to delete comment"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { commentService.getCommentById(commentId) }
        coVerify(exactly = 1) { postService.getUserIdByPost(postId) }
        coVerify(exactly = 1) { commentService.deleteComment(commentId) }
    }


    private fun createTestToken(userId: UUID): String {
        return JWT.create()
            .withClaim("userId", userId.toString())
            .withExpiresAt(Date(System.currentTimeMillis() + 60000))
            .sign(Algorithm.HMAC256("test-secret-key"))
    }
}