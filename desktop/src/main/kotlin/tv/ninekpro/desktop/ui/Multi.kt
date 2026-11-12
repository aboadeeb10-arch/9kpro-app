@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package tv.ninekpro.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import tv.ninekpro.desktop.data.AsyncImage
import tv.ninekpro.desktop.data.Item
import tv.ninekpro.desktop.data.Kind

/** Multi screen: 2 or 4 live channels at once. Click a tile = its sound; double-click = full screen; ✕ = empty the slot. Slots are remembered. */
@Composable
fun MultiScreen() {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current; val scope = rememberCoroutineScope()
    val pl = repo.activePlaylist
    val players = remember { List(4) { VlcPlayer() } }
    var layout by remember { mutableStateOf(if (repo.prefs.multiLayout == 2) 2 else 4) }
    val live = remember(pl?.id) { pl?.let { repo.cachedContent(it, Kind.LIVE)?.items } ?: emptyList() }
    var slots by remember { mutableStateOf<List<Item?>>(try { val a = JSONArray(repo.prefs.multiSlots); (0 until 4).map { i -> val k = a.optString(i, ""); if (k.isEmpty()) null else live.firstOrNull { it.key == k } } } catch (e: Exception) { listOf(null, null, null, null) }) }
    var audio by remember { mutableStateOf(0) }
    var picking by remember { mutableStateOf(-1) }
    fun save() { repo.prefs.multiSlots = JSONArray(slots.map { it?.key ?: "" }).toString(); repo.prefs.multiLayout = layout }
    fun applyAudio() { players.forEachIndexed { i, p -> p.mute(i != audio) } }
    fun play(i: Int, it: Item) { val p2 = pl ?: return; scope.launch { try { val u = repo.streamUrl(p2, it, true); players[i].title = it.name; players[i].play(u); applyAudio() } catch (e: Throwable) {} } }
    LaunchedEffect(Unit) { VlcPlayer.shared.stop(); repo.nowPlaying = null; slots.forEachIndexed { i, it -> if (it != null && i < layout) play(i, it) } }
    DisposableEffect(Unit) { onDispose { players.forEach { it.stop(); it.release() } } }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundBtn(Icons.Default.Close) { nav.pop() }; Spacer(Modifier.width(10.dp))
            Text(T("multi_screen"), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            listOf(2 to T("two_screens"), 4 to T("four_screens")).forEach { (n, t) -> Text(t, color = if (layout == n) Color.White else c.muted, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(8.dp)).background(if (layout == n) c.accent else c.card).clickable { layout = n; save(); if (n == 2) { for (i in 2 until 4) players[i].stop() } else slots.forEachIndexed { i, it -> if (i >= 2 && it != null) play(i, it) } }.padding(horizontal = 10.dp, vertical = 6.dp)) }
        }
        val rows = if (layout == 2) 1 else 2
        Column(Modifier.fillMaxSize().padding(4.dp)) {
            for (r in 0 until rows) Row(Modifier.weight(1f).fillMaxWidth()) {
                for (col in 0 until 2) { val i = r * 2 + col; val it = slots[i]; val p = players[i]
                    Box(Modifier.weight(1f).fillMaxHeight().padding(3.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0A0F1A)).border(2.dp, if (audio == i && it != null) c.accent else Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .combinedClickable(onDoubleClick = { if (it != null && pl != null) scope.launch { val u = repo.streamUrl(pl, it, repo.prefs.liveFormat == "ts"); repo.liveContext = live; nav.push(Screen.Play(u, it.name, it, live = true, image = it.icon)) } }) { if (it == null) picking = i else { audio = i; applyAudio() } }) {
                        if (it != null) {
                            VideoView(Modifier.fillMaxSize(), p)
                            Row(Modifier.align(Alignment.TopStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(it.icon, Modifier.size(28.dp, 18.dp), contentScale = ContentScale.Fit); Spacer(Modifier.width(6.dp))
                                Text(it.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                if (audio == i) Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = c.accent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp)); Icon(Icons.Default.Fullscreen, null, tint = Color.White, modifier = Modifier.size(16.dp).clickable { if (pl != null) scope.launch { val u = repo.streamUrl(pl, it, repo.prefs.liveFormat == "ts"); repo.liveContext = live; nav.push(Screen.Play(u, it.name, it, live = true, image = it.icon)) } })
                                Spacer(Modifier.width(6.dp)); Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp).clickable { p.stop(); slots = slots.toMutableList().also { l -> l[i] = null }; save() })
                            }
                            if (p.error) Text(T("playing_error"), color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(8.dp))
                        } else Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Add, null, tint = c.muted, modifier = Modifier.size(40.dp)); Text(T("add_channel"), color = c.muted, fontSize = 13.sp) }
                    }
                }
            }
        }
    }
    if (picking >= 0) {
        var q by remember { mutableStateOf("") }
        val res = remember(q) { val s = q.trim().lowercase(); (if (s.isEmpty()) live else live.filter { it.name.lowercase().contains(s) }).take(300) }
        AlertDialog(onDismissRequest = { picking = -1 }, title = { Text(T("pick_channel_for")) }, confirmButton = { TextButton({ picking = -1 }) { Text(T("cancel")) } }, text = {
            Column(Modifier.height(420.dp).width(420.dp)) {
                OutlinedTextField(q, { q = it }, singleLine = true, colors = fieldColors(c), placeholder = { Text(T("search")) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.fillMaxSize()) { items(res, key = { it.key }) { it ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).hoverGlow(c.accent, 8, 1f).clickable { val i = picking; picking = -1; slots = slots.toMutableList().also { l -> l[i] = it }; save(); play(i, it); audio = i; applyAudio() }.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(it.icon, Modifier.size(36.dp, 24.dp), contentScale = ContentScale.Fit); Spacer(Modifier.width(8.dp)); Text(it.name, color = c.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } } }
            } })
    }
}
