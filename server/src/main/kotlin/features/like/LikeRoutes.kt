package com.carspotter.features.like

import com.carspotter.core.util.getUuidClaim
import com.carspotter.core.util.toUuidOrNull
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.likeRoutes() {
    val likeService: ILikeService by application.inject()

    authenticate("jwt") {
        route("/likes") {
            post("/{postId}") {
                val userId = call.getUuidClaim("userId")
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing userId"))

                val postId = call.parameters["postId"].toUuidOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid or missing postId"))

                try {
                    likeService.likePost(userId, postId)
                    return@post call.respond(HttpStatusCode.OK, mapOf("message" to "Post liked successfully"))

                } catch (e: Exception) {
                    return@post call.respond(HttpStatusCode.Conflict, mapOf("error" to "You have already liked this post"))
                }
            }

            delete("/{postId}") {
                val userId = call.getUuidClaim("userId")
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing userId"))

                val postId = call.parameters["postId"].toUuidOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid or missing postId"))

                val rowsDeleted = likeService.unlikePost(userId, postId)

                if (rowsDeleted > 0) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Post unliked successfully"))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Like not found or already removed"))
                }
            }

            get("/posts/{postId}") {
                val postId = call.parameters["postId"].toUuidOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid or missing postId"))

                val users = likeService.getLikesForPost(postId)

                if (users.isEmpty()) {
                    call.respond(HttpStatusCode.NoContent, mapOf("error" to "No likes for this post"))
                } else {
                    call.respond(HttpStatusCode.OK, users)
                }
            }
        }
    }
}
