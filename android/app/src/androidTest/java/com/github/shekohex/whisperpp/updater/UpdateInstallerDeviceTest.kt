package com.github.shekohex.whisperpp.updater

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.github.shekohex.whisperpp.MainActivity
import com.github.shekohex.whisperpp.app.appContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.io.File

class UpdateInstallerDeviceTest {
    @Test
    fun stagesAndCommitsExactSelfUpdateFixture() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val apkPath = args.getString("updateApk")
        assumeNotNull(apkPath)
        val versionCode = requireNotNull(args.getString("updateVersionCode")).toInt()
        val versionName = requireNotNull(args.getString("updateVersionName"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(requireNotNull(apkPath))
        val platform = PlatformInfo("sha256:${ApkVerifier.computeSha256(file)}", "https://example.com/update.apk")
        val release = LatestRelease(versionName, versionCode, null, platforms = mapOf("universal" to platform))
        val update = UpdateCheckResult.Available(release, Abi.UNIVERSAL, platform)
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).apply {
            putExtra(UpdateCheckWorker.EXTRA_SHOW_UPDATE, true)
        }).use {
            context.appContainer.updateInstaller.install(file.absolutePath, update)
            val state = withTimeout(15_000) {
                context.appContainer.updateInstaller.installResult.first { it is InstallResult.Installing || it is InstallResult.Error }
            }
            assertTrue("Expected committed session, got $state", state is InstallResult.Installing)
        }
    }
}
