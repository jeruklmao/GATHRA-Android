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
internal data class RouteRiskResponseDto(
    val level: String,
    val score: Double,
    val intersectsBlockedArea: Boolean,
    val affectedDistanceMeters: Int,
    val confidence: Double? = null,
    val reasonCodes: List<String> = emptyList(),
    val evaluatedAt: String? = null,
    val validUntil: String? = null,
    val hazardSnapshotId: String? = null,
)

@Serializable
internal data class RouteResponseDto(
    val id: String,
    val isRecommended: Boolean,
    val risk: RouteRiskResponseDto? = null,
    val geometry: GeoJsonLineStringDto,
    val summary: RouteSummaryResponseDto,
    val steps: List<RouteStepResponseDto>,
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
internal data class RouteStepResponseDto(
    val index: Int,
    val instruction: String,
    val streetName: String,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val manoeuvre: RouteManeuverResponseDto,
    val geometryStartIndex: Int,
    val geometryEndIndex: Int,
)

@Serializable
internal data class RouteManeuverResponseDto(
    val type: String,
    val modifier: String,
    val bearingBefore: Int?,
    val bearingAfter: Int?,
)

@Serializable
internal data class FloodMetadataResponseDto(
    val source: String,
    val snapshotId: String,
    val evaluatedAt: String? = null,
    val validUntil: String? = null,
    val activeHazardCount: Int = 0,
)

@Serializable
internal data class RouteMetadataDto(
    val travelMode: String,
    val requestedAlternatives: Int,
    val returnedAlternatives: Int,
    val flood: FloodMetadataResponseDto? = null,
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
