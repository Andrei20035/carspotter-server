package com.carspotter.features.leaderboard

import com.carspotter.core.util.resolveZone
import java.time.Instant
import java.time.LocalDate

object StreakCalculator {
    /**
     * Returns the streak to display on the leaderboard for a user.
     *
     * A stored [currentStreak] is still "alive" if the user's last camera day
     * ([lastStreakDate]) is today or yesterday relative to [now] in their own timezone
     * ([lastStreakTimezone]).  Otherwise the streak expired and we return 0.
     */
    fun displayedStreak(
        currentStreak: Int,
        lastStreakDate: LocalDate?,
        lastStreakTimezone: String?,
        now: Instant,
    ): Int {
        if (lastStreakDate == null) return 0
        val zone = resolveZone(lastStreakTimezone)
        val userToday = now.atZone(zone).toLocalDate()
        return if (lastStreakDate >= userToday.minusDays(1)) currentStreak else 0
    }
}
