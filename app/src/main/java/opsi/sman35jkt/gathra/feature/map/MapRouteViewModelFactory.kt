package opsi.sman35jkt.gathra.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import opsi.sman35jkt.gathra.core.location.LocationRepository
import opsi.sman35jkt.gathra.domain.geocoding.GeocodingRepository
import opsi.sman35jkt.gathra.domain.route.RouteRepository

class MapRouteViewModelFactory(
    private val routeRepository: RouteRepository,
    private val locationRepository: LocationRepository,
    private val geocodingRepository: GeocodingRepository,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(MapRouteViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }

        @Suppress("UNCHECKED_CAST")
        return MapRouteViewModel(
            routeRepository = routeRepository,
            locationRepository = locationRepository,
            geocodingRepository = geocodingRepository,
            workDispatcher = workDispatcher,
        ) as T
    }
}
