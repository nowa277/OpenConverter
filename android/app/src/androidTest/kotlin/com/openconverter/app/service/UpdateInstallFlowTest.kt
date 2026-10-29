package com.openconverter.app.service

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.content.FileProvider
import com.openconverter.app.update.ApkUpdate
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateInstallFlowTest {
    @Test fun downloaded_debug_apk_is_readable_by_system_installer() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val installed = File(context.applicationInfo.sourceDir)
        val destination = File(context.cacheDir, "updates/installer-test.apk")
        val connection: HttpURLConnection = object : HttpURLConnection(URL("https://example.test/debug.apk")) {
            override fun connect() {}
            override fun disconnect() {}
            override fun usingProxy() = false
            override fun getResponseCode() = 200
            override fun getInputStream(): java.io.InputStream = installed.inputStream()
        }
        ApkUpdate.download(connection.url.toString(), destination) { connection }
        assertEquals(installed.length(), destination.length())
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", destination)
        assertTrue(context.contentResolver.openInputStream(uri)!!.use { it.read() } >= 0)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        instrumentation.waitForIdleSync()
        val dump = instrumentation.uiAutomation.executeShellCommand("dumpsys activity activities").use { fd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().readText()
        }
        assertTrue(dump.contains("com.android.packageinstaller"))
        instrumentation.uiAutomation.executeShellCommand("am force-stop com.android.packageinstaller").close()
    }
}
