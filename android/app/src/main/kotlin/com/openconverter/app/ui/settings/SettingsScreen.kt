package com.openconverter.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openconverter.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    themeMode: String,
    languageMode: String,
    onThemeChanged: (String) -> Unit,
    onLanguageChanged: (String) -> Unit
) {
    val s by viewModel.state.collectAsState()
    val ctx = LocalContext.current
    val keySourcePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importKggKeys(it.toString()) }
    }
    var showRootGuide by remember { mutableStateOf(false) }

    fun openUrl(url: String) {
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        painterResource(R.drawable.ic_logo),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(96.dp),
                    )
                    Text("OpenConverter", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Version ${s.versionName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.settings_kgg_keys_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.settings_kgg_keys_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.settings_kgg_keys_total, s.kggKeyCount),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        s.kggLastResult?.let { result ->
                            Text(
                                stringResource(
                                    R.string.settings_kgg_keys_result,
                                    result.added,
                                    result.updated,
                                    result.total,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        s.kggImportError?.let { error ->
                            Text(
                                stringResource(R.string.settings_kgg_keys_error, error),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Button(
                                enabled = !s.kggImporting,
                                onClick = {
                                    keySourcePicker.launch(
                                        arrayOf("application/octet-stream", "text/plain", "*/*"),
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    stringResource(
                                        if (s.kggImporting) R.string.settings_kgg_keys_importing
                                        else R.string.settings_kgg_keys_import,
                                    ),
                                )
                            }
                            Text(
                                text = stringResource(R.string.settings_kgg_root_guide_btn),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { showRootGuide = true }.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
            item {
                Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        var showThemeMenu by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
                            Box {
                                val themeText = when (themeMode) {
                                    "dark" -> stringResource(R.string.theme_dark)
                                    "light" -> stringResource(R.string.theme_light)
                                    else -> stringResource(R.string.theme_system)
                                }
                                Text(
                                    text = "$themeText  ▼",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { showThemeMenu = true }
                                )
                                DropdownMenu(
                                    expanded = showThemeMenu,
                                    onDismissRequest = { showThemeMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.theme_system)) },
                                        onClick = {
                                            onThemeChanged("system")
                                            showThemeMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.theme_dark)) },
                                        onClick = {
                                            onThemeChanged("dark")
                                            showThemeMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.theme_light)) },
                                        onClick = {
                                            onThemeChanged("light")
                                            showThemeMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        var showLangMenu by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.bodyLarge)
                            Box {
                                val langText = when (languageMode) {
                                    "zh" -> stringResource(R.string.lang_zh)
                                    "en" -> stringResource(R.string.lang_en)
                                    else -> stringResource(R.string.lang_system)
                                }
                                Text(
                                    text = "$langText  ▼",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { showLangMenu = true }
                                )
                                DropdownMenu(
                                    expanded = showLangMenu,
                                    onDismissRequest = { showLangMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.lang_system)) },
                                        onClick = {
                                            onLanguageChanged("system")
                                            showLangMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.lang_zh)) },
                                        onClick = {
                                            onLanguageChanged("zh")
                                            showLangMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.lang_en)) },
                                        onClick = {
                                            onLanguageChanged("en")
                                            showLangMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text(stringResource(R.string.settings_project), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { openUrl(s.githubUrl) }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_source_code), style = MaterialTheme.typography.bodyLarge)
                                Text(s.githubUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { openUrl(s.issuesUrl) }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_report_issue), style = MaterialTheme.typography.bodyLarge)
                                Text(s.issuesUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                Text(stringResource(R.string.settings_about_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.settings_about),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }

    if (showRootGuide) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRootGuide = false },
            title = { Text(stringResource(R.string.settings_kgg_root_guide_title)) },
            text = { Text(stringResource(R.string.settings_kgg_root_guide_content)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showRootGuide = false }) {
                    Text(stringResource(R.string.settings_kgg_root_guide_ok))
                }
            }
        )
    }
}
