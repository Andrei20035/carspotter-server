package com.carspotter.features.friend

import com.carspotter.core.util.getUuidClaim
import com.carspotter.core.util.toUuidOrNull
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.friendRoutes() {
    val friendService: IFriendService by application.inject()

    authenticate("jwt") {
        route("/friends") {
            authenticate("admin") {
                get("/admin") {
                    val principal = call.principal<JWTPrincipal>()
                    val isAdmin = principal?.getClaim("isAdmin", Boolean::class) ?: false
                    if (!isAdmin) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                        return@get
                    }
                    val allFriends = friendService.getAllFriendsInDb()
                    call.respond(HttpStatusCode.OK, allFriends)
                }
            }

            post("/{friendId}") {
                val userId = call.getUuidClaim("userId")
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing userId"))

                val friendId = call.parameters["friendId"].toUuidOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid or missing friendId"))

                if (userId == friendId) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Cannot add yourself as a friend"))
                }

                val result = friendService.addFriend(userId, friendId)

                if(result != friendId) {
                    return@post call.respond(HttpStatusCode.Conflict, mapOf("error" to "Friendship already exists"))
                } else {
                    return@post call.respond(HttpStatusCode.Created, mapOf("message" to "Friend added"))
                }
            }

            delete("/{friendId}") {
                val userId = call.getUuidClaim("userId")
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing userId"))

                val friendId = call.parameters["friendId"].toUuidOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid or missing friendId"))

                val deletedRows = friendService.deleteFriend(userId, friendId)

                if(deletedRows == 2) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Friend deleted"))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Friendship does not exist"))
                }
            }

            get {
                val userId = call.getUuidClaim("userId")
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing userId"))

                val friends = friendService.getAllFriends(userId)

                if(friends.isEmpty()) {
                    return@get call.respond(HttpStatusCode.NoContent)
                }
                call.respond(HttpStatusCode.OK, friends)
            }

        }
    }
}
