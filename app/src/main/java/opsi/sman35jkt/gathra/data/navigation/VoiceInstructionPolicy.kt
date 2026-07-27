package opsi.sman35jkt.gathra.data.navigation

import opsi.sman35jkt.gathra.core.model.RouteStep
import opsi.sman35jkt.gathra.domain.navigation.VoiceCue
import opsi.sman35jkt.gathra.domain.navigation.VoiceInstructionEvent
import kotlin.math.max
import kotlin.math.min

/**
 * Emits semantic TTS cues once per route, step, and threshold.
 */
class VoiceInstructionPolicy {
    private val highestCueByStep = mutableMapOf<StepKey, VoiceCue>()
    private val arrivedRouteIds = mutableSetOf<String>()

    fun nextEvent(
        routeId: String,
        step: RouteStep,
        distanceToManoeuvreMeters: Double,
        isArrived: Boolean,
    ): VoiceInstructionEvent? {
        require(routeId.isNotBlank())
        require(distanceToManoeuvreMeters >= 0.0)

        if (isArrived) {
            if (!arrivedRouteIds.add(routeId)) return null
            return VoiceInstructionEvent(
                routeId = routeId,
                step = step,
                cue = VoiceCue.ARRIVAL,
                thresholdMeters = 0.0,
            )
        }

        val thresholds = thresholdsFor(step.distanceMeters.toDouble())
        val cue = when {
            distanceToManoeuvreMeters <= thresholds.immediateMeters -> VoiceCue.IMMEDIATE
            distanceToManoeuvreMeters <= thresholds.advanceMeters -> VoiceCue.ADVANCE
            distanceToManoeuvreMeters <= thresholds.preparationMeters -> VoiceCue.PREPARATION
            else -> return null
        }
        val key = StepKey(routeId, step.index)
        val previousCue = highestCueByStep[key]
        if (previousCue != null && cue.ordinal <= previousCue.ordinal) return null
        highestCueByStep[key] = cue
        return VoiceInstructionEvent(
            routeId = routeId,
            step = step,
            cue = cue,
            thresholdMeters = when (cue) {
                VoiceCue.PREPARATION -> thresholds.preparationMeters
                VoiceCue.ADVANCE -> thresholds.advanceMeters
                VoiceCue.IMMEDIATE -> thresholds.immediateMeters
                VoiceCue.ARRIVAL -> 0.0
            },
        )
    }

    fun reset(routeId: String? = null) {
        if (routeId == null) {
            highestCueByStep.clear()
            arrivedRouteIds.clear()
        } else {
            highestCueByStep.keys.removeAll { it.routeId == routeId }
            arrivedRouteIds.remove(routeId)
        }
    }

    internal fun thresholdsFor(stepDistanceMeters: Double): VoiceThresholds =
        VoiceThresholds(
            preparationMeters = min(
                DEFAULT_PREPARATION_METERS,
                max(60.0, stepDistanceMeters * 0.75),
            ),
            advanceMeters = min(
                DEFAULT_ADVANCE_METERS,
                max(30.0, stepDistanceMeters * 0.40),
            ),
            immediateMeters = min(
                DEFAULT_IMMEDIATE_METERS,
                max(12.0, stepDistanceMeters * 0.15),
            ),
        )

    private data class StepKey(
        val routeId: String,
        val stepIndex: Int,
    )

    internal companion object {
        const val DEFAULT_PREPARATION_METERS = 500.0
        const val DEFAULT_ADVANCE_METERS = 150.0
        const val DEFAULT_IMMEDIATE_METERS = 40.0
    }
}

internal data class VoiceThresholds(
    val preparationMeters: Double,
    val advanceMeters: Double,
    val immediateMeters: Double,
)

