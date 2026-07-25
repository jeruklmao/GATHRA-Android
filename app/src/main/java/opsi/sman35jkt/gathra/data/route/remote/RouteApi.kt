package opsi.sman35jkt.gathra.data.route.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

internal interface RouteApi {
    @POST("api/v1/routes/preview")
    suspend fun previewRoute(
        @Body request: RoutePreviewRequestDto,
    ): Response<RoutePreviewResponseDto>
}
