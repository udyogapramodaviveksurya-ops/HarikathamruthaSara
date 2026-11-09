package com.example.harikathamruthasara.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.harikathamruthasara.BuildConfig
import com.example.harikathamruthasara.HkApp
import com.example.harikathamruthasara.data.server
import com.example.harikathamruthasara.i18n.strings

/** "Improvements are ready" — shown while the server has corrected text or better chant the app hasn't taken yet. */
@Composable
fun UpdatesBanner(app: HkApp) {
    val st by app.source.state.collectAsStateWithLifecycle()
    val S = strings()
    if (!st.hasUpdates && !st.updating) return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(S.updatesTitle, fontWeight = FontWeight.Bold)
            Text(S.updatesBody(st.textUpdate, st.packUpdates), fontSize = 13.sp)
            if (st.updating) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                Text(S.updating, fontSize = 12.sp)
            } else {
                Button(onClick = { app.source.updateAll() }, modifier = Modifier.padding(top = 6.dp)) { Text("⬇ " + S.updateNow) }
            }
        }
    }
}

/** Settings: the server, automatic updates, check now. */
@Composable
fun ServerSettings(app: HkApp) {
    val st by app.source.state.collectAsStateWithLifecycle()
    val s by app.settings.flow.collectAsStateWithLifecycle()
    val S = strings()
    var url by remember { mutableStateOf(s.serverUrl.ifBlank { BuildConfig.SERVER_URL }) }
    SectionTitle(S.sectionServer)
    Text(S.serverInfo, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(
        value = url, onValueChange = { url = it }, singleLine = true, label = { Text(S.serverAddress) },
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            val u = url.trim().trimEnd('/')
            app.settings.update { it.copy(serverUrl = if (u == BuildConfig.SERVER_URL) "" else u) }
            app.source.check(force = true)
        }) { Text(S.checkNow) }
        Text(
            when {
                st.checking -> "…"
                st.error != null && st.revision == 0 -> S.serverUnreachable
                st.revision > 0 -> S.serverStatus(st.revision, !st.hasUpdates)
                else -> ""
            },
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
        )
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(S.autoUpdate, fontWeight = FontWeight.SemiBold)
            Text(S.autoUpdateSub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = s.autoUpdate, onCheckedChange = { v -> app.settings.update { it.copy(autoUpdate = v) }; if (v) app.source.check(force = true) })
    }
    UpdatesBanner(app)
    Text(app.settings.value.server, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
}

/** Report a problem at this padya/line to the server, where it is corrected for everyone. */
@Composable
fun ReportDialog(app: HkApp, sandhi: Int, padya: Int, line: Int?, onClose: () -> Unit) {
    val S = strings()
    var kind by remember { mutableStateOf("audio") }
    var note by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Boolean?>(null) }
    var sending by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(S.reportWhat) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${S.padya(padya).takeIf { padya > 0 } ?: S.refrain}" + (line?.let { " · ${S.stepLine(it)}" } ?: ""),
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kind == "audio", onClick = { kind = "audio" }, label = { Text(S.reportAudio) })
                    FilterChip(selected = kind == "text", onClick = { kind = "text" }, label = { Text(S.reportText) })
                }
                OutlinedTextField(value = note, onValueChange = { note = it }, placeholder = { Text(S.reportNoteHint) }, minLines = 2)
                result?.let { Text(if (it) S.reportSent else S.reportFailed, color = if (it) HkColors.good else HkColors.bad, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            if (result == true) TextButton(onClick = onClose) { Text(S.ok) }
            else Button(enabled = !sending, onClick = {
                sending = true
                app.source.report(sandhi, padya, line, kind, note) { ok -> sending = false; result = ok }
            }) { Text(S.send) }
        },
        dismissButton = { if (result != true) TextButton(onClick = onClose) { Text(S.cancel) } },
    )
}
