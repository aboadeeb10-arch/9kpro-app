package tv.ninekpro.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.ninekpro.desktop.data.AsyncImage
import tv.ninekpro.desktop.data.Kind

/** Top bar like the app: Home | Live | Movies | Series (| Sports) · search · refresh · settings · logo · account. */
@Composable
fun TopTabs(tab: Int, onTab: (Int) -> Unit) {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current; val win = LocalWindow.current
    val brand by repo.brand.collectAsState(); val account by repo.account.collectAsState()
    val names = listOf(T("home_tab"), T("live"), T("movies"), T("series")) + (if (brand.footballOn) listOf(T("sports")) else emptyList())
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        names.forEachIndexed { i, t ->
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (tab == i) c.accent.copy(alpha = 0.16f) else Color.Transparent).hoverGlow(c.accent, 8, 1.02f).clickable { onTab(i) }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(t, color = if (tab == i) c.accent else c.text, fontSize = 16.sp, fontWeight = if (tab == i) FontWeight.ExtraBold else FontWeight.SemiBold)
            }
            if (i < names.size - 1) Box(Modifier.padding(horizontal = 2.dp).width(1.dp).height(18.dp).background(c.line))
        }
        Spacer(Modifier.width(12.dp))
        RoundBtn(Icons.Default.Search) { nav.push(Screen.Search) }
        Spacer(Modifier.weight(1f))
        RoundBtn(Icons.Default.Refresh) { repo.forceReload(); repo.syncNow(); win.toast(Strings.get(Strings.lang(repo.prefs.language), "refreshing")) }
        Spacer(Modifier.width(6.dp)); RoundBtn(Icons.Default.Settings) { nav.push(Screen.Settings) }
        Spacer(Modifier.width(12.dp)); BrandLogo(brand.logo, Modifier.height(32.dp).widthIn(max = 120.dp))
        val acc = account
        if (acc != null && !acc.username.startsWith("mac:")) { Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) { Text(acc.username, color = c.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                if (acc.exp.isNotEmpty()) Text(T("expires") + " " + acc.exp, color = if ((acc.days ?: 99) <= 7) Color(0xFFFF6B7A) else c.muted, fontSize = 10.5.sp, maxLines = 1) } }
    }
}

@Composable
fun RoundBtn(icon: ImageVector, onClick: () -> Unit) { val c = LocalColors.current
    Box(Modifier.size(38.dp).clip(RoundedCornerShape(19.dp)).glass(c, 19).hoverGlow(c.accent, 19).clickable { onClick() }, contentAlignment = Alignment.Center) { Icon(icon, null, tint = c.text, modifier = Modifier.size(19.dp)) } }

@Composable
fun MainScreen() {
    val nav = LocalNav.current; val repo = LocalRepo.current
    Column(Modifier.fillMaxSize()) {
        TopTabs(nav.mainTab) { nav.mainTab = it }
        Box(Modifier.fillMaxSize()) {
            if (repo.waitingForLine) WaitingPane()
            else when (nav.mainTab) { 0 -> HomePane { nav.mainTab = it }; 1 -> LivePane(); 2 -> VodPane(Kind.MOVIE); 3 -> VodPane(Kind.SERIES); else -> SportsPane() }
        }
    }
}

