package opsi.sman35jkt.gathra.data.geocoding

import kotlinx.coroutines.test.runTest
import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.domain.geocoding.GeocodingFailureReason
import opsi.sman35jkt.gathra.domain.geocoding.GeocodingRepositoryException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FakeGeocodingRepositoryTest {
    private val repository = FakeGeocodingRepository(
        loadingDelayMillis = 0,
    )

    @Test
    fun `autocomplete supports local aliases and Tangerang typo`() = runTest {
        val monas = repository.autocomplete("Monas", null)
        val typo = repository.autocomplete("Tanggerang", null, limit = 8)

        assertEquals("Monumen Nasional", monas.first().primaryText)
        assertTrue(typo.any { it.primaryText.contains("Tangerang") })
        assertTrue(typo.all { it.insideSupportedRegion })
    }

    @Test
    fun `fixtures cover all four supported regions`() = runTest {
        val queries = listOf(
            "Jakarta Pusat",
            "Jakarta Selatan",
            "Kota Tangerang",
            "Tangerang Selatan",
        )

        queries.forEach { query ->
            assertTrue(
                "Expected a fixture for $query",
                repository.search(query, null, limit = 8).isNotEmpty(),
            )
        }
    }

    @Test
    fun `lookup returns stable selected place`() = runTest {
        val selected = repository.lookup("demo-monas")

        assertEquals("demo-monas", selected.id)
        assertEquals("Monumen Nasional", selected.name)
        assertTrue(selected.insideSupportedRegion)
    }

    @Test
    fun `reverse preserves exact requested coordinate`() = runTest {
        val point = GeoPoint(-6.1761, 106.8268)

        val selected = repository.reverse(point)

        assertNotNull(selected)
        assertEquals(point, selected?.position)
    }

    @Test
    fun `failure simulation is isolated and typed`() = runTest {
        val failing = FakeGeocodingRepository(
            loadingDelayMillis = 0,
            failureMode = FakeGeocodingFailureMode.ALWAYS_FAIL,
        )

        val failure = try {
            failing.autocomplete("Monas", null)
            fail("Expected geocoding failure")
            error("unreachable")
        } catch (expected: GeocodingRepositoryException) {
            expected
        }

        assertEquals(
            GeocodingFailureReason.SERVER_UNAVAILABLE,
            failure.reason,
        )
    }
}
