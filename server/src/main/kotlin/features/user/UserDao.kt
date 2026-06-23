package com.carspotter.features.user

import com.carspotter.features.auth.AuthTable
import com.carspotter.features.post.PostTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.util.*

interface IUserDAO {
    suspend fun createUser(user: User): UUID
    suspend fun getUserById(userId: UUID): User?
    suspend fun getUserByAuthCredentialId(authCredentialId: UUID): User?
    suspend fun usernameExistsIgnoreCase(username: String): Boolean
    suspend fun updateProfilePicture(userId: UUID, imagePath: String): Int
    suspend fun countPostsByUser(userId: UUID): Long

    /**
     * Atomically add [delta] to spot_score (floored at 0) for [userId].
     * Must be called inside an existing [transaction] block.
     */
    suspend fun incrementSpotScore(userId: UUID, delta: Int)

    /**
     * Update streak fields for [userId] based on [localDay].
     * - If lastStreakDate == localDay: no change (already counted today).
     * - If lastStreakDate == localDay - 1: extend streak by 1.
     * - Otherwise: reset streak to 1.
     * Only advances when localDay > lastStreakDate.
     * Stores [timezoneId] as the user's reference zone for streak-reset computation at read time.
     * Must be called inside an existing [transaction] block.
     */
    suspend fun advanceStreak(userId: UUID, localDay: LocalDate, timezoneId: String?)
}

class UserDao : IUserDAO {
    override suspend fun createUser(user: User): UUID = transaction {
        UserTable.insertReturning(listOf(UserTable.id)) {
            it[authCredentialId] = user.authCredentialId
            it[profilePicturePath] = user.profilePicturePath
            it[fullName] = user.fullName
            it[phoneNumber] = user.phoneNumber
            it[birthDate] = user.birthDate
            it[username] = user.username
            it[country] = user.country
            it[spotScore] = user.spotScore
        }.singleOrNull()?.get(UserTable.id)?.value ?: throw UserCreationException("Failed to insert user")
    }

    override suspend fun getUserById(userId: UUID): User? = transaction {
        UserTable
            .selectAll()
            .where { UserTable.id eq userId }
            .mapNotNull { it.toUser() }
            .singleOrNull()
    }

    override suspend fun getUserByAuthCredentialId(authCredentialId: UUID): User? = transaction {
        UserTable
            .selectAll()
            .where { UserTable.authCredentialId eq authCredentialId }
            .mapNotNull { it.toUser() }
            .singleOrNull()
    }

    override suspend fun usernameExistsIgnoreCase(username: String): Boolean = transaction {
        UserTable
            .select(UserTable.id)
            .where { UserTable.username.lowerCase() eq username.lowercase() }
            .limit(1)
            .any()
    }

    override suspend fun updateProfilePicture(userId: UUID, imagePath: String): Int = transaction {
        UserTable.update({ UserTable.id eq userId }) {
            it[profilePicturePath] = imagePath
        }
    }

    override suspend fun countPostsByUser(userId: UUID): Long = transaction {
        PostTable.selectAll().where { PostTable.userId eq userId }.count()
    }

    override suspend fun incrementSpotScore(userId: UUID, delta: Int) = transaction {
        val current = UserTable
            .select(UserTable.spotScore)
            .where { UserTable.id eq userId }
            .singleOrNull()
            ?.get(UserTable.spotScore) ?: 0
        val newScore = maxOf(0, current + delta)
        UserTable.update({ UserTable.id eq userId }) {
            it[spotScore] = newScore
        }
        Unit
    }

    override suspend fun advanceStreak(userId: UUID, localDay: LocalDate, timezoneId: String?) = transaction {
        val row = UserTable.select(
            listOf(UserTable.currentStreak, UserTable.longestStreak, UserTable.lastStreakDate)
        ).where { UserTable.id eq userId }.singleOrNull() ?: return@transaction

        val lastDate = row[UserTable.lastStreakDate]

        // Guard: only advance if localDay is strictly after lastStreakDate.
        if (lastDate != null && !localDay.isAfter(lastDate)) return@transaction

        val prevStreak = row[UserTable.currentStreak]
        val prevLongest = row[UserTable.longestStreak]

        val newStreak = when {
            lastDate == null -> 1
            localDay == lastDate.plusDays(1) -> prevStreak + 1
            else -> 1
        }
        val newLongest = maxOf(prevLongest, newStreak)

        UserTable.update({ UserTable.id eq userId }) {
            it[currentStreak] = newStreak
            it[longestStreak] = newLongest
            it[lastStreakDate] = localDay
            it[lastStreakTimezone] = timezoneId
        }
    }

    private fun ResultRow.toUser() = User(
        id = this[UserTable.id].value,
        authCredentialId = this[UserTable.authCredentialId],
        profilePicturePath = this[UserTable.profilePicturePath],
        fullName = this[UserTable.fullName],
        phoneNumber = this[UserTable.phoneNumber],
        birthDate = this[UserTable.birthDate],
        username = this[UserTable.username],
        country = this[UserTable.country],
        spotScore = this[UserTable.spotScore],
        currentStreak = this[UserTable.currentStreak],
        longestStreak = this[UserTable.longestStreak],
        lastStreakDate = this[UserTable.lastStreakDate],
        lastStreakTimezone = this[UserTable.lastStreakTimezone],
        createdAt = this[UserTable.createdAt],
        updatedAt = this[UserTable.updatedAt],
    )
}

class UserCreationException(message: String) : Exception(message)
