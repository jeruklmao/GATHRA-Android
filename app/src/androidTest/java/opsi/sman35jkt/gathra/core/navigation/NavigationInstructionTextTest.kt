package opsi.sman35jkt.gathra.core.navigation

import androidx.test.platform.app.InstrumentationRegistry
import opsi.sman35jkt.gathra.core.model.ManeuverModifier
import opsi.sman35jkt.gathra.core.model.ManeuverType
import opsi.sman35jkt.gathra.core.model.RouteManeuver
import opsi.sman35jkt.gathra.core.model.RouteStep
import opsi.sman35jkt.gathra.domain.navigation.VoiceCue
import opsi.sman35jkt.gathra.domain.navigation.VoiceInstructionEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationInstructionTextTest {
    @Test
    fun shortStepVoiceUsesItsAdaptiveDistance() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val event = VoiceInstructionEvent(
            routeId = "short-route",
            step = RouteStep(
                index = 1,
                instruction = "Provider text is not exposed",
                streetName = "Jalan Uji",
                distanceMeters = 75,
                durationSeconds = 12,
                maneuver = RouteManeuver(
                    type = ManeuverType.TURN,
                    modifier = ManeuverModifier.RIGHT,
                    bearingBefore = 0,
                    bearingAfter = 90,
                ),
                geometryStartIndex = 1,
                geometryEndIndex = 2,
            ),
            cue = VoiceCue.ADVANCE,
            thresholdMeters = 30.0,
        )

        assertEquals(
            "Dalam 30 meter, Belok kanan ke Jalan Uji.",
            context.navigationVoiceInstruction(event),
        )
    }
}
