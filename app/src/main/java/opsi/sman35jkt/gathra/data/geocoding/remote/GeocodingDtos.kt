package opsi.sman35jkt.gathra.data.geocoding.remote

import kotlinx.serialization.Serializable

@Serializable
internal data class PlaceSuggestionsResponseDto(
    val suggestions: List<PlaceSuggestionResponseDto>,
    val requestId: String,
)

@Serializable
internal data class PlaceSuggestionResponseDto(
    val id: String,
    val primaryText: String,
    val secondaryText: String? = null,
    val category: String? = null,
    val position: GeocodingPositionDto? = null,
    val distanceMeters: Int? = null,
    val insideSupportedRegion: Boolean,
)

@Serializable
internal data class PlaceDetailsResponseDto(
    val id: String? = null,
    val name: String,
    val formattedAddress: String? = null,
    val position: GeocodingPositionDto,
    val category: String? = null,
    val insideSupportedRegion: Boolean,
)

@Serializable
internal data class GeocodingPositionDto(
    val latitude: Double,
    val longitude: Double,
)

@Serializable
internal data class GeocodingApiErrorEnvelopeDto(
    val requestId: String? = null,
    val error: GeocodingApiErrorDto,
)

@Serializable
internal data class GeocodingApiErrorDto(
    val code: String,
    val message: String,
    val retryable: Boolean,
)
