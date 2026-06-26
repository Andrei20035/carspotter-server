package routes

import com.carspotter.features.auth.JwtService
import com.carspotter.features.auth.RefreshTokenGenerator
import com.carspotter.features.auth.session.AuthSessionDAO
import com.carspotter.features.auth.session.RevokeReason
import com.carspotter.features.auth.session.SessionScope
import com.carspotter.features.auth.session.SessionService
import com.carspotter.core.error.AuthErrorCode
import com.carspotter.core.error.AuthErrorResponse
import com.carspotter.features.post.dto.FeedResponseDTO
import com.carspotter.features.post.dto.PostDTO
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.serialization.kotlinx.json.json
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
import testutils.LikeTestSeed
import testutils.TestDatabaseFactory
import testutils.TestEnv
import testutils.setTestEnv
import testutils.stopKoinSafely
import testutils.testPostModule
import java.util.UUID
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostRoutesTest {
    private val jwt = JwtService(
        jwtSecret = TestEnv.JWT_SECRET,
        jwtIssuer = TestEnv.JWT_ISSUER,
        jwtAudience = TestEnv.JWT_AUDIENCE,
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

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

    private fun postTest(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) =
        testApplication {
            application { testPostModule() }
            val client = createClient {
                install(ContentNegotiation) {
                    json(json)
                }
            }
            block(client)
        }

    private suspend fun tokenFor(authId: UUID, userId: UUID, email: String): String {
        val (session) = SessionService(AuthSessionDAO(), RefreshTokenGenerator()).createSession(
            credentialId = authId,
            scope = SessionScope.FULL,
            userId = userId,
            deviceId = null,
            deviceName = null,
            userAgent = null,
            ip = null,
        )
        return jwt.generateAccessToken(session, authId, email, userId)
    }

    private suspend fun expiredTokenFor(authId: UUID, userId: UUID, email: String): String {
        val token = tokenFor(authId, userId, email)
        val decoded = JWT.decode(token)
        return JWT.create()
            .withAudience(TestEnv.JWT_AUDIENCE)
            .withIssuer(TestEnv.JWT_ISSUER)
            .withSubject(decoded.subject)
            .withIssuedAt(Date(System.currentTimeMillis() - 120_000))
            .withExpiresAt(Date(System.currentTimeMillis() - 60_000))
            .withClaim("credentialId", decoded.getClaim("credentialId").asString())
            .withClaim("sid", decoded.getClaim("sid").asString())
            .withClaim("ver", decoded.getClaim("ver").asInt())
            .withClaim("scope", decoded.getClaim("scope").asString())
            .withClaim("email", email)
            .withClaim("userId", userId.toString())
            .withClaim("isAdmin", false)
            .sign(Algorithm.HMAC256(TestEnv.JWT_SECRET))
    }

    @Test
    fun `GET feed returns 200 with empty array when there are no posts`() = postTest { client ->
        val response = client.get("/api/posts/feed")

        assertEquals(HttpStatusCode.OK, response.status)
        val feed = response.body<FeedResponseDTO>()
        assertEquals(emptyList<PostDTO>(), feed.posts)
        assertEquals(false, feed.hasMore)
        assertEquals(null, feed.nextCursor)
    }

    @Test
    fun `GET feed with valid token returns personalized like state`() = postTest { client ->
        val viewer = CommentTestSeed.seedUser(username = "viewer")
        val author = CommentTestSeed.seedUser(username = "author", email = "author@example.com")
        val post = CommentTestSeed.seedPost(author.userId)
        LikeTestSeed.insertLike(viewer.userId, post.postId)
        val token = tokenFor(viewer.authId, viewer.userId, viewer.email)

        val response = client.get("/api/posts/feed") { bearerAuth(token) }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<FeedResponseDTO>()
        assertTrue(body.posts.any { it.id == post.postId })
        assertEquals(true, body.posts.single { it.id == post.postId }.likedByCurrentUser)
    }

    @Test
    fun `GET feed with expired token returns 401 ACCESS_TOKEN_EXPIRED`() = postTest { client ->
        val user = CommentTestSeed.seedUser()
        val token = expiredTokenFor(user.authId, user.userId, user.email)

        val response = client.get("/api/posts/feed") { bearerAuth(token) }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(AuthErrorCode.ACCESS_TOKEN_EXPIRED, response.body<AuthErrorResponse>().error.code)
    }

    @Test
    fun `GET feed with malformed token returns 401 ACCESS_TOKEN_INVALID`() = postTest { client ->
        val response = client.get("/api/posts/feed") { bearerAuth("not-a-jwt") }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(AuthErrorCode.ACCESS_TOKEN_INVALID, response.body<AuthErrorResponse>().error.code)
    }

    @Test
    fun `GET feed with revoked session returns 401 SESSION_REVOKED`() = postTest { client ->
        val user = CommentTestSeed.seedUser()
        val token = tokenFor(user.authId, user.userId, user.email)
        val session = AuthSessionDAO().listActiveSessions(user.authId).single()
        AuthSessionDAO().revokeSession(session.id, RevokeReason.LOGOUT)

        val response = client.get("/api/posts/feed") { bearerAuth(token) }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(AuthErrorCode.SESSION_REVOKED, response.body<AuthErrorResponse>().error.code)
    }

    @Test
    fun `GET user posts returns 200 with empty feed when user has no posts`() = postTest { client ->
        val user = CommentTestSeed.seedUser()

        val response = client.get("/api/users/${user.userId}/posts")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<FeedResponseDTO>()
        assertEquals(emptyList<PostDTO>(), body.posts)
        assertEquals(false, body.hasMore)
        assertEquals(null, body.nextCursor)
    }

    @Test
    fun `GET user posts returns real likeCount`() = postTest { client ->
        val author = CommentTestSeed.seedUser(username = "author")
        val liker1 = CommentTestSeed.seedUser(username = "liker1", email = "liker1@example.com")
        val liker2 = CommentTestSeed.seedUser(username = "liker2", email = "liker2@example.com")
        val post = CommentTestSeed.seedPost(author.userId)
        LikeTestSeed.insertLike(liker1.userId, post.postId)
        LikeTestSeed.insertLike(liker2.userId, post.postId)

        val response = client.get("/api/users/${author.userId}/posts")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(2L, response.body<FeedResponseDTO>().posts.single { it.id == post.postId }.likeCount)
    }

    @Test
    fun `GET user posts returns real commentCount`() = postTest { client ->
        val author = CommentTestSeed.seedUser(username = "author")
        val commenter = CommentTestSeed.seedUser(username = "commenter", email = "commenter@example.com")
        val post = CommentTestSeed.seedPost(author.userId)
        CommentTestSeed.insertComment(commenter.userId, post.postId, "nice!")
        CommentTestSeed.insertComment(commenter.userId, post.postId, "wow!")

        val response = client.get("/api/users/${author.userId}/posts")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(2L, response.body<FeedResponseDTO>().posts.single { it.id == post.postId }.commentCount)
    }

    @Test
    fun `GET user posts returns zero counts for post with no interactions`() = postTest { client ->
        val author = CommentTestSeed.seedUser(username = "author")
        val post = CommentTestSeed.seedPost(author.userId)

        val response = client.get("/api/users/${author.userId}/posts")

        assertEquals(HttpStatusCode.OK, response.status)
        val dto = response.body<FeedResponseDTO>().posts.single { it.id == post.postId }
        assertEquals(0L, dto.likeCount)
        assertEquals(0L, dto.commentCount)
    }

    @Test
    fun `GET user posts returns likedByCurrentUser true when viewer has liked`() = postTest { client ->
        val author = CommentTestSeed.seedUser(username = "author")
        val viewer = CommentTestSeed.seedUser(username = "viewer", email = "viewer@example.com")
        val post = CommentTestSeed.seedPost(author.userId)
        LikeTestSeed.insertLike(viewer.userId, post.postId)
        val token = tokenFor(viewer.authId, viewer.userId, viewer.email)

        val response = client.get("/api/users/${author.userId}/posts") { bearerAuth(token) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(true, response.body<FeedResponseDTO>().posts.single { it.id == post.postId }.likedByCurrentUser)
    }

    @Test
    fun `GET user posts returns likedByCurrentUser false when viewer has not liked`() = postTest { client ->
        val author = CommentTestSeed.seedUser(username = "author")
        val viewer = CommentTestSeed.seedUser(username = "viewer", email = "viewer@example.com")
        val post = CommentTestSeed.seedPost(author.userId)
        val token = tokenFor(viewer.authId, viewer.userId, viewer.email)

        val response = client.get("/api/users/${author.userId}/posts") { bearerAuth(token) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(false, response.body<FeedResponseDTO>().posts.single { it.id == post.postId }.likedByCurrentUser)
    }

    @Test
    fun `GET user posts anonymous request returns real counts but likedByCurrentUser false`() = postTest { client ->
        val author = CommentTestSeed.seedUser(username = "author")
        val liker = CommentTestSeed.seedUser(username = "liker", email = "liker@example.com")
        val post = CommentTestSeed.seedPost(author.userId)
        LikeTestSeed.insertLike(liker.userId, post.postId)

        val response = client.get("/api/users/${author.userId}/posts")

        assertEquals(HttpStatusCode.OK, response.status)
        val dto = response.body<FeedResponseDTO>().posts.single { it.id == post.postId }
        assertEquals(1L, dto.likeCount)
        assertEquals(false, dto.likedByCurrentUser)
    }

    @Test
    fun `GET user posts paginates with stable cursor and no overlap`() = postTest { client ->
        val author = CommentTestSeed.seedUser(username = "author")
        repeat(3) { CommentTestSeed.seedPost(author.userId) }

        val firstPage = client.get("/api/users/${author.userId}/posts") {
            parameter("limit", "2")
        }.body<FeedResponseDTO>()

        assertEquals(2, firstPage.posts.size)
        assertEquals(true, firstPage.hasMore)
        val cursor = firstPage.nextCursor
        assertNotNull(cursor)

        val secondPage = client.get("/api/users/${author.userId}/posts") {
            parameter("limit", "2")
            parameter("cursorCreatedAt", cursor!!.lastCreatedAt.toString())
            parameter("cursorPostId", cursor.lastPostId.toString())
        }.body<FeedResponseDTO>()

        assertEquals(1, secondPage.posts.size)
        assertEquals(false, secondPage.hasMore)
        assertEquals(null, secondPage.nextCursor)

        val seenIds = (firstPage.posts + secondPage.posts).map { it.id }
        assertEquals(3, seenIds.size)
        assertEquals(3, seenIds.toSet().size)
    }

    @Test
    fun `GET user posts aggregates are scoped to current page only`() = postTest { client ->
        val author = CommentTestSeed.seedUser(username = "author")
        val liker = CommentTestSeed.seedUser(username = "liker", email = "liker@example.com")
        repeat(3) {
            val post = CommentTestSeed.seedPost(author.userId)
            LikeTestSeed.insertLike(liker.userId, post.postId)
        }

        val firstPage = client.get("/api/users/${author.userId}/posts") {
            parameter("limit", "2")
        }.body<FeedResponseDTO>()

        assertEquals(2, firstPage.posts.size)
        assertTrue(firstPage.posts.all { it.likeCount == 1L })
    }

    @Test
    fun `GET user posts with invalid token returns 401 ACCESS_TOKEN_INVALID`() = postTest { client ->
        val author = CommentTestSeed.seedUser(username = "author")

        val response = client.get("/api/users/${author.userId}/posts") { bearerAuth("not-a-jwt") }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(AuthErrorCode.ACCESS_TOKEN_INVALID, response.body<AuthErrorResponse>().error.code)
    }

    @Test
    fun `GET user posts with expired token returns 401 ACCESS_TOKEN_EXPIRED`() = postTest { client ->
        val author = CommentTestSeed.seedUser(username = "author")
        val viewer = CommentTestSeed.seedUser(username = "viewer", email = "viewer@example.com")
        val token = expiredTokenFor(viewer.authId, viewer.userId, viewer.email)

        val response = client.get("/api/users/${author.userId}/posts") { bearerAuth(token) }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(AuthErrorCode.ACCESS_TOKEN_EXPIRED, response.body<AuthErrorResponse>().error.code)
    }

    @Test
    fun `GET user posts with revoked session returns 401 SESSION_REVOKED`() = postTest { client ->
        val author = CommentTestSeed.seedUser(username = "author")
        val viewer = CommentTestSeed.seedUser(username = "viewer", email = "viewer@example.com")
        val token = tokenFor(viewer.authId, viewer.userId, viewer.email)
        val session = AuthSessionDAO().listActiveSessions(viewer.authId).single()
        AuthSessionDAO().revokeSession(session.id, RevokeReason.LOGOUT)

        val response = client.get("/api/users/${author.userId}/posts") { bearerAuth(token) }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(AuthErrorCode.SESSION_REVOKED, response.body<AuthErrorResponse>().error.code)
    }

    @Test
    fun `POST without JWT returns 401`() = postTest { client ->
        val response = client.post("/api/posts") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("metadata", """{"customBrand":"BMW","customModel":"M3"}""")
                        append(
                            "image",
                            "fake-image".toByteArray(),
                            Headers.build {
                                append(HttpHeaders.ContentType, ContentType.Image.JPEG.toString())
                                append(
                                    HttpHeaders.ContentDisposition,
                                    ContentDisposition.File.withParameter(
                                        ContentDisposition.Parameters.Name,
                                        "image"
                                    ).withParameter(
                                        ContentDisposition.Parameters.FileName,
                                        "photo.jpg"
                                    ).toString()
                                )
                            }
                        )
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST with revoked session returns 401 SESSION_REVOKED`() = postTest { client ->
        val user = CommentTestSeed.seedUser()
        val token = tokenFor(user.authId, user.userId, user.email)
        val session = AuthSessionDAO().listActiveSessions(user.authId).single()
        AuthSessionDAO().revokeSession(session.id, RevokeReason.LOGOUT)

        val response = client.post("/api/posts") {
            bearerAuth(token)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("metadata", """{"customBrand":"BMW","customModel":"M3"}""")
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(AuthErrorCode.SESSION_REVOKED, response.body<AuthErrorResponse>().error.code)
    }

    @Test
    fun `POST returns 400 when both carModelId and custom fields are provided`() = postTest { client ->
        val user = CommentTestSeed.seedUser()
        val token = tokenFor(user.authId, user.userId, user.email)

        val response = client.post("/api/posts") {
            bearerAuth(token)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "metadata",
                            """{"carModelId":"${UUID.randomUUID()}","customBrand":"BMW","customModel":"M3"}"""
                        )
                        append(
                            "image",
                            "fake-image".toByteArray(),
                            Headers.build {
                                append(HttpHeaders.ContentType, ContentType.Image.JPEG.toString())
                                append(
                                    HttpHeaders.ContentDisposition,
                                    ContentDisposition.File.withParameter(
                                        ContentDisposition.Parameters.Name,
                                        "image"
                                    ).withParameter(
                                        ContentDisposition.Parameters.FileName,
                                        "photo.jpg"
                                    ).toString()
                                )
                            }
                        )
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST creates a custom-brand post and feed returns it`() = postTest { client ->
        val user = CommentTestSeed.seedUser(username = "alice")
        val token = tokenFor(user.authId, user.userId, user.email)

        val createResponse = client.post("/api/posts") {
            bearerAuth(token)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "metadata",
                            """{"customBrand":"BMW","customModel":"M3","caption":"clean shot"}"""
                        )
                        append(
                            "image",
                            "fake-image".toByteArray(),
                            Headers.build {
                                append(HttpHeaders.ContentType, ContentType.Image.JPEG.toString())
                                append(
                                    HttpHeaders.ContentDisposition,
                                    ContentDisposition.File.withParameter(
                                        ContentDisposition.Parameters.Name,
                                        "image"
                                    ).withParameter(
                                        ContentDisposition.Parameters.FileName,
                                        "photo.jpg"
                                    ).toString()
                                )
                            }
                        )
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.Created, createResponse.status)

        val feedResponse = client.get("/api/posts/feed")
        assertEquals(HttpStatusCode.OK, feedResponse.status)

        val feed: FeedResponseDTO = feedResponse.body()
        val posts = feed.posts
        assertEquals(1, posts.size)
        assertEquals(false, feed.hasMore)
        assertEquals(null, feed.nextCursor)
        assertEquals(user.userId, posts.first().userId)
        assertEquals("alice", posts.first().username)
        assertEquals("BMW", posts.first().brand)
        assertEquals("M3", posts.first().model)
        assertEquals("clean shot", posts.first().caption)
        assertEquals(0L, posts.first().likeCount)
        assertEquals(0L, posts.first().commentCount)
        assertTrue(posts.first().imageUrl.contains("/uploads/posts/"))
    }

    @Test
    fun `GET feed paginates with a stable cursor and no overlap`() = postTest { client ->
        val user = CommentTestSeed.seedUser(username = "bob")
        // Seeded in quick succession; some may share created_at, exercising the id tiebreak.
        repeat(3) { CommentTestSeed.seedPost(user.userId) }

        val firstPage = client.get("/api/posts/feed") {
            parameter("limit", "2")
        }.body<FeedResponseDTO>()

        assertEquals(2, firstPage.posts.size)
        assertEquals(true, firstPage.hasMore)
        val cursor = firstPage.nextCursor
        assertNotNull(cursor)

        val secondPage = client.get("/api/posts/feed") {
            parameter("limit", "2")
            parameter("cursorCreatedAt", cursor!!.lastCreatedAt.toString())
            parameter("cursorPostId", cursor.lastPostId.toString())
        }.body<FeedResponseDTO>()

        assertEquals(1, secondPage.posts.size)
        assertEquals(false, secondPage.hasMore)
        assertEquals(null, secondPage.nextCursor)

        // All three posts seen exactly once across the two pages — no overlap, no gaps.
        val seenIds = (firstPage.posts + secondPage.posts).map { it.id }
        assertEquals(3, seenIds.size)
        assertEquals(3, seenIds.toSet().size)
    }

    @Test
    fun `GET feed returns 400 when only one cursor part is provided`() = postTest { client ->
        val response = client.get("/api/posts/feed") {
            parameter("cursorPostId", UUID.randomUUID().toString())
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `DELETE returns 403 when trying to delete another user's post`() = postTest { client ->
        val owner = CommentTestSeed.seedUser(username = "owner")
        val intruder = CommentTestSeed.seedUser(username = "intruder", email = "intruder@example.com")
        val post = CommentTestSeed.seedPost(owner.userId)
        val token = tokenFor(intruder.authId, intruder.userId, intruder.email)

        val response = client.delete("/api/posts/${post.postId}") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `DELETE returns 204 for the post author`() = postTest { client ->
        val owner = CommentTestSeed.seedUser(username = "owner")
        val post = CommentTestSeed.seedPost(owner.userId)
        val token = tokenFor(owner.authId, owner.userId, owner.email)

        val response = client.delete("/api/posts/${post.postId}") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
    }
}
