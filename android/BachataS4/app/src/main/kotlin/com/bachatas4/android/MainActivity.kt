package com.bachatas4.android

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.bachatas4.android.designsystem.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BachataNavHost(startDestination = initialRouteForSoc(Build.SOC_MODEL))
                }
            }
        }
    }
}

internal fun initialRouteForSoc(soc: String): String =
    if (soc.equals("SM8650", ignoreCase = true) || soc.equals("SM8750", ignoreCase = true)) {
        BachataRoutes.Library
    } else {
        BachataRoutes.Setup
    }
