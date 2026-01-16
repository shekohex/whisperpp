package com.github.shekohex.whisperpp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.compose.runtime.*
import com.github.shekohex.whisperpp.ui.components.SplashScreen
import com.github.shekohex.whisperpp.ui.settings.SettingsNavigation
import com.github.shekohex.whisperpp.ui.theme.WhisperToInputTheme
import com.github.shekohex.whisperpp.updater.UpdateCheckWorker
import kotlinx.coroutines.delay

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

val SPEECH_TO_TEXT_BACKEND = stringPreferencesKey("speech-to-text-backend")
val ENDPOINT = stringPreferencesKey("endpoint")
val LANGUAGE_CODE = stringPreferencesKey("language-code")
val API_KEY = stringPreferencesKey("api-key")
val MODEL = stringPreferencesKey("model")
val AUTO_RECORDING_START = booleanPreferencesKey("is-auto-recording-start")
val AUTO_SWITCH_BACK = booleanPreferencesKey("auto-switch-back")
val AUTO_TRANSCRIBE_ON_PAUSE = booleanPreferencesKey("auto-transcribe-on-pause")
val CANCEL_CONFIRMATION = booleanPreferencesKey("cancel-confirmation")
val ADD_TRAILING_SPACE = booleanPreferencesKey("add-trailing-space")
val POSTPROCESSING = stringPreferencesKey("postprocessing")
val TIMEOUT = intPreferencesKey("timeout")
val PROMPT = stringPreferencesKey("prompt")
val USE_CONTEXT = booleanPreferencesKey("use-context")

val SMART_FIX_ENABLED = booleanPreferencesKey("smart-fix-enabled")
val SMART_FIX_BACKEND = stringPreferencesKey("smart-fix-backend")
val SMART_FIX_ENDPOINT = stringPreferencesKey("smart-fix-endpoint")
val SMART_FIX_API_KEY = stringPreferencesKey("smart-fix-api-key")
val SMART_FIX_MODEL = stringPreferencesKey("smart-fix-model")
val SMART_FIX_TEMPERATURE = floatPreferencesKey("smart-fix-temperature")
val SMART_FIX_PROMPT = stringPreferencesKey("smart-fix-prompt")

val HAPTIC_FEEDBACK_ENABLED = booleanPreferencesKey("haptic-feedback-enabled")
val SOUND_EFFECTS_ENABLED = booleanPreferencesKey("sound-effects-enabled")

val UPDATE_CHANNEL = stringPreferencesKey("update-channel")

private const val MICROPHONE_PERMISSION_REQUEST_CODE = 200
private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 201

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        checkPermissions()
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
                        SettingsNavigation(dataStore, showUpdate)
                    }
                }
            }
        }
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO to MICROPHONE_PERMISSION_REQUEST_CODE,
            Manifest.permission.POST_NOTIFICATIONS to NOTIFICATION_PERMISSION_REQUEST_CODE
        )
        for ((permission, code) in permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(permission), code)
            }
        }
    }
}
