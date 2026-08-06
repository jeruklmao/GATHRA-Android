package opsi.sman35jkt.gathra.feature.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import opsi.sman35jkt.gathra.domain.navigation.NavigationRepository
import opsi.sman35jkt.gathra.service.navigation.NavigationServiceController

class NavigationViewModelFactory(
    private val repository: NavigationRepository,
    private val serviceController: NavigationServiceController,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(NavigationViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return NavigationViewModel(
            repository = repository,
            serviceController = serviceController,
        ) as T
    }
}
