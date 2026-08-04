package com.revio.server.features.feedback

import com.revio.server.features.feedback.dto.FeedbackPromptStateDTO
import com.revio.server.features.feedback.dto.PromptEvent
import com.revio.server.features.feedback.dto.SubmitFirstPostFeedbackDTO
import com.revio.server.features.feedback.dto.SubmitUserFeedbackDTO
import org.jetbrains.exposed.exceptions.ExposedSQLException
import java.time.Instant
import java.util.UUID

const val FIRST_POST_FEEDBACK_KEY = "first_post_experience"
private const val MAX_AUTO_SHOWN_COUNT = 2
private const val MAX_COMMENT_LENGTH = 1000
private const val MAX_MESSAGE_LENGTH = 4000
private const val MAX_KEEP_IMPROVE_LENGTH = 2000

class FeedbackUserNotFoundException(userId: UUID) : RuntimeException("User $userId not found")

/** Outcome of submitting first-post feedback. */
enum class SubmitResult {
    /** A new feedback row was created. */
    CREATED,

    /** The user had already submitted feedback for this experience — treated as success (idempotent). */
    ALREADY_SUBMITTED,
}

interface IFeedbackService {
    suspend fun submit(userId: UUID, dto: SubmitFirstPostFeedbackDTO): SubmitResult
    suspend fun getPromptState(userId: UUID, key: String): FeedbackPromptStateDTO
    suspend fun recordPromptEvent(userId: UUID, key: String, event: PromptEvent)
    suspend fun submitUserFeedback(userId: UUID, dto: SubmitUserFeedbackDTO): SubmitResult
}

class FeedbackService(
    private val dao: IFeedbackDAO,
) : IFeedbackService {

    override suspend fun submit(userId: UUID, dto: SubmitFirstPostFeedbackDTO): SubmitResult {
        require(dto.rating in 1..5) { "rating must be between 1 and 5" }

        val sanitizedDto = dto.copy(comment = dto.comment?.take(MAX_COMMENT_LENGTH))

        return try {
            dao.submit(userId, sanitizedDto)
            dao.markSubmitted(userId, FIRST_POST_FEEDBACK_KEY)
            SubmitResult.CREATED
        } catch (e: ExposedSQLException) {
            when (e.sqlState) {
                // 23503 = FK violation → the user doesn't exist.
                "23503" -> throw FeedbackUserNotFoundException(userId)
                // 23505 = unique violation → already submitted feedback for this experience.
                "23505" -> SubmitResult.ALREADY_SUBMITTED
                else -> throw e
            }
        }
    }

    override suspend fun getPromptState(userId: UUID, key: String): FeedbackPromptStateDTO {
        return dao.getPromptState(userId, key)
            ?: FeedbackPromptStateDTO(promptKey = key, status = PromptStatus.ELIGIBLE, shownCount = 0, lastShownAt = null)
    }

    override suspend fun recordPromptEvent(userId: UUID, key: String, event: PromptEvent) {
        val current = getPromptState(userId, key)

        // Guard-rail: no state changes are accepted once feedback has been submitted.
        if (current.status == PromptStatus.SUBMITTED) return

        when (event) {
            PromptEvent.SHOWN -> {
                val newShownCount = (current.shownCount + 1).coerceAtMost(MAX_AUTO_SHOWN_COUNT)
                dao.upsertPromptState(userId, key, current.status, newShownCount, Instant.now())
            }

            PromptEvent.DISMISSED -> {
                val newStatus = when (current.status) {
                    PromptStatus.ELIGIBLE -> PromptStatus.DISMISSED_ONCE
                    PromptStatus.DISMISSED_ONCE -> PromptStatus.DISMISSED_TWICE
                    else -> current.status
                }
                dao.upsertPromptState(userId, key, newStatus, current.shownCount, current.lastShownAt)
            }
        }
    }

    override suspend fun submitUserFeedback(userId: UUID, dto: SubmitUserFeedbackDTO): SubmitResult {
        val hasGeneralRatingWithReason =
            dto.category == FeedbackCategory.GENERAL && dto.rating != null && dto.quickReason != null
        require(!dto.message.isNullOrBlank() || hasGeneralRatingWithReason) {
            "message is required unless this is general feedback with a rating and a reason"
        }
        if (dto.category == FeedbackCategory.NOT_WORKING) {
            require(!dto.message.isNullOrBlank()) { "message is required for NOT_WORKING feedback" }
        }
        require(dto.rating == null || dto.rating in 1..5) { "rating must be between 1 and 5" }

        // Diagnostics are only persisted when the user explicitly opted in — enforced here
        // regardless of what the client sends, so consent can't be bypassed by a malformed request.
        val sanitizedDto = dto.copy(
            message = dto.message?.take(MAX_MESSAGE_LENGTH).orEmpty(),
            secondaryMessage = dto.secondaryMessage?.take(MAX_MESSAGE_LENGTH),
            keepMessage = dto.keepMessage?.take(MAX_KEEP_IMPROVE_LENGTH),
            improveMessage = dto.improveMessage?.take(MAX_KEEP_IMPROVE_LENGTH),
            appVersion = if (dto.includeDiagnostics) dto.appVersion else null,
            androidVersion = if (dto.includeDiagnostics) dto.androidVersion else null,
            deviceModel = if (dto.includeDiagnostics) dto.deviceModel else null,
            connectionType = if (dto.includeDiagnostics) dto.connectionType else null,
            lastErrorCode = if (dto.includeDiagnostics) dto.lastErrorCode else null,
        )

        return try {
            dao.insertUserFeedback(userId, sanitizedDto)
            SubmitResult.CREATED
        } catch (e: ExposedSQLException) {
            when (e.sqlState) {
                // 23503 = FK violation → the user doesn't exist.
                "23503" -> throw FeedbackUserNotFoundException(userId)
                // 23505 = unique violation → duplicate clientFeedbackId, already submitted.
                "23505" -> SubmitResult.ALREADY_SUBMITTED
                else -> throw e
            }
        }
    }
}
