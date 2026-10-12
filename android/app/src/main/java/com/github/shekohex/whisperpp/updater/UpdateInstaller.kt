package com.github.shekohex.whisperpp.updater

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

sealed class InstallResult {
    data object Success : InstallResult()
    /** Installing needs the "install unknown apps" permission; the user must grant it in Settings. */
    data object PermissionRequired : InstallResult()
    data class Error(val message: String) : InstallResult()
}

class UpdateInstaller(private val context: Context) {

    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /** Opens the "install unknown apps" page for this app; false if no activity could handle it. */
    fun openInstallPermissionSettings(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    fun install(apkPath: String): InstallResult {
        val file = File(apkPath)
        if (!file.isFile) {
            return InstallResult.Error("The downloaded update file is missing. Please download it again.")
        }

        if (!canInstallPackages()) {
            return InstallResult.PermissionRequired
        }

        return try {
            val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } else {
                Uri.fromFile(file)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(intent)
            InstallResult.Success
        } catch (e: Exception) {
            InstallResult.Error("Could not start the installer: ${e.message ?: "unknown error"}")
        }
    }
}
