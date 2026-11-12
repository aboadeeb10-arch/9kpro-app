@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package tv.ninekpro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.ninekpro.app.R
import tv.ninekpro.app.data.Kind
import tv.ninekpro.app.data.ResumeEntry

fun clockText(fmt24: Boolean): String = java.text.SimpleDateFormat(if (fmt24) "HH:mm" else "h:mm a", java.util.Locale.getDefault()).format(java.util.Date())

/** Top bar like the old app: Home | Live | Movies | Series | Sports · search icon · logo. Sizes adapt to phone vs TV. */
@Composable
fun TopTabs(tab: Int, onTab: (Int) -> Unit) {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current
    val brand by repo.brand.collectAsState()
    val tv = tv.ninekpro.app.data.DeviceInfo.isTv || repo.prefs.deviceType == "tv"
    val fs = if (tv) 18.sp else 15.sp
    val names = listOf(stringResource(R.string.home_tab), stringResource(R.string.live), stringResource(R.string.movies), stringResource(R.string.series)) + (if (brand.footballOn) listOf(stringResource(R.string.sports)) else emptyList())
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        names.forEachIndexed { i, t ->
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (tab == i) c.accent.copy(alpha = 0.16f) else Color.Transparent).tvFocus(c.accent, 8).clickable { onTab(i) }.padding(horizontal = if (tv) 14.dp else 10.dp, vertical = 7.dp)) {
                Text(t, color = if (tab == i) c.accent else c.text, fontSize = fs, fontWeight = if (tab == i) FontWeight.ExtraBold else FontWeight.SemiBold)
            }
            if (i < names.size - 1) Box(Modifier.padding(horizontal = 2.dp).width(1.dp).height(18.dp).background(c.line))
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).glass(c, 18).tvFocus(c.accent, 18).clickable { nav.push(Screen.Search("")) }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Search, null, tint = c.text, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.weight(1f))
        val account by repo.account.collectAsState(); val acc = account
        val act = LocalActivity.current; val scope = rememberCoroutineScope()
        // Refresh: ask the panel for the current playlists first (a playlist the seller changed shows up now), then re-download the lists
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).glass(c, 18).tvFocus(c.accent, 18).clickable { scope.launch { repo.refreshAll(); repo.syncNow() }; try { android.widget.Toast.makeText(act, act.getString(R.string.refreshing), android.widget.Toast.LENGTH_SHORT).show() } catch (e: Exception) {}; nav.reset(Screen.Main(nav.mainTab)) }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Refresh, null, tint = c.text, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).glass(c, 18).tvFocus(c.accent, 18).clickable { nav.push(Screen.Settings) }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Settings, null, tint = c.text, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(10.dp))
        BrandLogo(brand.logo, Modifier.height(30.dp).widthIn(max = 110.dp))
        if (acc != null && !acc.username.startsWith("mac:")) { Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) { Text(acc.username, color = c.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                if (acc.exp.isNotEmpty()) Text(stringResource(R.string.expires) + " " + acc.exp, color = if ((acc.days ?: 99) <= 7) Color(0xFFFF6B7A) else c.muted, fontSize = 10.5.sp, maxLines = 1) } }
    }
}

@Composable
fun MainScreen() {
    val nav = LocalNav.current
    Column(Modifier.fillMaxSize()) {
        TopTabs(nav.mainTab) { nav.mainTab = it }
        Box(Modifier.fillMaxSize()) {
            when (nav.mainTab) { 0 -> HomePane { nav.mainTab = it }; 1 -> LivePane(); 2 -> VodPane(Kind.MOVIE); 3 -> VodPane(Kind.SERIES); else -> SportsPane() }
        }
    }
}

fun dateText(): String = java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.getDefault()).format(java.util.Date())

