package com.openconverter.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openconverter.app.R

@Composable
fun FileListScreen(onOpenSettings: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("OpenConverter v0.2.2")
        Text("M2 全部 4 个 decoder 字节级 TDD 通过")
        Button(
            onClick = { /* SAF picker in Task 3.3 */ },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(stringResource(R.string.pick_files))
        }
        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(stringResource(R.string.settings))
        }
    }
}
