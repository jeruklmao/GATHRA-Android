package opsi.sman35jkt.gathra.feature.geocoding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import opsi.sman35jkt.gathra.domain.geocoding.GeocodingRepository

class PlaceSearchViewModelFactory(
    private val repository: GeocodingRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(PlaceSearchViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return PlaceSearchViewModel(repository = repository) as T
    }
}
