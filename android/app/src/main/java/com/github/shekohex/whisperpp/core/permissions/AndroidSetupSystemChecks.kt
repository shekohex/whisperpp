package com.github.shekohex.whisperpp.core.permissions

import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationManagerCompat
import com.github.shekohex.whisperpp.domain.setup.SetupSystemChecks
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Enabled = listed in `InputMethodManager.enabledInputMethodList`; selected = the current
 * `Settings.Secure.DEFAULT_INPUT_METHOD` (§4.6).
 */
class AndroidSetupSystemChecks(context: Context) : SetupSystemChecks {
    private val appContext = context.applicationContext

    override fun isImeEnabled(): Boolean {
        val imm = appContext.getSystemService(InputMethodManager::class.java) ?: return false
        return imm.enabledInputMethodList.any { it.packageName == appContext.packageName }
    }

    override fun isImeSelected(): Boolean {
        val current = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val component = current?.let(ComponentName::unflattenFromString) ?: return false
        return component.packageName == appContext.packageName
    }

    override fun isMicGranted(): Boolean = MicPermission.isGranted(appContext)

    override fun areNotificationsGranted(): Boolean =
        NotificationManagerCompat.from(appContext).areNotificationsEnabled()

    override val changes: Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        val resolver = appContext.contentResolver
        resolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD), false, observer)
        resolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_INPUT_METHODS), false, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }
}
