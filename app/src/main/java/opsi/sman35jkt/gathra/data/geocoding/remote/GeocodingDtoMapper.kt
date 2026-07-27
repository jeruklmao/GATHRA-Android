package opsi.sman35jkt.gathra.data.geocoding.remote

import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.PlaceCategory
import opsi.sman35jkt.gathra.core.model.PlaceSuggestion
import opsi.sman35jkt.gathra.core.model.SelectedPlace

internal object GeocodingDtoMapper {
    private const val MAX_RESULTS = 8
    private const val MAX_ID_LENGTH = 2_048
    private const val MAX_LABEL_LENGTH = 500

    fun toSuggestions(
        response: PlaceSuggestionsResponseDto,
        requestedLimit: Int,
    ): List<PlaceSuggestion> {
        requireValid(response.requestId.isNotBlank())
        requireValid(requestedLimit in 1..MAX_RESULTS)
        requireValid(response.suggestions.size <= requestedLimit)
        val ids = mutableSetOf<String>()
        return response.suggestions.map { suggestion ->
            requireValid(suggestion.id.isNotBlank())
            requireValid(suggestion.id.length <= MAX_ID_LENGTH)
            requireValid(ids.add(suggestion.id))
            requireValid(suggestion.primaryText.isNotBlank())
            requireValid(suggestion.primaryText.length <= MAX_LABEL_LENGTH)
            requireValid(
                suggestion.secondaryText == null ||
                    suggestion.secondaryText.length <= MAX_LABEL_LENGTH,
            )
            requireValid(
                suggestion.distanceMeters == null ||
                    suggestion.distanceMeters >= 0,
            )
            PlaceSuggestion(
                id = suggestion.id,
                primaryText = suggestion.primaryText,
                secondaryText = suggestion.secondaryText,
                category = suggestion.category.toCategory(),
                position = suggestion.position?.toGeoPoint(),
                distanceMeters = suggestion.distanceMeters,
                insideSupportedRegion = suggestion.insideSupportedRegion,
            )
        }
    }

    fun toSelectedPlace(response: PlaceDetailsResponseDto): SelectedPlace {
        validateDetails(response)
        return SelectedPlace(
            id = response.id,
            name = response.name,
            formattedAddress = response.formattedAddress,
            position = response.position.toGeoPoint(),
            category = response.category.toCategory(),
            insideSupportedRegion = response.insideSupportedRegion,
        )
    }

    /**
     * A reverse result supplies display metadata only. The user's exact map point
     * remains authoritative even if Pelias returns a nearby feature centroid.
     */
    fun toReverseSelectedPlace(
        response: PlaceDetailsResponseDto,
        requestedPoint: GeoPoint,
    ): SelectedPlace {
        validateDetails(response)
        return SelectedPlace(
            id = response.id,
            name = response.name,
            formattedAddress = response.formattedAddress,
            position = requestedPoint,
            category = response.category.toCategory(),
            insideSupportedRegion = response.insideSupportedRegion,
        )
    }

    private fun validateDetails(response: PlaceDetailsResponseDto) {
        requireValid(response.id == null || response.id.isNotBlank())
        requireValid(response.id == null || response.id.length <= MAX_ID_LENGTH)
        requireValid(response.name.isNotBlank())
        requireValid(response.name.length <= MAX_LABEL_LENGTH)
        requireValid(
            response.formattedAddress == null ||
                response.formattedAddress.length <= MAX_LABEL_LENGTH,
        )
        // Validate provider coordinates even though reverse mapping does not use
        // them as the routing point.
        response.position.toGeoPoint()
    }

    private fun GeocodingPositionDto.toGeoPoint(): GeoPoint {
        requireValid(latitude.isFinite() && longitude.isFinite())
        return try {
            GeoPoint(latitude = latitude, longitude = longitude)
        } catch (invalid: IllegalArgumentException) {
            throw InvalidGeocodingResponseException(invalid)
        }
    }

    private fun String?.toCategory(): PlaceCategory? {
        if (this == null) return null
        return enumValues<PlaceCategory>().firstOrNull { it.name == this }
            ?: PlaceCategory.OTHER
    }

    private fun requireValid(condition: Boolean) {
        if (!condition) throw InvalidGeocodingResponseException()
    }
}

internal class InvalidGeocodingResponseException(
    cause: Throwable? = null,
) : IllegalArgumentException(
    "The geocoding service response violated the API contract.",
    cause,
)