/** Home (look 01 + Match day): welcome + clock/date/weather · live matches · three big tiles with counts · six buttons · rows. */
@Composable
fun HomePane(goTab: (Int) -> Unit) {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current; val act = LocalActivity.current
    val brand by repo.brand.collectAsState(); val account by repo.account.collectAsState(); val messages by repo.messages.collectAsState()
    val pv by repo.profileVersion.collectAsState(); val playlists by repo.playlists.collectAsState(); val serverOk by repo.serverOk.collectAsState()
    var busy by remember { mutableStateOf(false) }; var crashMsg by remember { mutableStateOf("") }; var crashAsk by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf(clockText(repo.prefs.timeFormat == "24")) }
    var tick by remember { mutableStateOf(0) }
    var wx by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var pick by remember { mutableStateOf<tv.ninekpro.app.data.Match?>(null) }
    var shown by remember { mutableStateOf(tv.ninekpro.app.data.DeviceInfo.lite) }
    LaunchedEffect(Unit) { shown = true; wx = try { repo.weather() } catch (e: Throwable) { null }; while (true) { clock = clockText(repo.prefs.timeFormat == "24"); tick++; delay(15_000) } }
    val scope = rememberCoroutineScope(); val active = repo.activePlaylist
    LaunchedEffect(active?.id) { repo.preloadAll() }
    val acc = account; val anon = acc?.username?.startsWith("mac:") == true
    val crashActive = remember(tick, playlists) { repo.crashFixActive() }
    val counts = remember(tick, active?.id) { Kind.values().associateWith { k -> active?.let { repo.cachedContent(it, k)?.items?.size } } }
    val isTv = tv.ninekpro.app.data.DeviceInfo.isTv || repo.prefs.deviceType == "tv"
    // TV: the remote starts on the first Home tile (otherwise nothing is focused and the first key press goes nowhere)
    val firstTile = remember { FocusRequester() }
    LaunchedEffect(shown) { if (shown && isTv) { delay(400); try { firstTile.requestFocus() } catch (e: Throwable) {} } }
    // sideload flavour: a newer APK on the panel → "Version X is available" banner (checkUpdate returns null in the Play flavour)
    var upd by remember { mutableStateOf<tv.ninekpro.app.data.UpdateInfo?>(null) }
    if (!tv.ninekpro.app.BuildConfig.PLAY_BUILD) LaunchedEffect(Unit) { try { tv.ninekpro.app.data.Updater.restore(act, tv.ninekpro.app.BuildConfig.VERSION_CODE) } catch (e: Throwable) {}; upd = try { repo.checkUpdate() } catch (e: Throwable) { null } }
    val updPending by tv.ninekpro.app.data.Updater.pendingInfo.collectAsState()
    MatchChannels(pick) { pick = null }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val tall = maxHeight > 560.dp
        val midTall = maxHeight > 480.dp   // read here: inside Column/Row lambdas the BoxWithConstraints receiver is not implicit (DSL marker)
        // faint brand watermark
        BrandLogo(brand.logo, Modifier.align(Alignment.BottomEnd).padding(24.dp).size(200.dp, 90.dp), alpha = 0.05f)
        androidx.compose.animation.AnimatedVisibility(visible = shown, enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(350)) + androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(350)) { it / 12 }) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            // welcome · clock · date · weather
            Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.welcome_to, brand.name), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(clock, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.width(10.dp))
                Text(dateText() + (wx?.let { "  ·  " + repo.weatherIcon() + " " + it.second + "°" + (if (it.first.isNotEmpty()) " " + it.first else "") } ?: ""), color = c.muted, fontSize = 12.5.sp, maxLines = 1)
            }
            var waQr by remember { mutableStateOf(false) }
            if (waQr && acc != null) WaQrDialog(brand.whatsapp, brand.renewText.replace("{username}", acc.username)) { waQr = false }
            val renewAction: () -> Unit = { if (isTv) waQr = true else openWhatsApp(act, brand.whatsapp, brand.renewText.replace("{username}", acc?.username ?: "")) }
            if (acc != null && !anon && acc.days != null) {
                if (acc.expired) Banner(stringResource(R.string.expired), Color(0xFFE11D48), if (brand.whatsapp.isNotEmpty()) stringResource(R.string.renew) else null, onAction = renewAction)
                else if (acc.days <= brand.renewDays) Banner(stringResource(R.string.expires_in, acc.days), Color(0xFFD97706), if (brand.whatsapp.isNotEmpty()) stringResource(R.string.renew) else null, onAction = renewAction)
            }
            messages.forEach { m ->
                if (m.kind == "banner") PromoBanner(m) { repo.markMessagesSeen(listOf(m.id)) }
                else Banner((if (m.title.isNotEmpty()) m.title + " — " else "") + m.text, when (m.kind) { "warn" -> Color(0xFFE11D48); "promo" -> c.accent; else -> Color(0xFF3B82F6) }, stringResource(R.string.ok)) { repo.markMessagesSeen(listOf(m.id)) }
            }
            if (crashMsg.isNotEmpty()) Banner(crashMsg, c.accent, stringResource(R.string.ok)) { crashMsg = "" }
            // sideload: a newer APK on the panel, or one downloaded earlier and not installed yet → the in-app updater (data/Updater.kt)
            if (!tv.ninekpro.app.BuildConfig.PLAY_BUILD) { val u: tv.ninekpro.app.data.UpdateInfo? = upd?.takeIf { it.code > tv.ninekpro.app.BuildConfig.VERSION_CODE } ?: updPending; if (u != null) UpdateBanner(u) }
            // match day
            MatchRow { pick = it }
            Gap(8)
            Row(Modifier.fillMaxWidth().weight(1f).heightIn(min = 120.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                BigTile(stringResource(R.string.live), Icons.Default.LiveTv, Modifier.weight(1f).focusRequester(firstTile), counts[Kind.LIVE]?.let { stringResource(R.string.n_channels, it) }) { goTab(1) }
                BigTile(stringResource(R.string.movies), Icons.Default.Movie, Modifier.weight(1f), counts[Kind.MOVIE]?.let { stringResource(R.string.n_titles, it) }) { goTab(2) }
                BigTile(stringResource(R.string.series), Icons.Default.Tv, Modifier.weight(1f), counts[Kind.SERIES]?.let { stringResource(R.string.n_shows, it) }) { goTab(3) }
            }
            Gap(8)
            var serverAsk by remember { mutableStateOf(false) }; var plAsk by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallTile(stringResource(R.string.downloads), Icons.Default.Download, Modifier.weight(1f)) { nav.push(Screen.Downloads) }
                SmallTile(stringResource(R.string.favorites), Icons.Default.Favorite, Modifier.weight(1f)) { nav.push(Screen.Favorites) }
                SmallTile(stringResource(R.string.change_playlist), Icons.AutoMirrored.Filled.PlaylistPlay, Modifier.weight(1f)) { nav.push(Screen.Playlists) }
                SmallTile(stringResource(R.string.multi_screen), Icons.Default.GridView, Modifier.weight(1f)) { nav.push(Screen.Multi) }
                SmallTile(stringResource(R.string.change_server), Icons.Default.SwapHoriz, Modifier.weight(1f)) { serverAsk = true }
                if (acc != null && !anon && !tv.ninekpro.app.BuildConfig.PLAY_BUILD) SmallTile(if (busy) "…" else stringResource(R.string.crash_fix), Icons.Default.Healing, Modifier.weight(1f), tint = Color(0xFFFF8A4C)) {
                    if (busy) return@SmallTile
                    if (crashActive) crashMsg = act.getString(R.string.crash_fix_active, repo.crashFixHoursLeft()) else crashAsk = true
                }
            }
            if (serverAsk) ChangeServerDialog("server") { serverAsk = false }
            if (plAsk) ChangeServerDialog("playlist") { plAsk = false }
            val cfp by repo.crashFixProgress.collectAsState()
            if (busy) androidx.compose.material3.AlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text(stringResource(R.string.crash_fix)) },
                text = { Row(verticalAlignment = Alignment.CenterVertically) { androidx.compose.material3.CircularProgressIndicator(color = c.accent, modifier = Modifier.size(28.dp)); Spacer(Modifier.width(14.dp)); Text(if (cfp > 0) stringResource(R.string.preparing_line) + "  " + cfp + "/12" else stringResource(R.string.loading), fontSize = 14.sp) } })
            if (crashAsk) androidx.compose.material3.AlertDialog(onDismissRequest = { crashAsk = false }, title = { Text(stringResource(R.string.crash_fix)) }, text = { Text(stringResource(R.string.crash_fix_q)) },
                confirmButton = { BigButton(stringResource(R.string.ok)) { crashAsk = false; busy = true; scope.launch { val e = repo.crashFix(); busy = false
                    if (e == null) try { android.widget.Toast.makeText(act, act.getString(R.string.switched_to, repo.activePlaylist?.name ?: ""), android.widget.Toast.LENGTH_LONG).show() } catch (x: Exception) {}
                    crashMsg = when { e == null -> "✅ " + act.getString(R.string.crash_fix_on) + " — " + (repo.activePlaylist?.name ?: ""); e == "crash_off" -> act.getString(R.string.crash_fix_off); e == "expired" -> act.getString(R.string.crash_fix_expired)
                        e.startsWith("cooldown:") -> act.getString(R.string.crash_fix_wait, e.substringAfter(':').toIntOrNull() ?: 5); e.startsWith("daily_limit:") -> act.getString(R.string.crash_fix_limit, e.substringAfter(':').toIntOrNull() ?: 3)
                        e == "panel_refused" -> act.getString(R.string.crash_fix_refused); else -> "❌ " + e }
                    if (e == null) nav.reset(Screen.Main(0)) } } },
                dismissButton = { BigButton(stringResource(R.string.cancel), filled = false) { crashAsk = false } })
            // continue watching: the last things played, with a progress bar · tap = resume · long-press = remove
            val cw = remember(pv) { repo.resumeList().take(12) }
            if (cw.isNotEmpty() && midTall) {
                Gap(8)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(stringResource(R.string.continue_watching).uppercase()); Spacer(Modifier.weight(1f))
                    Pill(stringResource(R.string.see_all) + " " + fwdArrow(), false) { nav.push(Screen.Continue) }
                }
                LazyRow(Modifier.restoreFocus(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(cw, key = { "cw:" + it.key }) { r -> ContinueCard(r, onClick = { resumePlay(repo, nav, r) }, onRemove = { repo.removeResume(r.key) }) } }
            }
            val favs = remember(pv) { repo.favorites().distinct().take(20) }
            val favItems = remember(favs, active?.id, tick) { favs.mapNotNull { k -> Kind.values().firstNotNullOfOrNull { kind -> active?.let { pl -> repo.cachedContent(pl, kind)?.items?.firstOrNull { it.key == k } } } }.distinctBy { it.key } }
            if (favItems.isNotEmpty() && tall) {
                Gap(10); SectionTitle(stringResource(R.string.favorites).uppercase())
                LazyRow(Modifier.restoreFocus(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(favItems, key = { "fav:" + it.key }) { it -> PosterCard(it, 84) { scope.launch { active?.let { pl -> if (it.kind == Kind.SERIES) nav.push(Screen.Series(it)) else if (it.kind == Kind.MOVIE) nav.push(Screen.Movie(it)) else { repo.liveContext = favItems.filter { x -> x.kind == Kind.LIVE }; playItem(repo, nav, pl, it) } } } } } }
            }
            Spacer(Modifier.weight(0.01f))
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text((if (acc != null && !anon) acc.username else "") + (if (active != null && (active.trial || playlists.size > 1)) "   ▶ " + active.name + (if (active.trial) " ⚡ " + repo.crashFixHoursLeft() + "h" else "") else ""), color = if (active?.trial == true) Color(0xFFF97316) else c.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                if (crashActive) { Text(stringResource(R.string.back_now), color = Color(0xFFF97316), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(6.dp)).tvFocus(c.accent, 6).clickable { repo.endCrashFix(); nav.reset(Screen.Main(0)) }.padding(6.dp, 2.dp)); Spacer(Modifier.width(10.dp)) }
                Text("v" + tv.ninekpro.app.BuildConfig.VERSION_NAME, color = c.muted.copy(alpha = 0.6f), fontSize = 11.sp)
            }
        } }
    }
}

