package opsi.sman35jkt.gathra.data.navigation

import opsi.sman35jkt.gathra.domain.navigation.VoiceCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceInstructionPolicyTest {
    private val step = testRoute().steps[1]

    @Test
    fun `emits preparation advance and immediate cues once`() {
        val policy = VoiceInstructionPolicy()

        assertNull(policy.nextEvent("route", step, 600.0, isArrived = false))
        assertEquals(
            VoiceCue.PREPARATION,
            policy.nextEvent("route", step, 80.0, isArrived = false)?.cue,
        )
        assertNull(policy.nextEvent("route", step, 80.0, isArrived = false))
        assertEquals(
            VoiceCue.ADVANCE,
            policy.nextEvent("route", step, 30.0, isArrived = false)?.cue,
        )
        assertEquals(
            VoiceCue.IMMEDIATE,
            policy.nextEvent("route", step, 10.0, isArrived = false)?.cue,
        )
        assertNull(policy.nextEvent("route", step, 5.0, isArrived = false))
    }

    @Test
    fun `arrival is announced once per route`() {
        val policy = VoiceInstructionPolicy()
        val arrival = testRoute().steps.last()

        assertEquals(
            VoiceCue.ARRIVAL,
            policy.nextEvent("route", arrival, 0.0, isArrived = true)?.cue,
        )
        assertNull(policy.nextEvent("route", arrival, 0.0, isArrived = true))
    }

    @Test
    fun `jumping directly to immediate does not later emit lower priority cues`() {
        val policy = VoiceInstructionPolicy()

        assertEquals(
            VoiceCue.IMMEDIATE,
            policy.nextEvent("route", step, 5.0, isArrived = false)?.cue,
        )
        assertNull(policy.nextEvent("route", step, 50.0, isArrived = false))
    }
}

