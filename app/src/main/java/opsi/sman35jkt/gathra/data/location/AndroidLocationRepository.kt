package opsi.sman35jkt.gathra.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import opsi.sman35jkt.gathra.core.location.LocationLookupResult
import opsi.sman35jkt.gathra.core.location.LocationRepository
import opsi.sman35jkt.gathra.core.model.GeoPoint

/**
 * Performs one bounded foreground location lookup and never retains an Activity.
 */
class AndroidLocationRepository(
    context: Context,
    private val requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
) : LocationRepository {

    private val applicationContext = context.applicationContext
    private val locationManager = applicationContext.getSystemService(
        Context.LOCATION_SERVICE,
    ) as LocationManager

    init {
        require(requestTimeoutMillis > 0) {
            "Location request timeout must be positive."
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun locateOnce(): LocationLookupResult {
        val hasFinePermission = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarsePermission = hasFinePermission ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!hasCoarsePermission) {
            return LocationLookupResult.PermissionDenied
        }

        return try {
            if (!LocationManagerCompat.isLocationEnabled(locationManager)) {
                return LocationLookupResult.LocationDisabled
            }

            val lastKnownProviders = availableLastKnownProviders(hasFinePermission)
            val lastKnownLocation = bestRecentLastKnownLocation(lastKnownProviders)
            val currentProvider = selectCurrentProvider(hasFinePermission)

            if (currentProvider == null) {
                return lastKnownLocation.toLookupResult()
                    ?: LocationLookupResult.LocationDisabled
            }

            val currentLocation = withTimeoutOrNull(requestTimeoutMillis) {
                requestSingleLocation(currentProvider)
            }?.toGeoPointOrNull()

            when {
                currentLocation != null -> LocationLookupResult.Success(
                    point = currentLocation,
                    fromLastKnown = false,
                )

                lastKnownLocation != null -> LocationLookupResult.Success(
                    point = lastKnownLocation,
                    fromLastKnown = true,
                )

                else -> LocationLookupResult.Unavailable
            }
        } catch (_: SecurityException) {
            LocationLookupResult.PermissionDenied
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: RuntimeException) {
            // Providers can disappear or become unavailable while a request is in flight.
            LocationLookupResult.Unavailable
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(applicationContext, permission) ==
            PackageManager.PERMISSION_GRANTED

    private fun availableLastKnownProviders(hasFinePermission: Boolean): List<String> =
        buildList {
            if (hasFinePermission && isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                add(LocationManager.GPS_PROVIDER)
            }
            if (isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
            if (isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                add(LocationManager.PASSIVE_PROVIDER)
            }
        }

    private fun selectCurrentProvider(hasFinePermission: Boolean): String? = when {
        hasFinePermission && isProviderEnabled(LocationManager.GPS_PROVIDER) ->
            LocationManager.GPS_PROVIDER

        isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
            LocationManager.NETWORK_PROVIDER

        else -> null
    }

    private fun isProviderEnabled(provider: String): Boolean =
        runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    private fun bestRecentLastKnownLocation(providers: List<String>): GeoPoint? =
        providers
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .filter { it.ageMillis() <= MAX_LAST_KNOWN_AGE_MILLIS }
            .maxByOrNull { it.elapsedRealtimeNanos }
            ?.toGeoPointOrNull()

    @SuppressLint("MissingPermission")
    private suspend fun requestSingleLocation(provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val cancellationSignal = CancellationSignal()
                continuation.invokeOnCancellation {
                    cancellationSignal.cancel()
                }
                locationManager.getCurrentLocation(
                    provider,
                    cancellationSignal,
                    applicationContext.mainExecutor,
                ) { location ->
                    continuation.resume(location) { _, _, _ -> }
                }
            } else {
                lateinit var listener: LocationListener
                listener = LocationListener { location ->
                    locationManager.removeUpdates(listener)
                    continuation.resume(location) { _, _, _ -> }
                }
                continuation.invokeOnCancellation {
                    runCatching { locationManager.removeUpdates(listener) }
                }
                @Suppress("DEPRECATION")
                locationManager.requestSingleUpdate(
                    provider,
                    listener,
                    Looper.getMainLooper(),
                )
            }
        }

    private fun Location.ageMillis(): Long {
        val elapsedNanos = elapsedRealtimeNanos
        return if (elapsedNanos > 0L) {
            ((SystemClock.elapsedRealtimeNanos() - elapsedNanos) / NANOS_PER_MILLISECOND)
                .coerceAtLeast(0L)
        } else {
            (System.currentTimeMillis() - time).coerceAtLeast(0L)
        }
    }

    private fun Location.toGeoPointOrNull(): GeoPoint? =
        runCatching {
            GeoPoint(
                latitude = latitude,
                longitude = longitude,
            )
        }.getOrNull()

    private fun GeoPoint?.toLookupResult(): LocationLookupResult.Success? =
        this?.let {
            LocationLookupResult.Success(
                point = it,
                fromLastKnown = true,
            )
        }

    private companion object {
        const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 4_000L
        const val MAX_LAST_KNOWN_AGE_MILLIS = 30 * 60 * 1_000L
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
