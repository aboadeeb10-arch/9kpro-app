package tv.ninekpro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import tv.ninekpro.app.R
import tv.ninekpro.app.data.Item
import tv.ninekpro.app.data.Match
import tv.ninekpro.app.data.TeamNames

private val LIVE_RED = Color(0xFFE11D48); private val GREEN = Color(0xFF22C55E)

@Composable
private fun teamName(en: String): String { val repo = LocalRepo.current; val lang = repo.prefs.language.ifEmpty { java.util.Locale.getDefault().language }; return TeamNames.local(en, lang) }

/** One match card: crests · names · score or kick-off · minute · league. Tap = channel chooser; ⏰ on upcoming = reminder. */
@Composable
fun MatchCard(m: Match, width: Int = 240, compact: Boolean = false, onPick: (Match) -> Unit) {
    val repo = LocalRepo.current; val c = LocalColors.current; val act = LocalActivity.current
    val pv by repo.profileVersion.collectAsState()
    val tfmt = remember { java.text.SimpleDateFormat(if (repo.prefs.timeFormat == "24") "HH:mm" else "h:mm a", java.util.Locale.getDefault()) }
    var remind by remember(m.id, pv) { mutableStateOf(repo.reminders().contains(m.id)) }
    Column(Modifier.width(width.dp).clip(RoundedCornerShape(12.dp)).glass(c, 12).then(if (m.live) Modifier.border(1.dp, LIVE_RED.copy(alpha = 0.7f), RoundedCornerShape(12.dp)) else Modifier).tvFocus(c.accent, 12).clickable { onPick(m) }.padding(if (compact) 7.dp else 10.dp, if (compact) 5.dp else 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (m.leagueLogo.isNotEmpty()) AsyncImage(model = m.leagueLogo, contentDescription = null, modifier = Modifier.size(if (compact) 11.dp else 14.dp)); Spacer(Modifier.width(5.dp))
            Text(m.league, color = c.muted, fontSize = if (compact) 9.sp else 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            when {
                m.live -> Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(LIVE_RED)); Spacer(Modifier.width(4.dp)); Text(if (m.status == "HT") "HT" else if (m.min > 0) m.min.toString() + "’" else "LIVE", color = LIVE_RED, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                m.finished -> Text("FT", color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                else -> Text("⏰", color = if (remind) c.accent else c.muted, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(6.dp)).tvFocus(c.accent, 6).clickable { if (android.os.Build.VERSION.SDK_INT >= 33 && act.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) act.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7); remind = repo.toggleReminder(m) }.padding(2.dp))
            }
        }
        Spacer(Modifier.height(if (compact) 3.dp else 6.dp))
        val crest = if (compact) 16.dp else 30.dp; val nameFs = if (compact) 9.sp else 11.5.sp
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { AsyncImage(model = m.homeLogo, contentDescription = null, modifier = Modifier.size(crest)); Text(teamName(m.home), color = c.text, fontSize = nameFs, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
            Text(if (m.upcoming) tfmt.format(java.util.Date(m.ts * 1000)) else (if (m.hg < 0) "–" else m.hg.toString()) + " – " + (if (m.ag < 0) "–" else m.ag.toString()), color = if (m.live) Color.White else c.text, fontSize = if (compact) (if (m.upcoming) 12.sp else 15.sp) else (if (m.upcoming) 15.sp else 21.sp), fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = if (compact) 4.dp else 8.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { AsyncImage(model = m.awayLogo, contentDescription = null, modifier = Modifier.size(crest)); Text(teamName(m.away), color = c.text, fontSize = nameFs, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
        }
    }
}

/** Dialog: which of the customer's channels show this match (from the guide), tap to play. */
@Composable
fun MatchChannels(m: Match?, onClose: () -> Unit) {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current; val scope = rememberCoroutineScope()
    if (m == null) return
    val pl = repo.activePlaylist ?: return
    val ev by repo.epgVersion.collectAsState()
    val chans = remember(m.id, ev) { repo.channelsForMatch(m) }
    androidx.compose.material3.AlertDialog(onDismissRequest = onClose, confirmButton = { BigButton(stringResource(R.string.cancel), filled = false) { onClose() } },
        title = { Text(teamName(m.home) + " – " + teamName(m.away), fontSize = 17.sp) },
        text = { Column {
            Text(stringResource(R.string.match_channels), color = c.muted, fontSize = 12.sp); Gap(6)
            if (chans.isEmpty()) Text(stringResource(R.string.match_no_channel), color = c.muted, fontSize = 13.sp)
            LazyColumn(Modifier.height((chans.size.coerceIn(1, 7) * 46).dp)) { items(chans, key = { it.key }) { ch ->
                val now = repo.nowNext(pl, ch).first?.title ?: ""
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).tvFocus(c.accent, 8).clickable { onClose(); repo.liveContext = chans; scope.launch { playItem(repo, nav, pl, ch) } }.padding(8.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Poster(ch.icon, Modifier.size(40.dp, 26.dp), contentScaleFit = true); Spacer(Modifier.width(10.dp))
                    Column { Text(ch.name, color = c.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); if (now.isNotEmpty()) Text(now, color = c.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                } } }
        } })
}

/** Sports tab: today (live first), tomorrow on demand, grouped by competition. */
@Composable
fun SportsPane() {
    val repo = LocalRepo.current; val c = LocalColors.current
    val brand by repo.brand.collectAsState()
    var day by remember { mutableStateOf(0) }
    var list by remember { mutableStateOf<List<Match>?>(null) }
    var pick by remember { mutableStateOf<Match?>(null) }
    LaunchedEffect(day) { list = null; list = try { repo.matches(repo.today(day)) } catch (e: Throwable) { emptyList() } }
    MatchChannels(pick) { pick = null }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("⚽ " + stringResource(R.string.sports), color = c.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Pill(stringResource(R.string.yesterday), day == -1) { day = -1 }; Pill(stringResource(R.string.today), day == 0) { day = 0 }; Pill(stringResource(R.string.tomorrow), day == 1) { day = 1 }
        }
        if (!brand.footballOn) { Empty(stringResource(R.string.football_off)); return }
        val l = list
        if (l == null) { Loading(); return }
        if (l.isEmpty()) { Empty(stringResource(R.string.no_matches)); return }
        val groups = remember(l) { val live = l.filter { it.live }; val rest = l.filter { !it.live }.sortedBy { it.ts }; (if (live.isNotEmpty()) listOf("LIVE" to live) else emptyList()) + rest.groupBy { it.league }.toList() }
        LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            groups.forEach { (name, ms) ->
                item { Row(verticalAlignment = Alignment.CenterVertically) { if (name == "LIVE") { Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(LIVE_RED)); Spacer(Modifier.width(6.dp)) }
                    Text(if (name == "LIVE") stringResource(R.string.live_now).uppercase() else name.uppercase(), color = if (name == "LIVE") LIVE_RED else c.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) } }
                item { LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(ms, key = { it.id }) { m -> MatchCard(m) { pick = it } } } }
            }
        }
    }
}

/** Home row: live matches first, then today's upcoming with ⏰. Hidden when nothing today or Match day is off. */
@Composable
fun MatchRow(onPick: (Match) -> Unit) {
    val repo = LocalRepo.current; val c = LocalColors.current; val nav = LocalNav.current
    val brand by repo.brand.collectAsState()
    if (!brand.footballOn) return
    var list by remember { mutableStateOf<List<Match>>(emptyList()) }
    LaunchedEffect(Unit) { list = try { repo.matches() } catch (e: Throwable) { emptyList() } }
    val show = remember(list) { list.filter { it.live } + list.filter { it.upcoming } }
    if (show.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (show.any { it.live }) { Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(LIVE_RED)); Spacer(Modifier.width(6.dp)) }
        SectionTitle((if (show.any { it.live }) stringResource(R.string.live_now) else stringResource(R.string.matches_today)).uppercase())
        Spacer(Modifier.weight(1f)); Pill(stringResource(R.string.sports) + " ›", false) { nav.mainTab = 4 }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(show.take(12), key = { it.id }) { m -> MatchCard(m, 140, compact = true) { onPick(it) } } }
}