/**
 * Sideload flavour only: the in-app update banner (Home and Settings → App update). Text follows `Updater.progress`:
 * available → "Update" starts the download · 0..99 "Downloading update… N %" · 100 "Update downloaded — tap to install" (or "Installing…" once
 * the installer opened) · API 26+ permission missing → "Allow installing updates…" · failed → "Download failed — tap to retry", and after two
 * failures the button opens the APK link in the browser instead.
 */
@Composable
fun UpdateBanner(u: tv.ninekpro.app.data.UpdateInfo) {
    val c = LocalColors.current; val act = LocalActivity.current
    val progress by tv.ninekpro.app.data.Updater.progress.collectAsState()
    val askPerm by tv.ninekpro.app.data.Updater.askPermission.collectAsState()
    val installing by tv.ninekpro.app.data.Updater.installing.collectAsState()
    val failures by tv.ninekpro.app.data.Updater.failures.collectAsState()
    val text: String = when {
        askPerm -> stringResource(R.string.update_allow)
        progress in 0..99 -> stringResource(R.string.update_downloading, progress)
        progress == 100 && installing -> stringResource(R.string.update_installing)
        progress == 100 -> stringResource(R.string.update_ready)
        progress == tv.ninekpro.app.data.Updater.FAILED -> stringResource(R.string.update_failed)
        else -> stringResource(R.string.update_available, u.name)
    }
    val browser: Boolean = progress == tv.ninekpro.app.data.Updater.FAILED && failures >= 2
    val action: String? = if (progress in 0..99) null else if (browser) stringResource(R.string.update_browser) else stringResource(R.string.update)
    val color: Color = if (progress == tv.ninekpro.app.data.Updater.FAILED) Color(0xFFE11D48) else c.accent
    Banner(text, color, action) { if (browser) tv.ninekpro.app.data.Updater.openInBrowser(act, u) else tv.ninekpro.app.data.Updater.start(act, u) }
}

