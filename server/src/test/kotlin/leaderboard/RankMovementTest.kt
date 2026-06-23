package leaderboard

import com.carspotter.features.leaderboard.RankMovement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RankMovementTest {

    @Test
    fun `no previous rank returns KEEP with 0`() {
        val result = RankMovement.of(5, null)
        assertEquals("KEEP", result.movement)
        assertEquals(0, result.placesMoved)
    }

    @Test
    fun `same rank returns KEEP with 0`() {
        val result = RankMovement.of(3, 3)
        assertEquals("KEEP", result.movement)
        assertEquals(0, result.placesMoved)
    }

    @Test
    fun `moved up returns UP with correct places`() {
        val result = RankMovement.of(7, 10)
        assertEquals("UP", result.movement)
        assertEquals(3, result.placesMoved)
    }

    @Test
    fun `moved down returns DOWN with correct places`() {
        val result = RankMovement.of(10, 7)
        assertEquals("DOWN", result.movement)
        assertEquals(3, result.placesMoved)
    }

    @Test
    fun `moved up by 1 returns UP with 1`() {
        val result = RankMovement.of(4, 5)
        assertEquals("UP", result.movement)
        assertEquals(1, result.placesMoved)
    }

    @Test
    fun `moved down by 1 returns DOWN with 1`() {
        val result = RankMovement.of(5, 4)
        assertEquals("DOWN", result.movement)
        assertEquals(1, result.placesMoved)
    }
}
