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
import com.github.shekohex.whisperpp.ui.settings.SettingsScreen
import com.github.shekohex.whisperpp.ui.settings.SettingsNavigation
import com.github.shekohex.whisperpp.ui.theme.WhisperToInputTheme
import com.github.shekohex.whisperpp.updater.UpdateCheckWorker
import kotlinx.coroutines.delay

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

val SPEECH_TO_TEXT_BACKEND = stringPreferencesKey("speech-to-text-backend")
val ACTIVE_STT_PROVIDER_ID = stringPreferencesKey("active-stt-provider-id")
val ACTIVE_STT_MODEL_ID = stringPreferencesKey("active-stt-model-id")
val ACTIVE_TEXT_PROVIDER_ID = stringPreferencesKey("active-text-provider-id")
val ACTIVE_TEXT_MODEL_ID = stringPreferencesKey("active-text-model-id")
val COMMAND_TEXT_PROVIDER_ID = stringPreferencesKey("command-text-provider-id")
val COMMAND_TEXT_MODEL_ID = stringPreferencesKey("command-text-model-id")
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

val ENHANCEMENT_PRESET_ID = stringPreferencesKey("enhancement-preset-id")
val COMMAND_PRESET_ID = stringPreferencesKey("command-preset-id")

val SMART_FIX_ENABLED = booleanPreferencesKey("smart-fix-enabled")
val SMART_FIX_BACKEND = stringPreferencesKey("smart-fix-backend")
val SMART_FIX_ENDPOINT = stringPreferencesKey("smart-fix-endpoint")
val SMART_FIX_API_KEY = stringPreferencesKey("smart-fix-api-key")
val SMART_FIX_MODEL = stringPreferencesKey("smart-fix-model")
val SMART_FIX_TEMPERATURE = floatPreferencesKey("smart-fix-temperature")
val SMART_FIX_PROMPT = stringPreferencesKey("smart-fix-prompt")

val HAPTIC_FEEDBACK_ENABLED = booleanPreferencesKey("haptic-feedback-enabled")
val SOUND_EFFECTS_ENABLED = booleanPreferencesKey("sound-effects-enabled")
val SECURE_FIELD_EXPLANATION_DONT_SHOW_AGAIN = booleanPreferencesKey("secure-field-explanation-dont-show-again")
val VERBOSE_NETWORK_LOGS_ENABLED = booleanPreferencesKey("verbose-network-logs-enabled")
val DISCLOSURE_SHOWN_DICTATION_AUDIO = booleanPreferencesKey("disclosure-shown-dictation-audio")
val DISCLOSURE_SHOWN_ENHANCEMENT_TEXT = booleanPreferencesKey("disclosure-shown-enhancement-text")
val DISCLOSURE_SHOWN_COMMAND_TEXT = booleanPreferencesKey("disclosure-shown-command-text")
val PER_APP_SEND_POLICY_JSON = stringPreferencesKey("per-app-send-policy-json")

val UPDATE_CHANNEL = stringPreferencesKey("update-channel")
const val EXTRA_SETTINGS_DESTINATION = "settings_destination"

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
                        val settingsDestination = intent?.getStringExtra(EXTRA_SETTINGS_DESTINATION)
                        val startRoute = resolveSettingsStartRoute(settingsDestination)
                        SettingsNavigation(dataStore, showUpdate = showUpdate, startRoute = startRoute)
                    }
                }
            }
        }
    }

    private fun resolveSettingsStartRoute(destination: String?): String {
        return SettingsScreen.resolveStartRoute(destination)
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
