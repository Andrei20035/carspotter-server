package com.carspotter.routes

import com.carspotter.features.auth.JwtService
import features.like.LikeStatusDTO
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import testutils.CommentTestSeed
import testutils.LikeTestSeed
import testutils.TestDatabaseFactory
import testutils.TestEnv
import testutils.setTestEnv
import testutils.stopKoinSafely
import testutils.testLikeModule
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LikeRoutesTest {

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

    private fun likeTest(block: suspend ApplicationTestBuilder.(io.ktor.client.HttpClient) -> Unit) =
        testApplication {
            application { testLikeModule() }
            val client = createClient {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; isLenient = true })
                }
            }
            block(client)
        }

    private fun tokenFor(
        authId: UUID,
        userId: UUID?,
        email: String = "user@example.com",
    ): String = jwt.generateJwtToken(credentialId = authId, userId = userId, email = email)

    // ---------- GET /posts/{postId}/likes ----------

    @Test
    fun `GET returns 200 with count=0 and liked=false for post without likes`() = likeTest { client ->
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)

        val resp = client.get("/api/posts/${post.postId}/likes")

        assertEquals(HttpStatusCode.OK, resp.status)
        val body: LikeStatusDTO = resp.body()
        assertEquals(0L, body.count)
        assertFalse(body.liked)
    }

    @Test
    fun `GET returns 200 with correct count and liked=false without JWT`() = likeTest { client ->
        val alice = CommentTestSeed.seedUser(username = "alice")
        val bob = CommentTestSeed.seedUser(username = "bob", email = "bob@example.com")
        val post = CommentTestSeed.seedPost(alice.userId)
        LikeTestSeed.insertLike(alice.userId, post.postId)
        LikeTestSeed.insertLike(bob.userId, post.postId)

        // Fără JWT — liked trebuie să fie false, count trebuie să fie 2
        val resp = client.get("/api/posts/${post.postId}/likes")

        assertEquals(HttpStatusCode.OK, resp.status)
        val body: LikeStatusDTO = resp.body()
        assertEquals(2L, body.count)
        assertFalse(body.liked)
    }

    @Test
    fun `GET returns liked=true when authenticated user has liked`() = likeTest { client ->
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)
        LikeTestSeed.insertLike(alice.userId, post.postId)
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        val resp = client.get("/api/posts/${post.postId}/likes") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.OK, resp.status)
        val body: LikeStatusDTO = resp.body()
        assertTrue(body.liked)
        assertEquals(1L, body.count)
    }

    @Test
    fun `GET returns liked=false when authenticated user has not liked`() = likeTest { client ->
        val alice = CommentTestSeed.seedUser(username = "alice")
        val bob = CommentTestSeed.seedUser(username = "bob", email = "bob@example.com")
        val post = CommentTestSeed.seedPost(alice.userId)
        LikeTestSeed.insertLike(alice.userId, post.postId)
        val bobToken = tokenFor(bob.authId, bob.userId, bob.email)

        // Bob nu a dat like, dar postul are 1 like (de la alice)
        val resp = client.get("/api/posts/${post.postId}/likes") {
            bearerAuth(bobToken)
        }

        assertEquals(HttpStatusCode.OK, resp.status)
        val body: LikeStatusDTO = resp.body()
        assertFalse(body.liked)
        assertEquals(1L, body.count)
    }

    @Test
    fun `GET returns 200 with count=0 for non-existent postId`() = likeTest { client ->
        // Post inexistent → 0 like-uri, nu 404. Comportamentul corect: nu verificăm existența postului la GET.
        val resp = client.get("/api/posts/${UUID.randomUUID()}/likes")

        assertEquals(HttpStatusCode.OK, resp.status)
        val body: LikeStatusDTO = resp.body()
        assertEquals(0L, body.count)
        assertFalse(body.liked)
    }

    @Test
    fun `GET returns 400 for invalid postId`() = likeTest { client ->
        val resp = client.get("/api/posts/not-a-uuid/likes")
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `GET does not require authentication`() = likeTest { client ->
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)

        // Fără niciun header — trebuie să meargă
        val resp = client.get("/api/posts/${post.postId}/likes")
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    // ---------- POST /posts/{postId}/likes (toggle) ----------

    @Test
    fun `POST without JWT returns 401`() = likeTest { client ->
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)

        val resp = client.post("/api/posts/${post.postId}/likes")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `POST with invalid postId returns 400`() = likeTest { client ->
        val alice = CommentTestSeed.seedUser()
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        val resp = client.post("/api/posts/not-a-uuid/likes") { bearerAuth(token) }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `POST toggles to liked=true and count=1 on first call`() = likeTest { client ->
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        val resp = client.post("/api/posts/${post.postId}/likes") { bearerAuth(token) }

        assertEquals(HttpStatusCode.OK, resp.status)
        val body: LikeStatusDTO = resp.body()
        assertTrue(body.liked)
        assertEquals(1L, body.count)
        assertTrue(LikeTestSeed.likeExists(alice.userId, post.postId))
    }

    @Test
    fun `POST toggles to liked=false and count=0 on second call`() = likeTest { client ->
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)
        val token = tokenFor(alice.authId, alice.userId, alice.email)
        LikeTestSeed.insertLike(alice.userId, post.postId)

        val resp = client.post("/api/posts/${post.postId}/likes") { bearerAuth(token) }

        assertEquals(HttpStatusCode.OK, resp.status)
        val body: LikeStatusDTO = resp.body()
        assertFalse(body.liked)
        assertEquals(0L, body.count)
        assertFalse(LikeTestSeed.likeExists(alice.userId, post.postId))
    }

    @Test
    fun `POST two consecutive calls toggle and return correct states`() = likeTest { client ->
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        val firstResp = client.post("/api/posts/${post.postId}/likes") { bearerAuth(token) }
        val firstBody: LikeStatusDTO = firstResp.body()
        assertTrue(firstBody.liked)
        assertEquals(1L, firstBody.count)

        val secondResp = client.post("/api/posts/${post.postId}/likes") { bearerAuth(token) }
        val secondBody: LikeStatusDTO = secondResp.body()
        assertFalse(secondBody.liked)
        assertEquals(0L, secondBody.count)
    }

    @Test
    fun `POST returns correct count with multiple users liking`() = likeTest { client ->
        val alice = CommentTestSeed.seedUser(username = "alice")
        val bob = CommentTestSeed.seedUser(username = "bob", email = "bob@example.com")
        val post = CommentTestSeed.seedPost(alice.userId)
        // Bob deja a dat like
        LikeTestSeed.insertLike(bob.userId, post.postId)
        val aliceToken = tokenFor(alice.authId, alice.userId, alice.email)

        // Alice dă like — count-ul trebuie să devină 2
        val resp = client.post("/api/posts/${post.postId}/likes") { bearerAuth(aliceToken) }
        val body: LikeStatusDTO = resp.body()

        assertTrue(body.liked)
        assertEquals(2L, body.count)
    }

    @Test
    fun `POST with non-existent postId returns 404`() = likeTest { client ->
        val alice = CommentTestSeed.seedUser()
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        val resp = client.post("/api/posts/${UUID.randomUUID()}/likes") { bearerAuth(token) }

        assertEquals(HttpStatusCode.NotFound, resp.status)
    }

    @Test
    fun `POST with token missing userId claim returns 401`() = likeTest { client ->
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)
        // Token cu credentialId dar fără userId
        val token = jwt.generateJwtToken(credentialId = alice.authId, userId = null, email = alice.email)

        val resp = client.post("/api/posts/${post.postId}/likes") { bearerAuth(token) }

        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `POST like from one user does not affect another users like status`() = likeTest { client ->
        val alice = CommentTestSeed.seedUser(username = "alice")
        val bob = CommentTestSeed.seedUser(username = "bob", email = "bob@example.com")
        val post = CommentTestSeed.seedPost(alice.userId)
        val aliceToken = tokenFor(alice.authId, alice.userId, alice.email)
        val bobToken = tokenFor(bob.authId, bob.userId, bob.email)

        // Alice dă like
        client.post("/api/posts/${post.postId}/likes") { bearerAuth(aliceToken) }
        // Bob verifică statusul — el nu a dat like
        val bobStatus: LikeStatusDTO = client.get("/api/posts/${post.postId}/likes") {
            bearerAuth(bobToken)
        }.body()

        assertFalse(bobStatus.liked)
        assertEquals(1L, bobStatus.count)
    }
}