package routes

import com.revio.server.features.auth.JwtService
import com.revio.server.features.auth.RefreshTokenGenerator
import com.revio.server.features.auth.session.AuthSessionDAO
import com.revio.server.features.auth.session.SessionScope
import com.revio.server.features.auth.session.SessionService
import com.revio.server.features.car_model.ICarModelDAO
import com.revio.server.features.comment.dto.CommentDTO
import com.revio.server.features.leaderboard.dto.LeaderboardResponseDTO
import com.revio.server.features.post.IPostDAO
import com.revio.server.features.post.PostCreationException
import com.revio.server.features.post.PostServiceImpl
import com.revio.server.features.post.dto.CreatePostDTO
import com.revio.server.features.post.dto.CreatePostResponse
import com.revio.server.features.post.dto.FeedResponseDTO
import com.revio.server.features.scoring.IScoringDao
import com.revio.server.features.scoring.IScoringService
import com.revio.server.features.user.dto.UserDTO
import com.revio.server.features.user_car.dto.UserCarDTO
import features.comment.ICommentDAO
import features.like.ILikeDAO
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import testutils.CommentTestSeed
import testutils.R2TestStorageFactory
import testutils.TestDatabaseFactory
import testutils.TestEnv
import testutils.setTestEnv
import testutils.stopKoinSafely
import testutils.testCommentModule
import testutils.testLeaderboardModule
import testutils.testPostModule
import testutils.testUserCarModule
import testutils.testUserModule
import java.util.UUID

