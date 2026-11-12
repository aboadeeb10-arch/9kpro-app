package tv.ninekpro.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tv.ninekpro.desktop.data.AsyncImage
import tv.ninekpro.desktop.data.Kind

/**
 * Full-screen guide: channels down, a whole day across (3 px = 1 min), day chips for today and the 7 days before (catch-up).
 * Click a past programme = catch-up (only on channels that have an archive and inside its archive window), the current one = play live.
 */
@Composable
fun GuideScreen() {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current; val scope = rememberCoroutineScope()
    val pl = repo.activePlaylist ?: return
    val ev by repo.epgVersion.collectAsState()
    val fmt24 = repo.prefs.timeFormat == "24"
    val live = repo.cachedContent(pl, Kind.LIVE)?.items ?: emptyList()
    val cats = repo.cachedContent(pl, Kind.LIVE)?.categories ?: emptyList()
    var cat by remember { mutableStateOf(repo.liveUiCat) }
    var q by remember { mutableStateOf("") }
    var dayOff by remember { mutableStateOf(0) }   // 0 = today, -1 = yesterday … -7
    val rows = remember(cat, live.size, q) { val s = q.trim().lowercase()
        (if (cat == "__all") live else if (cat == "__fav") { val f = repo.favorites().toSet(); live.filter { f.contains(it.key) } } else live.filter { it.categoryId == cat })
            .filter { it.epgId.isNotEmpty() && (s.isEmpty() || it.name.lowercase().contains(s)) }.take(400) }
    val now = System.currentTimeMillis() / 1000
    val dayStart = remember(dayOff) { val cal = java.util.Calendar.getInstance(); cal.add(java.util.Calendar.DATE, dayOff); cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0); cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0); cal.timeInMillis / 1000 }
    val start = dayStart; val hours = 24
    val pxPerMin = 3f
    val hs = rememberScrollState()
    val density = LocalDensity.current.density
    // today: open on "one hour ago"; other days: at 18:00 (prime time)
    LaunchedEffect(dayOff) { val at = if (dayOff == 0) (now - 3600 - start).coerceAtLeast(0) else 18 * 3600L; hs.scrollTo((at / 60f * pxPerMin * density).toInt()) }
    val dayNames = remember { (0 downTo -7).map { off -> val cal = java.util.Calendar.getInstance(); cal.add(java.util.Calendar.DATE, off); off to (if (off == 0) "" else java.text.SimpleDateFormat("EEE d", java.util.Locale.getDefault()).format(cal.time)) } }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackRow(T("guide")); Spacer(Modifier.weight(1f))
            OutlinedTextField(q, { q = it }, singleLine = true, colors = fieldColors(c), placeholder = { Text(T("search")) }, modifier = Modifier.width(240.dp).height(52.dp)); Spacer(Modifier.width(16.dp))
        }
        // day chips (catch-up)
        Row(Modifier.padding(horizontal = 12.dp).horizontalScroll(rememberScrollState())) {
            dayNames.forEach { (off, n) -> val s = off == dayOff
                Text(if (off == 0) T("day_today") else if (off == -1) T("yesterday") else n, color = if (s) Color.White else c.text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(8.dp)).background(if (s) c.accent else c.card).clickable { dayOff = off }.padding(horizontal = 10.dp, vertical = 6.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.padding(horizontal = 12.dp).horizontalScroll(rememberScrollState())) {
            (listOf("__all" to T("all"), "__fav" to T("favorites")) + cats.map { it.id to it.name }).forEach { (id, n) -> Text(n, color = if (id == cat) Color.White else c.text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(8.dp)).background(if (id == cat) c.accent.copy(alpha = 0.7f) else c.card).clickable { cat = id }.padding(horizontal = 10.dp, vertical = 6.dp)) }
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
                        AsyncImage(ch.icon, Modifier.size(36.dp, 24.dp), contentScale = ContentScale.Fit); Spacer(Modifier.width(6.dp))
                        Column { Text(ch.name, color = c.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); if (ch.archive) Text(T("catchup_days", ch.archiveDays.coerceAtLeast(1)), color = c.muted, fontSize = 9.5.sp, maxLines = 1) } }
                    Spacer(Modifier.width(4.dp))
                    Row(Modifier.horizontalScroll(hs).fillMaxHeight()) {
                        val list = repo.epgFor(pl, ch).filter { it.stop > start && it.start < start + hours * 3600 }
                        var cursor = start
                        for (e in list) {
                            val s = maxOf(e.start, start); if (s > cursor) Spacer(Modifier.width(((s - cursor) / 60f * pxPerMin).dp))
                            val w = ((minOf(e.stop, start + hours * 3600) - s) / 60f * pxPerMin).coerceAtLeast(2f)
                            val cur = e.start <= now && e.stop > now; val past = e.stop <= now
                            val archSecs: Long = (if (ch.archiveDays > 0) ch.archiveDays else 7) * 86400L
                            val ago: Long = now - e.start
                            val canCatch = past && ch.archive && ago < archSecs
                            Box(Modifier.width(w.dp).fillMaxHeight().padding(end = 1.dp).clip(RoundedCornerShape(6.dp)).background(if (cur) c.accent.copy(alpha = 0.45f) else if (canCatch) c.card else c.card2.copy(alpha = if (past) 0.5f else 1f)).hoverGlow(c.accent, 6, 1f)
                                .clickable(enabled = cur || canCatch) { scope.launch { repo.liveContext = rows; val u = if (cur) repo.streamUrl(pl, ch, repo.prefs.liveFormat == "ts") else repo.catchupUrl(pl, ch, e); nav.push(Screen.Play(u, ch.name + (if (cur) "" else " · " + e.title), ch, live = cur, image = ch.icon)) } }.padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
                                Column { Text(e.title, color = if (past && !canCatch) c.muted else c.text, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(hm(e.start, fmt24) + (if (cur) "  · " + T("live_now") else ""), color = if (cur) c.accent else c.muted, fontSize = 10.sp, fontWeight = if (cur) FontWeight.Bold else FontWeight.Normal) } }
                            cursor = minOf(e.stop, start + hours * 3600)
                        }
                    }
                }
            }
        }
    }
}
