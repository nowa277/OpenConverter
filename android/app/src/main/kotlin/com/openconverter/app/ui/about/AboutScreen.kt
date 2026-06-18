package com.openconverter.app.ui.about

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openconverter.app.BuildConfig
import com.openconverter.app.components.OpenConverterLogo
import com.openconverter.app.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen() {
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("关于") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            OpenConverterLogo(size = 120.dp)
            Spacer(Modifier.height(8.dp))
            Text("OpenConverter", style = MaterialTheme.typography.titleLarge)
            Text("v${BuildConfig.VERSION_NAME}", color = TextSecondary)
            Spacer(Modifier.height(24.dp))
            Text(
                "支持 11 种加密音频格式 → MP3 / FLAC / WAV / M4A / OGG 真转码",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("NCM · QMC0/3/FLAC/OGG · MFLAC/MGG/BKC · KGM/KGMA/VPR · KWM",
                style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            Text("github.com/nowa277/OpenConverter",
                style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}
