package opsi.sman35jkt.gathra.data.geocoding.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

internal interface GeocodingApi {
    @GET("api/v1/geocoding/autocomplete")
    suspend fun autocomplete(
        @Query("q") query: String,
        @Query("lat") latitude: Double?,
        @Query("lon") longitude: Double?,
        @Query("limit") limit: Int,
        @Query("language") language: String = "id",
    ): Response<PlaceSuggestionsResponseDto>

    @GET("api/v1/geocoding/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("lat") latitude: Double?,
        @Query("lon") longitude: Double?,
        @Query("limit") limit: Int,
        @Query("language") language: String = "id",
    ): Response<PlaceSuggestionsResponseDto>

    @GET("api/v1/geocoding/places/{id}")
    suspend fun lookup(
        @Path("id") id: String,
    ): Response<PlaceDetailsResponseDto>

    @GET("api/v1/geocoding/reverse")
    suspend fun reverse(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("language") language: String = "id",
    ): Response<PlaceDetailsResponseDto>
}
