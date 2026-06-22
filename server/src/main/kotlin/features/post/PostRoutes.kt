package com.carspotter.features.post

import com.carspotter.core.util.getUuidClaim
import com.carspotter.core.util.toUuidOrNull
import com.carspotter.features.post.PostSource
import com.carspotter.features.post.dto.CreatePostDTO
import com.carspotter.features.post.dto.CreatePostMetadata
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

private const val DEFAULT_LIMIT = 20
private const val MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024

fun Route.postRoutes() {
    val postService: IPostService by application.inject()
    val json = Json { ignoreUnknownKeys = true }

    // Public feed, but authenticate optionally so we can populate `likedByCurrentUser`
    // when a valid token is present.
    authenticate("jwt", optional = true) {
        get("/posts/feed") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_LIMIT
            val cursorCreatedAt = call.request.queryParameters["cursorCreatedAt"]
            val cursorPostId = call.request.queryParameters["cursorPostId"]
            val currentUserId = call.getUuidClaim("userId")

            try {
                call.respond(
                    HttpStatusCode.OK,
                    postService.listFeed(limit, cursorCreatedAt, cursorPostId, currentUserId),
                )
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid request")))
            }
        }
    }

    get("/users/{userId}/posts") {
        val userId = call.parameters["userId"].toUuidOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid userId"))
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_LIMIT
        val cursorCreatedAt = call.request.queryParameters["cursorCreatedAt"]
        val cursorPostId = call.request.queryParameters["cursorPostId"]

        try {
            call.respond(HttpStatusCode.OK, postService.listPostsByUser(userId, limit, cursorCreatedAt, cursorPostId))
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid request")))
        }
    }

    get("/posts/{postId}") {
        val postId = call.parameters["postId"].toUuidOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid postId"))

        val post = postService.findPostById(postId)
            ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Post not found"))

        call.respond(HttpStatusCode.OK, post)
    }

    authenticate("jwt") {
        post("/posts") {
            val userId = call.getUuidClaim("userId")
                ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing userId"))

            try {
                val multipart = call.receiveMultipart()
                var metadata: CreatePostMetadata? = null
                var imageBytes: ByteArray? = null
                var contentType: String? = null

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> if (part.name == "metadata") {
                            metadata = runCatching { json.decodeFromString<CreatePostMetadata>(part.value) }
                                .getOrElse { throw BadRequestException("Invalid metadata JSON") }
                        }

                        is PartData.FileItem -> if (part.name == "image") {
                            val bytes = part.streamProvider().readBytes()
                            if (bytes.size > MAX_IMAGE_SIZE_BYTES) {
                                throw BadRequestException("Image exceeds max size of $MAX_IMAGE_SIZE_BYTES bytes")
                            }
                            imageBytes = bytes
                            contentType = part.contentType?.toString()
                        }

                        else -> Unit
                    }
                    part.dispose()
                }

                val meta = metadata ?: throw BadRequestException("Missing metadata")
                val bytes = imageBytes ?: throw BadRequestException("Missing image")
                val ct = contentType ?: throw BadRequestException("Missing image content-type")

                val postId = postService.createPost(
                    CreatePostDTO(
                        authorId = userId,
                        carModelId = meta.carModelId,
                        customBrand = meta.customBrand,
                        customModel = meta.customModel,
                        latitude = meta.latitude,
                        longitude = meta.longitude,
                        town = meta.town,
                        country = meta.country,
                        caption = meta.caption,
                        imageBytes = bytes,
                        contentType = ct,
                        source = PostSource.fromStringOrGallery(meta.source),
                        createdAtTimezone = meta.createdAtTimezone,
                    )
                )

                call.respond(HttpStatusCode.Created, mapOf("postId" to postId.toString()))
            } catch (e: BadRequestException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid request")))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid request")))
            } catch (e: PostCreationException) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to create post"))
            }
        }

        route("/posts") {
            delete("/{postId}") {
                val postId = call.parameters["postId"].toUuidOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid postId"))
                val userId = call.getUuidClaim("userId")
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing userId"))

                try {
                    postService.deletePostAsAuthor(postId, userId)
                    call.respond(HttpStatusCode.NoContent, "")
                } catch (e: PostNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Post not found"))
                } catch (e: PostForbiddenException) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "You do not have permission to delete this post"))
                }
            }
        }
    }
}
