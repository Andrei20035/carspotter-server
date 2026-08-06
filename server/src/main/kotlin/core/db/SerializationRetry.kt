package com.revio.server.core.db

import kotlinx.coroutines.delay
import org.jetbrains.exposed.exceptions.ExposedSQLException

private const val POSTGRES_SERIALIZATION_FAILURE = "40001"
private const val MAX_ATTEMPTS = 3
private const val BACKOFF_MILLIS = 25L

/**
 * Retries [block] when it fails with a Postgres serialization failure (SQLSTATE 40001).
 *
 * The database runs under TRANSACTION_REPEATABLE_READ (config/Databases.kt), and nowhere else in
 * this codebase are two transactions expected to race on the same row — every other DAO method is
 * a single, independent statement. ChallengeProgressDAO's coarse-grained evaluation transaction is
 * the one exception: it locks and updates a shared challenge_participants row across concurrent
 * post creations for the same (challenge, user), which REPEATABLE READ resolves by aborting one
 * side with 40001 instead of blocking it. This helper exists solely to absorb that abort with a
 * bounded retry. Do not reuse it for other DAOs without the same justification.
 */
suspend fun <T> retrySerializationConflicts(block: suspend () -> T): T {
    var attempt = 1
    while (true) {
        try {
            return block()
        } catch (e: ExposedSQLException) {
            if (e.sqlState != POSTGRES_SERIALIZATION_FAILURE || attempt >= MAX_ATTEMPTS) throw e
            delay(BACKOFF_MILLIS * attempt)
            attempt++
        }
    }
}
