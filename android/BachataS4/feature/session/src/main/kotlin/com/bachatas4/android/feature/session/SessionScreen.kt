package com.bachatas4.android.feature.session

import android.content.Intent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.bachatas4.android.runtime.session.ManagedSession
import com.bachatas4.android.runtime.session.ManagedSessionState
import com.bachatas4.android.runtime.session.RuntimeSurface
import com.bachatas4.android.feature.session.controller.FixedControllerOverlay
import com.bachatas4.android.feature.session.controller.TouchLayout
import com.bachatas4.android.feature.session.controller.TouchLayoutRepository
import com.bachatas4.android.data.RuntimeProfileStore
import com.bachatas4.android.runtime.settings.ProfileScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@Composable
fun SessionScreen(
    gameId: String,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val dependencies = remember { EntryPointAccessors.fromApplication(context.applicationContext, TouchLayoutDependencies::class.java) }
    var touchLayout by remember { mutableStateOf(TouchLayout()) }
    val state by viewModel.state.collectAsState()
    val frames by viewModel.frameTelemetry.collectAsState()
    val device by viewModel.deviceTelemetry.collectAsState()
    LaunchedEffect(gameId) {
        val global = dependencies.runtimeProfileStore().load(ProfileScope.Global)
        val game = dependencies.runtimeProfileStore().load(ProfileScope.Game(gameId))
        touchLayout = dependencies.touchLayoutRepository().load(game.touchLayoutId ?: global.touchLayoutId)
        viewModel.launch(gameId)
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { currentContext ->
                    SurfaceView(currentContext).also { view ->
                        view.holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) = Unit
                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                                if (width > 0 && height > 0) {
                                    ManagedSession.attachSurface(RuntimeSurface(holder.surface, width, height))
                                }
                            }
                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                ManagedSession.detachSurface(holder.surface)
                            }
                        })
                    }
                },
            )
            FixedControllerOverlay(layout = touchLayout, onSnapshot = ManagedSession::submitController)
            Text(
                text = "FPS %.1f (%.1f ms)  GPU %s  RAM %d/%d MB".format(
                    frames.fps, frames.frameTimeMs, device.gpuLoad, device.ramUsedMb, device.ramTotalMb,
                ),
                color = Color.White,
                modifier = Modifier.align(androidx.compose.ui.Alignment.TopStart)
                    .padding(8.dp).background(Color.Black.copy(alpha = 0.65f)).padding(6.dp),
            )
        }
        Text(state.label(), modifier = Modifier.padding(12.dp))
        Button(
            modifier = Modifier.padding(12.dp),
            onClick = {
                context.startService(
                    Intent(ManagedSession.ACTION_STOP).setClassName(context.packageName, ManagedSession.SERVICE_CLASS),
                )
            },
        ) { Text("Stop") }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TouchLayoutDependencies {
    fun runtimeProfileStore(): RuntimeProfileStore
    fun touchLayoutRepository(): TouchLayoutRepository
}

private fun ManagedSessionState.label(): String = when (this) {
    ManagedSessionState.Idle -> "Idle"
    is ManagedSessionState.Preparing -> "Preparing: $stage"
    is ManagedSessionState.Running -> "Running: $gameId"
    is ManagedSessionState.Failed -> "Failed: $detail"
    is ManagedSessionState.Stopped -> "Stopped: ${exitCode ?: "unknown"}"
}
