package com.bachatas4.android.feature.session

import android.content.Intent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.bachatas4.android.runtime.session.ManagedSession
import com.bachatas4.android.runtime.session.ManagedSessionState
import com.bachatas4.android.runtime.session.RuntimeSurface

@Composable
fun SessionScreen(
    gameId: String,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    LaunchedEffect(gameId) { viewModel.launch(gameId) }
    Column(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.weight(1f),
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
        Text(state.label(), modifier = Modifier.padding(12.dp))
        Button(
            modifier = Modifier.padding(12.dp),
            onClick = {
                context.startService(Intent(ManagedSession.ACTION_STOP).setPackage(context.packageName))
            },
        ) { Text("Stop") }
    }
}

private fun ManagedSessionState.label(): String = when (this) {
    ManagedSessionState.Idle -> "Idle"
    is ManagedSessionState.Preparing -> "Preparing: $stage"
    is ManagedSessionState.Running -> "Running: $gameId"
    is ManagedSessionState.Failed -> "Failed: $detail"
    is ManagedSessionState.Stopped -> "Stopped: ${exitCode ?: "unknown"}"
}
