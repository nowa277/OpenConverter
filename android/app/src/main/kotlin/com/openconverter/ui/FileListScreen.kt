package com.openconverter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openconverter.app.R

@Composable
fun FileListScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("OpenConverter v0.2.2")
        Text("M1 风险解锁 - NcmDecoder 14/14 sha256 通过")
        Button(
            onClick = { /* SAF picker in Task 3.3 */ },
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(stringResource(R.string.pick_files))
        }
    }
}