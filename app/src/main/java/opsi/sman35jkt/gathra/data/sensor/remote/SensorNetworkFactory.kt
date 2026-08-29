package opsi.sman35jkt.gathra.data.sensor.remote

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import opsi.sman35jkt.gathra.domain.sensor.SensorRepository
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object SensorNetworkFactory {
    fun createRepository(baseUrl: String): SensorRepository {
        val json = Json { ignoreUnknownKeys = true; isLenient = false; explicitNulls = false }
        val client = OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS).build()
        val api = Retrofit.Builder().baseUrl(baseUrl).client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(SensorApi::class.java)
        return RemoteSensorRepository(api)
    }
}
