package opsi.sman35jkt.gathra.feature.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import opsi.sman35jkt.gathra.core.map.NavigationCameraMode
import opsi.sman35jkt.gathra.domain.navigation.NavigationRepository
import opsi.sman35jkt.gathra.service.navigation.NavigationServiceController

class NavigationViewModel(
    private val repository: NavigationRepository,
    private val serviceController: NavigationServiceController,
    simulationEnabled: Boolean,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        NavigationUiState(simulationEnabled = simulationEnabled),
    )
    val uiState: StateFlow<NavigationUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<NavigationEffect>(
        extraBufferCapacity = 4,
    )
    val effects: SharedFlow<NavigationEffect> = _effects.asSharedFlow()

    private var voiceUnavailableReported = false

    init {
        viewModelScope.launch {
            repository.session.collect { session ->
                _uiState.update { it.copy(session = session) }
                if (session?.voiceUnavailable == true && !voiceUnavailableReported) {
                    voiceUnavailableReported = true
                    _effects.emit(NavigationEffect.VOICE_UNAVAILABLE)
                }
            }
        }
    }

    fun onAction(action: NavigationAction) {
        when (action) {
            NavigationAction.MapPanned -> {
                _uiState.update { it.copy(cameraMode = NavigationCameraMode.FREE) }
            }
            NavigationAction.RecenterClicked -> {
                _uiState.update { it.copy(cameraMode = NavigationCameraMode.FOLLOW) }
            }
            NavigationAction.OverviewClicked -> {
                _uiState.update {
                    it.copy(
                        cameraMode = if (
                            it.cameraMode == NavigationCameraMode.OVERVIEW
                        ) {
                            NavigationCameraMode.FOLLOW
                        } else {
                            NavigationCameraMode.OVERVIEW
                        },
                    )
                }
            }
            NavigationAction.MuteClicked -> {
                val muted = !(_uiState.value.session?.muted ?: false)
                repository.setMuted(muted)
            }
            NavigationAction.StopRequested -> {
                _uiState.update { it.copy(stopConfirmationVisible = true) }
            }
            NavigationAction.StopDismissed -> {
                _uiState.update { it.copy(stopConfirmationVisible = false) }
            }
            NavigationAction.StopConfirmed,
            NavigationAction.FinishClicked,
            -> {
                _uiState.update { it.copy(stopConfirmationVisible = false) }
                if (!serviceController.stop()) {
                    _effects.tryEmit(NavigationEffect.SERVICE_ACTION_FAILED)
                }
            }
            NavigationAction.RetryReroute -> {
                if (!serviceController.retryReroute()) {
                    _effects.tryEmit(NavigationEffect.SERVICE_ACTION_FAILED)
                }
            }
            NavigationAction.ToggleSimulationPause -> {
                val paused = !_uiState.value.simulationPaused
                if (serviceController.setSimulationPaused(paused)) {
                    _uiState.update { it.copy(simulationPaused = paused) }
                } else {
                    _effects.tryEmit(NavigationEffect.SERVICE_ACTION_FAILED)
                }
            }
            is NavigationAction.SimulationSpeedSelected -> {
                if (serviceController.setSimulationSpeed(action.multiplier)) {
                    _uiState.update { it.copy(simulationSpeed = action.multiplier) }
                } else {
                    _effects.tryEmit(NavigationEffect.SERVICE_ACTION_FAILED)
                }
            }
            NavigationAction.SimulateOffRoute -> {
                if (!serviceController.simulateOffRoute()) {
                    _effects.tryEmit(NavigationEffect.SERVICE_ACTION_FAILED)
                }
            }
        }
    }
}
