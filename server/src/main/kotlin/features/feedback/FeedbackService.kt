package com.revio.server.features.feedback

import com.revio.server.features.feedback.dto.FeedbackPromptStateDTO
import com.revio.server.features.feedback.dto.PromptEvent
import com.revio.server.features.feedback.dto.SubmitFirstPostFeedbackDTO
import org.jetbrains.exposed.exceptions.ExposedSQLException
import java.time.Instant
import java.util.UUID

const val FIRST_POST_FEEDBACK_KEY = "first_post_experience"
private const val MAX_AUTO_SHOWN_COUNT = 2
private const val MAX_COMMENT_LENGTH = 1000

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
}
