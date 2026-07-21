package com.revio.server.features.account_deletion

import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

interface IAccountDeletionFeedbackDAO {
    suspend fun insert(
        reason: DeletionReason,
        details: String?,
        accountAgeDays: Int?,
        postCount: Int?,
        spotScore: Int?,
        streakDays: Int?,
        provider: String?,
    )
}

class AccountDeletionFeedbackDAO : IAccountDeletionFeedbackDAO {

    override suspend fun insert(
        reason: DeletionReason,
        details: String?,
        accountAgeDays: Int?,
        postCount: Int?,
        spotScore: Int?,
        streakDays: Int?,
        provider: String?,
    ): Unit = transaction {
        AccountDeletionFeedbackTable.insert {
            it[AccountDeletionFeedbackTable.reason] = reason.name
            it[AccountDeletionFeedbackTable.details] = details
            it[AccountDeletionFeedbackTable.accountAgeDays] = accountAgeDays
            it[AccountDeletionFeedbackTable.postCount] = postCount
            it[AccountDeletionFeedbackTable.spotScore] = spotScore
            it[AccountDeletionFeedbackTable.streakDays] = streakDays
            it[AccountDeletionFeedbackTable.provider] = provider
        }
        Unit
    }
}
