package com.openconverter.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openconverter.app.BuildConfig
import com.openconverter.app.components.OpenConverterLogo
import com.openconverter.app.theme.SurfaceCard
import com.openconverter.app.theme.TextSecondary

private data class SettingsGroup(val title: String, val items: List<SettingsItem>)
private data class SettingsItem(val title: String, val subtitle: String? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val vm: SettingsViewModel = viewModel()
    val currentEkey by vm.qmcEkey.collectAsState()
    var ekeyInput by remember(currentEkey) { mutableStateOf(currentEkey) }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("设置") }) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                GroupHeader("QMC 加密密钥 (ekey)")
                GroupCard {
                    Column(modifier = Modifier.padding(14.dp)) {
                        OutlinedTextField(
                            value = ekeyInput,
                            onValueChange = { ekeyInput = it },
                            label = { Text("ekey (QQ Music 客户端 DB 提取)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "用于解密 QMC v2 加密音频 (mflac / mgg / bkc*)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(onClick = { vm.saveEkey(ekeyInput) }) { Text("保存") }
                            OutlinedButton(onClick = {
                                ekeyInput = ""
                                vm.saveEkey("")
                            }) { Text("清空") }
                        }
                    }
                }
            }
            item {
                GroupHeader("编码")
                GroupCard {
                    Column {
                        StaticRow("默认输出格式", "FLAC")
                        Divider()
                        StaticRow("默认比特率", "256 KBPS (MP3 / AAC)")
                    }
                }
            }
            item {
                GroupHeader("行为")
                GroupCard {
                    Column {
                        StaticRow("输出命名", "始终保留源文件名 (song.flac → song.mp3)")
                        Divider()
                        StaticRow("转换完成通知", "启用", valueColor = MaterialTheme.colorScheme.primary)
                        Divider()
                        StaticRow("主题模式", "DARK (锁定)", valueColor = TextSecondary)
                    }
                }
            }
            item {
                GroupHeader("诊断")
                GroupCard {
                    Column {
                        StaticRow("失败日志", "查看")
                        Divider()
                        StaticRow("FFmpeg 信息", "ffmpeg-kit 6.0-2.LTS", valueColor = TextSecondary)
                        Divider()
                        StaticRow("应用版本", "v${BuildConfig.VERSION_NAME}", valueColor = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondary,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun GroupCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
    ) { Column { content() } }
}

@Composable
private fun StaticRow(title: String, value: String, valueColor: androidx.compose.ui.graphics.Color = TextSecondary) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
}
