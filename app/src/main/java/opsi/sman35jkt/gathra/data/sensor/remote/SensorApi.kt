package opsi.sman35jkt.gathra.data.sensor.remote

import retrofit2.http.GET
import retrofit2.http.Path

interface SensorApi {
    @GET("api/v1/sensors/{nodeId}")
    suspend fun current(@Path("nodeId") nodeId: String): SensorCurrentDto
}
