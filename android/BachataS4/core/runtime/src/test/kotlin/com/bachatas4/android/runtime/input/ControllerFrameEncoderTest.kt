package com.bachatas4.android.runtime.input

import com.bachatas4.android.runtime.session.ManagedSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControllerFrameEncoderTest {
    @Test fun managedSessionRoutesOnlyToAttachedSink() {
        val received = mutableListOf<ControllerSnapshot>()
        val sink: (ControllerSnapshot) -> Unit = received::add
        ManagedSession.attachControllerSink(sink)

        ManagedSession.submitController(ControllerSnapshot.Neutral)
        ManagedSession.detachControllerSink(sink)
        ManagedSession.submitController(ControllerSnapshot.normalized(buttons = Ps4Button.CROSS))

        assertEquals(listOf(ControllerSnapshot.Neutral), received)
    }

    @Test fun encodesNormalizedSnapshotWithMonotonicSequence() {
        val encoder = ControllerFrameEncoder()
        val snapshot = ControllerSnapshot.normalized(
            buttons = Ps4Button.CROSS or Ps4Button.L1,
            leftX = -2f,
            leftY = 0.079f,
            rightX = 2f,
            rightY = -0.5f,
            leftTrigger = -1f,
            rightTrigger = 0.5f,
            touchDown = true,
            touchX = 1f,
            touchY = 1f,
        )

        assertEquals(
            "BACHATA/1 INPUT seq=1 buttons=17408 lx=0 ly=128 rx=255 ry=64 l2=0 r2=128 " +
                "touch=1 tx=1919 ty=1079\n",
            encoder.encode(snapshot)?.decodeToString(),
        )
        assertNull(encoder.encode(snapshot))
        assertEquals(
            "BACHATA/1 INPUT seq=2 buttons=0 lx=128 ly=128 rx=128 ry=128 l2=0 r2=0 " +
                "touch=0 tx=0 ty=0\n",
            encoder.encode(ControllerSnapshot.Neutral)?.decodeToString(),
        )
    }

    @Test fun appliesStickDeadzoneButNotTriggerDeadzone() {
        val snapshot = ControllerSnapshot.normalized(
            leftX = -0.08f,
            leftY = 0.081f,
            leftTrigger = 0.04f,
        )

        assertEquals(-0.08f, snapshot.leftX)
        assertEquals(0.081f, snapshot.leftY)
        assertEquals(0.04f, snapshot.leftTrigger)
    }
}
