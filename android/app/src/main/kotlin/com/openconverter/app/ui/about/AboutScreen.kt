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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.openconverter.app.update.UpdatePhase
import com.openconverter.app.update.UpdateViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(viewModel: UpdateViewModel) {
    val versionName = com.openconverter.app.BuildConfig.VERSION_NAME
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val busy = state.busy
    val checking = stringResource(R.string.about_checking)
    val upToDate = stringResource(R.string.about_up_to_date)
    val available = stringResource(R.string.about_available)
    val downloading = stringResource(R.string.about_downloading)
    val failed = stringResource(R.string.about_update_failed)
    val noPackage = stringResource(R.string.about_no_package)
    val needPermission = stringResource(R.string.about_need_permission)

    fun launchInstaller() {
        val apk = viewModel.state.value.pendingInstall ?: return
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            viewModel.installLaunched()
        } catch (_: android.content.ActivityNotFoundException) {
            viewModel.installFailed()
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()) launchInstaller()
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var permissionRequested by rememberSaveable(state.pendingInstall?.path) { mutableStateOf(false) }
    LaunchedEffect(lifecycle, state.pendingInstall) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (viewModel.state.value.pendingInstall != null) {
                if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                    if (!permissionRequested) {
                        permissionRequested = true
                        permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                    }
                } else launchInstaller()
            }
        }
    }
    val note = when (state.phase) {
        UpdatePhase.Idle -> ""
        UpdatePhase.Checking -> checking
        UpdatePhase.Current -> upToDate
        UpdatePhase.Available -> available.format(state.asset!!.version)
        UpdatePhase.Downloading -> downloading
        UpdatePhase.Ready -> needPermission
        UpdatePhase.NoPackage -> noPackage
        UpdatePhase.Failed -> failed
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
                    onClick = viewModel::check,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    height = 48.dp,
                )
                GreenCta(
                    text = stringResource(R.string.about_update),
                    onClick = {
                        if (state.pendingInstall != null) {
                            if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                                permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                            } else launchInstaller()
                        } else viewModel.update()
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
