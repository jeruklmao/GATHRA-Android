package opsi.sman35jkt.gathra.data.geocoding

import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.PlaceCategory
import opsi.sman35jkt.gathra.core.model.PlaceSuggestion
import opsi.sman35jkt.gathra.core.model.SelectedPlace
import opsi.sman35jkt.gathra.domain.geocoding.GeocodingFailureReason
import opsi.sman35jkt.gathra.domain.geocoding.GeocodingRepository
import opsi.sman35jkt.gathra.domain.geocoding.GeocodingRepositoryException

enum class FakeGeocodingFailureMode {
    NONE,
    ALWAYS_FAIL,
}

/**
 * Deterministic, network-free place data for automated tests.
 */
class FakeGeocodingRepository(
    private val loadingDelayMillis: Long = DEFAULT_LOADING_DELAY_MILLIS,
    private val failureMode: FakeGeocodingFailureMode =
        FakeGeocodingFailureMode.NONE,
) : GeocodingRepository {

    init {
        require(loadingDelayMillis >= 0) {
            "Loading delay cannot be negative."
        }
    }

    override suspend fun autocomplete(
        query: String,
        proximity: GeoPoint?,
        limit: Int,
    ): List<PlaceSuggestion> {
        simulateRequest()
        return find(query, proximity, limit)
    }

    override suspend fun search(
        query: String,
        proximity: GeoPoint?,
        limit: Int,
    ): List<PlaceSuggestion> {
        simulateRequest()
        return find(query, proximity, limit)
    }

    override suspend fun lookup(id: String): SelectedPlace {
        simulateRequest()
        val fixture = FIXTURES.firstOrNull { it.id == id }
            ?: throw GeocodingRepositoryException(
                GeocodingFailureReason.PLACE_NOT_FOUND,
            )
        return fixture.toSelectedPlace()
    }

    override suspend fun reverse(point: GeoPoint): SelectedPlace? {
        simulateRequest()
        if (!point.isInsideBufferedCoverage()) return null

        val nearest = FIXTURES.minByOrNull { distanceMeters(point, it.point) }
        val nearestDistance = nearest?.let { distanceMeters(point, it.point) }
        val label = if (nearest != null && nearestDistance != null &&
            nearestDistance <= REVERSE_NEARBY_LIMIT_METERS
        ) {
            nearest.name
        } else {
            "Titik pilihan ${regionLabel(point)}"
        }
        val address = nearest
            ?.takeIf { nearestDistance != null &&
                nearestDistance <= REVERSE_NEARBY_LIMIT_METERS
            }
            ?.secondaryText
            ?: regionLabel(point)
        return SelectedPlace(
            id = nearest
                ?.takeIf { nearestDistance != null &&
                    nearestDistance <= REVERSE_NEARBY_LIMIT_METERS
                }
                ?.id,
            name = label,
            formattedAddress = address,
            // The map-selected coordinate remains authoritative.
            position = point,
            category = nearest
                ?.takeIf { nearestDistance != null &&
                    nearestDistance <= REVERSE_NEARBY_LIMIT_METERS
                }
                ?.category
                ?: PlaceCategory.ADDRESS,
            insideSupportedRegion = true,
        )
    }

    private suspend fun simulateRequest() {
        delay(loadingDelayMillis)
        if (failureMode == FakeGeocodingFailureMode.ALWAYS_FAIL) {
            throw GeocodingRepositoryException(
                GeocodingFailureReason.SERVER_UNAVAILABLE,
            )
        }
    }

    private fun find(
        rawQuery: String,
        proximity: GeoPoint?,
        limit: Int,
    ): List<PlaceSuggestion> {
        require(limit in 1..MAX_RESULTS) { "Result limit must be between 1 and 8." }
        val query = normalize(rawQuery)
        if (query.length < 2) {
            throw GeocodingRepositoryException(
                GeocodingFailureReason.INVALID_QUERY,
            )
        }

        return FIXTURES
            .mapNotNull { fixture ->
                val searchable = fixture.searchableLabels.map(::normalize)
                val score = when {
                    searchable.any { it == query } -> 0
                    searchable.any { it.startsWith(query) } -> 1
                    searchable.any { it.contains(query) } -> 2
                    query.split(' ').all { token ->
                        searchable.any { it.contains(token) }
                    } -> 3
                    else -> null
                } ?: return@mapNotNull null
                val distance = proximity?.let {
                    distanceMeters(it, fixture.point).toInt().coerceAtLeast(0)
                }
                Triple(score, distance ?: Int.MAX_VALUE, fixture)
            }
            .sortedWith(
                compareBy<Triple<Int, Int, FakePlaceFixture>>(
                    { it.first },
                    { it.second },
                    { it.third.name },
                ),
            )
            .take(limit)
            .map { (_, distance, fixture) ->
                fixture.toSuggestion(
                    distanceMeters = distance.takeUnless { it == Int.MAX_VALUE },
                )
            }
    }

    private data class FakePlaceFixture(
        val id: String,
        val name: String,
        val secondaryText: String,
        val category: PlaceCategory,
        val point: GeoPoint,
        val aliases: List<String>,
    ) {
        val searchableLabels: List<String>
            get() = listOf(name, secondaryText) + aliases

        fun toSuggestion(distanceMeters: Int?): PlaceSuggestion =
            PlaceSuggestion(
                id = id,
                primaryText = name,
                secondaryText = secondaryText,
                category = category,
                position = point,
                distanceMeters = distanceMeters,
                insideSupportedRegion = true,
            )

        fun toSelectedPlace(): SelectedPlace =
            SelectedPlace(
                id = id,
                name = name,
                formattedAddress = secondaryText,
                position = point,
                category = category,
                insideSupportedRegion = true,
            )
    }

    private companion object {
        const val DEFAULT_LOADING_DELAY_MILLIS = 380L
        const val MAX_RESULTS = 8
        const val REVERSE_NEARBY_LIMIT_METERS = 700.0
        const val EARTH_RADIUS_METERS = 6_371_000.0
        const val MIN_LONGITUDE = 106.479
        const val MAX_LONGITUDE = 106.955
        const val MIN_LATITUDE = -6.437
        const val MAX_LATITUDE = -6.025

        val FIXTURES = listOf(
            FakePlaceFixture(
                id = "demo-monas",
                name = "Monumen Nasional",
                secondaryText = "Gambir, Jakarta Pusat",
                category = PlaceCategory.LANDMARK,
                point = GeoPoint(-6.1754, 106.8272),
                aliases = listOf("Monas", "Tugu Monas"),
            ),
            FakePlaceFixture(
                id = "demo-sman-35",
                name = "SMA Negeri 35 Jakarta",
                secondaryText = "Karet Tengsin, Jakarta Pusat",
                category = PlaceCategory.SCHOOL,
                point = GeoPoint(-6.2093, 106.8142),
                aliases = listOf("SMAN 35", "SMA 35 Jakarta"),
            ),
            FakePlaceFixture(
                id = "demo-ragunan",
                name = "Taman Margasatwa Ragunan",
                secondaryText = "Pasar Minggu, Jakarta Selatan",
                category = PlaceCategory.LANDMARK,
                point = GeoPoint(-6.3114, 106.8208),
                aliases = listOf("Ragunan", "Kebun Binatang Ragunan"),
            ),
            FakePlaceFixture(
                id = "demo-blok-m",
                name = "MRT Blok M BCA",
                secondaryText = "Kebayoran Baru, Jakarta Selatan",
                category = PlaceCategory.TRANSIT,
                point = GeoPoint(-6.2445, 106.7982),
                aliases = listOf("Blok M", "Stasiun MRT Blok M"),
            ),
            FakePlaceFixture(
                id = "demo-tangerang-government",
                name = "Pusat Pemerintahan Kota Tangerang",
                secondaryText = "Sukaasih, Kota Tangerang",
                category = PlaceCategory.GOVERNMENT,
                point = GeoPoint(-6.1765, 106.6302),
                aliases = listOf(
                    "Pemkot Tangerang",
                    "Puspem Kota Tangerang",
                    "Pemkot Tanggerang",
                ),
            ),
            FakePlaceFixture(
                id = "demo-tangerang-station",
                name = "Stasiun Tangerang",
                secondaryText = "Sukasari, Kota Tangerang",
                category = PlaceCategory.TRANSIT,
                point = GeoPoint(-6.1768, 106.6325),
                aliases = listOf("Stasiun Tanggerang"),
            ),
            FakePlaceFixture(
                id = "demo-tangsel-government",
                name = "Pusat Pemerintahan Kota Tangerang Selatan",
                secondaryText = "Ciputat, Kota Tangerang Selatan",
                category = PlaceCategory.GOVERNMENT,
                point = GeoPoint(-6.3053, 106.7086),
                aliases = listOf(
                    "Pemkot Tangsel",
                    "Pemkot Tangerang Selatan",
                    "Pemkot Tanggerang Selatan",
                ),
            ),
            FakePlaceFixture(
                id = "demo-taman-kota-bsd",
                name = "Taman Kota 1 BSD",
                secondaryText = "Serpong, Kota Tangerang Selatan",
                category = PlaceCategory.LANDMARK,
                point = GeoPoint(-6.2871, 106.6675),
                aliases = listOf("Taman Kota BSD", "Taman Kota Tangsel"),
            ),
        )

        fun normalize(value: String): String =
            value
                .trim()
                .lowercase(Locale.ROOT)
                .replace("tanggerang", "tangerang")
                .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
                .trim()

        fun GeoPoint.isInsideBufferedCoverage(): Boolean =
            longitude in MIN_LONGITUDE..MAX_LONGITUDE &&
                latitude in MIN_LATITUDE..MAX_LATITUDE

        fun regionLabel(point: GeoPoint): String = when {
            point.longitude < 106.69 && point.latitude > -6.25 ->
                "Kota Tangerang"
            point.longitude < 106.76 && point.latitude <= -6.25 ->
                "Kota Tangerang Selatan"
            point.latitude <= -6.23 -> "Jakarta Selatan"
            else -> "Jakarta Pusat"
        }

        fun distanceMeters(first: GeoPoint, second: GeoPoint): Double {
            val firstLatitude = Math.toRadians(first.latitude)
            val secondLatitude = Math.toRadians(second.latitude)
            val latitudeDelta = secondLatitude - firstLatitude
            val longitudeDelta = Math.toRadians(
                second.longitude - first.longitude,
            )
            val haversine = sin(latitudeDelta / 2).let { it * it } +
                cos(firstLatitude) * cos(secondLatitude) *
                sin(longitudeDelta / 2).let { it * it }
            return EARTH_RADIUS_METERS * 2 *
                asin(sqrt(haversine.coerceIn(0.0, 1.0)))
        }
    }
}
