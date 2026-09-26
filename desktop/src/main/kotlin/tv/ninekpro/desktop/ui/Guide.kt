package tv.ninekpro.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import tv.ninekpro.desktop.data.AsyncImage
import tv.ninekpro.desktop.data.Kind

/** Timeline guide: channels down, time across (6 h window from now-1h), 1 px = 1/6 min. Click a past programme = catch-up, current = play. */
@Composable
fun GuideScreen() {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current; val scope = rememberCoroutineScope()
    val pl = repo.activePlaylist ?: return
    val ev by repo.epgVersion.collectAsState()
    val fmt24 = repo.prefs.timeFormat == "24"
    val live = repo.cachedContent(pl, Kind.LIVE)?.items ?: emptyList()
    val cats = repo.cachedContent(pl, Kind.LIVE)?.categories ?: emptyList()
    var cat by remember { mutableStateOf(repo.liveUiCat) }
    val rows = remember(cat, live.size) { (if (cat == "__all") live else if (cat == "__fav") { val f = repo.favorites().toSet(); live.filter { f.contains(it.key) } } else live.filter { it.categoryId == cat }).filter { it.epgId.isNotEmpty() }.take(400) }
    val now = System.currentTimeMillis() / 1000
    val start = (now - 3600) / 1800 * 1800; val hours = 8
    val pxPerMin = 3f
    val hs = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        BackRow(T("guide"))
        Row(Modifier.padding(horizontal = 12.dp).horizontalScroll(rememberScrollState())) {
            (listOf("__all" to T("all"), "__fav" to T("favorites")) + cats.map { it.id to it.name }).forEach { (id, n) -> Text(n, color = if (id == cat) Color.White else c.text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(8.dp)).background(if (id == cat) c.accent else c.card).clickable { cat = id }.padding(horizontal = 10.dp, vertical = 6.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(start = 12.dp)) {
            Spacer(Modifier.width(190.dp))
            Row(Modifier.horizontalScroll(hs)) { for (h in 0 until hours * 2) { Text(hm(start + h * 1800L, fmt24), color = c.muted, fontSize = 11.sp, modifier = Modifier.width((30 * pxPerMin).dp)) } }
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            items(rows, key = { it.key }) { ch ->
                Row(Modifier.fillMaxWidth().height(48.dp).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.width(186.dp).fillMaxHeight().glass(c, 8).hoverGlow(c.accent, 8, 1f).clickable { scope.launch { val u = repo.streamUrl(pl, ch, repo.prefs.liveFormat == "ts"); repo.liveContext = rows; nav.push(Screen.Play(u, ch.name, ch, live = true, image = ch.icon)) } }.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(ch.icon, Modifier.size(36.dp, 24.dp), contentScale = ContentScale.Fit); Spacer(Modifier.width(6.dp)); Text(ch.name, color = c.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Spacer(Modifier.width(4.dp))
                    Row(Modifier.horizontalScroll(hs).fillMaxHeight()) {
                        val list = repo.epgFor(pl, ch).filter { it.stop > start && it.start < start + hours * 3600 }
                        var cursor = start
                        for (e in list) {
                            val s = maxOf(e.start, start); if (s > cursor) Spacer(Modifier.width(((s - cursor) / 60f * pxPerMin).dp))
                            val w = ((minOf(e.stop, start + hours * 3600) - s) / 60f * pxPerMin).coerceAtLeast(2f)
                            val cur = e.start <= now && e.stop > now; val past = e.stop <= now
                            Box(Modifier.width(w.dp).fillMaxHeight().padding(end = 1.dp).clip(RoundedCornerShape(6.dp)).background(if (cur) c.accent.copy(alpha = 0.45f) else if (past && ch.archive) c.card else c.card2).hoverGlow(c.accent, 6, 1f)
                                .clickable(enabled = cur || (past && ch.archive)) { scope.launch { repo.liveContext = rows; val u = if (cur) repo.streamUrl(pl, ch, repo.prefs.liveFormat == "ts") else repo.catchupUrl(pl, ch, e); nav.push(Screen.Play(u, ch.name + (if (cur) "" else " · " + e.title), ch, live = cur, image = ch.icon)) } }.padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
                                Column { Text(e.title, color = c.text, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(hm(e.start, fmt24), color = c.muted, fontSize = 10.sp) } }
                            cursor = minOf(e.stop, start + hours * 3600)
                        }
                    }
                }
            }
        }
    }
}
