package opsi.sman35jkt.gathra.core.location

import opsi.sman35jkt.gathra.core.model.GeoPoint

interface LocationRepository {
    suspend fun locateOnce(): LocationLookupResult
}

sealed interface LocationLookupResult {
    data class Success(
        val point: GeoPoint,
        val fromLastKnown: Boolean,
    ) : LocationLookupResult

    data object PermissionDenied : LocationLookupResult

    data object LocationDisabled : LocationLookupResult

    data object Unavailable : LocationLookupResult
}
