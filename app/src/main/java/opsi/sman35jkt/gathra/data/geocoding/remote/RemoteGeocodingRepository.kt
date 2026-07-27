package opsi.sman35jkt.gathra.data.geocoding.remote

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.PlaceSuggestion
import opsi.sman35jkt.gathra.core.model.SelectedPlace
import opsi.sman35jkt.gathra.domain.geocoding.GeocodingFailureReason
import opsi.sman35jkt.gathra.domain.geocoding.GeocodingRepository
import opsi.sman35jkt.gathra.domain.geocoding.GeocodingRepositoryException
import retrofit2.Response

internal class RemoteGeocodingRepository(
    private val api: GeocodingApi,
    private val json: Json,
) : GeocodingRepository {

    override suspend fun autocomplete(
        query: String,
        proximity: GeoPoint?,
        limit: Int,
    ): List<PlaceSuggestion> = execute {
        validateQueryAndLimit(query, limit)
        val response = api.autocomplete(
            query = query.trim(),
            latitude = proximity?.latitude,
            longitude = proximity?.longitude,
            limit = limit,
        )
        response.requireBody()
            .let { GeocodingDtoMapper.toSuggestions(it, limit) }
    }

    override suspend fun search(
        query: String,
        proximity: GeoPoint?,
        limit: Int,
    ): List<PlaceSuggestion> = execute {
        validateQueryAndLimit(query, limit)
        val response = api.search(
            query = query.trim(),
            latitude = proximity?.latitude,
            longitude = proximity?.longitude,
            limit = limit,
        )
        response.requireBody()
            .let { GeocodingDtoMapper.toSuggestions(it, limit) }
    }

    override suspend fun lookup(id: String): SelectedPlace = execute {
        if (id.isBlank()) {
            throw GeocodingRepositoryException(
                GeocodingFailureReason.PLACE_NOT_FOUND,
            )
        }
        GeocodingDtoMapper.toSelectedPlace(api.lookup(id).requireBody())
    }

    override suspend fun reverse(point: GeoPoint): SelectedPlace? = execute {
        val response = api.reverse(point.latitude, point.longitude)
        if (response.code() == HTTP_NO_CONTENT) {
            null
        } else {
            GeocodingDtoMapper.toReverseSelectedPlace(
                response = response.requireBody(),
                requestedPoint = point,
            )
        }
    }

    private suspend fun <T> execute(block: suspend () -> T): T = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (expected: GeocodingRepositoryException) {
        throw expected
    } catch (timeout: SocketTimeoutException) {
        throw GeocodingRepositoryException(
            GeocodingFailureReason.TIMEOUT,
            timeout,
        )
    } catch (timeout: InterruptedIOException) {
        throw GeocodingRepositoryException(
            GeocodingFailureReason.TIMEOUT,
            timeout,
        )
    } catch (offline: UnknownHostException) {
        throw GeocodingRepositoryException(
            GeocodingFailureReason.OFFLINE,
            offline,
        )
    } catch (offline: ConnectException) {
        throw GeocodingRepositoryException(
            GeocodingFailureReason.OFFLINE,
            offline,
        )
    } catch (offline: NoRouteToHostException) {
        throw GeocodingRepositoryException(
            GeocodingFailureReason.OFFLINE,
            offline,
        )
    } catch (invalid: SerializationException) {
        throw GeocodingRepositoryException(
            GeocodingFailureReason.INVALID_RESPONSE,
            invalid,
        )
    } catch (invalid: InvalidGeocodingResponseException) {
        throw GeocodingRepositoryException(
            GeocodingFailureReason.INVALID_RESPONSE,
            invalid,
        )
    } catch (network: IOException) {
        throw GeocodingRepositoryException(
            GeocodingFailureReason.OFFLINE,
            network,
        )
    } catch (unexpected: Exception) {
        throw GeocodingRepositoryException(
            GeocodingFailureReason.UNKNOWN,
            unexpected,
        )
    }

    private fun validateQueryAndLimit(query: String, limit: Int) {
        if (query.trim().length !in MIN_QUERY_LENGTH..MAX_QUERY_LENGTH) {
            throw GeocodingRepositoryException(
                GeocodingFailureReason.INVALID_QUERY,
            )
        }
        if (limit !in 1..MAX_RESULTS) {
            throw GeocodingRepositoryException(
                GeocodingFailureReason.INVALID_QUERY,
            )
        }
    }

    private fun <T> Response<T>.requireBody(): T {
        if (isSuccessful) {
            return body() ?: throw GeocodingRepositoryException(
                GeocodingFailureReason.INVALID_RESPONSE,
            )
        }
        throw mapHttpFailure(this)
    }

    private fun mapHttpFailure(response: Response<*>): GeocodingRepositoryException {
        val backendCode = runCatching {
            val errorBody = response.errorBody()?.string().orEmpty()
            if (errorBody.isBlank()) {
                null
            } else {
                json.decodeFromString<GeocodingApiErrorEnvelopeDto>(
                    errorBody,
                ).error.code
            }
        }.getOrNull()

        val reason = when (backendCode) {
            "INVALID_QUERY" -> GeocodingFailureReason.INVALID_QUERY
            "INVALID_COORDINATES" -> GeocodingFailureReason.INVALID_COORDINATES
            "OUTSIDE_SUPPORTED_REGION" ->
                GeocodingFailureReason.OUTSIDE_SUPPORTED_REGION
            "PLACE_NOT_FOUND" -> GeocodingFailureReason.PLACE_NOT_FOUND
            "GEOCODER_TIMEOUT" -> GeocodingFailureReason.TIMEOUT
            "GEOCODER_UNAVAILABLE" -> GeocodingFailureReason.SERVER_UNAVAILABLE
            "INVALID_PROVIDER_RESPONSE" ->
                GeocodingFailureReason.INVALID_RESPONSE
            else -> when (response.code()) {
                400 -> GeocodingFailureReason.INVALID_QUERY
                404 -> GeocodingFailureReason.PLACE_NOT_FOUND
                422 -> GeocodingFailureReason.OUTSIDE_SUPPORTED_REGION
                502 -> GeocodingFailureReason.INVALID_RESPONSE
                503 -> GeocodingFailureReason.SERVER_UNAVAILABLE
                504 -> GeocodingFailureReason.TIMEOUT
                else -> GeocodingFailureReason.UNKNOWN
            }
        }
        return GeocodingRepositoryException(reason)
    }

    private companion object {
        const val HTTP_NO_CONTENT = 204
        const val MIN_QUERY_LENGTH = 2
        const val MAX_QUERY_LENGTH = 120
        const val MAX_RESULTS = 8
    }
}
