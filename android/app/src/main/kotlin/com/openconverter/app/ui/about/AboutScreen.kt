package com.openconverter.app.ui.about

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.openconverter.app.R
import com.openconverter.app.ui.components.GreenCta
import com.openconverter.app.ui.components.PillButton
import com.openconverter.app.update.ApkUpdate
import com.openconverter.app.update.ReleaseAsset
import com.openconverter.app.update.ReleaseCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(versionName: String) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var newer by remember { mutableStateOf<ReleaseAsset?>(null) }
    val checking = stringResource(R.string.about_checking)
    val upToDate = stringResource(R.string.about_up_to_date)
    val available = stringResource(R.string.about_available)
    val downloading = stringResource(R.string.about_downloading)
    val failed = stringResource(R.string.about_update_failed)
    val noPackage = stringResource(R.string.about_no_package)
    val needPermission = stringResource(R.string.about_need_permission)

    fun lookup() {
        if (busy) return
        scope.launch {
            busy = true
            note = checking
            val found = withContext(Dispatchers.IO) {
                runCatching { ApkUpdate.fetchNewest(abi) }
            }
            busy = false
            found.onFailure { note = failed }
            found.onSuccess { asset ->
                when {
                    asset == null -> {
                        newer = null
                        note = noPackage
                    }
                    ReleaseCheck.isNewer(asset.version, versionName) -> {
                        newer = asset
                        note = available.format(asset.version)
                    }
                    else -> {
                        newer = null
                        note = upToDate
                    }
                }
            }
        }
    }

    fun install(asset: ReleaseAsset) {
        if (busy) return
        scope.launch {
            busy = true
            note = downloading
            val file = File(context.cacheDir, "updates/openconverter-update.apk")
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    if (file.exists()) file.delete()
                    ApkUpdate.download(asset.apkUrl, file)
                    file
                }
            }
            busy = false
            saved.onFailure { note = failed }
            saved.onSuccess { apk ->
                if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                    note = needPermission
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}"),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    return@onSuccess
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
                context.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_about), style = MaterialTheme.typography.headlineSmall) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(12.dp))
            Image(
                painter = painterResource(if (dark) R.drawable.brand_wordmark else R.drawable.brand_wordmark_light),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_version) + " " + versionName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PillButton(
                    text = stringResource(R.string.about_check),
                    onClick = { lookup() },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    height = 48.dp,
                )
                GreenCta(
                    text = stringResource(R.string.about_update),
                    onClick = {
                        val known = newer
                        if (known != null) install(known)
                        else scope.launch {
                            if (busy) return@launch
                            busy = true
                            note = checking
                            val found = withContext(Dispatchers.IO) {
                                runCatching { ApkUpdate.fetchNewest(abi) }
                            }
                            busy = false
                            found.onFailure { note = failed }
                            found.onSuccess { asset ->
                                when {
                                    asset == null -> note = noPackage
                                    ReleaseCheck.isNewer(asset.version, versionName) -> {
                                        newer = asset
                                        install(asset)
                                    }
                                    else -> note = upToDate
                                }
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    height = 48.dp,
                )
            }
            if (note.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.settings_about),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
