package opsi.sman35jkt.gathra.data.route.remote

import kotlinx.serialization.Serializable

@Serializable
internal data class GeoPointRequestDto(
    val latitude: Double,
    val longitude: Double,
)

@Serializable
internal data class RoutePreviewRequestDto(
    val origin: GeoPointRequestDto,
    val destination: GeoPointRequestDto,
    val travelMode: String,
    val alternatives: Int,
)

@Serializable
internal data class RoutePreviewResponseDto(
    val requestId: String,
    val routes: List<RouteResponseDto>,
    val metadata: RouteMetadataDto,
)

@Serializable
internal data class RouteResponseDto(
    val id: String,
    val isRecommended: Boolean,
    val geometry: GeoJsonLineStringDto,
    val summary: RouteSummaryResponseDto,
)

@Serializable
internal data class GeoJsonLineStringDto(
    val type: String,
    val coordinates: List<List<Double>>,
)

@Serializable
internal data class RouteSummaryResponseDto(
    val distanceMeters: Int,
    val durationSeconds: Int,
)

@Serializable
internal data class RouteMetadataDto(
    val travelMode: String,
    val requestedAlternatives: Int,
    val returnedAlternatives: Int,
)

@Serializable
internal data class ApiErrorEnvelopeDto(
    val requestId: String? = null,
    val error: ApiErrorDto,
)

@Serializable
internal data class ApiErrorDto(
    val code: String,
    val message: String,
    val retryable: Boolean,
    val details: List<ApiErrorDetailDto>? = null,
)

@Serializable
internal data class ApiErrorDetailDto(
    val field: String,
    val reason: String,
)
