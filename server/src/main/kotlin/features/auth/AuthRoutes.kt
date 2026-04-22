package com.carspotter.features.auth

import com.carspotter.features.auth.dto.LoginRequest
import com.carspotter.features.auth.dto.RegisterRequest
import com.carspotter.features.auth.dto.UpdatePasswordRequest
import com.carspotter.core.util.getUuidClaim
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.authRoutes() {
    val authCredentialService: IAuthService by application.inject()
    val jwtService: JwtService by application.inject()

    route("/auth") {
        post("/login") {
            val request = call.receive<LoginRequest>()

            if (request.email.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Email must not be empty"))
                return@post
            }

            if (!isValidEmail(request.email)) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid email format"))
                return@post
            }

            val result = when (request.provider) {
                AuthProvider.REGULAR -> {
                    if (request.password.isNullOrBlank()) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Password must not be empty"))
                        return@post
                    }
                    authCredentialService.regularLogin(request.email, request.password)
                }

                AuthProvider.GOOGLE -> {
                    if (request.googleIdToken.isNullOrBlank()) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Google ID must not be empty"))
                        return@post
                    }
                    authCredentialService.googleLogin(request.email, request.googleIdToken)
                }
            }

            if (result != null) {
                call.respond(jwtService.generateJwtToken(credentialId = result.id, email = result.email))
            } else {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid credentials"))
            }
        }

        post("/register") {
            val request = call.receive<RegisterRequest>()

            if (request.email.isBlank() || !isValidEmail(request.email)) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid email"))
                return@post
            }

            if (request.provider == AuthProvider.REGULAR && request.password.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Password is required for regular registration"))
                return@post
            }

            val authCredential = AuthCredential(
                email = request.email,
                password = request.password,
                googleId = null,
                provider = request.provider,
            )

            try {
                val credentialId = authCredentialService.createCredentials(authCredential)
                call.respond(jwtService.generateJwtToken(credentialId = credentialId, email = authCredential.email))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            }

        }

        authenticate("jwt") {
            delete("/account") {
                val credentialId = call.getUuidClaim("credentialId")
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing credentialId"))

                val deletedRows = authCredentialService.deleteCredentials(credentialId)

                if (deletedRows > 0) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Account deleted successfully"))
                } else {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to delete account"))
                }
            }

            put("/password") {
                val credentialId = call.getUuidClaim("credentialId")
                    ?: return@put call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing credentialId"))

                val request = call.receive<UpdatePasswordRequest>()

                if(request.newPassword.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid new password"))
                    return@put
                }
                val updatedRows = authCredentialService.updatePassword(credentialId, request.newPassword)

                if (updatedRows > 0) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Password updated successfully"))
                } else {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to update password"))
                }
            }
        }
    }
}

private fun isValidEmail(email: String): Boolean {
    val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    return emailRegex.matches(email)
}