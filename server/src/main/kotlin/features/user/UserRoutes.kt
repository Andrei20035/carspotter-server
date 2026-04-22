package com.carspotter.features.user

import com.carspotter.features.user.dto.CreateUserRequest
import com.carspotter.features.user.dto.UpdateProfilePictureRequest
import com.carspotter.features.user.dto.toUser
import com.carspotter.features.auth.dto.CreateUserResponse
import com.carspotter.features.auth.JwtService
import com.carspotter.core.util.getUuidClaim
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.userRoutes() {
    val userService: IUserService by application.inject()
    val jwtService: JwtService by application.inject()

    authenticate("jwt") {
        route("/user") {
            get("/me") {
                val userId = call.getUuidClaim("userId")
                    ?: return@get call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf("error" to "Invalid or missing userId")
                    )

                val user = userService.getUserById(userId)

                if (user != null) {
                    return@get call.respond(user)
                } else {
                    return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                }
            }
            authenticate("admin") {
                get("/all") {
                    val principal = call.principal<JWTPrincipal>()
                    val isAdmin = principal?.getClaim("isAdmin", Boolean::class) ?: false
                    if (!isAdmin) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                        return@get
                    }
                    val users = userService.getAllUsers()
                    call.respond(users)
                }
            }
            get("/by-username/{username}") {
                val username = call.parameters["username"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing username"))

                val users = userService.getUserByUsername(username)
                return@get call.respond(HttpStatusCode.OK, users)
            }

            post {
                val request = call.receive<CreateUserRequest>()
                val credentialId = call.getUuidClaim("userId")
                    ?: return@post call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf("error" to "Invalid or missing credentialId")
                    )

                val email = call.principal<JWTPrincipal>()?.payload?.getClaim("email")?.asString()
                    ?: return@post call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf("error" to "Invalid or missing email")
                    )

                try {
                    val user = request.toUser(credentialId)
                    val newUserId = userService.createUser(user)
                    val newJwtToken = jwtService.generateJwtToken(
                        credentialId = credentialId,
                        userId = newUserId,
                        email = email
                    )

                    val response = CreateUserResponse(
                        jwtToken = newJwtToken.values.first(),
                        userId = newUserId
                    )

                    return@post call.respond(HttpStatusCode.Created, response)

                } catch (e: Exception) {
                    return@post call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Failed to create user")
                    )
                }

            }

            put("/profile-picture") {
                val userId = call.getUuidClaim("userId")
                    ?: return@put call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf("error" to "Invalid or missing userId")
                    )

                val request = call.receive<UpdateProfilePictureRequest>()
                val result = userService.updateProfilePicture(userId, request.imagePath)

                if (result > 0) {
                    call.respond(HttpStatusCode.OK, mapOf("error" to "Profile picture updated"))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                }
            }

            delete("/me") {
                val userId = call.getUuidClaim("userId")
                    ?: return@delete call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf("error" to "Invalid or missing userId")
                    )

                val result = userService.deleteUser(userId)

                if (result > 0) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "User deleted"))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                }
            }
        }
    }
}
