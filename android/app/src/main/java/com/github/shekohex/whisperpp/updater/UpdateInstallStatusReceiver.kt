package com.github.shekohex.whisperpp.updater

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.app.appContainer
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class UpdateInstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val container = context.appContainer
        container.appScope.launch {
            try {
                if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                    container.updateInstaller.reconcile()
                } else {
                    container.updateInstaller.handleStatus(intent)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("UpdateInstaller", "Could not process installation status", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "update_installation"
        private const val NOTIFICATION_ID = 1002

        fun showConfirmation(context: Context, sessionId: Int, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) return
            val manager = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(NotificationChannel(
                    CHANNEL_ID, context.getString(R.string.update_install_channel_name), NotificationManager.IMPORTANCE_DEFAULT,
                ))
            }
            val action = PendingIntent.getActivity(
                context, sessionId, Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.update_confirm_install))
                .setContentText(context.getString(R.string.update_confirm_install_desc))
                .setContentIntent(action)
                .setAutoCancel(true)
                .build())
        }

        fun dismissConfirmation(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
