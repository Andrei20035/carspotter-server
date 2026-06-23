package com.carspotter.features.auth

import com.carspotter.core.error.AuthBadRequestException
import com.carspotter.core.error.AuthConflictException
import com.carspotter.core.error.AuthErrorCode
import com.carspotter.core.error.AuthUnauthorizedException
import com.carspotter.features.auth.dto.LoginRequest
import com.carspotter.features.auth.dto.RefreshRequest
import com.carspotter.features.auth.dto.RegisterRequest
import com.carspotter.features.auth.dto.SessionDTO
import com.carspotter.features.auth.dto.UpdatePasswordRequest
import com.carspotter.core.util.getUuidClaim
import com.carspotter.features.auth.dto.AuthResponse
import com.carspotter.features.auth.dto.OnboardingStep
import com.carspotter.features.auth.session.ISessionService
import com.carspotter.features.auth.session.IAuthSessionDAO
import com.carspotter.features.auth.session.RefreshTokenConsumedException
import com.carspotter.features.auth.session.RefreshTokenExpiredException
import com.carspotter.features.auth.session.RefreshTokenInvalidException
import com.carspotter.features.auth.session.RefreshTokenReusedException
import com.carspotter.features.auth.session.RevokeReason
import com.carspotter.features.auth.session.SessionRevokedException
import com.carspotter.features.auth.session.SessionScope
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
    val sessionService: ISessionService by application.inject()
    val sessionDao: IAuthSessionDAO by application.inject()

    route("/auth") {
        post("/register") {
            val request = call.receive<RegisterRequest>()

            val authCredential = when (request.provider) {
                AuthProvider.REGULAR -> {
                    val normalizedEmail = request.email
                        ?.trim()
                        ?.lowercase()
                        ?: throw AuthBadRequestException(
                            AuthErrorCode.VALIDATION_ERROR,
                            "Email is required"
                        )

                    if (normalizedEmail.isBlank() || !isValidEmail(normalizedEmail)) {
                        throw AuthBadRequestException(
                            AuthErrorCode.VALIDATION_ERROR,
                            "Invalid email"
                        )
                    }

                    val password = request.password
                        ?.trim()
                        ?: throw AuthBadRequestException(
                            AuthErrorCode.VALIDATION_ERROR,
                            "Password is required for regular registration"
                        )

                    if (!isValidPassword(password)) {
                        throw AuthBadRequestException(
                            AuthErrorCode.WEAK_PASSWORD,
                            PASSWORD_REQUIREMENTS_MESSAGE
                        )
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
                        throw AuthBadRequestException(
                            AuthErrorCode.VALIDATION_ERROR,
                            "Google ID token is required"
                        )
                    }

                    val googleUser = googleTokenVerifier.verify(request.googleIdToken)
                        ?: throw AuthUnauthorizedException(
                            AuthErrorCode.INVALID_GOOGLE_TOKEN,
                            "Invalid Google token"
                        )

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
                val (session, refreshToken) = sessionService.createSession(
                    credentialId = credentialId,
                    scope = SessionScope.ONBOARDING,
                    deviceId = request.deviceId,
                    deviceName = request.deviceName,
                    userAgent = call.request.headers[HttpHeaders.UserAgent],
                    ip = call.request.local.remoteHost
                )
                val accessToken = jwtService.generateAccessToken(
                    session = session,
                    credentialId = credentialId,
                    email = authCredential.email
                )

                call.respond(
                    HttpStatusCode.Created,
                    AuthResponse(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresIn = JwtService.EXPIRES_IN_SECONDS,
                        scope = session.scope.name,
                        onboardingStep = OnboardingStep.PROFILE_REQUIRED
                    )
                )
            } catch (e: IllegalArgumentException) {
                throw AuthConflictException(
                    AuthErrorCode.EMAIL_TAKEN,
                    e.message ?: "Email is already registered"
                )
            }
        }

        post("/login") {
            val request = call.receive<LoginRequest>()

            val result = try {
                when (request.provider) {
                    AuthProvider.REGULAR -> {
                        val email = request.email
                            ?.trim()
                            ?.lowercase()
                            ?: throw AuthBadRequestException(
                                AuthErrorCode.VALIDATION_ERROR,
                                "Email is required"
                            )

                        if (!isValidEmail(email)) {
                            throw AuthBadRequestException(
                                AuthErrorCode.VALIDATION_ERROR,
                                "Invalid email format"
                            )
                        }

                        val password = request.password
                            ?: throw AuthBadRequestException(
                                AuthErrorCode.VALIDATION_ERROR,
                                "Password is required"
                            )
                        authService.regularLogin(email, password)
                    }
                    AuthProvider.GOOGLE -> {
                        val googleIdToken = request.googleIdToken
                            ?: throw AuthBadRequestException(
                                AuthErrorCode.VALIDATION_ERROR,
                                "Google ID token is required"
                            )
                        authService.googleLogin(googleIdToken)
                    }
                }
            } catch (e: IllegalArgumentException) {
                if (e.message?.contains("registered with password login", ignoreCase = true) == true) {
                    throw AuthConflictException(
                        AuthErrorCode.PROVIDER_MISMATCH,
                        e.message ?: "Account uses a different sign-in provider"
                    )
                }
                throw e
            }
            if (result != null) {
                val scope = if (result.userId == null) SessionScope.ONBOARDING else SessionScope.FULL
                val (session, refreshToken) = sessionService.createSession(
                    credentialId = result.id,
                    scope = scope,
                    userId = result.userId,
                    deviceId = request.deviceId,
                    deviceName = request.deviceName,
                    userAgent = call.request.headers[HttpHeaders.UserAgent],
                    ip = call.request.local.remoteHost
                )
                val accessToken = jwtService.generateAccessToken(
                    session = session,
                    credentialId = result.id,
                    email = result.email,
                    userId = result.userId
                )
                val onboardingStep =
                    if (result.userId == null) OnboardingStep.PROFILE_REQUIRED
                    else OnboardingStep.COMPLETED
                call.respond(
                    HttpStatusCode.OK,
                    AuthResponse(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresIn = JwtService.EXPIRES_IN_SECONDS,
                        scope = session.scope.name,
                        onboardingStep = onboardingStep
                    )
                )
            } else {
                throw AuthUnauthorizedException(
                    AuthErrorCode.INVALID_CREDENTIALS,
                    "Invalid credentials"
                )
            }
        }

        post("/refresh") {
            val request = call.receive<RefreshRequest>()
            if (request.refreshToken.isBlank()) {
                throw AuthBadRequestException(
                    AuthErrorCode.VALIDATION_ERROR,
                    "Refresh token is required"
                )
            }

            val (session, refreshToken) = try {
                sessionService.refreshTokens(request.refreshToken, request.deviceId)
            } catch (_: RefreshTokenConsumedException) {
                throw AuthUnauthorizedException(
                    AuthErrorCode.REFRESH_TOKEN_CONSUMED,
                    "Refresh token was already consumed"
                )
            } catch (_: RefreshTokenReusedException) {
                throw AuthUnauthorizedException(
                    AuthErrorCode.REFRESH_TOKEN_REUSED,
                    "Refresh token reuse detected"
                )
            } catch (_: RefreshTokenExpiredException) {
                throw AuthUnauthorizedException(
                    AuthErrorCode.REFRESH_TOKEN_EXPIRED,
                    "Refresh token has expired"
                )
            } catch (_: SessionRevokedException) {
                throw AuthUnauthorizedException(
                    AuthErrorCode.SESSION_REVOKED,
                    "Session is revoked"
                )
            } catch (_: RefreshTokenInvalidException) {
                throw AuthUnauthorizedException(
                    AuthErrorCode.REFRESH_TOKEN_INVALID,
                    "Refresh token is invalid"
                )
            }

            val credential = authService.getCredentialsById(session.credentialId)
                ?: throw AuthUnauthorizedException(
                    AuthErrorCode.REFRESH_TOKEN_INVALID,
                    "Refresh token is invalid"
                )
            val accessToken = jwtService.generateAccessToken(
                session = session,
                credentialId = session.credentialId,
                email = credential.email,
                userId = session.userId
            )

            call.respond(
                HttpStatusCode.OK,
                AuthResponse(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresIn = JwtService.EXPIRES_IN_SECONDS,
                    scope = session.scope.name,
                    onboardingStep = if (session.scope == SessionScope.FULL) {
                        OnboardingStep.COMPLETED
                    } else {
                        OnboardingStep.PROFILE_REQUIRED
                    }
                )
            )
        }

        authenticate("jwt") {
            post("/logout") {
                val sessionId = call.getUuidClaim("sid")
                    ?: throw AuthUnauthorizedException(
                        AuthErrorCode.ACCESS_TOKEN_INVALID,
                        "Invalid or missing session id"
                    )
                sessionService.revokeSession(sessionId, RevokeReason.LOGOUT)
                call.respond(HttpStatusCode.NoContent)
            }

            post("/logout-all") {
                val credentialId = call.getUuidClaim("credentialId")
                    ?: throw AuthUnauthorizedException(
                        AuthErrorCode.ACCESS_TOKEN_INVALID,
                        "Invalid or missing credential id"
                    )
                sessionService.revokeAllSessions(credentialId, RevokeReason.LOGOUT_ALL)
                call.respond(HttpStatusCode.NoContent)
            }

            get("/sessions") {
                val credentialId = call.getUuidClaim("credentialId")
                    ?: throw AuthUnauthorizedException(
                        AuthErrorCode.ACCESS_TOKEN_INVALID,
                        "Invalid or missing credential id"
                    )
                val currentSessionId = call.getUuidClaim("sid")
                    ?: throw AuthUnauthorizedException(
                        AuthErrorCode.ACCESS_TOKEN_INVALID,
                        "Invalid or missing session id"
                    )
                val sessions = sessionDao.listActiveSessions(credentialId).map { session ->
                    SessionDTO.from(session, current = session.id == currentSessionId)
                }
                call.respond(HttpStatusCode.OK, sessions)
            }

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
                    ?: throw AuthUnauthorizedException(
                        AuthErrorCode.ACCESS_TOKEN_INVALID,
                        "Invalid or missing credential id"
                    )
                val sessionId = call.getUuidClaim("sid")
                    ?: throw AuthUnauthorizedException(
                        AuthErrorCode.ACCESS_TOKEN_INVALID,
                        "Invalid or missing session id"
                    )

                val request = call.receive<UpdatePasswordRequest>()

                if (request.oldPassword.isBlank()) {
                    throw AuthBadRequestException(
                        AuthErrorCode.VALIDATION_ERROR,
                        "Current password is required"
                    )
                }

                if (!isValidPassword(request.newPassword)) {
                    throw AuthBadRequestException(
                        AuthErrorCode.WEAK_PASSWORD,
                        PASSWORD_REQUIREMENTS_MESSAGE
                    )
                }

                try {
                    val updatedRows = authService.updatePassword(
                        credentialId = credentialId,
                        oldPassword = request.oldPassword,
                        newPassword = request.newPassword
                    )

                    if (updatedRows > 0) {
                        val (session, refreshToken) = sessionService.rotateForPasswordChange(sessionId)
                        val credential = authService.getCredentialsById(credentialId)
                            ?: throw AuthUnauthorizedException(
                                AuthErrorCode.ACCOUNT_NOT_FOUND,
                                "Account not found"
                            )
                        val accessToken = jwtService.generateAccessToken(
                            session = session,
                            credentialId = credentialId,
                            email = credential.email,
                            userId = session.userId,
                        )
                        call.respond(
                            HttpStatusCode.OK,
                            AuthResponse(
                                accessToken = accessToken,
                                refreshToken = refreshToken,
                                expiresIn = JwtService.EXPIRES_IN_SECONDS,
                                scope = session.scope.name,
                                onboardingStep = if (session.scope == SessionScope.FULL) {
                                    OnboardingStep.COMPLETED
                                } else {
                                    OnboardingStep.PROFILE_REQUIRED
                                }
                            )
                        )
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Credentials not found"))
                    }
                } catch (e: IllegalArgumentException) {
                    throw AuthBadRequestException(
                        if (e.message?.contains("provider", ignoreCase = true) == true) {
                            AuthErrorCode.PROVIDER_NOT_REGULAR
                        } else {
                            AuthErrorCode.INVALID_CURRENT_PASSWORD
                        },
                        e.message ?: "Unable to update password"
                    )
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
    return password.length >= 8 &&
        password.any(Char::isUpperCase) &&
        password.any(Char::isLowerCase) &&
        password.any(Char::isDigit) &&
        password.any { !it.isLetterOrDigit() }
}

private const val PASSWORD_REQUIREMENTS_MESSAGE =
    "Password must be at least 8 characters long and include uppercase, lowercase, digit, and special characters."
