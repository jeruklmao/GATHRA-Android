package opsi.sman35jkt.gathra.data.flood.remote

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import opsi.sman35jkt.gathra.domain.flood.FloodHazardRepository
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object FloodNetworkFactory {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        explicitNulls = false
    }

    fun createRepository(baseUrl: String): FloodHazardRepository {
        require(baseUrl.endsWith('/')) {
            "API_BASE_URL must end with '/'."
        }
        val client = OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
        val api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(
                json.asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create(FloodApi::class.java)
        return RemoteFloodHazardRepository(api = api)
    }
}
