package com.carspotter.features.user

import com.carspotter.core.util.getUuidClaim
import com.carspotter.core.util.toUuidOrNull
import com.carspotter.features.auth.JwtService
import com.carspotter.features.user.dto.CreateUserRequest
import com.carspotter.features.user.dto.CreateUserResponse
import com.carspotter.features.user.dto.UpdateProfilePictureRequest
import com.carspotter.features.user.dto.toUser
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.userRoutes() {
    val userService: IUserService by application.inject()
    val jwtService: JwtService by application.inject()

    route("/users") {
        get("/{userId}") {
            val userId = call.parameters["userId"].toUuidOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid userId"))

            val user = userService.getUserById(userId)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))

            call.respond(HttpStatusCode.OK, user)
        }

        authenticate("jwt") {
            post {
                val request = call.receive<CreateUserRequest>()
                val credentialId = call.getUuidClaim("credentialId")
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing credentialId"))

                val email = call.principal<JWTPrincipal>()
                    ?.payload
                    ?.getClaim("email")
                    ?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing email"))

                try {
                    val newUserId = userService.createUserProfile(
                        authCredentialId = credentialId,
                        user = request.toUser(credentialId),
                    )
                    val newJwtToken = jwtService.generateJwtToken(
                        credentialId = credentialId,
                        userId = newUserId,
                        email = email,
                    )
                    call.respond(
                        HttpStatusCode.Created,
                        CreateUserResponse(jwtToken = newJwtToken, userId = newUserId)
                    )
                } catch (e: UsernameAlreadyExistsException) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to (e.message ?: "Username is already taken")))
                } catch (e: UserProfileAlreadyExistsException) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to (e.message ?: "Profile already exists")))
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid request")))
                }
            }

            get("/me") {
                val userId = call.getUuidClaim("userId")
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing userId"))

                val user = userService.getUserById(userId)
                    ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))

                call.respond(HttpStatusCode.OK, user)
            }

            patch("/me/profile-picture") {
                val userId = call.getUuidClaim("userId")
                    ?: return@patch call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing userId"))

                try {
                    val request = call.receive<UpdateProfilePictureRequest>()
                    val updated = userService.updateProfilePicture(userId, request.imagePath)
                    call.respond(HttpStatusCode.OK, updated)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid request")))
                } catch (e: UserNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                }
            }
        }
    }
}
