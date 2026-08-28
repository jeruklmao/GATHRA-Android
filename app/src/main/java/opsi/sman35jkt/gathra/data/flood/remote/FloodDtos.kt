package opsi.sman35jkt.gathra.data.flood.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FloodHazardPropertiesDto(
    @SerialName("riskLevel")
    val riskLevel: String? = null,
    @SerialName("confidence")
    val confidence: Double? = null,
    @SerialName("description")
    val description: String? = null,
    @SerialName("observedAt")
    val observedAt: String? = null,
    @SerialName("validUntil")
    val validUntil: String? = null,
    @SerialName("source")
    val source: String? = null,
    @SerialName("sourceNodeIds")
    val sourceNodeIds: List<String>? = null,
    @SerialName("routingMultiplier")
    val routingMultiplier: Double? = null,
    @SerialName("reasonCodes")
    val reasonCodes: List<String>? = null,
    @SerialName("freshness")
    val freshness: String? = null,
)

@Serializable
data class GeoJsonPolygonGeometryDto(
    @SerialName("type")
    val type: String,
    @SerialName("coordinates")
    val coordinates: List<List<List<Double>>> = emptyList(),
)

@Serializable
data class FloodHazardFeatureDto(
    @SerialName("type")
    val type: String,
    @SerialName("id")
    val id: String,
    @SerialName("properties")
    val properties: FloodHazardPropertiesDto? = null,
    @SerialName("geometry")
    val geometry: GeoJsonPolygonGeometryDto? = null,
)

@Serializable
data class FloodHazardsResponseDto(
    @SerialName("type")
    val type: String,
    @SerialName("snapshotId")
    val snapshotId: String,
    @SerialName("generatedAt")
    val generatedAt: String,
    @SerialName("validUntil")
    val validUntil: String? = null,
    @SerialName("source")
    val source: String? = null,
    @SerialName("features")
    val features: List<FloodHazardFeatureDto> = emptyList(),
)
