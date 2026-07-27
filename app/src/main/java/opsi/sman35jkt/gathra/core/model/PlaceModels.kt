package opsi.sman35jkt.gathra.core.model

/**
 * Provider-independent place categories understood by the GATHRA UI.
 */
enum class PlaceCategory {
    ADDRESS,
    ROAD,
    SCHOOL,
    HOSPITAL,
    LANDMARK,
    GOVERNMENT,
    TRANSIT,
    NEIGHBOURHOOD,
    OTHER,
}

data class PlaceSuggestion(
    val id: String,
    val primaryText: String,
    val secondaryText: String?,
    val category: PlaceCategory?,
    val position: GeoPoint?,
    val distanceMeters: Int?,
    val insideSupportedRegion: Boolean,
) {
    init {
        require(id.isNotBlank()) { "A place suggestion ID cannot be blank." }
        require(primaryText.isNotBlank()) {
            "A place suggestion primary label cannot be blank."
        }
        require(distanceMeters == null || distanceMeters >= 0) {
            "A place suggestion distance cannot be negative."
        }
    }
}

data class SelectedPlace(
    val id: String?,
    val name: String,
    val formattedAddress: String?,
    val position: GeoPoint,
    val category: PlaceCategory?,
    val insideSupportedRegion: Boolean,
) {
    init {
        require(id == null || id.isNotBlank()) { "A place ID cannot be blank." }
        require(name.isNotBlank()) { "A selected place name cannot be blank." }
    }
}