/** Change server: pick another playlist (line), or another host of the current playlist. */
@Composable
fun ChangeServerDialog(mode: String, onClose: () -> Unit) {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current
    val playlists by repo.playlists.collectAsState(); val active = repo.activePlaylist
    androidx.compose.material3.AlertDialog(onDismissRequest = onClose, confirmButton = { BigButton(stringResource(R.string.cancel), filled = false) { onClose() } }, title = { Text(stringResource(if (mode == "playlist") R.string.change_playlist else R.string.change_server)) },
        text = { Column {
            if (mode == "playlist") { if (playlists.size <= 1) Text(stringResource(R.string.one_playlist_only), color = c.muted, fontSize = 13.sp)
                playlists.forEach { pl -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (pl.id == active?.id) c.accent.copy(alpha = 0.18f) else Color.Transparent).tvFocus(c.accent, 8).clickable { repo.prefs.activePlaylistId = pl.id; onClose(); nav.reset(Screen.Playlists); }.padding(10.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(pl.name + (if (pl.trial) " ⚡" else "") + (if (pl.panel.isNotEmpty()) "  ·  " + pl.panel else ""), color = if (pl.id == active?.id) c.accent else c.text, fontSize = 14.sp, modifier = Modifier.weight(1f)); if (pl.id == active?.id) Text("✓", color = c.accent) } } }
            if (mode == "server" && active != null && active.hosts.size > 1) { SectionTitle(stringResource(R.string.servers).uppercase())
                val cur = repo.currentHost(active)
                active.hosts.forEachIndexed { i, h -> val on = cur.isNotEmpty() && h.contains(cur.removePrefix("http://").removePrefix("https://"))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (on) c.accent.copy(alpha = 0.18f) else Color.Transparent).tvFocus(c.accent, 8).clickable { repo.setHost(active, h); onClose(); nav.reset(Screen.Main(0)) }.padding(10.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.server_n, i + 1), color = if (on) c.accent else c.text, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis); if (on) Text("✓", color = c.accent) } } }
            if (mode == "server" && (active == null || active.hosts.size <= 1)) Text(stringResource(R.string.one_server_only), color = c.muted, fontSize = 13.sp)
        } })
}