/**
 * End-to-end route tests exercising the real multipart upload/read flows
 * (posts, profile picture, user car, comments, leaderboard) with STORAGE_PROVIDER
 * effectively "r2" — IStorageService is R2StorageService pointed at a real MinIO
 * container (R2TestStorageFactory), so these validate the actual Put/Delete/URL
 * contract the routes rely on, not just the local-disk path.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2StorageRoutesTest {

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
        R2TestStorageFactory.start()
    }

    @AfterAll
    fun tearDown() {
        R2TestStorageFactory.stop()
        TestDatabaseFactory.stop()
    }

    @BeforeEach
    fun clean() {
        TestDatabaseFactory.cleanDatabase()
        stopKoinSafely()
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

    private fun imagePart(bytes: ByteArray, fileName: String = "photo.jpg", contentType: String = "image/jpeg") =
        formData {
            append(
                "image",
                bytes,
                Headers.build {
                    append(HttpHeaders.ContentType, contentType)
                    append(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.File.withParameter(ContentDisposition.Parameters.Name, "image")
                            .withParameter(ContentDisposition.Parameters.FileName, fileName)
                            .toString()
                    )
                }
            )
        }

    // ---------- Posts ----------

    private fun postTest(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) =
        testApplication {
            application { testPostModule(storage = R2TestStorageFactory.storageService()) }
            val client = createClient { install(ContentNegotiation) { json(json) } }
            block(client)
        }

    @Test
    fun `POST posts stores the image in R2 and returns an R2 imageUrl`() = postTest { client ->
        val user = CommentTestSeed.seedUser(username = "alice")
        val token = tokenFor(user.authId, user.userId, user.email)

        val response = client.post("/api/posts") {
            bearerAuth(token)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("metadata", """{"customBrand":"BMW","customModel":"M3"}""")
                    } + imagePart("fake-jpeg-bytes".toByteArray())
                )
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)

        val feed = client.get("/api/posts/feed").body<FeedResponseDTO>()
        val imageUrl = feed.posts.single().imageUrl
        assertFalse(imageUrl.contains("/uploads/"))
        assertTrue(imageUrl.startsWith(R2TestStorageFactory.PUBLIC_BASE_URL))
        assertTrue(R2TestStorageFactory.objectExists(R2TestStorageFactory.keyFromUrl(imageUrl)))
    }

    @Test
    fun `createPost rolls back the R2 object when the DB insert fails`() = runTest {
        // PostRoutes validates carModelId existence *before* ever touching storage, so a bad
        // carModelId can't reach the DB insert over HTTP. Rollback is exercised directly against
        // PostServiceImpl with a DAO that fails on insert, using the real R2 storage backend.
        val postDao = mockk<IPostDAO>()
        val carModelDao = mockk<ICarModelDAO>(relaxed = true)
        val likeDao = mockk<ILikeDAO>(relaxed = true)
        val commentDao = mockk<ICommentDAO>(relaxed = true)
        val scoringService = mockk<IScoringService>(relaxed = true)
        val scoringDao = mockk<IScoringDao>(relaxed = true)
        coEvery { postDao.insert(any()) } throws RuntimeException("simulated DB failure")

        val service = PostServiceImpl(
            postDao = postDao,
            storageService = R2TestStorageFactory.storageService(),
            carModelDao = carModelDao,
            likeDao = likeDao,
            commentDao = commentDao,
            scoringService = scoringService,
            scoringDao = scoringDao,
        )
        val keysBefore = R2TestStorageFactory.keysWithPrefix("posts/")

        assertThrows(PostCreationException::class.java) {
            runBlocking {
                service.createPost(
                    CreatePostDTO(
                        authorId = UUID.randomUUID(),
                        carModelId = null,
                        customBrand = "BMW",
                        customModel = "M3",
                        latitude = null,
                        longitude = null,
                        town = null,
                        country = null,
                        caption = null,
                        imageBytes = "fake-jpeg-bytes".toByteArray(),
                        contentType = "image/jpeg",
                    )
                )
            }
        }

        assertEquals(keysBefore, R2TestStorageFactory.keysWithPrefix("posts/"))
    }

    @Test
    fun `DELETE post removes the object from R2`() = postTest { client ->
        val user = CommentTestSeed.seedUser(username = "carol")
        val token = tokenFor(user.authId, user.userId, user.email)

        val createResponse = client.post("/api/posts") {
            bearerAuth(token)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("metadata", """{"customBrand":"BMW","customModel":"M3"}""")
                    } + imagePart("fake-jpeg-bytes".toByteArray())
                )
            )
        }
        val postId = json.decodeFromString<CreatePostResponse>(createResponse.bodyAsTextCompat()).postId

        val feed = client.get("/api/posts/feed").body<FeedResponseDTO>()
        val key = R2TestStorageFactory.keyFromUrl(feed.posts.single().imageUrl)
        assertTrue(R2TestStorageFactory.objectExists(key))

        val deleteResponse = client.delete("/api/posts/$postId") { bearerAuth(token) }

        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)
        assertFalse(R2TestStorageFactory.objectExists(key))
    }

    @Test
    fun `GET feed returns R2 URLs for post image and author avatar`() = postTest { client ->
        val author = CommentTestSeed.seedUser(
            username = "dave",
            profilePicturePath = "profile-pictures/2026/01/01/seed-avatar.jpg",
        )
        val token = tokenFor(author.authId, author.userId, author.email)

        client.post("/api/posts") {
            bearerAuth(token)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("metadata", """{"customBrand":"BMW","customModel":"M3"}""")
                    } + imagePart("fake-jpeg-bytes".toByteArray())
                )
            )
        }

        val feed = client.get("/api/posts/feed").body<FeedResponseDTO>()
        val post = feed.posts.single()
        assertTrue(post.imageUrl.startsWith(R2TestStorageFactory.PUBLIC_BASE_URL))
        assertEquals(
            "${R2TestStorageFactory.PUBLIC_BASE_URL}/profile-pictures/2026/01/01/seed-avatar.jpg",
            post.authorProfilePictureUrl,
        )
    }

    @Test
    fun `POST posts with a disallowed content type returns 400 and does not write to R2`() = postTest { client ->
        val user = CommentTestSeed.seedUser(username = "erin")
        val token = tokenFor(user.authId, user.userId, user.email)
        val keysBefore = R2TestStorageFactory.keysWithPrefix("posts/")

        val response = client.post("/api/posts") {
            bearerAuth(token)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("metadata", """{"customBrand":"BMW","customModel":"M3"}""")
                    } + imagePart("fake-gif-bytes".toByteArray(), fileName = "photo.gif", contentType = "image/gif")
                )
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(keysBefore, R2TestStorageFactory.keysWithPrefix("posts/"))
    }

    @Test
    fun `POST posts with an oversized image returns 400 and does not write to R2`() = postTest { client ->
        val user = CommentTestSeed.seedUser(username = "frank")
        val token = tokenFor(user.authId, user.userId, user.email)
        val keysBefore = R2TestStorageFactory.keysWithPrefix("posts/")
        val oversized = ByteArray(10 * 1024 * 1024 + 1)

        val response = client.post("/api/posts") {
            bearerAuth(token)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("metadata", """{"customBrand":"BMW","customModel":"M3"}""")
                    } + imagePart(oversized)
                )
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(keysBefore, R2TestStorageFactory.keysWithPrefix("posts/"))
    }

    // ---------- Profile picture ----------

    private fun userTest(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) =
        testApplication {
            application { testUserModule(storage = R2TestStorageFactory.storageService()) }
            val client = createClient { install(ContentNegotiation) { json(json) } }
            block(client)
        }

    @Test
    fun `PATCH profile-picture multipart stores the object in R2 and returns an R2 URL`() = userTest { client ->
        val user = CommentTestSeed.seedUser(username = "grace")
        val token = tokenFor(user.authId, user.userId, user.email)

        val response = client.patch("/api/users/me/profile-picture") {
            bearerAuth(token)
            setBody(MultiPartFormDataContent(imagePart("fake-avatar-bytes".toByteArray())))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val updated = response.body<UserDTO>()
        val url = requireNotNull(updated.profilePicturePath)
        assertTrue(url.startsWith(R2TestStorageFactory.PUBLIC_BASE_URL))
        assertTrue(R2TestStorageFactory.objectExists(R2TestStorageFactory.keyFromUrl(url)))
    }

    @Test
    fun `PATCH profile-picture JSON variant round-trips a full R2 URL back to the same URL`() = userTest { client ->
        val user = CommentTestSeed.seedUser(username = "heidi")
        val token = tokenFor(user.authId, user.userId, user.email)

        val multipartResponse = client.patch("/api/users/me/profile-picture") {
            bearerAuth(token)
            setBody(MultiPartFormDataContent(imagePart("fake-avatar-bytes".toByteArray())))
        }
        val uploadedUrl = requireNotNull(multipartResponse.body<UserDTO>().profilePicturePath)

        val jsonResponse = client.patch("/api/users/me/profile-picture") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"imagePath":"$uploadedUrl"}""")
        }

        assertEquals(HttpStatusCode.OK, jsonResponse.status)
        assertEquals(uploadedUrl, jsonResponse.body<UserDTO>().profilePicturePath)
    }

    // ---------- User car ----------

    private fun userCarTest(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) =
        testApplication {
            application { testUserCarModule(storage = R2TestStorageFactory.storageService()) }
            val client = createClient { install(ContentNegotiation) { json(json) } }
            block(client)
        }

    @Test
    fun `POST me car stores the image in R2`() = userCarTest { client ->
        val user = CommentTestSeed.seedUser(username = "ivan")
        val token = tokenFor(user.authId, user.userId, user.email)

        val response = client.post("/api/me/car") {
            bearerAuth(token)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("metadata", """{"customBrand":"BMW","customModel":"M3"}""")
                    } + imagePart("fake-car-bytes".toByteArray())
                )
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val car = response.body<UserCarDTO>()
        assertTrue(car.imageUrl.startsWith(R2TestStorageFactory.PUBLIC_BASE_URL))
        assertTrue(R2TestStorageFactory.objectExists(R2TestStorageFactory.keyFromUrl(car.imageUrl)))
    }

    @Test
    fun `PATCH me car with a new image deletes the old R2 object`() = userCarTest { client ->
        val user = CommentTestSeed.seedUser(username = "judy")
        val token = tokenFor(user.authId, user.userId, user.email)

        val created = client.post("/api/me/car") {
            bearerAuth(token)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("metadata", """{"customBrand":"BMW","customModel":"M3"}""")
                    } + imagePart("fake-car-bytes".toByteArray())
                )
            )
        }.body<UserCarDTO>()
        val oldKey = R2TestStorageFactory.keyFromUrl(created.imageUrl)
        assertTrue(R2TestStorageFactory.objectExists(oldKey))

        val patched = client.patch("/api/me/car") {
            bearerAuth(token)
            setBody(MultiPartFormDataContent(imagePart("fake-car-bytes-v2".toByteArray())))
        }.body<UserCarDTO>()
        val newKey = R2TestStorageFactory.keyFromUrl(patched.imageUrl)

        assertFalse(R2TestStorageFactory.objectExists(oldKey))
        assertTrue(R2TestStorageFactory.objectExists(newKey))
    }

    @Test
    fun `PATCH me car metadata-only does not touch R2`() = userCarTest { client ->
        val user = CommentTestSeed.seedUser(username = "kevin")
        val token = tokenFor(user.authId, user.userId, user.email)

        val created = client.post("/api/me/car") {
            bearerAuth(token)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("metadata", """{"customBrand":"BMW","customModel":"M3"}""")
                    } + imagePart("fake-car-bytes".toByteArray())
                )
            )
        }.body<UserCarDTO>()

        val patched = client.patch("/api/me/car") {
            bearerAuth(token)
            setBody(
                MultiPartFormDataContent(
                    formData { append("metadata", """{"customBrand":"Audi","customModel":"RS3"}""") }
                )
            )
        }.body<UserCarDTO>()

        assertEquals(created.imageUrl, patched.imageUrl)
        assertTrue(R2TestStorageFactory.objectExists(R2TestStorageFactory.keyFromUrl(created.imageUrl)))
    }

    // ---------- Comments ----------

    @Test
    fun `GET comments resolves author avatar to an R2 URL`() = testApplication {
        application { testCommentModule(storage = R2TestStorageFactory.storageService()) }
        val client = createClient { install(ContentNegotiation) { json(json) } }

        val author = CommentTestSeed.seedUser(
            username = "liam",
            profilePicturePath = "profile-pictures/2026/01/01/liam-avatar.jpg",
        )
        val post = CommentTestSeed.seedPost(author.userId)
        CommentTestSeed.insertComment(author.userId, post.postId, "nice shot!")

        val comments = client.get("/api/posts/${post.postId}/comments").body<List<CommentDTO>>()

        assertEquals(
            "${R2TestStorageFactory.PUBLIC_BASE_URL}/profile-pictures/2026/01/01/liam-avatar.jpg",
            comments.single().profilePicturePath,
        )
    }

    // ---------- Leaderboard ----------

    @Test
    fun `GET leaderboard resolves avatarUrl to an R2 URL`() = testApplication {
        application { testLeaderboardModule(storage = R2TestStorageFactory.storageService()) }
        val client = createClient { install(ContentNegotiation) { json(json) } }

        val user = CommentTestSeed.seedUser(
            username = "mia",
            profilePicturePath = "profile-pictures/2026/01/01/mia-avatar.jpg",
        )
        val token = tokenFor(user.authId, user.userId, user.email)

        val response = client.get("/api/leaderboard") { bearerAuth(token) }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<LeaderboardResponseDTO>()
        val entry = body.entries.single { it.userId == user.userId }
        assertEquals(
            "${R2TestStorageFactory.PUBLIC_BASE_URL}/profile-pictures/2026/01/01/mia-avatar.jpg",
            entry.avatarUrl,
        )
    }
}

private suspend fun io.ktor.client.statement.HttpResponse.bodyAsTextCompat(): String = this.body()
