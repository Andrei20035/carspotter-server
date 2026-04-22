package com.carspotter.features.post

import com.carspotter.features.post.dto.CreatePostDTO
import com.carspotter.features.post.dto.CreatePostMetadata
import com.carspotter.features.post.dto.FeedRequest
import com.carspotter.features.post.dto.PostEditRequest
import com.carspotter.features.post.dto.addId
import com.carspotter.core.util.getUuidClaim
import com.carspotter.core.util.toUuidOrNull
import io.ktor.http.*
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.auth.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.time.ZoneId

private val logger = LoggerFactory.getLogger("PostRoutes")

fun Route.postRoutes() {
    val postService: IPostService by application.inject()

    authenticate("jwt") {
        route("/posts") {
            get("/feed") {
                val userId = call.getUuidClaim("userId")
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing userId"))
                val request = call.receive<FeedRequest>().addId(userId)

                try {
                    val response = postService.getFeedPostsForUser(
                        userId = request.userId!!,
                        latitude = request.latitude,
                        longitude = request.longitude,
                        radiusKm = request.radiusKm,
                        limit = request.limit,
                        cursor = request.cursor
                    )
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: Exception) {
                    logger.error("Error fetching feed for user ${request.userId}", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Unable to fetch feed. Please try again later."))
                }

            }

            // routes/PostRoutes.kt
            post {
                val userId = call.getUuidClaim("userId")
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid userId"))

                val multipart = call.receiveMultipart()

                var metadata: CreatePostMetadata? = null
                var imageBytes: ByteArray? = null
                var contentType: String? = null

                val maxSize = 10 * 1024 * 1024 // 10 MB

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            if (part.name == "metadata") {
                                metadata = Json.decodeFromString<CreatePostMetadata>(part.value)
                            }
                        }
                        is PartData.FileItem -> {
                            if (part.name == "image") {
                                contentType = part.contentType?.toString()
                                val bytes = part.streamProvider().readBytes()
                                if (bytes.size > maxSize) {
                                    part.dispose()
                                    throw BadRequestException("Image exceeds max size of $maxSize bytes")
                                }
                                imageBytes = bytes
                            }
                        }
                        else -> Unit
                    }
                    part.dispose()
                }

                val meta = metadata
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing metadata"))
                val bytes = imageBytes
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing image"))
                val ct = contentType
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing image content-type"))

                try {
                    val createDto = CreatePostDTO(
                        userId = userId,
                        carModelId = meta.carModelId,
                        latitude = meta.latitude,
                        longitude = meta.longitude,
                        description = meta.description,
                        imageBytes = bytes,
                        contentType = ct
                    )
                    val postId = postService.createPost(createDto)
                    call.respond(HttpStatusCode.Created, mapOf("postId" to postId.toString()))
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid input")))
                } catch (e: PostCreationException) {
                    logger.error("Failed to create post for user $userId", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to create post"))
                }
            }

            get("/{postId}") {
                val postId = call.parameters["postId"].toUuidOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid postId"))

                val post = postService.getPostById(postId)

                if (post != null) {
                    return@get call.respond(HttpStatusCode.OK, post)
                } else {
                    return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Post not found"))
                }
            }

            get {
                val posts = postService.getAllPosts()
                return@get call.respond(HttpStatusCode.OK, posts)
            }

            get("/current-day") {
                val userId = call.getUuidClaim("userId")
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing userId"))

                val userTimeZone = ZoneId.of(call.request.headers["Time-Zone"] ?: "UTC")  // Default to UTC if not specified

                val posts = postService.getCurrentDayPostsForUser(userId, userTimeZone)
                call.respond(HttpStatusCode.OK, posts)
            }

            put("/{postId}") {
                val postId = call.parameters["postId"].toUuidOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid or missing postId"))

                val userId = call.getUuidClaim("userId")
                    ?: return@put call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing userId"))

                val request = call.receive<PostEditRequest>()

                if(postService.getUserIdByPost(postId) != userId) {
                    return@put call.respond(HttpStatusCode.Forbidden, mapOf("error" to "You do not have permission to edit this post"))
                }

                val updatedRows = postService.editPost(postId, request.newDescription)

                if (updatedRows > 0) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Post updated successfully"))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Post not found or failed to update"))
                }
            }

            delete("/{postId}") {
                val postId = call.parameters["postId"].toUuidOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid postId"))

                val userId = call.getUuidClaim("userId")
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid invalid JWT token"))

                if(postService.getUserIdByPost(postId) != userId) {
                    return@delete call.respond(HttpStatusCode.Forbidden, mapOf("error" to "You do not have permission to edit this post"))
                }

                val deletedRows = postService.deletePost(postId)

                if (deletedRows > 0) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Post deleted successfully"))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Post not found or already deleted"))
                }
            }

        }
    }
}
