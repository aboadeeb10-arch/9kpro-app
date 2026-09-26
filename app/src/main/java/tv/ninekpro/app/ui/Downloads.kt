package tv.ninekpro.app.ui

import android.app.DownloadManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import tv.ninekpro.app.R
import tv.ninekpro.app.data.Downloads
import tv.ninekpro.app.data.DlStore
import tv.ninekpro.app.data.Kind

@Composable
fun DownloadsScreen() {
    val nav = LocalNav.current; val c = LocalColors.current; val act = LocalActivity.current
    data class U(val id: Long, val title: String, val done: Boolean, val failed: Boolean, val rec: Boolean, val running: Boolean, val pct: Int, val bytes: Long, val modified: Long, val uri: String, val legacy: Boolean, val error: String)
    fun load(): List<U> {
        val enc = DlStore.list(act).map { r -> U(r.id, r.title, r.status == DlStore.STATUS_DONE, r.status == DlStore.STATUS_FAILED, r.isRecording, r.status == DlStore.STATUS_RUNNING, r.pct, r.bytes, r.modified, r.uri, false, r.error) }
        val old = Downloads.list(act).map { r -> U(r.id, r.title, r.status == DownloadManager.STATUS_SUCCESSFUL, r.status == DownloadManager.STATUS_FAILED, false, r.status == DownloadManager.STATUS_RUNNING, r.pct, r.bytes, r.modified, r.localUri, true, "") }
        return (enc + old).sortedByDescending { it.modified }
    }
    var rows by remember { mutableStateOf(load()) }
    LaunchedEffect(Unit) { while (true) { rows = load(); delay(2000) } }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.downloads))
        if (rows.isEmpty()) Empty(stringResource(R.string.no_items))
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(rows, key = { (if (it.legacy) "o" else "e") + it.id }) { r ->
                var ask by remember { mutableStateOf(false) }
                val when_ = remember(r.modified) { if (r.modified > 0) java.text.SimpleDateFormat("EEE d MMM · HH:mm", java.util.Locale.getDefault()).format(java.util.Date(r.modified)) else "" }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.card).tvFocus(c.accent, 10).clickable(enabled = r.done) { nav.push(Screen.Play(r.uri, r.title, "dl:" + r.id, Kind.MOVIE)) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (r.rec && r.running) "●" else if (r.done) "✓" else if (r.failed) "✕" else r.pct.toString() + "%", color = if (r.rec && r.running) Color(0xFFE11D48) else if (r.done) Color(0xFF22C55E) else if (r.failed) Color(0xFFE11D48) else c.accent, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(46.dp))
                    Column(Modifier.weight(1f)) {
                        Text((if (r.rec) "REC · " else "") + r.title, color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        if (r.running && !r.rec) LinearProgressIndicator(progress = { r.pct / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), color = c.accent)
                        Text((if (r.rec && r.running) stringResource(R.string.recording) + "  ·  " else if (r.done) stringResource(R.string.downloaded_on) + " " + when_ + "  ·  " else if (r.failed) stringResource(R.string.download_failed) + (if (r.error.isNotEmpty()) " (" + r.error + ")" else "") + "  ·  " else stringResource(R.string.downloading) + "  ·  ") + String.format("%.0f MB", r.bytes / 1048576.0) + (if (!r.legacy) "  🔒" else ""), color = c.muted, fontSize = 12.sp, maxLines = 1)
                    }
                    if (r.rec && r.running) Pill("■ " + stringResource(R.string.stop_recording), true) { tv.ninekpro.app.DlService.stop(act, r.id); rows = load() }
                    else Pill(stringResource(R.string.remove), false) { ask = true }
                }
                if (ask) androidx.compose.material3.AlertDialog(onDismissRequest = { ask = false }, title = { Text(stringResource(R.string.remove) + "?") }, text = { Text(r.title) },
                    confirmButton = { BigButton(stringResource(R.string.remove)) { ask = false; if (r.legacy) Downloads.remove(act, r.id) else { if (r.running) tv.ninekpro.app.DlService.stop(act, r.id); DlStore.remove(act, r.id) }; rows = load() } }, dismissButton = { BigButton(stringResource(R.string.cancel), filled = false) { ask = false } })
            }
        }
    }
}
