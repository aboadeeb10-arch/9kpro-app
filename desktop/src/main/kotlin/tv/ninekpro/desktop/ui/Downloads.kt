package tv.ninekpro.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.ninekpro.desktop.data.AsyncImage
import tv.ninekpro.desktop.data.DlStore

/** Downloads: movies / episodes saved on this PC and live recordings — play (offline), stop a running recording, delete. */
@Composable
fun DownloadsScreen() {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current
    val list by DlStore.list.collectAsState(); val prog by DlStore.progress.collectAsState()
    var delAsk by remember { mutableStateOf<String?>(null) }
    val fmt24 = repo.prefs.timeFormat == "24"
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackRow(T("downloads")); Spacer(Modifier.weight(1f))
            Text(T("space_used") + ": " + T("size_mb", (DlStore.usedBytes() / 1048576).toInt()), color = c.muted, fontSize = 12.sp)
            Spacer(Modifier.width(10.dp)); PBtn(T("downloads_folder"), Icons.Default.FolderOpen) { try { java.awt.Desktop.getDesktop().open(DlStore.dir) } catch (e: Throwable) {} }; Spacer(Modifier.width(16.dp))
        }
        Text(T("downloads_hint"), color = c.muted, fontSize = 12.5.sp, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))
        if (list.isEmpty()) Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) { Text(T("no_downloads"), color = c.muted, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.widthIn(max = 520.dp)) }
        else LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(list.sortedByDescending { it.created }, key = { it.id }) { e ->
                val p = prog[e.id]
                Row(Modifier.fillMaxWidth().glass(c, 10).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(120.dp, 68.dp).clip(RoundedCornerShape(8.dp)).background(c.card2)) { if (e.image.isNotEmpty()) AsyncImage(e.image, Modifier.fillMaxSize()) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = when {
                            e.error.isNotEmpty() -> T("download_failed") + " · " + e.error
                            e.done -> (if (e.kind == "rec") T("recordings") else T("downloaded_on")) + " · " + T("size_mb", (e.size / 1048576).toInt()) + " · " + java.text.SimpleDateFormat(if (fmt24) "d MMM HH:mm" else "d MMM h:mm a").format(java.util.Date(e.created))
                            e.kind == "rec" -> T("recording") + " " + fmtTime(((p ?: 0f) * -1000).toLong()) + " · " + T("size_mb", (e.size / 1048576).toInt())
                            else -> T("downloading") + " " + ((p ?: 0f) * 100).toInt() + "% · " + T("size_mb", (e.size / 1048576).toInt()) + (if (e.total > 0) " / " + T("size_mb", (e.total / 1048576).toInt()) else "")
                        }
                        Text(sub, color = if (e.error.isNotEmpty()) Color(0xFFFF6B7A) else c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!e.done && e.error.isEmpty() && e.kind != "rec") { Spacer(Modifier.height(6.dp)); LinearProgressIndicator(progress = { (p ?: 0f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(4.dp), color = c.accent, trackColor = c.line) }
                    }
                    Spacer(Modifier.width(10.dp))
                    if (e.done) PBtn(T("play_local"), Icons.Default.PlayArrow, primary = true) { nav.push(Screen.Play(DlStore.playUrl(e.id), e.title, live = false, image = e.image)) }
                    else if (e.kind == "rec" && e.error.isEmpty()) PBtn(T("stop_recording"), Icons.Default.Stop) { DlStore.stopRecording(e.id) }
                    Spacer(Modifier.width(6.dp)); PBtn(T("delete"), Icons.Default.Delete) { delAsk = e.id }
                }
            }
        }
    }
    val d = delAsk
    if (d != null) AlertDialog(onDismissRequest = { delAsk = null }, title = { Text(T("delete_q")) }, text = { Text(list.firstOrNull { it.id == d }?.title ?: "") },
        confirmButton = { TextButton({ DlStore.delete(d); delAsk = null }) { Text(T("delete")) } }, dismissButton = { TextButton({ delAsk = null }) { Text(T("cancel")) } })
}
