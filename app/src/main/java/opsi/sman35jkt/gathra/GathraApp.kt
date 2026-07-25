package opsi.sman35jkt.gathra

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import opsi.sman35jkt.gathra.feature.map.MapRouteRoute
import opsi.sman35jkt.gathra.feature.map.MapRouteViewModel
import opsi.sman35jkt.gathra.feature.map.MapRouteViewModelFactory
import opsi.sman35jkt.gathra.ui.theme.GATHRATheme

class GathraApplication : Application() {
    val appContainer: AppContainer by lazy {
        AppContainer(applicationContext)
    }
}

@Composable
fun GathraApp(
    appContainer: AppContainer,
) {
    val factory = remember(appContainer) {
        MapRouteViewModelFactory(
            routeRepository = appContainer.routeRepository,
            locationRepository = appContainer.locationRepository,
        )
    }
    val viewModel: MapRouteViewModel = viewModel(factory = factory)

    GATHRATheme {
        MapRouteRoute(viewModel = viewModel)
    }
}
