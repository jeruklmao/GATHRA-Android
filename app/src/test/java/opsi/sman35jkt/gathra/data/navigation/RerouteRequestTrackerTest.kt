package opsi.sman35jkt.gathra.data.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RerouteRequestTrackerTest {
    @Test
    fun `only newest reroute response can be applied`() {
        val tracker = RerouteRequestTracker()
        val staleRequest = tracker.beginRequest()
        val currentRequest = tracker.beginRequest()

        assertFalse(tracker.isCurrent(staleRequest))
        assertTrue(tracker.isCurrent(currentRequest))

        tracker.invalidate()
        assertFalse(tracker.isCurrent(currentRequest))
    }
}

