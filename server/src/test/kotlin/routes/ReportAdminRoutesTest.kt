package routes

import com.revio.server.features.auth.JwtService
import com.revio.server.features.auth.session.AuthSessionDAO
import com.revio.server.features.auth.RefreshTokenGenerator
import com.revio.server.features.auth.session.SessionScope
import com.revio.server.features.auth.session.SessionService
import com.revio.server.features.post.PostNotFoundException
import com.revio.server.features.report.ReportReason
import com.revio.server.features.report.ReportStatus
import features.report.IReportService
import features.report.ModerationDecision
import features.report.Report
import features.report.ReportNotFoundException
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.exceptions.ExposedSQLException
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
import testutils.testReportAdminModule
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

/**
 * HTTP contract of POST /api/admin/reports/{id}/resolve. Domain failures the moderator can
 * actually hit must come back as controlled statuses; an unexpected DB error must stay a 500
 * rather than being dressed up as a success (the report is left retryable by ReportService).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportAdminRoutesTest {

    private val jwt = JwtService(
        jwtSecret = TestEnv.JWT_SECRET,
        jwtIssuer = TestEnv.JWT_ISSUER,
        jwtAudience = TestEnv.JWT_AUDIENCE,
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val reportId = UUID.randomUUID()
    private val postId = UUID.randomUUID()

    @BeforeAll
    fun setup() {
        setTestEnv()
        TestDatabaseFactory.start()
    }

    @AfterAll
    fun tearDown() = TestDatabaseFactory.stop()

    @BeforeEach
    fun clean() {
        TestDatabaseFactory.cleanDatabase()
        stopKoinSafely()
    }

    private fun adminTest(
        reportService: IReportService,
        block: suspend ApplicationTestBuilder.(HttpClient, String) -> Unit,
    ) = testApplication {
        application { testReportAdminModule(reportService) }
        val client = createClient { install(ContentNegotiation) { json(json) } }
        block(client, adminToken())
    }

    /** A real session plus isAdmin=true — the admin realm validates both. */
    private suspend fun adminToken(): String {
        val seeded = CommentTestSeed.seedUser(username = "moderator")
        val (session) = SessionService(AuthSessionDAO(), RefreshTokenGenerator()).createSession(
            credentialId = seeded.authId,
            scope = SessionScope.FULL,
            userId = seeded.userId,
            deviceId = null,
            deviceName = null,
            userAgent = null,
            ip = null,
        )
        return jwt.generateAccessToken(session, seeded.authId, seeded.email, seeded.userId, isAdmin = true)
    }

    private fun resolvedReport(status: ReportStatus) = Report(
        id = reportId,
        reporterId = UUID.randomUUID(),
        postId = postId,
        reason = ReportReason.INAPPROPRIATE_CONTENT,
        status = status,
        createdAt = Instant.now(),
    )

    private suspend fun HttpClient.resolve(token: String, decision: ModerationDecision) =
        post("/api/admin/reports/$reportId/resolve") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"decision":"${decision.name}"}""")
        }

    @Test
    fun `resolve returns 200 when the takedown succeeds`() {
        val service = mockk<IReportService>()
        coEvery { service.resolveReport(reportId, any(), ModerationDecision.UPHOLD) } returns
            resolvedReport(ReportStatus.REVIEWED)

        adminTest(service) { client, token ->
            assertEquals(HttpStatusCode.OK, client.resolve(token, ModerationDecision.UPHOLD).status)
        }
    }

    @Test
    fun `resolve returns 404 for an unknown report`() {
        val service = mockk<IReportService>()
        coEvery { service.resolveReport(reportId, any(), any()) } throws ReportNotFoundException(reportId)

        adminTest(service) { client, token ->
            assertEquals(HttpStatusCode.NotFound, client.resolve(token, ModerationDecision.UPHOLD).status)
        }
    }

    @Test
    fun `resolve returns 409 when the reported post no longer exists`() {
        val service = mockk<IReportService>()
        coEvery { service.resolveReport(reportId, any(), any()) } throws PostNotFoundException(postId)

        adminTest(service) { client, token ->
            assertEquals(HttpStatusCode.Conflict, client.resolve(token, ModerationDecision.UPHOLD).status)
        }
    }

    @Test
    fun `resolve returns 500 for an unexpected database error`() {
        val service = mockk<IReportService>()
        coEvery { service.resolveReport(reportId, any(), any()) } throws
            ExposedSQLException(SQLException("boom", "08006"), emptyList(), mockk(relaxed = true))

        adminTest(service) { client, token ->
            assertEquals(HttpStatusCode.InternalServerError, client.resolve(token, ModerationDecision.UPHOLD).status)
        }
    }

    @Test
    fun `resolve rejects a non-admin token`() {
        val service = mockk<IReportService>(relaxed = true)

        testApplication {
            application { testReportAdminModule(service) }
            val client = createClient { install(ContentNegotiation) { json(json) } }

            val seeded = CommentTestSeed.seedUser(username = "plainuser")
            val (session) = SessionService(AuthSessionDAO(), RefreshTokenGenerator()).createSession(
                credentialId = seeded.authId,
                scope = SessionScope.FULL,
                userId = seeded.userId,
                deviceId = null,
                deviceName = null,
                userAgent = null,
                ip = null,
            )
            val userToken = jwt.generateAccessToken(session, seeded.authId, seeded.email, seeded.userId)

            assertEquals(HttpStatusCode.Forbidden, client.resolve(userToken, ModerationDecision.UPHOLD).status)
        }
    }
}
