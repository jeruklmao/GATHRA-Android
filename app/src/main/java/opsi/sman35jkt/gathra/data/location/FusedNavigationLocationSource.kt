package opsi.sman35jkt.gathra.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import opsi.sman35jkt.gathra.core.location.NavigationLocationEvent
import opsi.sman35jkt.gathra.core.location.NavigationLocationSource
import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.RouteOption
import opsi.sman35jkt.gathra.domain.navigation.NavigationLocationSample

/**
 * High-accuracy updates used only while the foreground navigation service collects this flow.
 */
class FusedNavigationLocationSource(
    context: Context,
    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context.applicationContext),
) : NavigationLocationSource {
    private val applicationContext = context.applicationContext
    private val locationManager = applicationContext.getSystemService(
        Context.LOCATION_SERVICE,
    ) as LocationManager

    @SuppressLint("MissingPermission")
    override fun updates(route: RouteOption): Flow<NavigationLocationEvent> = callbackFlow {
        val collectorClosed = AtomicBoolean(false)
        val fineGranted = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseGranted = fineGranted ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!coarseGranted) {
            trySend(NavigationLocationEvent.PermissionDenied)
            close()
            return@callbackFlow
        }
        if (!LocationManagerCompat.isLocationEnabled(locationManager)) {
            trySend(NavigationLocationEvent.LocationDisabled)
            close()
            return@callbackFlow
        }

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    val point = runCatching {
                        GeoPoint(
                            latitude = location.latitude,
                            longitude = location.longitude,
                        )
                    }.getOrNull() ?: return@forEach
                    trySend(
                        NavigationLocationEvent.Location(
                            NavigationLocationSample(
                                point = point,
                                accuracyMeters = location.accuracy
                                    .toDouble()
                                    .coerceAtLeast(0.0),
                                bearingDegrees = location.bearing
                                    .toDouble()
                                    .takeIf { location.hasBearing() },
                                speedMetersPerSecond = location.speed
                                    .toDouble()
                                    .takeIf { location.hasSpeed() },
                                elapsedRealtimeMillis =
                                    location.elapsedRealtimeNanos / NANOS_PER_MILLISECOND,
                                epochTimeMillis = location.time.coerceAtLeast(0L),
                                isApproximate = !fineGranted,
                            ),
                        ),
                    )
                }
            }
        }
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            UPDATE_INTERVAL_MILLIS,
        )
            .setMinUpdateIntervalMillis(MINIMUM_UPDATE_INTERVAL_MILLIS)
            .setMinUpdateDistanceMeters(MINIMUM_UPDATE_DISTANCE_METERS)
            .setMaxUpdateDelayMillis(UPDATE_INTERVAL_MILLIS)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            client.requestLocationUpdates(
                request,
                callback,
                Looper.getMainLooper(),
            )
                .addOnSuccessListener {
                    // Cancellation can win the race with asynchronous registration.
                    // Remove again so no callback survives a stopped navigation session.
                    if (collectorClosed.get()) {
                        client.removeLocationUpdates(callback)
                    }
                }
                .addOnFailureListener { failure ->
                    if (failure is SecurityException) {
                        trySend(NavigationLocationEvent.PermissionDenied)
                    } else {
                        trySend(NavigationLocationEvent.TemporarilyUnavailable)
                    }
                    close()
                }
        } catch (_: SecurityException) {
            trySend(NavigationLocationEvent.PermissionDenied)
            close()
        } catch (_: RuntimeException) {
            trySend(NavigationLocationEvent.TemporarilyUnavailable)
            close()
        }

        awaitClose {
            collectorClosed.set(true)
            client.removeLocationUpdates(callback)
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(applicationContext, permission) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val UPDATE_INTERVAL_MILLIS = 2_000L
        const val MINIMUM_UPDATE_INTERVAL_MILLIS = 1_000L
        const val MINIMUM_UPDATE_DISTANCE_METERS = 3f
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
