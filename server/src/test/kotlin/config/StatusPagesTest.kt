package com.carspotter.config

import com.carspotter.core.error.AuthBadRequestException
import com.carspotter.core.error.AuthConflictException
import com.carspotter.core.error.AuthErrorCode
import com.carspotter.core.error.AuthErrorResponse
import com.carspotter.core.error.AuthForbiddenException
import com.carspotter.core.error.AuthNotFoundException
import com.carspotter.core.error.AuthUnauthorizedException
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingRoot
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class StatusPagesTest {

    @Test
    fun `auth exceptions are serialized with their status and machine-readable code`() = testApplication {
        application {
            configureSerialization()
            configureAuthStatusPages()
            install(RoutingRoot)
            routing {
                get("/bad-request") {
                    throw AuthBadRequestException(AuthErrorCode.VALIDATION_ERROR, "Invalid input")
                }
                get("/unauthorized") {
                    throw AuthUnauthorizedException(AuthErrorCode.INVALID_CREDENTIALS, "Invalid credentials")
                }
                get("/forbidden") {
                    throw AuthForbiddenException(AuthErrorCode.ONBOARDING_REQUIRED, "Complete onboarding")
                }
                get("/not-found") {
                    throw AuthNotFoundException(AuthErrorCode.ACCOUNT_NOT_FOUND, "Account not found")
                }
                get("/conflict") {
                    throw AuthConflictException(AuthErrorCode.EMAIL_TAKEN, "Email already in use")
                }
            }
        }

        assertAuthError("/bad-request", HttpStatusCode.BadRequest, AuthErrorCode.VALIDATION_ERROR)
        assertAuthError("/unauthorized", HttpStatusCode.Unauthorized, AuthErrorCode.INVALID_CREDENTIALS)
        assertAuthError("/forbidden", HttpStatusCode.Forbidden, AuthErrorCode.ONBOARDING_REQUIRED)
        assertAuthError("/not-found", HttpStatusCode.NotFound, AuthErrorCode.ACCOUNT_NOT_FOUND)
        assertAuthError("/conflict", HttpStatusCode.Conflict, AuthErrorCode.EMAIL_TAKEN)
    }

    @Test
    fun `non-auth exceptions are not converted to auth error envelopes`() = testApplication {
        application {
            configureSerialization()
            configureAuthStatusPages()
            install(RoutingRoot)
            routing {
                get("/plain") {
                    call.respondText("ok")
                }
            }
        }

        assertEquals(HttpStatusCode.OK, client.get("/plain").status)
    }
}

suspend fun ApplicationTestBuilder.assertAuthError(
    path: String,
    expectedStatus: HttpStatusCode,
    expectedCode: AuthErrorCode
) {
    val response = client.get(path)
    assertEquals(expectedStatus, response.status)
    val body = Json.decodeFromString<AuthErrorResponse>(response.bodyAsText())
    assertEquals(expectedCode, body.error.code)
}
