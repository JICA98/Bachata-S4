package com.bachatas4.android.runtime.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerBindingResolverTest {
    private val key = ControllerDeviceKey("stable", 1, 2, "Pad")
    private val resolver = ControllerBindingResolver()

    @Test
    fun stableIdentityIgnoresTransientAndroidDeviceId() {
        val profile = ControllerProfile.standard(key)
        assertEquals(mapOf(0 to 99), resolver.assignSlots(listOf(profile), listOf(ConnectedController(99, key))))
    }

    @Test
    fun appliesDeadZoneInversionTriggersAndDisconnectNeutralization() {
        val axis = PhysicalBinding(PhysicalBindingKind.AXIS, 0)
        val trigger = PhysicalBinding(PhysicalBindingKind.AXIS, 17)
        val profile = ControllerProfile(
            device = key,
            bindings = mapOf("left_x" to axis, "left_trigger" to trigger),
            deadZone = 0.1f,
            invertAxes = setOf("left_x"),
            triggerThreshold = 0.4f,
        )
        val snapshot = resolver.snapshot(profile, mapOf(axis to 0.5f, trigger to 0.6f))
        assertEquals(-0.5f, snapshot.leftX)
        assertEquals(0.6f, snapshot.leftTrigger)
        assertTrue(snapshot.buttons and Ps4Button.L2 != 0L)
        assertEquals(ControllerSnapshot.Neutral, resolver.snapshotOrNeutral(profile, null, emptyMap()))
    }

    @Test
    fun limitsProfilesToFourSlots() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            resolver.assignSlots(List(5) { ControllerProfile() }, emptyList())
        }
    }
}
