package com.carspotter.features.auth

import com.carspotter.features.auth.dto.LoginRequest
import com.carspotter.features.auth.dto.RegisterRequest
import com.carspotter.features.auth.dto.UpdatePasswordRequest
import com.carspotter.core.util.getUuidClaim
import com.carspotter.features.auth.dto.AuthResponse
import com.carspotter.features.auth.dto.OnboardingStep
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.authRoutes() {
    val authService: IAuthService by application.inject()
    val googleTokenVerifier: GoogleTokenVerifier by application.inject()
    val jwtService: JwtService by application.inject()

    route("/auth") {
        post("/register") {
            val request = call.receive<RegisterRequest>()

            val authCredential = when (request.provider) {
                AuthProvider.REGULAR -> {
                    val normalizedEmail = request.email
                        ?.trim()
                        ?.lowercase()
                        ?: run {
                            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Email is required"))
                            return@post
                        }

                    if (normalizedEmail.isBlank() || !isValidEmail(normalizedEmail)) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid email"))
                        return@post
                    }

                    val password = request.password
                        ?.trim()
                        ?: run {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("error" to "Password is required for regular registration")
                            )
                            return@post
                        }

                    if (!isValidPassword(password)) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Password must be at least 8 characters long and include an uppercase letter and a special character.")
                        )
                        return@post
                    }

                    AuthCredential(
                        email = normalizedEmail,
                        password = password,
                        googleId = null,
                        provider = AuthProvider.REGULAR
                    )
                }
                AuthProvider.GOOGLE -> {
                    if (request.googleIdToken.isNullOrBlank()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Google ID token is required")
                        )
                        return@post
                    }

                    val googleUser = googleTokenVerifier.verify(request.googleIdToken)
                        ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid Google token"))

                    AuthCredential(
                        email = googleUser.email.trim().lowercase(),
                        password = null,
                        googleId = googleUser.googleId,
                        provider = AuthProvider.GOOGLE
                    )
                }
            }

            try {
                val credentialId = authService.createCredentials(authCredential)

                val token = jwtService.generateJwtToken(
                    credentialId = credentialId,
                    email = authCredential.email
                )

                call.respond(
                    HttpStatusCode.Created,
                    AuthResponse(
                        token = token,
                        onboardingStep = OnboardingStep.PROFILE_REQUIRED
                    )
                )
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to e.message)
                )
            }
        }

        post("/login") {
            val request = call.receive<LoginRequest>()

            val result = when (request.provider) {
                AuthProvider.REGULAR -> {
                    val email = request.email
                        ?.trim()
                        ?.lowercase()
                        ?: run {
                            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Email is required"))
                            return@post
                        }

                    if (!isValidEmail(email)) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid email format"))
                        return@post
                    }

                    val password = request.password
                        ?: run {
                            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Password is required"))
                            return@post
                        }

                    if (!isValidPassword(password)) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Password must be at least 8 characters long"))
                        return@post
                    }
                    authService.regularLogin(email, password)
                }
                AuthProvider.GOOGLE -> {
                    val googleIdToken = request.googleIdToken
                        ?: run {
                            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Google ID token is required"))
                            return@post
                        }
                    authService.googleLogin(googleIdToken)
                }
            }
            if (result != null) {
                val token = jwtService.generateJwtToken(
                    credentialId = result.id,
                    email = result.email
                )
                call.respond(
                    HttpStatusCode.OK,
                    AuthResponse(
                        token = token,
                        onboardingStep = OnboardingStep.COMPLETED
                    )
                )
            } else {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid credentials"))
            }
        }

        authenticate("jwt") {
            delete("/account") {
                val credentialId = call.getUuidClaim("credentialId")
                    ?: return@delete call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf("error" to "Invalid or missing credentialId")
                    )
                val credential = authService.getCredentialsById(credentialId)
                    ?: return@delete call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Account not found")
                    )
                val deletedRows = authService.deleteCredentials(credentialId)
                if (deletedRows > 0) {
                    call.respond(
                        HttpStatusCode.OK,
                        mapOf("message" to "Account deleted successfully")
                    )
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Account not found")
                    )
                }
            }

            put("/password") {
                val credentialId = call.getUuidClaim("credentialId")
                    ?: return@put call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf("error" to "Invalid or missing credentialId")
                    )

                val request = call.receive<UpdatePasswordRequest>()

                if (request.oldPassword.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Current password is required"))
                    return@put
                }

                if (request.newPassword.isBlank() || request.newPassword.length < 8) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "New password must be at least 8 characters"))
                    return@put
                }

                try {
                    val updatedRows = authService.updatePassword(
                        credentialId = credentialId,
                        oldPassword = request.oldPassword,
                        newPassword = request.newPassword
                    )

                    if (updatedRows > 0) {
                        call.respond(HttpStatusCode.OK, mapOf("message" to "Password updated successfully"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Credentials not found"))
                    }
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
                }
            }
        }
    }
}

private fun isValidEmail(email: String): Boolean {
    val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    return emailRegex.matches(email)
}

private fun isValidPassword(password: String): Boolean {
    return password.length >= 8
}