/** Reseller banner from the panel: picture (optional) + title/text + button that opens the link. */
@Composable
fun PromoBanner(m: tv.ninekpro.app.data.Message, onClose: () -> Unit) {
    val c = LocalColors.current; val act = LocalActivity.current
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).glass(c, 14)) {
        if (m.image.isNotEmpty()) AsyncImage(model = m.image, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(120.dp))
        Row(Modifier.fillMaxWidth().then(if (m.image.isNotEmpty()) Modifier.height(120.dp).background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(c.bg3.copy(alpha = 0.92f), Color.Transparent))) else Modifier).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { if (m.title.isNotEmpty()) Text(m.title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(m.text, color = c.text.copy(alpha = 0.9f), fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.width(10.dp))
            if (m.link.isNotEmpty()) BigButton(stringResource(R.string.open)) { try { act.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(m.link))) } catch (e: Exception) {} }
            Spacer(Modifier.width(6.dp)); Pill("✕", false) { onClose() }
        }
    }
}

@Composable
fun BigTile(text: String, icon: ImageVector, modifier: Modifier, sub: String? = null, onClick: () -> Unit) {
    val c = LocalColors.current
    Column(modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).glass(c, 14).tvFocus(c.accent, 14).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(32.dp)).background(c.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = c.accent, modifier = Modifier.size(38.dp)) }
        Spacer(Modifier.height(8.dp)); Text(text, color = c.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        if (sub != null) Text(sub, color = c.muted, fontSize = 11.5.sp)
    }
}

