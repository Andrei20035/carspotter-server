package com.carspotter.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import java.util.UUID

fun Application.configureSecurity() {
    val jwtAudience = requireEnv("JWT_AUDIENCE")
    val jwtIssuer   = requireEnv("JWT_ISSUER")
    val jwtSecret   = requireEnv("JWT_SECRET")
    val jwtRealm    = "CarSpotter-server"

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

                if (credentialId != null) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Missing or invalid JWT token"))
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

                if (credentialId != null && isAdmin) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
            }
        }
    }
}

private fun requireEnv(name: String): String =
    System.getenv(name) ?: error("$name environment variable is not set")