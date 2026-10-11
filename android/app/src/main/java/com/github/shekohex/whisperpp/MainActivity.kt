package com.github.shekohex.whisperpp

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.compose.runtime.*
import com.github.shekohex.whisperpp.app.appContainer
import com.github.shekohex.whisperpp.ui.components.SplashScreen
import com.github.shekohex.whisperpp.ui.settings.SettingsScreen
import com.github.shekohex.whisperpp.ui.settings.SettingsNavigation
import com.github.shekohex.whisperpp.ui.theme.WhisperToInputTheme
import com.github.shekohex.whisperpp.updater.UpdateCheckWorker
import kotlinx.coroutines.delay

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

const val EXTRA_SETTINGS_DESTINATION = "settings_destination"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        UpdateCheckWorker.schedule(this)
        
        setContent {
            WhisperToInputTheme {
                var showSplash by remember { mutableStateOf(true) }
                
                LaunchedEffect(Unit) {
                    delay(2500) // Show splash for 2.5 seconds
                    showSplash = false
                }
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showSplash) {
                        SplashScreen()
                    } else {
                        val showUpdate = intent?.getBooleanExtra(UpdateCheckWorker.EXTRA_SHOW_UPDATE, false) ?: false
                        val settingsDestination = intent?.getStringExtra(EXTRA_SETTINGS_DESTINATION)
                        val startRoute = resolveSettingsStartRoute(settingsDestination)
                        SettingsNavigation(appContainer.settingsDataStore, showUpdate = showUpdate, startRoute = startRoute)
                    }
                }
            }
        }
    }

    private fun resolveSettingsStartRoute(destination: String?): String {
        return SettingsScreen.resolveStartRoute(destination)
    }
}
