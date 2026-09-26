@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package tv.ninekpro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tv.ninekpro.app.R
import tv.ninekpro.app.data.Kind

private const val PX_PER_MIN = 4       // 1 hour = 240 dp
private const val CH_COL = 170         // channel column width (dp)
private fun secsDp(secs: Long) = ((secs / 60) * PX_PER_MIN).toInt().dp

/**
 * TiviMate-style TV guide: channels down the left, time across the top, all rows scroll together, a red "now" line.
 * Tap a programme: current → play the channel · past + catch-up → play that programme · future → nothing yet.
 */
@Composable
fun GuideScreen() {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current; val act = LocalActivity.current
    val pl = repo.activePlaylist ?: run { nav.pop(); return }
    val ev by repo.epgVersion.collectAsState(); val pv by repo.profileVersion.collectAsState()
    val scope = rememberCoroutineScope()
    var channels by remember { mutableStateOf(repo.liveContext) }
    LaunchedEffect(pl.id) { if (channels.isEmpty()) try { channels = repo.content(pl, Kind.LIVE).items } catch (e: Throwable) {}; repo.ensureEpg(pl) }
    val list = remember(channels) { channels.take(600) }
    val now = System.currentTimeMillis() / 1000
    val start = remember { (now - 3600) / 1800 * 1800 }            // half-hour before now, rounded
    val end = start + 26 * 3600
    val hState = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(Unit) { hState.scrollTo(with(density) { secsDp(now - 1800 - start).roundToPx() }) }
    val tfmt = remember { java.text.SimpleDateFormat(if (repo.prefs.timeFormat == "24") "HH:mm" else "h:mm a", java.util.Locale.getDefault()) }
    val dfmt = remember { java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.getDefault()) }
    val totalW = secsDp(end - start)

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.guide)) { Pill(stringResource(R.string.now), false) { scope.launch { hState.animateScrollTo(with(density) { secsDp(now - 1800 - start).roundToPx() }) } } }
        // time header
        Row(Modifier.fillMaxWidth().height(30.dp).background(c.card2)) {
            Box(Modifier.width(CH_COL.dp).fillMaxHeight(), contentAlignment = Alignment.Center) { Text(dfmt.format(java.util.Date()), color = c.muted, fontSize = 11.sp) }
            Row(Modifier.fillMaxHeight().horizontalScroll(hState)) {
                var t = start
                while (t < end) { Box(Modifier.width((30 * PX_PER_MIN).dp).fillMaxHeight().border(0.5.dp, c.line), contentAlignment = Alignment.CenterStart) { Text(tfmt.format(java.util.Date(t * 1000)), color = c.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp)) }; t += 1800 }
            }
        }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.key }) { ch ->
                    val progs = remember(ev, ch.key) { repo.epgFor(pl, ch).filter { it.stop > start && it.start < end } }
                    Row(Modifier.fillMaxWidth().height(54.dp)) {
                        Row(Modifier.width(CH_COL.dp).fillMaxHeight().background(c.card2).tvFocus(c.accent, 0).clickable { repo.liveContext = list; scope.launch { playItem(repo, nav, pl, ch) } }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (ch.num > 0) ch.num.toString() else "", color = c.muted, fontSize = 11.sp, modifier = Modifier.width(26.dp))
                            Poster(ch.icon, Modifier.size(36.dp, 24.dp), contentScaleFit = true); Spacer(Modifier.width(6.dp))
                            Text(ch.name, color = c.text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Row(Modifier.fillMaxHeight().horizontalScroll(hState)) {
                            if (progs.isEmpty()) Box(Modifier.width(totalW).fillMaxHeight().padding(1.dp).background(c.card.copy(alpha = 0.4f)), contentAlignment = Alignment.CenterStart) { Text(stringResource(R.string.no_epg), color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp)) }
                            else {
                                var cursor = start
                                for (p in progs) {
                                    val ps = maxOf(p.start, start); val pe = minOf(p.stop, end)
                                    if (ps > cursor) Spacer(Modifier.width(secsDp(ps - cursor)))
                                    val isNow = p.start <= now && p.stop > now; val past = p.stop <= now
                                    Box(Modifier.width(secsDp(pe - ps)).fillMaxHeight().padding(1.dp).clip(RoundedCornerShape(4.dp))
                                        .background(if (isNow) c.accent.copy(alpha = 0.28f) else if (past) c.card.copy(alpha = 0.5f) else c.card).border(0.5.dp, if (isNow) c.accent else c.line, RoundedCornerShape(4.dp)).tvFocus(c.accent, 4)
                                        .combinedClickable(onLongClick = { if (!past) { scope.launch { try { val u = repo.streamUrl(pl, ch, ts = true); val on = repo.toggleRecSchedule(ch.key, p.start, p.stop, u, ch.name + " · " + p.title); android.widget.Toast.makeText(act, (if (on) "● " + act.getString(R.string.record_scheduled) else act.getString(R.string.record_unscheduled)) + " · " + p.title, android.widget.Toast.LENGTH_SHORT).show() } catch (e: Exception) {} } } }) { repo.liveContext = list; if (!isNow && !past) { if (android.os.Build.VERSION.SDK_INT >= 33 && act.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) act.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7); val on = repo.toggleProgReminder(ch.key, p.start, p.title, ch.name); android.widget.Toast.makeText(act, (if (on) "⏰ " + act.getString(R.string.reminder_set) else act.getString(R.string.reminder_off)) + " · " + p.title, android.widget.Toast.LENGTH_SHORT).show() }
                                            else scope.launch { try { if (isNow) playItem(repo, nav, pl, ch) else if (past && ch.archive) { val u = repo.catchupUrl(pl, ch, p); nav.push(Screen.Play(u, p.title, "cu:" + ch.id + ":" + p.start, Kind.MOVIE, ch.icon, startMs = 0L)) } } catch (e: Exception) {} } }
                                        .padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
                                        Column { Text(p.title, color = if (past) c.muted else c.text, fontSize = 12.sp, fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(tfmt.format(java.util.Date(p.start * 1000)) + (if (past && ch.archive) "  ⏪" else if (!past && !isNow && pv >= 0 && repo.hasProgReminder(ch.key, p.start)) "  ⏰" else "") + (if (!past && pv >= 0 && repo.hasRecSchedule(ch.key, p.start)) "  ●" else ""), color = if (!past && !isNow && repo.hasProgReminder(ch.key, p.start)) c.accent else if (!past && repo.hasRecSchedule(ch.key, p.start)) Color(0xFFE11D48) else c.muted, fontSize = 10.sp, maxLines = 1) }
                                    }
                                    cursor = pe
                                }
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.line.copy(alpha = 0.35f)))
                }
            }
            // now line
            val nowX = with(density) { (secsDp(now - start).roundToPx() - hState.value).toDp() }
            if (nowX > 0.dp) Box(Modifier.offset(x = CH_COL.dp + nowX).width(2.dp).fillMaxHeight().background(Color(0xFFE11D48)))
        }
    }
}
