@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package tv.ninekpro.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
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
import org.json.JSONObject
import tv.ninekpro.desktop.data.AsyncImage
import tv.ninekpro.desktop.data.EpgEntry
import tv.ninekpro.desktop.data.Item
import tv.ninekpro.desktop.data.Kind
import tv.ninekpro.desktop.data.XtreamApi

/** Live: categories | channels | preview player with now/next + buttons. Double-click a channel (or the preview) = full screen without reloading. */
@Composable
fun LivePane() {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current; val win = LocalWindow.current
    val scope = rememberCoroutineScope(); val p = VlcPlayer.shared
    val pl = repo.activePlaylist
    var content by remember { mutableStateOf<XtreamApi.Content?>(null) }; var err by remember { mutableStateOf("") }
    val lv by repo.loadedVersion.collectAsState(); val pv by repo.profileVersion.collectAsState(); val ev by repo.epgVersion.collectAsState()
    var cat by remember { mutableStateOf(repo.liveUiCat) }
    var sel by remember { mutableStateOf(repo.liveUiSel) }
    var nn by remember { mutableStateOf<Pair<EpgEntry?, EpgEntry?>>(null to null) }
    val fmt24 = repo.prefs.timeFormat == "24"
    LaunchedEffect(pl?.id, lv) { if (pl != null) { try { content = repo.content(pl, Kind.LIVE) } catch (e: Throwable) { err = e.message ?: "error" } } }
    val cats = remember(content, pv) { val l = content?.categories ?: emptyList(); val locked = repo.locked(); val hid = repo.hidden()
        listOf(tv.ninekpro.desktop.data.Category("__all", Strings.get(Strings.lang(repo.prefs.language), "all"), Kind.LIVE), tv.ninekpro.desktop.data.Category("__fav", Strings.get(Strings.lang(repo.prefs.language), "favorites"), Kind.LIVE)) + repo.applyOrder("cats:LIVE", l.filter { !hid.contains("cat:" + it.id) && !(repo.prefs.parentalOn && (repo.isAdultName(it.name) || locked.contains("cat:" + it.id)) && repo.unlockedUntil < System.currentTimeMillis()) }) { "cat:" + it.id } }
    val channels = remember(content, cat, pv) { val all = content?.items ?: emptyList(); val hid = repo.hidden()
        val base = when (cat) { "__all" -> all; "__fav" -> { val f = repo.favorites().toSet(); all.filter { f.contains(it.key) } }; else -> all.filter { it.categoryId == cat } }.filter { !hid.contains(it.key) }
        val sorted = when (repo.prefs.liveSort) { "az" -> base.sortedBy { it.name.lowercase() }; "number" -> base.sortedBy { it.num }; else -> base }
        repo.applyOrder("live:$cat", sorted) { it.key } }
    LaunchedEffect(sel?.key, ev) { val s = sel; if (s != null && pl != null) nn = repo.nowNextLive(pl, s) }
    fun preview(it: Item) { sel = it; repo.liveUiSel = it; repo.liveContext = channels; if (pl == null) return
        scope.launch { try { val u = repo.streamUrl(pl, it, repo.prefs.liveFormat == "ts"); p.play(u); repo.setLastChannel(it.key); repo.nowPlaying = JSONObject().put("id", it.key).put("name", it.name).put("kind", "LIVE") } catch (e: Throwable) { win.toast(e.message ?: "error") } } }
    fun full() { val s = sel ?: return; repo.liveContext = channels; p.handoff = true; nav.push(Screen.Play(p.url, s.name, s, live = true, image = s.icon)) }
    // leaving the Live tab (Home / Movies / Settings…) stops the preview; going to full screen keeps it (handoff)
    DisposableEffect(Unit) { onDispose { if (!p.handoff) { p.stop(); repo.nowPlaying = null } } }

    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) {
        // categories
        LazyColumn(Modifier.weight(0.22f).fillMaxHeight().glass(c, 12).padding(6.dp)) {
            items(cats, key = { it.id }) { ct ->
                val s = ct.id == cat
                Text(ct.name, color = if (s) Color.White else c.text, fontSize = 13.5.sp, fontWeight = if (s) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(8.dp)).background(if (s) c.accent.copy(alpha = 0.35f) else Color.Transparent).hoverGlow(c.accent, 8, 1f).clickable { cat = ct.id; repo.liveUiCat = ct.id }.padding(horizontal = 10.dp, vertical = 8.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        // channels
        val ls = rememberLazyListState()
        LaunchedEffect(cat, channels.size) { val i = channels.indexOfFirst { it.key == sel?.key }; if (i >= 0) ls.scrollToItem(i) }
        Box(Modifier.weight(0.30f).fillMaxHeight().glass(c, 12).padding(6.dp)) {
            if (content == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { if (err.isNotEmpty()) Text(err, color = c.muted) else Loading(T("loading")) }
            LazyColumn(state = ls) {
                items(channels, key = { it.key }) { it ->
                    val s = it.key == sel?.key
                    val now = pl?.let { p2 -> repo.nowNext(p2, it).first }
                    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(8.dp)).background(if (s) c.accent.copy(alpha = 0.35f) else Color.Transparent).hoverGlow(c.accent, 8, 1f)
                        .combinedClickable(onDoubleClick = { if (sel?.key != it.key) preview(it); full() }, onLongClick = { repo.toggleFavorite(it.key) }) { preview(it) }.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (it.num > 0) it.num.toString() else "", color = c.muted, fontSize = 11.sp, modifier = Modifier.width(30.dp))
                        AsyncImage(it.icon, Modifier.size(40.dp, 26.dp), contentScale = ContentScale.Fit) { Icon(Icons.Default.LiveTv, null, tint = c.muted, modifier = Modifier.size(18.dp)) }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) { Text(it.name, color = c.text, fontSize = 13.sp, fontWeight = if (s) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (now != null) { Text(now.title, color = c.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val prog = ((System.currentTimeMillis() / 1000 - now.start).toFloat() / (now.stop - now.start).coerceAtLeast(1)).coerceIn(0f, 1f)
                                LinearProgressIndicator(progress = { prog }, modifier = Modifier.fillMaxWidth().height(2.dp).padding(top = 2.dp), color = c.accent, trackColor = c.line) } }
                        if (repo.isFavorite(it.key)) Icon(Icons.Default.Favorite, null, tint = Color(0xFFFF5C7A), modifier = Modifier.size(13.dp))
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        // preview
        Column(Modifier.weight(0.48f).fillMaxHeight()) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Color.Black).combinedClickable(onDoubleClick = { full() }) { if (sel != null && p.url.isEmpty()) preview(sel!!) else full() }) {
                if (sel != null) VideoView(Modifier.fillMaxSize()) else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(T("pick_channel"), color = c.muted) }
                if (p.error) Text(T("playing_error"), color = Color.White, modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(10.dp))
            }
            val s = sel
            if (s != null) {
                Spacer(Modifier.height(10.dp))
                Column(Modifier.fillMaxWidth().glass(c, 12).padding(12.dp)) {
                    Text(s.name, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val now = nn.first; val next = nn.second
                    if (now != null) Text(T("now") + "  " + hm(now.start, fmt24) + "  " + now.title, color = c.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) else Text(T("no_epg"), color = c.muted, fontSize = 12.sp)
                    if (next != null) Text(T("next") + "  " + hm(next.start, fmt24) + "  " + next.title, color = c.muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PBtn(T("open_full"), Icons.Default.Fullscreen, primary = true) { if (p.url.isEmpty()) preview(s); full() }
                        val fav = repo.isFavorite(s.key); PBtn(if (fav) T("remove_favorite") else T("add_favorite"), if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder) { repo.toggleFavorite(s.key) }
                        if (s.archive) PBtn(T("catchup"), Icons.Default.History) { nav.push(Screen.Guide) }
                        PBtn(T("guide"), Icons.Default.GridView) { nav.push(Screen.Guide) }
                    }
                }
                Spacer(Modifier.height(8.dp)); Text(T("shortcuts"), color = c.muted, fontSize = 11.sp, maxLines = 2)
            }
        }
    }
}
