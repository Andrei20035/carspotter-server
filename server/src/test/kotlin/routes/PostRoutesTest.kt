package routes

import com.carspotter.features.auth.JwtService
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
import testutils.TestDatabaseFactory
import testutils.TestEnv
import testutils.setTestEnv
import testutils.stopKoinSafely
import testutils.testPostModule
import java.util.UUID

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

    private fun tokenFor(authId: UUID, userId: UUID, email: String): String =
        jwt.generateJwtToken(credentialId = authId, userId = userId, email = email)

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
    fun `GET user posts returns 200 with empty array when user has no posts`() = postTest { client ->
        val user = CommentTestSeed.seedUser()

        val response = client.get("/api/users/${user.userId}/posts")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(emptyList<PostDTO>(), response.body<List<PostDTO>>())
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
