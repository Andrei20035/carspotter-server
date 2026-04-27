package features.comment

import com.carspotter.core.util.getUuidClaim
import com.carspotter.core.util.toUuidOrNull
import com.carspotter.features.comment.dto.CommentRequest
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.commentRoutes() {
    val commentService: ICommentService by application.inject()

    route("/posts/{postId}/comments") {
        get {
            val postId = call.parameters["postId"].toUuidOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid postId"))

            // Listă goală e răspuns valid (200 OK + []).
            // Nu verificăm dacă postul există — economie de o interogare.
            call.respond(HttpStatusCode.OK, commentService.getCommentsForPost(postId))
        }

        authenticate("jwt") {
            post {
                val postId = call.parameters["postId"].toUuidOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid postId"))
                val userId = call.getUuidClaim("userId")
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid userId claim"))

                val req = call.receive<CommentRequest>()

                try {
                    val created = commentService.addComment(userId, postId, req.commentText)
                    call.respond(HttpStatusCode.Created, created)
                } catch (e: CommentValidationException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
                } catch (e: PostNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Post not found"))
                }
            }
        }
    }

    authenticate("jwt") {
        delete("/comments/{commentId}") {
            val commentId = call.parameters["commentId"].toUuidOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid commentId"))
            val userId = call.getUuidClaim("userId")
                ?: return@delete call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid userId claim"))

            try {
                commentService.deleteComment(commentId, userId)
                call.respond(HttpStatusCode.NoContent)
            } catch (e: CommentNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Comment not found"))
            } catch (e: CommentForbiddenException) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Not authorized"))
            }
        }
    }
}