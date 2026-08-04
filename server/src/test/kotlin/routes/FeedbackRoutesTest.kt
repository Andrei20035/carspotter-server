package com.revio.server.routes

import com.revio.server.features.auth.JwtService
import com.revio.server.features.auth.RefreshTokenGenerator
import com.revio.server.features.auth.session.AuthSessionDAO
import com.revio.server.features.auth.session.SessionScope
import com.revio.server.features.auth.session.SessionService
import com.revio.server.features.feedback.FIRST_POST_FEEDBACK_KEY
import com.revio.server.features.feedback.FirstPostFeedbackTable
import com.revio.server.features.feedback.UserFeedbackTable
import com.revio.server.features.user.UserTable
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import testutils.CommentTestSeed
import testutils.TestDatabaseFactory
import testutils.TestEnv
import testutils.setTestEnv
import testutils.stopKoinSafely
import testutils.testFeedbackModule
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FeedbackRoutesTest {

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

    private fun feedbackTest(block: suspend ApplicationTestBuilder.(io.ktor.client.HttpClient) -> Unit) =
        testApplication {
            application { testFeedbackModule() }
            val client = createClient {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; isLenient = true })
                }
            }
            block(client)
        }

    private suspend fun tokenFor(authId: UUID, userId: UUID?, email: String = "user@example.com"): String {
        val (session) = SessionService(AuthSessionDAO(), RefreshTokenGenerator()).createSession(
            credentialId = authId,
            scope = if (userId != null) SessionScope.FULL else SessionScope.ONBOARDING,
            userId = userId,
            deviceId = null,
            deviceName = null,
            userAgent = null,
            ip = null,
        )
        return jwt.generateAccessToken(session, authId, email, userId)
    }

    private fun HttpRequestBuilder.ratingBody(rating: Int, comment: String? = null) {
        contentType(ContentType.Application.Json)
        val commentField = comment?.let { ""","comment":"$it"""" } ?: ""
        setBody("""{"rating":$rating$commentField}""")
    }

    private fun countFeedbackRows(userId: UUID): Long = transaction {
        FirstPostFeedbackTable.selectAll()
            .where { FirstPostFeedbackTable.userId eq userId }
            .count()
    }

    @Test
    fun `POST first-post without JWT returns 401`() = feedbackTest { client ->
        val resp = client.post("/api/feedback/first-post") {
            ratingBody(5)
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `POST first-post with rating 0 returns 400`() = feedbackTest { client ->
        val alice = CommentTestSeed.seedUser()
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        val resp = client.post("/api/feedback/first-post") {
            bearerAuth(token)
            ratingBody(0)
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `POST first-post with rating 6 returns 400`() = feedbackTest { client ->
        val alice = CommentTestSeed.seedUser()
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        val resp = client.post("/api/feedback/first-post") {
            bearerAuth(token)
            ratingBody(6)
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `POST first-post creates feedback and returns 201`() = feedbackTest { client ->
        val alice = CommentTestSeed.seedUser()
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        val resp = client.post("/api/feedback/first-post") {
            bearerAuth(token)
            ratingBody(5)
        }

        assertEquals(HttpStatusCode.Created, resp.status)
        assertEquals(1L, countFeedbackRows(alice.userId))
    }

    @Test
    fun `POST first-post duplicate submit returns 200 already_submitted`() = feedbackTest { client ->
        val alice = CommentTestSeed.seedUser()
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        client.post("/api/feedback/first-post") {
            bearerAuth(token)
            ratingBody(5)
        }

        val resp = client.post("/api/feedback/first-post") {
            bearerAuth(token)
            ratingBody(4)
        }

        assertEquals(HttpStatusCode.OK, resp.status)
        val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals("already_submitted", body["status"]?.jsonPrimitive?.content)
        assertEquals(1L, countFeedbackRows(alice.userId))
    }

    @Test
    fun `POST first-post truncates comment longer than 1000 characters`() = feedbackTest { client ->
        val alice = CommentTestSeed.seedUser()
        val token = tokenFor(alice.authId, alice.userId, alice.email)
        val longComment = "a".repeat(1500)

        val resp = client.post("/api/feedback/first-post") {
            bearerAuth(token)
            ratingBody(5, longComment)
        }

        assertEquals(HttpStatusCode.Created, resp.status)
        val storedComment = transaction {
            FirstPostFeedbackTable.selectAll()
                .where { FirstPostFeedbackTable.userId eq alice.userId }
                .single()[FirstPostFeedbackTable.comment]
        }
        assertEquals(1000, storedComment?.length)
    }

    @Test
    fun `GET prompt-state for user with no state returns ELIGIBLE and shownCount 0`() = feedbackTest { client ->
        val alice = CommentTestSeed.seedUser()
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        val resp = client.get("/api/feedback/prompt-state?key=$FIRST_POST_FEEDBACK_KEY") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.OK, resp.status)
        val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals("ELIGIBLE", body["status"]?.jsonPrimitive?.content)
        assertEquals(0, body["shownCount"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun `POST prompt-state SHOWN three times caps shownCount at 2`() = feedbackTest { client ->
        val alice = CommentTestSeed.seedUser()
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        repeat(3) {
            client.post("/api/feedback/prompt-state") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody("""{"promptKey":"$FIRST_POST_FEEDBACK_KEY","event":"SHOWN"}""")
            }
        }

        val resp = client.get("/api/feedback/prompt-state?key=$FIRST_POST_FEEDBACK_KEY") {
            bearerAuth(token)
        }
        val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals(2, body["shownCount"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun `POST prompt-state SHOWN after SUBMITTED keeps status SUBMITTED`() = feedbackTest { client ->
        val alice = CommentTestSeed.seedUser()
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        client.post("/api/feedback/first-post") {
            bearerAuth(token)
            ratingBody(5)
        }

        client.post("/api/feedback/prompt-state") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"promptKey":"$FIRST_POST_FEEDBACK_KEY","event":"SHOWN"}""")
        }

        val resp = client.get("/api/feedback/prompt-state?key=$FIRST_POST_FEEDBACK_KEY") {
            bearerAuth(token)
        }
        val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals("SUBMITTED", body["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun `POST first-post with token missing userId claim returns 401`() = feedbackTest { client ->
        val alice = CommentTestSeed.seedUser()
        val token = tokenFor(alice.authId, userId = null, email = alice.email)

        val resp = client.post("/api/feedback/first-post") {
            bearerAuth(token)
            ratingBody(5)
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    private fun countUserFeedbackRows(userId: UUID): Long = transaction {
        UserFeedbackTable.selectAll()
            .where { UserFeedbackTable.userId eq userId }
            .count()
    }

    private fun HttpRequestBuilder.userFeedbackBody(
        category: String = "GENERAL",
        message: String? = "hello",
        rating: Int? = 4,
        quickReason: String? = "OTHER",
        includeDiagnostics: Boolean = false,
        appVersion: String? = null,
        clientFeedbackId: UUID = UUID.randomUUID(),
    ) {
        contentType(ContentType.Application.Json)
        val messageField = message?.let { ""","message":"$it"""" } ?: ""
        val ratingField = rating?.let { ""","rating":$it""" } ?: ""
        val quickReasonField = quickReason?.let { ""","quickReason":"$it"""" } ?: ""
        val appVersionField = appVersion?.let { ""","appVersion":"$it"""" } ?: ""
        setBody(
            """{"category":"$category"$messageField$ratingField$quickReasonField,"source":"SETTINGS_FEEDBACK","includeDiagnostics":$includeDiagnostics$appVersionField,"clientFeedbackId":"$clientFeedbackId"}""",
        )
    }

    @Test
    fun `POST user without JWT returns 401`() = feedbackTest { client ->
        val resp = client.post("/api/feedback/user") {
            userFeedbackBody()
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `POST user with NOT_WORKING and blank message returns 400`() = feedbackTest { client ->
        val alice = CommentTestSeed.seedUser()
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        val resp = client.post("/api/feedback/user") {
            bearerAuth(token)
            userFeedbackBody(category = "NOT_WORKING", message = " ", rating = null, quickReason = null)
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `POST user with GENERAL rating and reason creates feedback and returns 201`() = feedbackTest { client ->
        val alice = CommentTestSeed.seedUser()
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        val resp = client.post("/api/feedback/user") {
            bearerAuth(token)
            userFeedbackBody(category = "GENERAL", message = null, rating = 4, quickReason = "OTHER")
        }

        assertEquals(HttpStatusCode.Created, resp.status)
        assertEquals(1L, countUserFeedbackRows(alice.userId))
    }

    @Test
    fun `POST user duplicate clientFeedbackId returns 200 already_submitted and does not duplicate row`() =
        feedbackTest { client ->
            val alice = CommentTestSeed.seedUser()
            val token = tokenFor(alice.authId, alice.userId, alice.email)
            val clientFeedbackId = UUID.randomUUID()

            client.post("/api/feedback/user") {
                bearerAuth(token)
                userFeedbackBody(clientFeedbackId = clientFeedbackId)
            }

            val resp = client.post("/api/feedback/user") {
                bearerAuth(token)
                userFeedbackBody(clientFeedbackId = clientFeedbackId)
            }

            assertEquals(HttpStatusCode.OK, resp.status)
            val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
            assertEquals("already_submitted", body["status"]?.jsonPrimitive?.content)
            assertEquals(1L, countUserFeedbackRows(alice.userId))
        }

    @Test
    fun `POST user allows repeated submissions with different clientFeedbackId`() = feedbackTest { client ->
        val alice = CommentTestSeed.seedUser()
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        client.post("/api/feedback/user") {
            bearerAuth(token)
            userFeedbackBody()
        }
        client.post("/api/feedback/user") {
            bearerAuth(token)
            userFeedbackBody()
        }

        assertEquals(2L, countUserFeedbackRows(alice.userId))
    }

    @Test
    fun `POST user with includeDiagnostics false discards appVersion`() = feedbackTest { client ->
        val alice = CommentTestSeed.seedUser()
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        client.post("/api/feedback/user") {
            bearerAuth(token)
            userFeedbackBody(includeDiagnostics = false, appVersion = "1.2.3")
        }

        val storedAppVersion = transaction {
            UserFeedbackTable.selectAll()
                .where { UserFeedbackTable.userId eq alice.userId }
                .single()[UserFeedbackTable.appVersion]
        }
        assertEquals(null, storedAppVersion)
    }

    @Test
    fun `deleting user cascades user_feedback rows`() = feedbackTest { client ->
        val alice = CommentTestSeed.seedUser()
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        client.post("/api/feedback/user") {
            bearerAuth(token)
            userFeedbackBody()
        }
        assertEquals(1L, countUserFeedbackRows(alice.userId))

        transaction {
            UserTable.deleteWhere { UserTable.id eq alice.userId }
        }

        assertEquals(0L, countUserFeedbackRows(alice.userId))
    }
}
