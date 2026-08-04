package com.revio.server.features.feedback

import com.revio.server.core.util.getUuidClaim
import com.revio.server.features.feedback.dto.PromptStateUpdateDTO
import com.revio.server.features.feedback.dto.SubmitFirstPostFeedbackDTO
import com.revio.server.features.feedback.dto.SubmitUserFeedbackDTO
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.feedbackRoutes() {
    val feedbackService: IFeedbackService by application.inject()

    route("/feedback") {
        authenticate("jwt") {
            post("/first-post") {
                val userId = call.getUuidClaim("userId")
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid userId claim"))

                val dto = try {
                    call.receive<SubmitFirstPostFeedbackDTO>()
                } catch (e: BadRequestException) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid feedback payload"))
                }

                try {
                    when (feedbackService.submit(userId, dto)) {
                        SubmitResult.CREATED ->
                            call.respond(HttpStatusCode.Created, mapOf("status" to "recorded"))
                        SubmitResult.ALREADY_SUBMITTED ->
                            call.respond(HttpStatusCode.OK, mapOf("status" to "already_submitted"))
                    }
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid rating")))
                } catch (e: FeedbackUserNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                }
            }

            get("/prompt-state") {
                val userId = call.getUuidClaim("userId")
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid userId claim"))

                val key = call.request.queryParameters["key"] ?: FIRST_POST_FEEDBACK_KEY
                call.respond(HttpStatusCode.OK, feedbackService.getPromptState(userId, key))
            }

            post("/prompt-state") {
                val userId = call.getUuidClaim("userId")
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid userId claim"))

                val update = try {
                    call.receive<PromptStateUpdateDTO>()
                } catch (e: BadRequestException) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid prompt state update"))
                }

                feedbackService.recordPromptEvent(userId, update.promptKey, update.event)
                call.respond(HttpStatusCode.OK, mapOf("status" to "recorded"))
            }

            post("/user") {
                val userId = call.getUuidClaim("userId")
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid userId claim"))

                val dto = try {
                    call.receive<SubmitUserFeedbackDTO>()
                } catch (e: BadRequestException) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid feedback payload"))
                }

                try {
                    when (feedbackService.submitUserFeedback(userId, dto)) {
                        SubmitResult.CREATED ->
                            call.respond(HttpStatusCode.Created, mapOf("status" to "recorded"))
                        SubmitResult.ALREADY_SUBMITTED ->
                            call.respond(HttpStatusCode.OK, mapOf("status" to "already_submitted"))
                    }
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid feedback")))
                } catch (e: FeedbackUserNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                }
            }
        }
    }
}