@Composable
fun WaitingPane() { val repo = LocalRepo.current; val c = LocalColors.current; val nav = LocalNav.current
    LaunchedEffect(Unit) { while (true) { delay(15_000); repo.refreshSession(); if (repo.playlists.value.isNotEmpty()) { repo.preloadAll(); break } } }
    Column(Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(repo.prefs.deviceMac().uppercase(), color = c.text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Text(T("device_key") + ": " + repo.prefs.deviceKey(), color = c.muted, fontSize = 14.sp)
        Spacer(Modifier.height(14.dp)); Text(T("waiting_line"), color = c.text, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.widthIn(max = 520.dp))
        Spacer(Modifier.height(18.dp)); PBtn(T("sign_in")) { nav.reset(Screen.Login) }
    } }

@Composable
fun HomePane(goTab: (Int) -> Unit) {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current; val win = LocalWindow.current
    val brand by repo.brand.collectAsState(); val account by repo.account.collectAsState(); val messages by repo.messages.collectAsState()
    val pv by repo.profileVersion.collectAsState(); val lv by repo.loadedVersion.collectAsState(); val serverOk by repo.serverOk.collectAsState(); val crashAvail by repo.crashTrialAvailable.collectAsState()
    val scope = rememberCoroutineScope(); val lang = LocalLang.current
    var clock by remember { mutableStateOf(clockText(repo.prefs.timeFormat == "24")) }
    var crashAsk by remember { mutableStateOf(false) }; var crashMsg by remember { mutableStateOf("") }; var exitAsk by remember { mutableStateOf(false) }
    val prog by repo.crashFixProgress.collectAsState()
    LaunchedEffect(Unit) { while (true) { clock = clockText(repo.prefs.timeFormat == "24"); delay(10_000) } }
    LaunchedEffect(Unit) { repo.preloadAll() }
    val pl = repo.activePlaylist
    val counts = Kind.values().associateWith { k -> pl?.let { repo.cachedContent(it, k)?.items?.size } ?: 0 }
    val acc = account
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(T("welcome_to", brand.name), color = c.text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                if (acc != null && acc.days != null && acc.days <= brand.renewDays && acc.days >= 0) Text(T("expires_in", acc.days), color = Color(0xFFFFB347), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            Column(horizontalAlignment = Alignment.End) { Text(clock, color = c.text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold); Text(dateText(), color = c.muted, fontSize = 12.sp) }
        }
        if (!serverOk) Text(T("server_down"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp).fillMaxWidth().background(Color(0xFFC62828), RoundedCornerShape(8.dp)).padding(10.dp))
        if (repo.crashFixActive()) Row(Modifier.padding(top = 8.dp).fillMaxWidth().background(Color(0xFFE67E22), RoundedCornerShape(8.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Text(T("crash_fix_active", repo.crashFixHoursLeft()), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)) }
        if (prog > 0) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) { Loading(); Spacer(Modifier.width(10.dp)); Text(T("preparing_line") + " (" + prog + "/12)", color = c.muted, fontSize = 13.sp) }
        messages.filter { it.kind != "silent" }.take(2).forEach { m ->
            Row(Modifier.padding(top = 8.dp).fillMaxWidth().glass(c, 10).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (m.image.isNotEmpty()) { AsyncImage(m.image, Modifier.size(90.dp, 54.dp).clip(RoundedCornerShape(6.dp))); Spacer(Modifier.width(10.dp)) }
                Column(Modifier.weight(1f)) { Text(m.title, color = c.text, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(m.text, color = c.muted, fontSize = 12.5.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                if (m.link.isNotEmpty()) PBtn(T("open")) { try { java.awt.Desktop.getDesktop().browse(java.net.URI(m.link)) } catch (e: Throwable) {} }
                Spacer(Modifier.width(6.dp)); Text("✕", color = c.muted, modifier = Modifier.clickable { repo.markMessagesSeen(listOf(m.id)) }.padding(6.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Tile(Icons.Default.LiveTv, T("live"), T("n_channels", counts[Kind.LIVE] ?: 0), Modifier.weight(1f)) { goTab(1) }
            Tile(Icons.Default.Movie, T("movies"), T("n_titles", counts[Kind.MOVIE] ?: 0), Modifier.weight(1f)) { goTab(2) }
            Tile(Icons.Default.Tv, T("series"), T("n_shows", counts[Kind.SERIES] ?: 0), Modifier.weight(1f)) { goTab(3) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallBtn(Icons.Default.Favorite, T("favorites"), Modifier.weight(1f)) { nav.push(Screen.Search) }
            SmallBtn(Icons.Default.GridView, T("guide"), Modifier.weight(1f)) { nav.push(Screen.Guide) }
            SmallBtn(Icons.AutoMirrored.Filled.PlaylistPlay, T("playlists"), Modifier.weight(1f)) { nav.push(Screen.Playlists) }
            SmallBtn(Icons.Default.SwapHoriz, T("change_server"), Modifier.weight(1f)) { nav.push(Screen.Playlists) }
            if (crashAvail || repo.crashFixActive()) SmallBtn(Icons.Default.Healing, T("crash_fix"), Modifier.weight(1f), accent = true) { crashAsk = true }
        }
        // continue watching
        val resume = repo.resumeList()
        if (resume.isNotEmpty()) { SectionTitle(T("continue_watching"))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(resume, key = { it.key }) { r ->
                Column(Modifier.width(190.dp).hoverGlow(c.accent, 10).clickable { scope.launch { openResume(repo, nav, r) } }.padding(4.dp)) {
                    Box(Modifier.fillMaxWidth().height(108.dp).clip(RoundedCornerShape(8.dp)).background(c.card)) { AsyncImage(r.image, Modifier.fillMaxSize()); if (r.durationMs > 0) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(r.positionMs.toFloat() / r.durationMs).height(3.dp).background(c.accent))
                        Text("✕", color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopEnd).clickable { repo.removeResume(r.key) }.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 2.dp)) }
                    Text(r.title, color = c.text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text((if (r.extra.isNotEmpty()) r.extra + " · " else "") + T("min_left", ((r.durationMs - r.positionMs) / 60000).coerceAtLeast(0)), color = c.muted, fontSize = 11.sp) } } } }
        // favorites
        val favKeys = repo.favorites()
        if (favKeys.isNotEmpty() && pl != null) {
            val all = Kind.values().flatMap { repo.cachedContent(pl, it)?.items ?: emptyList() }.associateBy { it.key }
            val favs = favKeys.mapNotNull { all[it] }
            if (favs.isNotEmpty()) { SectionTitle(T("favorites"))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(favs, key = { it.key }) { it -> if (it.kind == Kind.LIVE) ChannelChip(it) { scope.launch { val u = repo.streamUrl(pl, it, repo.prefs.liveFormat == "ts"); repo.liveContext = favs.filter { f -> f.kind == Kind.LIVE }; nav.push(Screen.Play(u, it.name, it, live = true, image = it.icon)) } } else Poster(it, 120) { nav.push(if (it.kind == Kind.MOVIE) Screen.Movie(it) else Screen.Series(it)) } } } }
        }
        // new this week
        if (pl != null) { val week = System.currentTimeMillis() / 1000 - 7 * 86400
            val fresh = (repo.cachedContent(pl, Kind.MOVIE)?.items ?: emptyList()).filter { it.added > week }.sortedByDescending { it.added }.take(20)
            if (fresh.isNotEmpty()) { SectionTitle(T("new_this_week")); LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(fresh, key = { it.key }) { Poster(it, 120) { nav.push(Screen.Movie(it)) } } } } }
        Spacer(Modifier.height(20.dp))
    }
    if (crashAsk) AlertDialog(onDismissRequest = { crashAsk = false }, title = { Text(T("crash_fix")) }, text = { Text(if (crashMsg.isNotEmpty()) crashMsg else T("crash_fix_q")) },
        confirmButton = { TextButton({ crashAsk = false; scope.launch { val e = repo.crashFix(); win.toast(when { e == null -> Strings.get(lang, "crash_fix_on"); e.startsWith("cooldown") -> String.format(Strings.get(lang, "crash_fix_wait"), e.substringAfter(':').toIntOrNull() ?: 5); e.startsWith("daily_limit") -> Strings.get(lang, "crash_fix_limit"); else -> Strings.get(lang, "crash_fix_refused") }) } }) { Text(T("ok")) } },
        dismissButton = { TextButton({ crashAsk = false }) { Text(T("cancel")) } })
}

suspend fun openResume(repo: tv.ninekpro.desktop.data.Repo, nav: Nav, r: tv.ninekpro.desktop.data.ResumeEntry) {
    val pl = repo.activePlaylist ?: return
    if (r.kind == "MOVIE") { val it = repo.cachedContent(pl, Kind.MOVIE)?.items?.firstOrNull { x -> x.key == r.key } ?: return; nav.push(Screen.Movie(it)) }
    else { val sid = r.key.substringAfter("EP:").substringBefore(':'); val ser = repo.cachedContent(pl, Kind.SERIES)?.items?.firstOrNull { x -> r.extra.isNotEmpty() && r.title.startsWith(x.name) } ; if (ser != null) nav.push(Screen.Series(ser)) }
}

@Composable
fun SectionTitle(t: String) { val c = LocalColors.current; Text(t, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)) }

@Composable
fun Tile(icon: ImageVector, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) { val c = LocalColors.current
    Column(modifier.fillMaxHeight().glass(c, 14).hoverGlow(c.accent, 14, 1.03f).clickable { onClick() }.padding(18.dp), verticalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(40.dp)); Spacer(Modifier.height(10.dp))
        Text(title, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold); Text(sub, color = c.muted, fontSize = 13.sp) } }

@Composable
fun SmallBtn(icon: ImageVector, title: String, modifier: Modifier, accent: Boolean = false, onClick: () -> Unit) { val c = LocalColors.current
    Row(modifier.glass(c, 10, selected = accent).hoverGlow(c.accent, 10, 1.03f).clickable { onClick() }.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (accent) Color(0xFFFFB347) else c.accent, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(title, color = c.text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) } }

@Composable
fun ChannelChip(it: tv.ninekpro.desktop.data.Item, onClick: () -> Unit) { val c = LocalColors.current
    Column(Modifier.width(120.dp).glass(c, 10).hoverGlow(c.accent, 10).clickable { onClick() }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(it.icon, Modifier.size(80.dp, 46.dp), contentScale = ContentScale.Fit) { Icon(Icons.Default.LiveTv, null, tint = c.muted, modifier = Modifier.size(30.dp)) }
        Text(it.name, color = c.text, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } }

@Composable
fun SportsPane() { val repo = LocalRepo.current; val c = LocalColors.current; val nav = LocalNav.current; val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<tv.ninekpro.desktop.data.Match>>(emptyList()) }; var busy by remember { mutableStateOf(true) }; var sel by remember { mutableStateOf<tv.ninekpro.desktop.data.Match?>(null) }
    LaunchedEffect(Unit) { list = repo.matches(); busy = false }
    val pl = repo.activePlaylist
    Row(Modifier.fillMaxSize().padding(16.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(T("matches_today"), color = c.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(8.dp))
            if (busy) Loading() else if (list.isEmpty()) Text(T("no_matches"), color = c.muted)
            list.sortedWith(compareBy({ !it.live }, { it.ts })).forEach { m ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).glass(c, 10, selected = sel?.id == m.id).hoverGlow(c.accent, 10, 1.01f).clickable { sel = m }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(m.homeLogo, Modifier.size(26.dp), contentScale = ContentScale.Fit); Spacer(Modifier.width(8.dp)); Text(m.home, color = c.text, fontSize = 13.5.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (m.upcoming) hm(m.ts, repo.prefs.timeFormat == "24") else "${m.hg.coerceAtLeast(0)} – ${m.ag.coerceAtLeast(0)}", color = if (m.live) Color(0xFF6BFF8E) else c.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 12.dp))
                    Text(m.away, color = c.text, fontSize = 13.5.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.End); Spacer(Modifier.width(8.dp)); AsyncImage(m.awayLogo, Modifier.size(26.dp), contentScale = ContentScale.Fit)
                    Spacer(Modifier.width(10.dp)); Text(if (m.live) m.min.toString() + "'" else m.league, color = c.muted, fontSize = 11.sp, modifier = Modifier.width(90.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        val m = sel
        if (m != null && pl != null) { Spacer(Modifier.width(14.dp))
            Column(Modifier.width(300.dp).glass(c, 12).padding(12.dp)) { Text(T("match_channels"), color = c.text, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp))
                val chs = repo.channelsForMatch(m); if (chs.isEmpty()) Text(T("match_no_channel"), color = c.muted, fontSize = 12.5.sp)
                chs.forEach { ch -> Row(Modifier.fillMaxWidth().hoverGlow(c.accent, 8, 1.01f).clickable { scope.launch { val u = repo.streamUrl(pl, ch, repo.prefs.liveFormat == "ts"); repo.liveContext = chs; nav.push(Screen.Play(u, ch.name, ch, live = true, image = ch.icon)) } }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) { AsyncImage(ch.icon, Modifier.size(40.dp, 26.dp), contentScale = ContentScale.Fit); Spacer(Modifier.width(8.dp)); Text(ch.name, color = c.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } } } }
    }
}
