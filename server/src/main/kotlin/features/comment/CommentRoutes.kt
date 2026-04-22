package com.carspotter.features.comment

import com.carspotter.features.comment.dto.CommentRequest
import com.carspotter.features.post.IPostService
import com.carspotter.core.util.getUuidClaim
import com.carspotter.core.util.toUuidOrNull
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.commentRoutes() {
    val commentService: ICommentService by application.inject()
    val postService: IPostService by application.inject()

        get("/comments/{postId}") {
            val postId = call.parameters["postId"].toUuidOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid or missing postId"))

            val comments = commentService.getCommentsForPost(postId)

            if (comments.isEmpty()) {
                return@get call.respond(HttpStatusCode.NoContent, mapOf("error" to "No comments found for this post"))
            }

            call.respond(HttpStatusCode.OK, comments)
        }

        authenticate("jwt") {
            route("/comments") {
                post {
                    val request = call.receive<CommentRequest>()
                    val userId = call.getUuidClaim("userId")
                        ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing userId"))

                    if (request.commentText.isBlank()) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Comment text cannot be blank"))
                        return@post
                    }

                    try {
                        commentService.addComment(userId, request.postId, request.commentText)
                        call.respond(HttpStatusCode.Created, mapOf("message" to "Comment created successfully"))
                        return@post

                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to create comment"))
                        return@post
                    }
                }

                delete("/{commentId}") {
                    val commentId = call.parameters["commentId"].toUuidOrNull()
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid or missing commentId"))

                    val userId = call.getUuidClaim("userId")
                        ?: return@delete call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing userId"))

                    val comment = commentService.getCommentById(commentId)

                    if (comment == null) {
                        return@delete call.respond(HttpStatusCode.NotFound, mapOf("error" to "Comment not found"))
                    }

                    val postOwnerId = postService.getUserIdByPost(comment.postId)

                    if (comment.userId != userId && postOwnerId != userId) {
                        return@delete call.respond(HttpStatusCode.Forbidden, mapOf("error" to "You are not authorized to delete this comment"))
                    }

                    val rowsAffected = commentService.deleteComment(commentId)

                    if (rowsAffected > 0) {
                        call.respond(HttpStatusCode.OK, mapOf("message" to "Comment deleted successfully"))
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to delete comment"))
                    }
                }
            }
        }
    }