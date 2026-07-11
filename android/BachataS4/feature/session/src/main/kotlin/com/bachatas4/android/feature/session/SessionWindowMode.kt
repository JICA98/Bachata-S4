package com.bachatas4.android.feature.session

import android.content.pm.ActivityInfo

enum class SessionWindowMode(
    val orientation: Int,
    val hideSystemBars: Boolean,
) {
    Portrait(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, false),
    ImmersiveLandscape(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, true),
}
