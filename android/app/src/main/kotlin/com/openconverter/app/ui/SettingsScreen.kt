package com.openconverter.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openconverter.app.R
import com.openconverter.app.failures.FailureLog
import com.openconverter.app.ui.vm.SettingsViewModel

@Composable
fun SettingsScreen(
    vm: SettingsViewModel = viewModel(),
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    // Use plain collectAsState (not collectAsStateWithLifecycle) to avoid the
    // STOPPED-state IllegalStateException observed on vivo y78 / OriginOS when
    // SettingsViewModel._ekey is written by a coroutine after the screen
    // recedes into STOPPED.
    val ekey by vm.ekey.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(stringResource(R.string.ekey_label)) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.saveEkey(input); input = "" }) { Text("保存") }
            OutlinedButton(onClick = { vm.clearEkey() }, enabled = ekey.isNotBlank()) { Text("清空") }
        }
        if (ekey.isNotBlank()) {
            Text(
                "已保存 (长度 ${ekey.length})",
                color = MaterialTheme.colorScheme.primary
            )
        }
        HorizontalDivider()
        Text("诊断", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(
            onClick = {
                val log = FailureLog(context)
                context.startActivity(log.shareIntent())
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("分享失败日志")
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = onBack) { Text("返回") }
    }
}
