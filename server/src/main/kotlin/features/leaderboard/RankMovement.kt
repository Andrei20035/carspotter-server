package com.carspotter.features.leaderboard

data class RankMovementResult(val movement: String, val placesMoved: Int)

object RankMovement {
    /**
     * Computes rank movement compared to the previous day's snapshot.
     *
     * Lower rank number = higher position on the leaderboard.
     */
    fun of(currentRank: Int, previousRank: Int?): RankMovementResult {
        if (previousRank == null) return RankMovementResult("KEEP", 0)
        return when {
            currentRank < previousRank -> RankMovementResult("UP", previousRank - currentRank)
            currentRank > previousRank -> RankMovementResult("DOWN", currentRank - previousRank)
            else -> RankMovementResult("KEEP", 0)
        }
    }
}
