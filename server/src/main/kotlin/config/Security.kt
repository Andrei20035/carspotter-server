package com.carspotter.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.carspotter.core.error.AuthApiError
import com.carspotter.core.error.AuthErrorCode
import com.carspotter.core.error.AuthErrorResponse
import com.carspotter.features.auth.session.ISessionService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import org.koin.ktor.ext.getKoin
import java.util.UUID

fun Application.configureSecurity(
    sessionService: ISessionService = getKoin().get(),
    jwtAudience: String = requireConfig("JWT_AUDIENCE"),
    jwtIssuer: String = requireConfig("JWT_ISSUER"),
    jwtSecret: String = requireConfig("JWT_SECRET"),
) {
    val jwtRealm = "CarSpotter-server"

    val jwtVerifier = JWT
        .require(Algorithm.HMAC256(jwtSecret))
        .withAudience(jwtAudience)
        .withIssuer(jwtIssuer)
        .build()

    authentication {
        jwt("jwt") {
            realm = jwtRealm
            verifier(jwtVerifier)
            validate { credential ->
                val credentialId = credential.payload
                    .getClaim("credentialId")
                    .asString()
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

                val sessionId = credential.payload.getClaim("sid").asString()
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                val version = credential.payload.getClaim("ver").asInt()

                if (credentialId == null || sessionId == null || version == null) {
                    null
                } else if (sessionService.validateSessionForRequest(sessionId, credentialId, version) != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                val authorization = call.request.headers[HttpHeaders.Authorization]
                val rawToken = authorization?.removePrefix("Bearer ")
                val tokenExpired = rawToken?.let { token ->
                    runCatching { JWT.decode(token).expiresAt?.before(java.util.Date()) == true }
                        .getOrDefault(false)
                } ?: false
                val hasSessionClaims = rawToken?.let { token ->
                    runCatching {
                        val decoded = JWT.decode(token)
                        decoded.getClaim("sid").asString() != null &&
                            decoded.getClaim("ver").asInt() != null
                    }.getOrDefault(false)
                } ?: false
                val code = when {
                    authorization == null -> AuthErrorCode.ACCESS_TOKEN_MISSING
                    tokenExpired -> AuthErrorCode.ACCESS_TOKEN_EXPIRED
                    !hasSessionClaims -> AuthErrorCode.ACCESS_TOKEN_INVALID
                    else -> AuthErrorCode.SESSION_REVOKED
                }
                call.respond(
                    HttpStatusCode.Unauthorized,
                    AuthErrorResponse(AuthApiError(code, authErrorMessage(code)))
                )
            }
        }

        jwt("admin") {
            realm = jwtRealm
            verifier(jwtVerifier)
            validate { credential ->
                val credentialId = credential.payload
                    .getClaim("credentialId")
                    .asString()
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

                val isAdmin = credential.payload.getClaim("isAdmin").asBoolean() ?: false

                val sessionId = credential.payload.getClaim("sid").asString()
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                val version = credential.payload.getClaim("ver").asInt()
                val sessionValid = credentialId != null && sessionId != null && version != null &&
                    sessionService.validateSessionForRequest(sessionId, credentialId, version) != null

                if (sessionValid && isAdmin) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
            }
        }
    }
}

private fun authErrorMessage(code: AuthErrorCode): String = when (code) {
    AuthErrorCode.ACCESS_TOKEN_MISSING -> "Access token is missing"
    AuthErrorCode.ACCESS_TOKEN_EXPIRED -> "Access token has expired"
    AuthErrorCode.SESSION_REVOKED -> "Session is revoked or no longer valid"
    else -> "Access token is invalid"
}

private fun requireConfig(name: String): String =
    System.getProperty(name)
        ?: System.getenv(name)
        ?: error("$name environment variable is not set")
