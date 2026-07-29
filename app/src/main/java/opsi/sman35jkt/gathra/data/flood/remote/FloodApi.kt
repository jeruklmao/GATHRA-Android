package opsi.sman35jkt.gathra.data.flood.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface FloodApi {
    @GET("api/v1/flood-hazards")
    suspend fun getActiveHazards(
        @Query("minLat") minLat: Double? = null,
        @Query("minLon") minLon: Double? = null,
        @Query("maxLat") maxLat: Double? = null,
        @Query("maxLon") maxLon: Double? = null,
    ): FloodHazardsResponseDto
}