@Composable
fun SmallTile(text: String, icon: ImageVector, modifier: Modifier, tint: Color? = null, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).glass(c, 10).tvFocus(c.accent, 10).clickable(onClick = onClick).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint ?: c.accent, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text(text, color = c.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun PosterCard(it: tv.ninekpro.app.data.Item, w: Int, onClick: () -> Unit) {
    val c = LocalColors.current; val repo = LocalRepo.current
    Column(Modifier.width(w.dp).clip(RoundedCornerShape(8.dp)).tvFocus(c.accent, 8).clickable(onClick = onClick)) {
        Box { Poster(it.icon, Modifier.fillMaxWidth().height(if (it.kind == Kind.LIVE) 70.dp else (w * 1.45f).dp), contentScaleFit = it.kind == Kind.LIVE)
            if (it.rating.isNotEmpty() && it.rating != "0") Text("★ " + it.rating.take(3), color = Color.White, fontSize = 10.sp, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(Color(0x99000000), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 2.dp))
            repo.resumeOf(it.key)?.let { r -> if (r.durationMs > 0) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth((r.positionMs.toFloat() / r.durationMs).coerceIn(0.02f, 1f)).height(3.dp).background(c.accent)) } }
        Text(it.name, color = c.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(4.dp))
    }
}

/** Home "Continue watching" card: wide thumbnail + progress bar + title. Tap = resume, long-press = remove from the list. */
@Composable
fun ContinueCard(r: ResumeEntry, onClick: () -> Unit, onRemove: () -> Unit) {
    val c = LocalColors.current
    Column(Modifier.width(124.dp).clip(RoundedCornerShape(8.dp)).tvFocus(c.accent, 8).combinedClickable(onClick = onClick, onLongClick = onRemove)) {
        Box { Poster(r.image, Modifier.fillMaxWidth().height(70.dp), contentScaleFit = r.kind == "LIVE")
            if (r.durationMs > 0) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x66000000))) { Box(Modifier.fillMaxWidth((r.positionMs.toFloat() / r.durationMs).coerceIn(0.02f, 1f)).height(3.dp).background(c.accent)) } }
        Text(r.title, color = c.text, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(4.dp, 3.dp))
    }
}

fun openWhatsApp(act: android.app.Activity, number: String, text: String) {
    try { act.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/" + number.filter { it.isDigit() } + "?text=" + android.net.Uri.encode(text)))) } catch (e: Exception) { }
}
