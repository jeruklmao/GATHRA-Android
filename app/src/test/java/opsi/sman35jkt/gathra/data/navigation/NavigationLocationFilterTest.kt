package opsi.sman35jkt.gathra.data.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationLocationFilterTest {
    private val filter = NavigationLocationFilter()

    @Test
    fun `rejects stale and excessively inaccurate samples`() {
        assertFalse(
            filter.isAcceptable(
                candidate = location(0.0, 0.0, elapsedRealtimeMillis = 1_000L),
                previousAccepted = null,
                nowElapsedRealtimeMillis = 20_000L,
            ),
        )
        assertFalse(
            filter.isAcceptable(
                candidate = location(
                    0.0,
                    0.0,
                    elapsedRealtimeMillis = 20_000L,
                    accuracyMeters = 300.0,
                ),
                previousAccepted = null,
                nowElapsedRealtimeMillis = 20_000L,
            ),
        )
    }

    @Test
    fun `rejects an obviously invalid jump`() {
        val previous = location(0.0, 0.0, elapsedRealtimeMillis = 1_000L)
        val jump = location(0.0, 0.02, elapsedRealtimeMillis = 2_000L)

        assertFalse(
            filter.isAcceptable(
                candidate = jump,
                previousAccepted = previous,
                nowElapsedRealtimeMillis = 2_000L,
            ),
        )
    }

    @Test
    fun `accepts plausible foreground location progression`() {
        val previous = location(0.0, 0.0, elapsedRealtimeMillis = 1_000L)
        val next = location(0.0, 0.0001, elapsedRealtimeMillis = 2_000L)

        assertTrue(
            filter.isAcceptable(
                candidate = next,
                previousAccepted = previous,
                nowElapsedRealtimeMillis = 2_000L,
            ),
        )
    }
}

