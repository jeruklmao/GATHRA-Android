package opsi.sman35jkt.gathra.data.geocoding.remote

import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.PlaceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GeocodingDtoMapperTest {

    @Test
    fun `suggestions map without leaking DTOs`() {
        val result = GeocodingDtoMapper.toSuggestions(
            response = PlaceSuggestionsResponseDto(
                suggestions = listOf(suggestion()),
                requestId = "request-1",
            ),
            requestedLimit = 6,
        )

        assertEquals(1, result.size)
        assertEquals("SMA Negeri 35 Jakarta", result.single().primaryText)
        assertEquals(PlaceCategory.SCHOOL, result.single().category)
        assertEquals(GeoPoint(-6.2093, 106.8142), result.single().position)
    }

    @Test
    fun `unknown category maps to other for forward compatibility`() {
        val result = GeocodingDtoMapper.toSuggestions(
            PlaceSuggestionsResponseDto(
                suggestions = listOf(suggestion().copy(category = "NEW_KIND")),
                requestId = "request-1",
            ),
            requestedLimit = 6,
        )

        assertEquals(PlaceCategory.OTHER, result.single().category)
    }

    @Test
    fun `reverse mapping keeps requested map coordinate authoritative`() {
        val selectedPoint = GeoPoint(-6.2, 106.8167)
        val providerCentroid = GeocodingPositionDto(-6.199, 106.818)

        val result = GeocodingDtoMapper.toReverseSelectedPlace(
            response = details().copy(position = providerCentroid),
            requestedPoint = selectedPoint,
        )

        assertEquals(selectedPoint, result.position)
        assertEquals("Jalan Mutiara", result.name)
    }

    @Test
    fun `malformed coordinates are rejected`() {
        assertThrows(InvalidGeocodingResponseException::class.java) {
            GeocodingDtoMapper.toSelectedPlace(
                details().copy(
                    position = GeocodingPositionDto(-91.0, 106.8),
                ),
            )
        }
    }

    @Test
    fun `duplicate suggestion IDs are rejected`() {
        assertThrows(InvalidGeocodingResponseException::class.java) {
            GeocodingDtoMapper.toSuggestions(
                PlaceSuggestionsResponseDto(
                    suggestions = listOf(suggestion(), suggestion()),
                    requestId = "request-1",
                ),
                requestedLimit = 6,
            )
        }
    }

    private fun suggestion() = PlaceSuggestionResponseDto(
        id = "opaque-1",
        primaryText = "SMA Negeri 35 Jakarta",
        secondaryText = "Karet Tengsin, Jakarta Pusat",
        category = "SCHOOL",
        position = GeocodingPositionDto(-6.2093, 106.8142),
        distanceMeters = 500,
        insideSupportedRegion = true,
    )

    private fun details() = PlaceDetailsResponseDto(
        id = "opaque-1",
        name = "Jalan Mutiara",
        formattedAddress = "Karet Tengsin, Jakarta Pusat",
        position = GeocodingPositionDto(-6.2093, 106.8142),
        category = "ROAD",
        insideSupportedRegion = true,
    )
}
