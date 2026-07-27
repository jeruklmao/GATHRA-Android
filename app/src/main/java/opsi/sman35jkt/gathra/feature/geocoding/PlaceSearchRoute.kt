package opsi.sman35jkt.gathra.feature.geocoding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import opsi.sman35jkt.gathra.core.model.SelectedPlace

@Composable
fun PlaceSearchRoute(
    viewModel: PlaceSearchViewModel,
    onPlaceSelected: (SearchTargetField, SelectedPlace) -> Unit,
    onUseCurrentLocation: (SearchTargetField) -> Unit,
    onChooseOnMap: (SearchTargetField) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PlaceSearchEffect.PlaceSelected -> onPlaceSelected(
                    effect.targetField,
                    effect.place,
                )
                is PlaceSearchEffect.UseCurrentLocation ->
                    onUseCurrentLocation(effect.targetField)
                is PlaceSearchEffect.ChooseOnMap ->
                    onChooseOnMap(effect.targetField)
            }
        }
    }

    if (state.isVisible) {
        PlaceSearchScreen(
            state = state,
            onAction = viewModel::onAction,
            modifier = modifier,
        )
    }
}
