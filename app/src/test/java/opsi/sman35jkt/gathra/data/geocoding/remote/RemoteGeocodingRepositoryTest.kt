package opsi.sman35jkt.gathra.data.geocoding.remote

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.domain.geocoding.GeocodingFailureReason
import opsi.sman35jkt.gathra.domain.geocoding.GeocodingRepositoryException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Response

class RemoteGeocodingRepositoryTest {

    @Test
    fun `autocomplete trims query and maps normalized response`() = runTest {
        var capturedQuery: String? = null
        val repository = repositoryWith(
            autocomplete = { query, _, _, _ ->
                capturedQuery = query
                Response.success(suggestions())
            },
        )

        val result = repository.autocomplete(
            query = "  Monas  ",
            proximity = GeoPoint(-6.2, 106.81),
        )

        assertEquals("Monas", capturedQuery)
        assertEquals("Monumen Nasional", result.single().primaryText)
    }

    @Test
    fun `reverse accepts no-content as no useful label`() = runTest {
        val repository = repositoryWith(
            reverse = { _, _ ->
                Response.success<PlaceDetailsResponseDto>(204, null)
            },
        )

        assertNull(repository.reverse(GeoPoint(-6.2, 106.81)))
    }

    @Test
    fun `backend timeout code maps to typed timeout`() = runTest {
        val repository = repositoryWith(
            autocomplete = { _, _, _, _ ->
                Response.error(
                    504,
                    errorJson("GEOCODER_TIMEOUT")
                        .toResponseBody(JSON_MEDIA_TYPE),
                )
            },
        )

        val failure = expectFailure {
            repository.autocomplete("Monas", null)
        }

        assertEquals(GeocodingFailureReason.TIMEOUT, failure.reason)
    }

    @Test
    fun `in-flight Retrofit coroutine cancellation propagates`() = runTest {
        val started = CompletableDeferred<Unit>()
        val repository = repositoryWith(
            autocomplete = { _, _, _, _ ->
                started.complete(Unit)
                kotlinx.coroutines.awaitCancellation()
            },
        )

        val job = launch {
            repository.autocomplete("Monas", null)
        }
        started.await()
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
    }

    private fun repositoryWith(
        autocomplete: suspend (
            String,
            Double?,
            Double?,
            Int,
        ) -> Response<PlaceSuggestionsResponseDto> = { _, _, _, _ ->
            Response.success(suggestions())
        },
        reverse: suspend (
            Double,
            Double,
        ) -> Response<PlaceDetailsResponseDto> = { _, _ ->
            Response.success(details())
        },
    ): RemoteGeocodingRepository {
        val api = object : GeocodingApi {
            override suspend fun autocomplete(
                query: String,
                latitude: Double?,
                longitude: Double?,
                limit: Int,
                language: String,
            ) = autocomplete(query, latitude, longitude, limit)

            override suspend fun search(
                query: String,
                latitude: Double?,
                longitude: Double?,
                limit: Int,
                language: String,
            ) = Response.success(suggestions())

            override suspend fun lookup(
                id: String,
            ) = Response.success(details())

            override suspend fun reverse(
                latitude: Double,
                longitude: Double,
                language: String,
            ) = reverse(latitude, longitude)
        }
        return RemoteGeocodingRepository(api = api, json = Json)
    }

    private suspend fun expectFailure(
        block: suspend () -> Unit,
    ): GeocodingRepositoryException = try {
        block()
        fail("Expected GeocodingRepositoryException")
        error("unreachable")
    } catch (failure: GeocodingRepositoryException) {
        failure
    }

    private fun suggestions() = PlaceSuggestionsResponseDto(
        suggestions = listOf(
            PlaceSuggestionResponseDto(
                id = "opaque-monas",
                primaryText = "Monumen Nasional",
                secondaryText = "Gambir, Jakarta Pusat",
                category = "LANDMARK",
                position = GeocodingPositionDto(-6.1754, 106.8272),
                distanceMeters = 1_000,
                insideSupportedRegion = true,
            ),
        ),
        requestId = "request-1",
    )

    private fun details() = PlaceDetailsResponseDto(
        id = "opaque-monas",
        name = "Monumen Nasional",
        formattedAddress = "Gambir, Jakarta Pusat",
        position = GeocodingPositionDto(-6.1754, 106.8272),
        category = "LANDMARK",
        insideSupportedRegion = true,
    )

    private fun errorJson(code: String) =
        """{"requestId":"request-1","error":{"code":"$code","message":"failed","retryable":true}}"""

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
