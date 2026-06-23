package com.carspotter.features.user

import com.carspotter.core.storage.IStorageService
import com.carspotter.core.util.getUuidClaim
import com.carspotter.core.util.toUuidOrNull
import com.carspotter.features.auth.JwtService
import com.carspotter.features.auth.session.ISessionService
import com.carspotter.features.user.dto.CreateUserRequest
import com.carspotter.features.user.dto.CreateUserResponse
import com.carspotter.features.user.dto.UpdateProfilePictureRequest
import com.carspotter.features.user.dto.toUser
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.contentType
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.time.LocalDate
import java.util.UUID

private const val PROFILE_PICTURE_MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024
private val profilePictureAllowedContentTypes = setOf("image/jpeg", "image/png", "image/webp")
private val profilePictureExtensions = mapOf(
    "image/jpeg" to "jpg",
    "image/png" to "png",
    "image/webp" to "webp",
)

fun Route.userRoutes() {
    val userService: IUserService by application.inject()
    val jwtService: JwtService by application.inject()
    val sessionService: ISessionService by application.inject()
    val storageService: IStorageService by application.inject()

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

                val sessionId = call.getUuidClaim("sid")
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing sessionId"))

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
                    val (session, refreshToken) = sessionService.promoteSession(sessionId, newUserId)
                    val accessToken = jwtService.generateAccessToken(
                        session = session,
                        credentialId = credentialId,
                        userId = newUserId,
                        email = email,
                    )
                    call.respond(
                        HttpStatusCode.Created,
                        CreateUserResponse(
                            accessToken = accessToken,
                            refreshToken = refreshToken,
                            userId = newUserId,
                        )
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
                    val updated = if (call.request.contentType().withoutParameters() == ContentType.MultiPart.FormData) {
                        val payload = parseProfilePictureMultipart(call.receiveMultipart())
                        val imageKey = createProfilePictureImageKey(payload.contentType)
                        storageService.uploadImage(payload.imageBytes, imageKey, payload.contentType)
                        try {
                            userService.updateProfilePicture(userId, imageKey)
                        } catch (e: Exception) {
                            runCatching { storageService.deleteImage(imageKey) }
                            throw e
                        }
                    } else {
                        val request = call.receive<UpdateProfilePictureRequest>()
                        userService.updateProfilePicture(userId, request.imagePath)
                    }
                    call.respond(HttpStatusCode.OK, updated)
                } catch (e: BadRequestException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid request")))
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid request")))
                } catch (e: UserNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                }
            }
        }
    }
}

private data class ProfilePictureMultipartPayload(
    val imageBytes: ByteArray,
    val contentType: String,
)

private suspend fun parseProfilePictureMultipart(
    multipart: io.ktor.http.content.MultiPartData,
): ProfilePictureMultipartPayload {
    var imageBytes: ByteArray? = null
    var contentType: String? = null

    multipart.forEachPart { part ->
        when (part) {
            is PartData.FileItem -> if (part.name == "image") {
                val bytes = part.streamProvider().readBytes()
                if (bytes.size > PROFILE_PICTURE_MAX_IMAGE_SIZE_BYTES) {
                    throw BadRequestException("Image exceeds max size of $PROFILE_PICTURE_MAX_IMAGE_SIZE_BYTES bytes")
                }
                imageBytes = bytes
                contentType = part.contentType?.toString()
            }

            else -> Unit
        }
        part.dispose()
    }

    val bytes = imageBytes ?: throw BadRequestException("Missing image")
    val ct = contentType ?: throw BadRequestException("Missing image content-type")
    require(bytes.isNotEmpty()) { "Image is required" }
    require(ct in profilePictureAllowedContentTypes) { "Unsupported image content type" }

    return ProfilePictureMultipartPayload(bytes, ct)
}

private fun createProfilePictureImageKey(contentType: String): String {
    val ext = profilePictureExtensions.getValue(contentType)
    val today = LocalDate.now()
    return "profile-pictures/%04d/%02d/%02d/%s.%s".format(
        today.year,
        today.monthValue,
        today.dayOfMonth,
        UUID.randomUUID(),
        ext,
    )
}
