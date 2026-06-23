package leaderboard

import com.carspotter.features.leaderboard.StreakCalculator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class StreakCalculatorTest {

    private val utcNow: Instant = LocalDate.of(2025, 6, 15)
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()

    @Test
    fun `null lastStreakDate returns 0`() {
        assertEquals(0, StreakCalculator.displayedStreak(5, null, null, utcNow))
    }

    @Test
    fun `lastStreakDate is today returns currentStreak`() {
        val today = LocalDate.of(2025, 6, 15)
        assertEquals(7, StreakCalculator.displayedStreak(7, today, "UTC", utcNow))
    }

    @Test
    fun `lastStreakDate is yesterday returns currentStreak`() {
        val yesterday = LocalDate.of(2025, 6, 14)
        assertEquals(4, StreakCalculator.displayedStreak(4, yesterday, "UTC", utcNow))
    }

    @Test
    fun `lastStreakDate is two days ago returns 0`() {
        val twoDaysAgo = LocalDate.of(2025, 6, 13)
        assertEquals(0, StreakCalculator.displayedStreak(10, twoDaysAgo, "UTC", utcNow))
    }

    @Test
    fun `lastStreakDate is one month ago returns 0`() {
        val old = LocalDate.of(2025, 5, 1)
        assertEquals(0, StreakCalculator.displayedStreak(30, old, "UTC", utcNow))
    }

    @Test
    fun `invalid timezone falls back to UTC`() {
        val yesterday = LocalDate.of(2025, 6, 14)
        assertEquals(3, StreakCalculator.displayedStreak(3, yesterday, "Not/AZone", utcNow))
    }

    @Test
    fun `null timezone falls back to UTC`() {
        val yesterday = LocalDate.of(2025, 6, 14)
        assertEquals(2, StreakCalculator.displayedStreak(2, yesterday, null, utcNow))
    }

    @Test
    fun `positive offset timezone — streak still alive when yesterday in that zone`() {
        // Pacific/Kiritimati is UTC+14 — "today" there is already June 16 when UTC is still June 15 00:00
        // So for a user in UTC+14, June 14 (UTC) = yesterday local, streak alive.
        val lastDate = LocalDate.of(2025, 6, 14)
        assertEquals(5, StreakCalculator.displayedStreak(5, lastDate, "Pacific/Kiritimati", utcNow))
    }

    @Test
    fun `negative offset timezone — streak expires earlier for west zones`() {
        // Pacific/Honolulu is UTC-10.
        // utcNow = 2025-06-15 00:00 UTC = 2025-06-14 14:00 Hawaii.
        // So "today" in Hawaii is still June 14.
        // If lastStreakDate = June 13 (two days ago in UTC), that's still yesterday in Hawaii → alive.
        val lastDate = LocalDate.of(2025, 6, 13)
        assertEquals(8, StreakCalculator.displayedStreak(8, lastDate, "Pacific/Honolulu", utcNow))
    }

    @Test
    fun `currentStreak 0 with valid recent date still returns 0`() {
        val today = LocalDate.of(2025, 6, 15)
        assertEquals(0, StreakCalculator.displayedStreak(0, today, "UTC", utcNow))
    }
}
