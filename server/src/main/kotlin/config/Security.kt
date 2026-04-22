package com.carspotter.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*

fun Application.configureSecurity() {
    val jwtAudience = System.getenv("JWT_AUDIENCE") ?: throw IllegalStateException("JWT_AUDIENCE environment variable is not set")
    val jwtIssuer = System.getenv("JWT_ISSUER") ?: throw IllegalStateException("JWT_ISSUER environment variable is not set")
    val jwtRealm = "CarSpotter-server"
    val jwtSecret = System.getenv("JWT_SECRET") ?: throw IllegalStateException("JWT_SECRET environment variable is not set")

    // JWT authentication
    authentication {
        jwt("jwt") {
            realm = jwtRealm
            verifier(
                JWT
                    .require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtIssuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains(jwtAudience) &&
                    credential.payload.getClaim("credentialId").asInt() != null) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Missing or invalid JWT token"))
            }
        }
        jwt("admin") {
            realm = "CarSpotter Admin"
            verifier(
                JWT
                    .require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtIssuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.getClaim("email").asString().endsWith("@admin.com")) { // TODO: Rethink the logic for admin
                    JWTPrincipal(credential.payload)
                } else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Missing or invalid JWT token"))
            }

        }
    }

    // Google OAuth
    authentication { // TODO: Delete this because
        oauth("auth-oauth-google") {
            urlProvider = { System.getenv("APP_URL") + "/callback"  }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "google",
                    authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
                    accessTokenUrl = "https://accounts.google.com/o/oauth2/token",
                    requestMethod = HttpMethod.Post,
                    clientId = System.getenv("GOOGLE_CLIENT_ID"),
                    clientSecret = System.getenv("GOOGLE_CLIENT_SECRET"),
                    defaultScopes = listOf("https://www.googleapis.com/auth/userinfo.profile")
                )
            }
            client = HttpClient(Apache)
        }
    }
}
