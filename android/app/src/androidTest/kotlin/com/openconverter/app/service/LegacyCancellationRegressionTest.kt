package com.openconverter.app.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openconverter.app.MainActivity
import com.openconverter.app.ui.components.FileState
import com.openconverter.app.ui.home.HomeViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith

/** This test also compiles against the original issue baseline. */
@RunWith(AndroidJUnit4::class)
class LegacyCancellationRegressionTest {
    @Test fun cancelling_an_active_conversion_releases_the_start_button() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("auto_sync_kugou", false).putBoolean("ncm_lyrics_enabled", false)
            .remove("output_folder_uri").commit()
        val authority = "com.openconverter.app.test.documents"
        context.contentResolver.call(Uri.parse("content://$authority"), "prepare", context.packageName, null)
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "root")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: HomeViewModel
            try {
                scenario.onActivity { activity ->
                    vm = ViewModelProvider(activity)[HomeViewModel::class.java]
                    vm.setOutputFolder(tree)
                    vm.setFiles(listOf(DocumentsContract.buildDocumentUriUsingTree(tree, "long.wav")))
                    vm.setTargetFormat("mp3")
                    vm.start(activity)
                }
                runBlocking { withTimeout(10_000) { vm.state.first { it.files.any { file -> file.state == FileState.Running } } } }
                scenario.onActivity { activity ->
                    val cancel = HomeViewModel::class.java.methods.single { it.name == "cancel" }
                    if (cancel.parameterCount == 0) cancel.invoke(vm) else cancel.invoke(vm, activity)
                }
                runBlocking { withTimeout(3_000) { vm.state.first { !it.running } } }
            } finally {
                context.stopService(Intent(context, ConversionService::class.java))
            }
        }
    }
}
