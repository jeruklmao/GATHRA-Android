package opsi.sman35jkt.gathra.data.route.remote

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import opsi.sman35jkt.gathra.core.model.RouteOption
import opsi.sman35jkt.gathra.core.model.RouteRequest
import opsi.sman35jkt.gathra.domain.route.RouteFailureReason
import opsi.sman35jkt.gathra.domain.route.RouteRepository
import opsi.sman35jkt.gathra.domain.route.RouteRepositoryException
import retrofit2.Response

internal class RemoteRouteRepository(
    private val routeApi: RouteApi,
    private val json: Json,
) : RouteRepository {

    override suspend fun getRoutes(request: RouteRequest): List<RouteOption> {
        return try {
            val response = routeApi.previewRoute(RouteDtoMapper.toRequest(request))
            if (response.isSuccessful) {
                val body = response.body()
                    ?: throw RouteRepositoryException(
                        RouteFailureReason.INVALID_RESPONSE,
                    )
                RouteDtoMapper.toDomain(body, request)
            } else {
                throw mapHttpFailure(response)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (expected: RouteRepositoryException) {
            throw expected
        } catch (timeout: SocketTimeoutException) {
            throw RouteRepositoryException(RouteFailureReason.TIMEOUT, timeout)
        } catch (timeout: InterruptedIOException) {
            throw RouteRepositoryException(RouteFailureReason.TIMEOUT, timeout)
        } catch (offline: UnknownHostException) {
            throw RouteRepositoryException(RouteFailureReason.OFFLINE, offline)
        } catch (offline: ConnectException) {
            throw RouteRepositoryException(RouteFailureReason.OFFLINE, offline)
        } catch (offline: NoRouteToHostException) {
            throw RouteRepositoryException(RouteFailureReason.OFFLINE, offline)
        } catch (invalid: SerializationException) {
            throw RouteRepositoryException(RouteFailureReason.INVALID_RESPONSE, invalid)
        } catch (invalid: InvalidRouteResponseException) {
            throw RouteRepositoryException(RouteFailureReason.INVALID_RESPONSE, invalid)
        } catch (network: IOException) {
            throw RouteRepositoryException(RouteFailureReason.OFFLINE, network)
        } catch (unexpected: Exception) {
            throw RouteRepositoryException(RouteFailureReason.UNKNOWN, unexpected)
        }
    }

    private fun mapHttpFailure(
        response: Response<RoutePreviewResponseDto>,
    ): RouteRepositoryException {
        val backendCode = runCatching {
            val body = response.errorBody()?.string().orEmpty()
            if (body.isBlank()) null else {
                json.decodeFromString<ApiErrorEnvelopeDto>(body).error.code
            }
        }.getOrNull()

        val reason = when (backendCode) {
            ERROR_NO_ROUTE -> RouteFailureReason.NO_ROUTE
            ERROR_ROUTING_TIMEOUT -> RouteFailureReason.TIMEOUT
            ERROR_ROUTING_RESPONSE_INVALID -> RouteFailureReason.INVALID_RESPONSE
            ERROR_ROUTING_UNAVAILABLE -> RouteFailureReason.SERVER_UNAVAILABLE
            else -> when (response.code()) {
                422 -> RouteFailureReason.NO_ROUTE
                502 -> RouteFailureReason.INVALID_RESPONSE
                503 -> RouteFailureReason.SERVER_UNAVAILABLE
                504 -> RouteFailureReason.TIMEOUT
                else -> RouteFailureReason.UNKNOWN
            }
        }
        return RouteRepositoryException(reason)
    }

    private companion object {
        const val ERROR_NO_ROUTE = "NO_ROUTE"
        const val ERROR_ROUTING_TIMEOUT = "ROUTING_TIMEOUT"
        const val ERROR_ROUTING_RESPONSE_INVALID = "ROUTING_RESPONSE_INVALID"
        const val ERROR_ROUTING_UNAVAILABLE = "ROUTING_UNAVAILABLE"
    }
}
