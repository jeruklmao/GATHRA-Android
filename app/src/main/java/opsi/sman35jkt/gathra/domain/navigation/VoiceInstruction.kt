package opsi.sman35jkt.gathra.domain.navigation

import opsi.sman35jkt.gathra.core.model.RouteStep

enum class VoiceCue {
    PREPARATION,
    ADVANCE,
    IMMEDIATE,
    ARRIVAL,
}

/**
 * A semantic voice event. Android string resources are responsible for turning
 * this into localized spoken text.
 */
data class VoiceInstructionEvent(
    val routeId: String,
    val step: RouteStep,
    val cue: VoiceCue,
    val thresholdMeters: Double,
)

