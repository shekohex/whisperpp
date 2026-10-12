package com.github.shekohex.whisperpp

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import com.github.shekohex.whisperpp.app.appContainer
import com.github.shekohex.whisperpp.ui.navigation.AppNavHost
import com.github.shekohex.whisperpp.ui.navigation.Destination
import com.github.shekohex.whisperpp.ui.navigation.LaunchRequest
import com.github.shekohex.whisperpp.ui.theme.WhisperToInputTheme
import com.github.shekohex.whisperpp.updater.UpdateCheckWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Legacy settings deep-link extra; values are resolved by `SettingsDeepLinks.resolve`. */
const val EXTRA_SETTINGS_DESTINATION = "settings_destination"

class MainActivity : ComponentActivity() {

    /** Consumed once by the nav host; not restored after recreation so rotation doesn't re-navigate. */
    private var launchRequest by mutableStateOf<LaunchRequest?>(null)

    /** Chosen once per task and kept across recreation so the restored back stack matches the graph. */
    private var startDestination by mutableStateOf<Destination?>(null)

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val container = appContainer
        installSplashScreen().setKeepOnScreenCondition {
            !container.migrationsDone.value || startDestination == null
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        UpdateCheckWorker.schedule(this)

        if (savedInstanceState?.containsKey(STATE_ONBOARDING_START) == true) {
            startDestination = startDestinationFor(savedInstanceState.getBoolean(STATE_ONBOARDING_START))
        } else {
            lifecycleScope.launch {
                val completed = try {
                    container.settingsRepository.onboardingCompleted.first()
                } catch (e: IOException) {
                    Log.w(TAG, "Could not read onboarding state", e)
                    true
                }
                startDestination = startDestinationFor(!completed)
            }
        }

        if (savedInstanceState == null) {
            launchRequest = LaunchRequest.from(
                deepLink = intent?.getStringExtra(EXTRA_SETTINGS_DESTINATION),
                showUpdate = intent?.getBooleanExtra(UpdateCheckWorker.EXTRA_SHOW_UPDATE, false) ?: false,
            )
        }

        setContent {
            WhisperToInputTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val start = startDestination
                    if (start != null) {
                        AppNavHost(
                            widthSizeClass = calculateWindowSizeClass(this).widthSizeClass,
                            startDestination = start,
                            launchRequest = launchRequest,
                            onLaunchRequestHandled = { launchRequest = null },
                        )
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        startDestination?.let { outState.putBoolean(STATE_ONBOARDING_START, it == Destination.Onboarding) }
    }

    private fun startDestinationFor(onboarding: Boolean): Destination =
        if (onboarding) Destination.Onboarding else Destination.Home

    private companion object {
        const val TAG = "MainActivity"
        const val STATE_ONBOARDING_START = "onboarding_start"
    }
}
