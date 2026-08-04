package com.revio.server.features.feedback

import com.revio.server.features.feedback.dto.FeedbackPromptStateDTO
import com.revio.server.features.feedback.dto.SubmitFirstPostFeedbackDTO
import com.revio.server.features.feedback.dto.SubmitUserFeedbackDTO
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

interface IFeedbackDAO {
    /** Inserts a new feedback row. Throws on FK violation (user missing) or unique violation (already submitted). */
    suspend fun submit(userId: UUID, dto: SubmitFirstPostFeedbackDTO): UUID

    suspend fun getPromptState(userId: UUID, key: String): FeedbackPromptStateDTO?

    suspend fun upsertPromptState(userId: UUID, key: String, status: PromptStatus, shownCount: Int, lastShownAt: Instant?)

    suspend fun markSubmitted(userId: UUID, key: String)

    /** Inserts a new user-feedback row. Throws on FK violation (user missing) or unique violation (duplicate clientFeedbackId). */
    suspend fun insertUserFeedback(userId: UUID, dto: SubmitUserFeedbackDTO): UUID
}

class FeedbackDAO : IFeedbackDAO {

    override suspend fun submit(userId: UUID, dto: SubmitFirstPostFeedbackDTO): UUID = transaction {
        FirstPostFeedbackTable.insert {
            it[FirstPostFeedbackTable.userId] = userId
            it[rating] = dto.rating.toShort()
            it[quickReason] = dto.quickReason?.name
            it[comment] = dto.comment
            it[surface] = dto.surface?.name
            it[appVersion] = dto.appVersion
            it[androidVersion] = dto.androidVersion
            it[deviceModel] = dto.deviceModel
            it[connectionType] = dto.connectionType
            it[uploadDurationMs] = dto.uploadDurationMs
            it[hadRetries] = dto.hadRetries
            it[lastErrorCode] = dto.lastErrorCode
            it[clientSubmittedAt] = dto.clientSubmittedAt
        } get FirstPostFeedbackTable.id
    }.value

    override suspend fun getPromptState(userId: UUID, key: String): FeedbackPromptStateDTO? = transaction {
        FeedbackPromptStateTable
            .selectAll()
            .where { (FeedbackPromptStateTable.userId eq userId) and (FeedbackPromptStateTable.promptKey eq key) }
            .singleOrNull()
            ?.let { row ->
                FeedbackPromptStateDTO(
                    promptKey = row[FeedbackPromptStateTable.promptKey],
                    status = PromptStatus.valueOf(row[FeedbackPromptStateTable.status]),
                    shownCount = row[FeedbackPromptStateTable.shownCount].toInt(),
                    lastShownAt = row[FeedbackPromptStateTable.lastShownAt],
                )
            }
    }

    override suspend fun upsertPromptState(
        userId: UUID,
        key: String,
        status: PromptStatus,
        shownCount: Int,
        lastShownAt: Instant?,
    ): Unit = transaction {
        val updated = FeedbackPromptStateTable.update({
            (FeedbackPromptStateTable.userId eq userId) and (FeedbackPromptStateTable.promptKey eq key)
        }) {
            it[FeedbackPromptStateTable.status] = status.name
            it[FeedbackPromptStateTable.shownCount] = shownCount.toShort()
            it[FeedbackPromptStateTable.lastShownAt] = lastShownAt
            it[FeedbackPromptStateTable.updatedAt] = Instant.now()
        }

        if (updated == 0) {
            FeedbackPromptStateTable.insert {
                it[FeedbackPromptStateTable.userId] = userId
                it[FeedbackPromptStateTable.promptKey] = key
                it[FeedbackPromptStateTable.status] = status.name
                it[FeedbackPromptStateTable.shownCount] = shownCount.toShort()
                it[FeedbackPromptStateTable.lastShownAt] = lastShownAt
            }
        }
    }

    override suspend fun markSubmitted(userId: UUID, key: String): Unit = transaction {
        val updated = FeedbackPromptStateTable.update({
            (FeedbackPromptStateTable.userId eq userId) and (FeedbackPromptStateTable.promptKey eq key)
        }) {
            it[status] = PromptStatus.SUBMITTED.name
            it[updatedAt] = Instant.now()
        }

        if (updated == 0) {
            FeedbackPromptStateTable.insert {
                it[FeedbackPromptStateTable.userId] = userId
                it[promptKey] = key
                it[status] = PromptStatus.SUBMITTED.name
                it[shownCount] = 0
            }
        }
    }

    override suspend fun insertUserFeedback(userId: UUID, dto: SubmitUserFeedbackDTO): UUID = transaction {
        UserFeedbackTable.insert {
            it[UserFeedbackTable.userId] = userId
            it[category] = dto.category.name
            it[area] = dto.area?.name
            it[message] = dto.message.orEmpty()
            it[secondaryMessage] = dto.secondaryMessage
            it[quickReason] = dto.quickReason?.name
            it[priority] = dto.priority?.name
            it[rating] = dto.rating?.toShort()
            it[keepMessage] = dto.keepMessage
            it[improveMessage] = dto.improveMessage
            it[feedbackSource] = dto.source.name
            it[originScreen] = dto.originScreen
            it[includeDiagnostics] = dto.includeDiagnostics
            it[appVersion] = dto.appVersion
            it[androidVersion] = dto.androidVersion
            it[deviceModel] = dto.deviceModel
            it[connectionType] = dto.connectionType
            it[lastErrorCode] = dto.lastErrorCode
            it[clientFeedbackId] = dto.clientFeedbackId
            it[clientSubmittedAt] = dto.clientSubmittedAt
        } get UserFeedbackTable.id
    }.value
}
