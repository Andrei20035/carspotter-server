package com.carspotter.routes

import com.carspotter.features.auth.JwtService
import com.carspotter.features.report.ReportReason
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import testutils.CommentTestSeed
import testutils.ReportTestSeed
import testutils.TestDatabaseFactory
import testutils.TestEnv
import testutils.setTestEnv
import testutils.stopKoinSafely
import testutils.testReportModule
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportRoutesTest {

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

    private fun reportTest(block: suspend ApplicationTestBuilder.(io.ktor.client.HttpClient) -> Unit) =
        testApplication {
            application { testReportModule() }
            val client = createClient {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; isLenient = true })
                }
            }
            block(client)
        }

    private fun tokenFor(authId: UUID, userId: UUID?, email: String = "user@example.com"): String =
        jwt.generateJwtToken(credentialId = authId, userId = userId, email = email)

    private fun HttpRequestBuilder.reasonBody(reasonJson: String) {
        contentType(ContentType.Application.Json)
        setBody("""{"reason":"$reasonJson"}""")
    }

    @Test
    fun `POST without JWT returns 401`() = reportTest { client ->
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)

        val resp = client.post("/api/posts/${post.postId}/reports") {
            reasonBody("DUPLICATE_POST")
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `POST with invalid postId returns 400`() = reportTest { client ->
        val alice = CommentTestSeed.seedUser()
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        val resp = client.post("/api/posts/not-a-uuid/reports") {
            bearerAuth(token)
            reasonBody("DUPLICATE_POST")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `POST with unknown reason returns 400`() = reportTest { client ->
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        val resp = client.post("/api/posts/${post.postId}/reports") {
            bearerAuth(token)
            reasonBody("NOT_A_REAL_REASON")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `POST creates report and returns 201`() = reportTest { client ->
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        val resp = client.post("/api/posts/${post.postId}/reports") {
            bearerAuth(token)
            reasonBody("INCORRECT_CAR_MODEL")
        }

        assertEquals(HttpStatusCode.Created, resp.status)
        assert(ReportTestSeed.reportExists(alice.userId, post.postId, ReportReason.INCORRECT_CAR_MODEL))
    }

    @Test
    fun `POST duplicate report returns 200 and does not create a second row`() = reportTest { client ->
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)
        val token = tokenFor(alice.authId, alice.userId, alice.email)
        ReportTestSeed.insertReport(alice.userId, post.postId, ReportReason.INAPPROPRIATE_CONTENT)

        val resp = client.post("/api/posts/${post.postId}/reports") {
            bearerAuth(token)
            reasonBody("INAPPROPRIATE_CONTENT")
        }

        assertEquals(HttpStatusCode.OK, resp.status)
        assertEquals(1L, ReportTestSeed.countReports(post.postId))
    }

    @Test
    fun `POST with non-existent postId returns 404`() = reportTest { client ->
        val alice = CommentTestSeed.seedUser()
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        val resp = client.post("/api/posts/${UUID.randomUUID()}/reports") {
            bearerAuth(token)
            reasonBody("DUPLICATE_POST")
        }
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }

    @Test
    fun `POST with token missing userId claim returns 401`() = reportTest { client ->
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)
        val token = jwt.generateJwtToken(credentialId = alice.authId, userId = null, email = alice.email)

        val resp = client.post("/api/posts/${post.postId}/reports") {
            bearerAuth(token)
            reasonBody("DUPLICATE_POST")
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }
}
