package features.like

import com.carspotter.core.util.getUuidClaim
import com.carspotter.core.util.toUuidOrNull
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.likeRoutes() {
    val likeService: ILikeService by application.inject()

    route("/posts/{postId}/likes") {
        authenticate("jwt", optional = true) {
            get {
                val postId = call.parameters["postId"].toUuidOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid postId"))

                val userId = call.getUuidClaim("userId")
                call.respond(HttpStatusCode.OK, likeService.getLikeStatus(postId, userId))
            }
        }

        authenticate("jwt") {
            post {
                val postId = call.parameters["postId"].toUuidOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid postId"))

                val userId = call.getUuidClaim("userId")
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid userId claim"))

                try {
                    val status = likeService.toggleLike(userId, postId)
                    call.respond(HttpStatusCode.OK, status)
                } catch (e: LikePostNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Post not found"))
                }
            }
        }
    }
}