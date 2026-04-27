package com.carspotter.features.user_car

import com.carspotter.core.util.getUuidClaim
import com.carspotter.core.util.toUuidOrNull
import com.carspotter.features.user_car.dto.UserCarRequest
import com.carspotter.features.user_car.dto.UserCarUpdateRequest
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject

private const val USER_CAR_MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024

fun Route.userCarRoutes() {
    val userCarService: IUserCarService by application.inject()
    val json = Json { ignoreUnknownKeys = true }

    get("/users/{userId}/car") {
        val userId = call.parameters["userId"].toUuidOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid userId"))

        val car = userCarService.getUserCar(userId)
            ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "User car not found"))

        call.respond(HttpStatusCode.OK, car)
    }

    authenticate("jwt") {
        get("/me/car") {
            val userId = call.getUuidClaim("userId")
                ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing userId"))

            val car = userCarService.getMyCar(userId)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "User car not found"))

            call.respond(HttpStatusCode.OK, car)
        }

        post("/me/car") {
            val userId = call.getUuidClaim("userId")
                ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing userId"))

            try {
                val payload = parseUserCarMultipart(call.receiveMultipart(), requireMetadata = true)
                val request = payload.request ?: throw BadRequestException("Missing metadata")
                val imageBytes = payload.imageBytes ?: throw BadRequestException("Missing image")
                val contentType = payload.contentType ?: throw BadRequestException("Missing image content-type")

                val created = userCarService.createMyCar(userId, request, imageBytes, contentType)
                call.respond(HttpStatusCode.Created, created)
            } catch (e: BadRequestException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid request")))
            } catch (e: UserCarValidationException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid request")))
            } catch (e: UserCarAlreadyExistsException) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to "User already has a car"))
            } catch (e: UserCarWriteException) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to create user car"))
            }
        }

        patch("/me/car") {
            val userId = call.getUuidClaim("userId")
                ?: return@patch call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing userId"))

            try {
                val payload = parseUserCarMultipart(call.receiveMultipart(), requireMetadata = false)
                val updated = userCarService.patchMyCar(
                    userId = userId,
                    request = payload.updateRequest,
                    imageBytes = payload.imageBytes,
                    contentType = payload.contentType,
                )
                call.respond(HttpStatusCode.OK, updated)
            } catch (e: BadRequestException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid request")))
            } catch (e: UserCarValidationException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid request")))
            } catch (e: UserCarNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "User car not found"))
            } catch (e: UserCarWriteException) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to update user car"))
            }
        }

        delete("/me/car") {
            val userId = call.getUuidClaim("userId")
                ?: return@delete call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing userId"))

            try {
                userCarService.deleteMyCar(userId)
                call.respond(HttpStatusCode.NoContent, "")
            } catch (e: UserCarNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "User car not found"))
            }
        }
    }
}

private data class UserCarMultipartPayload(
    val request: UserCarRequest? = null,
    val updateRequest: UserCarUpdateRequest? = null,
    val imageBytes: ByteArray? = null,
    val contentType: String? = null,
)

private suspend fun parseUserCarMultipart(
    multipart: io.ktor.http.content.MultiPartData,
    requireMetadata: Boolean,
): UserCarMultipartPayload {
    val json = Json { ignoreUnknownKeys = true }
    var createRequest: UserCarRequest? = null
    var updateRequest: UserCarUpdateRequest? = null
    var imageBytes: ByteArray? = null
    var contentType: String? = null

    multipart.forEachPart { part ->
        when (part) {
            is PartData.FormItem -> if (part.name == "metadata") {
                if (requireMetadata) {
                    createRequest = runCatching { json.decodeFromString<UserCarRequest>(part.value) }
                        .getOrElse { throw BadRequestException("Invalid metadata JSON") }
                } else {
                    updateRequest = runCatching { json.decodeFromString<UserCarUpdateRequest>(part.value) }
                        .getOrElse { throw BadRequestException("Invalid metadata JSON") }
                }
            }

            is PartData.FileItem -> if (part.name == "image") {
                val bytes = part.streamProvider().readBytes()
                if (bytes.size > USER_CAR_MAX_IMAGE_SIZE_BYTES) {
                    throw BadRequestException("Image exceeds max size of $USER_CAR_MAX_IMAGE_SIZE_BYTES bytes")
                }
                imageBytes = bytes
                contentType = part.contentType?.toString()
            }

            else -> Unit
        }
        part.dispose()
    }

    return UserCarMultipartPayload(
        request = createRequest,
        updateRequest = updateRequest,
        imageBytes = imageBytes,
        contentType = contentType,
    )
}
