package opsi.sman35jkt.gathra.data.route.remote

import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import opsi.sman35jkt.gathra.core.map.JakartaDemoPoints
import opsi.sman35jkt.gathra.core.model.RouteRequest
import opsi.sman35jkt.gathra.core.model.TravelMode
import opsi.sman35jkt.gathra.domain.route.RouteFailureReason
import opsi.sman35jkt.gathra.domain.route.RouteRepositoryException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteRouteRepositoryTest {

    @Test
    fun `repository maps request and returns two domain routes`() = runTest {
        var capturedRequest: RoutePreviewRequestDto? = null
        val repository = repositoryWith { request ->
            capturedRequest = request
            Response.success(validResponse(travelMode = "MOTORCYCLE"))
        }

        val routes = repository.getRoutes(routeRequest(TravelMode.MOTORCYCLE))

        assertEquals("MOTORCYCLE", capturedRequest?.travelMode)
        assertEquals(1, capturedRequest?.alternatives)
        assertEquals(2, routes.size)
        assertEquals(10, routes.first().summary.etaMinutes)
    }

    @Test
    fun `repository maps backend no route error`() = runTest {
        val repository = repositoryWith {
            Response.error(
                422,
                errorJson("NO_ROUTE", retryable = false)
                    .toResponseBody(JSON_MEDIA_TYPE),
            )
        }

        val failure = expectRepositoryFailure {
            repository.getRoutes(routeRequest())
        }

        assertEquals(RouteFailureReason.NO_ROUTE, failure.reason)
    }

    @Test
    fun `repository maps DNS failure to offline`() = runTest {
        val repository = repositoryWith { throw UnknownHostException("offline") }

        val failure = expectRepositoryFailure {
            repository.getRoutes(routeRequest())
        }

        assertEquals(RouteFailureReason.OFFLINE, failure.reason)
    }

    @Test
    fun `repository maps socket timeout`() = runTest {
        val repository = repositoryWith { throw SocketTimeoutException("timeout") }

        val failure = expectRepositoryFailure {
            repository.getRoutes(routeRequest())
        }

        assertEquals(RouteFailureReason.TIMEOUT, failure.reason)
    }

    @Test
    fun `repository maps whole call timeout`() = runTest {
        val repository = repositoryWith {
            throw InterruptedIOException("timeout")
        }

        val failure = expectRepositoryFailure {
            repository.getRoutes(routeRequest())
        }

        assertEquals(RouteFailureReason.TIMEOUT, failure.reason)
    }

    @Test
    fun `repository rejects malformed successful response`() = runTest {
        val repository = repositoryWith {
            Response.success(validResponse().copy(routes = emptyList()))
        }

        val failure = expectRepositoryFailure {
            repository.getRoutes(routeRequest())
        }

        assertEquals(RouteFailureReason.INVALID_RESPONSE, failure.reason)
    }

    @Test
    fun `repository propagates coroutine cancellation`() = runTest {
        val callStarted = CompletableDeferred<Unit>()
        val repository = repositoryWith {
            callStarted.complete(Unit)
            awaitCancellation()
        }

        val requestJob = launch {
            repository.getRoutes(routeRequest())
        }
        callStarted.await()
        requestJob.cancelAndJoin()

        assertTrue(requestJob.isCancelled)
    }

    private fun repositoryWith(
        response: suspend (RoutePreviewRequestDto) -> Response<RoutePreviewResponseDto>,
    ) = RemoteRouteRepository(
        routeApi = object : RouteApi {
            override suspend fun previewRoute(
                request: RoutePreviewRequestDto,
            ): Response<RoutePreviewResponseDto> = response(request)
        },
        json = Json,
    )

    private suspend fun expectRepositoryFailure(
        block: suspend () -> Unit,
    ): RouteRepositoryException = try {
        block()
        fail("Expected RouteRepositoryException")
        error("unreachable")
    } catch (failure: RouteRepositoryException) {
        failure
    }

    private fun routeRequest(mode: TravelMode = TravelMode.CAR) = RouteRequest(
        origin = JakartaDemoPoints.origin,
        destination = JakartaDemoPoints.suggestedDestination,
        travelMode = mode,
    )

    private fun validResponse(
        travelMode: String = "CAR",
    ) = RoutePreviewResponseDto(
        requestId = "request-1",
        routes = listOf(
            route(id = "primary", recommended = true, durationSeconds = 600),
            route(id = "alternative", recommended = false, durationSeconds = 720),
        ),
        metadata = RouteMetadataDto(
            travelMode = travelMode,
            requestedAlternatives = 1,
            returnedAlternatives = 1,
        ),
    )

    private fun route(
        id: String,
        recommended: Boolean,
        durationSeconds: Int,
    ) = RouteResponseDto(
        id = id,
        isRecommended = recommended,
        geometry = GeoJsonLineStringDto(
            type = "LineString",
            coordinates = if (recommended) {
                listOf(
                    listOf(106.8167, -6.2),
                    listOf(106.8272, -6.1754),
                )
            } else {
                listOf(
                    listOf(106.8167, -6.2),
                    listOf(106.81, -6.18),
                    listOf(106.8272, -6.1754),
                )
            },
        ),
        summary = RouteSummaryResponseDto(
            distanceMeters = if (recommended) 4_000 else 4_400,
            durationSeconds = durationSeconds,
        ),
        steps = listOf(
            RouteStepResponseDto(
                index = 0,
                instruction = "Mulai mengikuti rute",
                streetName = "Jalan Demo",
                distanceMeters = if (recommended) 4_000 else 4_400,
                durationSeconds = durationSeconds,
                manoeuvre = RouteManeuverResponseDto(
                    type = "DEPART",
                    modifier = "STRAIGHT",
                    bearingBefore = null,
                    bearingAfter = 90,
                ),
                geometryStartIndex = 0,
                geometryEndIndex = if (recommended) 1 else 2,
            ),
            RouteStepResponseDto(
                index = 1,
                instruction = "Anda telah tiba",
                streetName = "",
                distanceMeters = 0,
                durationSeconds = 0,
                manoeuvre = RouteManeuverResponseDto(
                    type = "ARRIVE",
                    modifier = "NONE",
                    bearingBefore = 90,
                    bearingAfter = null,
                ),
                geometryStartIndex = if (recommended) 1 else 2,
                geometryEndIndex = if (recommended) 1 else 2,
            ),
        ),
    )

    private fun errorJson(code: String, retryable: Boolean) =
        """{"requestId":"request-1","error":{"code":"$code","message":"failed","retryable":$retryable}}"""

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
