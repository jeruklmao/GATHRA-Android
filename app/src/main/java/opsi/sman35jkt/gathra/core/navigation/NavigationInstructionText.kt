package opsi.sman35jkt.gathra.core.navigation

import android.content.Context
import androidx.annotation.StringRes
import opsi.sman35jkt.gathra.R
import opsi.sman35jkt.gathra.core.model.ManeuverModifier
import opsi.sman35jkt.gathra.core.model.ManeuverType
import opsi.sman35jkt.gathra.core.model.RouteStep
import opsi.sman35jkt.gathra.domain.navigation.VoiceCue
import opsi.sman35jkt.gathra.domain.navigation.VoiceInstructionEvent
import kotlin.math.roundToInt

fun Context.navigationInstruction(step: RouteStep): String {
    val action = getString(step.instructionResource())
    return if (
        step.streetName.isNotBlank() &&
        step.maneuver.type != ManeuverType.ARRIVE
    ) {
        getString(
            R.string.navigation_instruction_with_road,
            action,
            step.streetName,
        )
    } else {
        action
    }
}

fun Context.navigationVoiceInstruction(event: VoiceInstructionEvent): String {
    if (event.cue == VoiceCue.ARRIVAL) {
        return getString(R.string.navigation_voice_arrival)
    }
    val instruction = navigationInstruction(event.step)
    return when (event.cue) {
        VoiceCue.PREPARATION -> event.thresholdMeters.roundToInt().let { distance ->
            resources.getQuantityString(
                R.plurals.navigation_voice_prepare,
                distance,
                distance,
                instruction,
            )
        }
        VoiceCue.ADVANCE -> event.thresholdMeters.roundToInt().let { distance ->
            resources.getQuantityString(
                R.plurals.navigation_voice_advance,
                distance,
                distance,
                instruction,
            )
        }
        VoiceCue.IMMEDIATE -> getString(
            R.string.navigation_voice_immediate,
            instruction,
        )
        VoiceCue.ARRIVAL -> getString(R.string.navigation_voice_arrival)
    }
}

@StringRes
private fun RouteStep.instructionResource(): Int = when (maneuver.type) {
    ManeuverType.DEPART -> R.string.navigation_depart
    ManeuverType.CONTINUE -> R.string.navigation_continue_straight
    ManeuverType.TURN -> when (maneuver.modifier) {
        ManeuverModifier.LEFT,
        ManeuverModifier.SHARP_LEFT,
        ManeuverModifier.SLIGHT_LEFT,
        -> R.string.navigation_turn_left
        else -> R.string.navigation_turn_right
    }
    ManeuverType.SLIGHT_TURN -> when (maneuver.modifier) {
        ManeuverModifier.LEFT,
        ManeuverModifier.SHARP_LEFT,
        ManeuverModifier.SLIGHT_LEFT,
        -> R.string.navigation_slight_left
        else -> R.string.navigation_slight_right
    }
    ManeuverType.SHARP_TURN -> when (maneuver.modifier) {
        ManeuverModifier.LEFT,
        ManeuverModifier.SHARP_LEFT,
        ManeuverModifier.SLIGHT_LEFT,
        -> R.string.navigation_sharp_left
        else -> R.string.navigation_sharp_right
    }
    ManeuverType.U_TURN -> R.string.navigation_u_turn
    ManeuverType.ROUNDABOUT -> R.string.navigation_roundabout
    ManeuverType.EXIT_ROUNDABOUT -> R.string.navigation_exit_roundabout
    ManeuverType.MERGE -> R.string.navigation_merge
    ManeuverType.FORK -> when (maneuver.modifier) {
        ManeuverModifier.LEFT,
        ManeuverModifier.SHARP_LEFT,
        ManeuverModifier.SLIGHT_LEFT,
        -> R.string.navigation_fork_left
        else -> R.string.navigation_fork_right
    }
    ManeuverType.ARRIVE -> R.string.navigation_arrive_instruction
    ManeuverType.UNKNOWN -> R.string.navigation_no_instruction
}
