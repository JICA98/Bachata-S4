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
import androidx.lifecycle.lifecycleScope
import com.bachatas4.android.data.LegacyRuntimeSettingsMigration
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var legacyRuntimeSettingsMigration: LegacyRuntimeSettingsMigration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { legacyRuntimeSettingsMigration.migrate() }
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
