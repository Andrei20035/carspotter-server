package routes

import com.carspotter.features.auth.JwtService
import com.carspotter.features.comment.dto.CommentDTO
import com.carspotter.features.comment.dto.CommentRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import testutils.CommentTestSeed
import testutils.TestDatabaseFactory
import testutils.TestEnv
import testutils.setTestEnv
import testutils.stopKoinSafely
import testutils.testCommentModule
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommentRoutesTest {

    private val jwt = JwtService(
        jwtSecret = TestEnv.JWT_SECRET,
        jwtIssuer = TestEnv.JWT_ISSUER,
        jwtAudience = TestEnv.JWT_AUDIENCE,
    )

    @BeforeAll
    fun setup() {
        setTestEnv()
        TestDatabaseFactory.start()
    }

    @AfterAll
    fun tearDown() {
        TestDatabaseFactory.stop()
    }

    @BeforeEach
    fun clean() {
        TestDatabaseFactory.cleanDatabase()
        stopKoinSafely()
    }

    private fun commentTest(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) =
        testApplication {
            application { testCommentModule() }
            val client = createClient {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; isLenient = true })
                }
            }
            block(client)
        }

    /** Helper: generează un JWT cu claim-urile pe care ruta le citește. */
    private fun tokenFor(authId: UUID, userId: UUID, email: String = "user@example.com"): String =
        jwt.generateJwtToken(credentialId = authId, userId = userId, email = email)

    // ---------- GET /posts/{postId}/comments ----------

    @Test
    fun `GET returns 200 and empty array when post has no comments`() = commentTest { client ->
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)

        val resp = client.get("/api/posts/${post.postId}/comments")

        assertEquals(HttpStatusCode.OK, resp.status)
        val body: List<CommentDTO> = resp.body()
        assertTrue(body.isEmpty(), "expected [], not 404 or 204")
    }

    @Test
    fun `GET returns 200 and empty array for non-existent post`() = commentTest { client ->
        val resp = client.get("/api/posts/${UUID.randomUUID()}/comments")

        assertEquals(HttpStatusCode.OK, resp.status)
        val body: List<CommentDTO> = resp.body()
        assertTrue(body.isEmpty())
    }

    @Test
    fun `GET returns 400 for invalid postId`() = commentTest { client ->
        val resp = client.get("/api/posts/not-a-uuid/comments")
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `GET returns comments with username avatar and timestamp ordered ASC`() = commentTest { client ->
        val alice = CommentTestSeed.seedUser(username = "alice", profilePicturePath = "/uploads/alice.jpg")
        val bob = CommentTestSeed.seedUser(username = "bob", email = "bob@example.com")
        val post = CommentTestSeed.seedPost(alice.userId)

        CommentTestSeed.insertComment(alice.userId, post.postId, "first")
        Thread.sleep(15)
        CommentTestSeed.insertComment(bob.userId, post.postId, "second")

        val resp = client.get("/api/posts/${post.postId}/comments")
        assertEquals(HttpStatusCode.OK, resp.status)

        val body: List<CommentDTO> = resp.body()
        assertEquals(2, body.size)
        assertEquals("first", body[0].commentText)
        assertEquals("alice", body[0].username)
        assertEquals("/uploads/alice.jpg", body[0].profilePicturePath)
        assertNotNull(body[0].createdAt)
        assertEquals("second", body[1].commentText)
        assertEquals("bob", body[1].username)
    }

    @Test
    fun `GET does not require authentication`() = commentTest { client ->
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)
        CommentTestSeed.insertComment(alice.userId, post.postId, "public")

        val resp = client.get("/api/posts/${post.postId}/comments")  // no Authorization header

        assertEquals(HttpStatusCode.OK, resp.status)
        val body: List<CommentDTO> = resp.body()
        assertEquals(1, body.size)
    }

    // ---------- POST /posts/{postId}/comments ----------

    @Test
    fun `POST without JWT returns 401`() = commentTest { client ->
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)

        val resp = client.post("/api/posts/${post.postId}/comments") {
            contentType(ContentType.Application.Json)
            setBody(CommentRequest(commentText = "hi"))
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `POST returns 201 with full CommentDTO including username`() = commentTest { client ->
        val alice = CommentTestSeed.seedUser(username = "alice", profilePicturePath = "/uploads/alice.jpg")
        val post = CommentTestSeed.seedPost(alice.userId)
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        val resp = client.post("/api/posts/${post.postId}/comments") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(CommentRequest(commentText = "great car!"))
        }

        assertEquals(HttpStatusCode.Created, resp.status)
        val body: CommentDTO = resp.body()
        assertNotNull(body.id)
        assertEquals(alice.userId, body.userId)
        assertEquals(post.postId, body.postId)
        assertEquals("alice", body.username)
        assertEquals("/uploads/alice.jpg", body.profilePicturePath)
        assertEquals("great car!", body.commentText)
        assertNotNull(body.createdAt)
    }

    @Test
    fun `POST with blank text returns 400`() = commentTest { client ->
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        val resp = client.post("/api/posts/${post.postId}/comments") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(CommentRequest(commentText = "   "))
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `POST with text over 1000 chars returns 400`() = commentTest { client ->
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        val resp = client.post("/api/posts/${post.postId}/comments") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(CommentRequest(commentText = "a".repeat(1001)))
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `POST with non-existent postId returns 404`() = commentTest { client ->
        val alice = CommentTestSeed.seedUser()
        val token = tokenFor(alice.authId, alice.userId, alice.email)
        val ghostPostId = UUID.randomUUID()

        val resp = client.post("/api/posts/$ghostPostId/comments") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(CommentRequest(commentText = "hi"))
        }
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }

    @Test
    fun `POST with invalid postId UUID returns 400`() = commentTest { client ->
        val alice = CommentTestSeed.seedUser()
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        val resp = client.post("/api/posts/not-a-uuid/comments") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(CommentRequest(commentText = "hi"))
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `POST without userId claim in token returns 401`() = commentTest { client ->
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)
        // Token fără userId claim — credentialId există dar userId nu
        val token = jwt.generateJwtToken(credentialId = alice.authId, userId = null, email = alice.email)

        val resp = client.post("/api/posts/${post.postId}/comments") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(CommentRequest(commentText = "hi"))
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    // ---------- DELETE /comments/{commentId} ----------

    @Test
    fun `DELETE without JWT returns 401`() = commentTest { client ->
        val resp = client.delete("/api/comments/${UUID.randomUUID()}")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `DELETE own comment returns 204 and removes it`() = commentTest { client ->
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)
        val commentId = CommentTestSeed.insertComment(alice.userId, post.postId, "mine")
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        val resp = client.delete("/api/comments/$commentId") { bearerAuth(token) }

        assertEquals(HttpStatusCode.NoContent, resp.status)

        // Verifică că nu mai apare în listă
        val list: List<CommentDTO> = client.get("/api/posts/${post.postId}/comments").body()
        assertTrue(list.isEmpty())
    }

    @Test
    fun `DELETE comment of another user returns 403`() = commentTest { client ->
        val alice = CommentTestSeed.seedUser(username = "alice")
        val bob = CommentTestSeed.seedUser(username = "bob", email = "bob@example.com")
        val post = CommentTestSeed.seedPost(alice.userId)
        val aliceCommentId = CommentTestSeed.insertComment(alice.userId, post.postId, "by alice")
        val bobToken = tokenFor(bob.authId, bob.userId, bob.email)

        val resp = client.delete("/api/comments/$aliceCommentId") { bearerAuth(bobToken) }

        assertEquals(HttpStatusCode.Forbidden, resp.status)

        // Comentariul rămâne neatins
        val list: List<CommentDTO> = client.get("/api/posts/${post.postId}/comments").body()
        assertEquals(1, list.size)
    }

    @Test
    fun `DELETE non-existent comment returns 404`() = commentTest { client ->
        val alice = CommentTestSeed.seedUser()
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        val resp = client.delete("/api/comments/${UUID.randomUUID()}") { bearerAuth(token) }

        assertEquals(HttpStatusCode.NotFound, resp.status)
    }

    @Test
    fun `DELETE with invalid commentId UUID returns 400`() = commentTest { client ->
        val alice = CommentTestSeed.seedUser()
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        val resp = client.delete("/api/comments/not-a-uuid") { bearerAuth(token) }

        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `DELETE without userId claim in token returns 401`() = commentTest { client ->
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)
        val commentId = CommentTestSeed.insertComment(alice.userId, post.postId, "hi")
        val token = jwt.generateJwtToken(credentialId = alice.authId, userId = null, email = alice.email)

        val resp = client.delete("/api/comments/$commentId") { bearerAuth(token) }

        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }
}