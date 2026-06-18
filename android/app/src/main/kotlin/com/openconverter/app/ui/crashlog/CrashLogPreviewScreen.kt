package com.openconverter.app.ui.crashlog

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.openconverter.app.crash.CrashLogStore
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashLogPreviewScreen(
    crashDir: File,
    fileName: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val body = remember(fileName) { CrashLogStore(crashDir).read(fileName) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(fileName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                },
                actions = {
                    TextButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, body)
                            putExtra(Intent.EXTRA_SUBJECT, "OpenConverter 崩溃日志: $fileName")
                        }
                        context.startActivity(Intent.createChooser(send, "分享崩溃日志"))
                    }) { Text("分享") }
                },
            )
        }
    ) { innerPadding ->
        Text(
            text = body,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
        )
    }
}
