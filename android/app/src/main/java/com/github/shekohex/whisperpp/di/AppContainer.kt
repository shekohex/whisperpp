package com.github.shekohex.whisperpp.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.github.shekohex.whisperpp.analytics.AnalyticsRepository
import com.github.shekohex.whisperpp.analytics.analyticsDataStore
import com.github.shekohex.whisperpp.core.network.HttpClientFactory
import com.github.shekohex.whisperpp.core.network.RedactingLoggingInterceptor
import com.github.shekohex.whisperpp.data.SettingsRepository
import com.github.shekohex.whisperpp.dataStore
import com.github.shekohex.whisperpp.privacy.SecretsStore
import com.github.shekohex.whisperpp.privacy.SendPolicyRepository
import io.ktor.client.HttpClient
import okhttp3.OkHttpClient

/**
 * Manual DI: owns process-wide singletons. Created once by [com.github.shekohex.whisperpp.app.WhisperApp].
 */
class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext

    val settingsDataStore: DataStore<Preferences> = appContext.dataStore
    val analyticsDataStore: DataStore<Preferences> = appContext.analyticsDataStore

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(settingsDataStore) }
    val analyticsRepository: AnalyticsRepository by lazy { AnalyticsRepository(analyticsDataStore) }
    val secretsStore: SecretsStore by lazy { SecretsStore(appContext) }
    val sendPolicyRepository: SendPolicyRepository by lazy { SendPolicyRepository(settingsDataStore) }

    val httpLoggingInterceptor: RedactingLoggingInterceptor = RedactingLoggingInterceptor()
    val okHttpClient: OkHttpClient by lazy { HttpClientFactory.createOkHttpClient(httpLoggingInterceptor) }
    val updaterHttpClient: HttpClient by lazy { HttpClientFactory.createKtorClient(okHttpClient) }
}
