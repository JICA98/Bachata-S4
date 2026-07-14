package com.bachatas4.android.runtime.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.bachatas4.android.runtime.session.ManagedSession
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

data class NavControllerEvent(
    val control: String,
    val pressed: Boolean,
)

object GamepadInputManager {
    private val mapper = ControllerMapper()
    private val resolver = ControllerBindingResolver()
    private val profile = ControllerProfile.standard()
    private val perDeviceState = HashMap<Int, MutableMap<PhysicalBinding, Float>>()
    private val captureSink = AtomicReference<((PhysicalBinding) -> Unit)?>(null)
    private val navSink = AtomicReference<((NavControllerEvent) -> Boolean)?>(null)
    private val AXIS_CODES = intArrayOf(
        MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
        MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
        MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER,
        MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
    )

    @Volatile
    var hasPhysicalController: Boolean = false
        private set

    fun registerCaptureListener(listener: (PhysicalBinding) -> Unit) {
        captureSink.set(listener)
    }

    fun unregisterCaptureListener() {
        captureSink.set(null)
    }

    fun registerNavListener(listener: (NavControllerEvent) -> Boolean) {
        navSink.set(listener)
    }

    fun unregisterNavListener() {
        navSink.set(null)
    }

    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val deviceId = event.deviceId
        if (!isGameController(deviceId)) return false

        val capture = captureSink.get()
        if (capture != null) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val device = InputDevice.getDevice(deviceId)
                val key = device?.let { ControllerDeviceKey(it.descriptor, it.vendorId, it.productId, it.name) }
                    ?: ControllerDeviceKey("", 0, 0, "")
                capture(mapper.physicalButton(deviceId, key, event.keyCode, true).binding)
            }
            return true
        }

        if (event.repeatCount > 0) return true

        val controlEvent = mapper.button(
            deviceId,
            event.eventTime,
            event.keyCode,
            event.action == KeyEvent.ACTION_DOWN,
        ) ?: return false

        if (event.action == KeyEvent.ACTION_DOWN) {
            val nav = navSink.get()
            if (nav != null && nav(NavControllerEvent(controlEvent.control, true))) return true
        }

        val binding = profile.bindings[controlEvent.control] ?: return false
        val state = perDeviceState.getOrPut(deviceId) { HashMap() }
        state[binding] = controlEvent.value
        submitFromDevice(deviceId, state)
        return true
    }

    fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val deviceId = event.deviceId
        if (!isGameController(deviceId)) return false

        val capture = captureSink.get()
        if (capture != null) {
            val device = InputDevice.getDevice(deviceId)
            val key = device?.let { ControllerDeviceKey(it.descriptor, it.vendorId, it.productId, it.name) }
                ?: ControllerDeviceKey("", 0, 0, "")
            for (axis in AXIS_CODES) {
                val raw = event.getAxisValue(axis)
                if (abs(raw) >= AXIS_CAPTURE_THRESHOLD) {
                    capture(mapper.physicalAxis(deviceId, key, axis, raw).binding)
                    return true
                }
            }
            return false
        }

        val nav = navSink.get()
        if (nav != null) {
            for (axis in AXIS_CODES) {
                val raw = event.getAxisValue(axis)
                val controlEvent = mapper.axis(deviceId, event.eventTime, axis, raw) ?: continue
                val navEvent = NavControllerEvent(controlEvent.control, abs(controlEvent.value) >= AXIS_CAPTURE_THRESHOLD)
                if (nav(navEvent)) return true
            }
            return false
        }

        val state = perDeviceState.getOrPut(deviceId) { HashMap() }
        var handled = false

        for (axis in AXIS_CODES) {
            val raw = event.getAxisValue(axis)
            val controlEvent = mapper.axis(deviceId, event.eventTime, axis, raw) ?: continue
            val binding = profile.bindings[controlEvent.control] ?: continue
            state[binding] = controlEvent.value
            handled = true
        }

        if (handled) {
            submitFromDevice(deviceId, state)
        }
        return handled
    }

    fun onSessionStart() {
        perDeviceState.clear()
        hasPhysicalController = false
    }

    fun onSessionEnd() {
        perDeviceState.clear()
        hasPhysicalController = false
    }

    private fun submitFromDevice(deviceId: Int, state: Map<PhysicalBinding, Float>) {
        hasPhysicalController = perDeviceState.isNotEmpty()
        val snapshot = resolver.snapshot(profile, state)
        ManagedSession.submitController(snapshot)
    }

    private fun isGameController(deviceId: Int): Boolean {
        val device = InputDevice.getDevice(deviceId) ?: return false
        if (device.isVirtual) return false
        val name = device.name ?: ""
        if (name.contains("uinput-fpc") || name.contains("goodix_fp") || name.startsWith("uinput-")) return false
        val sources = device.sources
        return (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
    }

    private const val AXIS_CAPTURE_THRESHOLD = 0.5f
}
