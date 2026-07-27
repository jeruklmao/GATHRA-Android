package opsi.sman35jkt.gathra.domain.geocoding

import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.PlaceSuggestion
import opsi.sman35jkt.gathra.core.model.SelectedPlace

interface GeocodingRepository {
    suspend fun autocomplete(
        query: String,
        proximity: GeoPoint?,
        limit: Int = 6,
    ): List<PlaceSuggestion>

    suspend fun search(
        query: String,
        proximity: GeoPoint?,
        limit: Int = 8,
    ): List<PlaceSuggestion>

    suspend fun lookup(id: String): SelectedPlace

    suspend fun reverse(point: GeoPoint): SelectedPlace?
}
