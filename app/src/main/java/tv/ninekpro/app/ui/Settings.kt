package tv.ninekpro.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Support
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.ninekpro.app.BuildConfig
import tv.ninekpro.app.R
import tv.ninekpro.app.data.Category
import tv.ninekpro.app.data.Kind

/** Settings: the classic tile grid, same tiles in the same order as the old app. Each tile opens a panel on the same screen. */
@Composable
fun SettingsScreen(themeMode: String, onTheme: (String) -> Unit) {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current; val act = LocalActivity.current
    val brand by repo.brand.collectAsState()
    var panel by remember { mutableStateOf("") }
    var add by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = panel.isNotEmpty()) { panel = "" }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (panel.isNotEmpty()) panel = "" else nav.pop() }, modifier = Modifier.tvFocus(c.accent, 24)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = c.text) }
            Spacer(Modifier.weight(1f)); Text(stringResource(R.string.settings), color = c.text, fontSize = 18.sp); Spacer(Modifier.weight(1f)); Spacer(Modifier.width(48.dp))
        }
        Box(Modifier.weight(1f)) {
            if (panel.isEmpty()) LazyVerticalGrid(columns = GridCells.Fixed(4), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Tile(stringResource(R.string.add_playlist), Icons.Default.Add) { add = true } }
                item { Tile(stringResource(R.string.parental), Icons.Default.Lock) { panel = "pin" } }
                item { Tile(stringResource(R.string.playlists), Icons.AutoMirrored.Filled.PlaylistPlay) { nav.reset(Screen.Playlists) } }
                item { Tile(stringResource(R.string.change_language), Icons.Default.Language) { panel = "lang" } }
                item { Tile(stringResource(R.string.change_layout), Icons.Default.GridView) { panel = "layout" } }
                item { Tile(stringResource(R.string.hide_live), Icons.Default.VisibilityOff) { panel = "hideL" } }
                item { Tile(stringResource(R.string.hide_vod), Icons.Default.VisibilityOff) { panel = "hideM" } }
                item { Tile(stringResource(R.string.hide_series), Icons.Default.VisibilityOff) { panel = "hideS" } }
                item { Tile(stringResource(R.string.clear_channels), Icons.Default.DeleteSweep) { repo.clearResume("LIVE"); repo.setLastChannel(""); panel = "done" } }
                item { Tile(stringResource(R.string.clear_movies), Icons.Default.DeleteSweep) { repo.clearResume("MOVIE"); panel = "done" } }
                item { Tile(stringResource(R.string.clear_series), Icons.Default.DeleteSweep) { repo.clearResume("SERIES"); panel = "done" } }
                item { Tile(stringResource(R.string.live_sort), Icons.AutoMirrored.Filled.Sort) { panel = "sort" } }
                item { Tile(stringResource(R.string.live_format), Icons.Default.Videocam) { panel = "format" } }
                item { Tile(stringResource(R.string.change_player), Icons.Default.PlayCircle) { panel = "player" } }
                item { Tile(stringResource(R.string.external_players), Icons.Default.OpenInNew) { panel = "external" } }
                item { Tile(stringResource(R.string.automatic), Icons.Default.AutoMode) { panel = "auto" } }
                item { Tile(stringResource(R.string.time_format), Icons.Default.Schedule) { panel = "time" } }
                item { Tile(stringResource(R.string.subtitle_settings), Icons.Default.ClosedCaption) { panel = "subs" } }
                item { Tile(stringResource(R.string.device_type), Icons.Default.Devices) { panel = "device" } }
                item { Tile(stringResource(R.string.update_now), Icons.Default.Refresh) { repo.forceReload(); repo.syncNow(); nav.reset(Screen.Main(0)) } }
                item { Tile(stringResource(R.string.parental_onoff), if (repo.prefs.parentalOn) Icons.Default.Lock else Icons.Default.LockOpen) { panel = "ponoff" } }
                item { Tile(stringResource(R.string.theme), Icons.Default.Palette) { panel = "theme" } }
                item { Tile(stringResource(R.string.speed_test), Icons.Default.Speed) { panel = "speed" } }
                item { Tile(stringResource(R.string.support), Icons.Default.Support) { panel = "support" } }
                item { Tile(stringResource(R.string.report_problem), Icons.Default.BugReport) { panel = "report"; Thread { tv.ninekpro.app.Crash.send("REPORT from Settings\nplaylist=" + (repo.activePlaylist?.name ?: "-") + " engine=" + repo.prefs.playerEngine + " format=" + repo.prefs.liveFormat + " lite=" + tv.ninekpro.app.data.DeviceInfo.lite + " tv=" + tv.ninekpro.app.data.DeviceInfo.isTv + "\nlast error: " + (tv.ninekpro.app.Crash.lastTrace(act) ?: "none")) }.start() } }
                item { Tile(stringResource(R.string.logout), Icons.Default.Logout) { repo.logout(); nav.reset(Screen.Playlists) } }
            } else Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
                when (panel) {
                    "pin" -> { var pin by remember { mutableStateOf(repo.pin) }; SectionTitle(stringResource(R.string.parental).uppercase()); Field(pin, { pin = it.filter { ch -> ch.isDigit() }.take(6); repo.pin = pin }, stringResource(R.string.set_pin), password = true); Gap(8); Text(stringResource(R.string.parental_hint), color = c.muted, fontSize = 12.sp) }
                    "ponoff" -> { var on by remember { mutableStateOf(repo.prefs.parentalOn) }; ToggleRow(stringResource(R.string.parental_onoff), on) { on = it; repo.prefs.parentalOn = it } }
                    "lang" -> { SectionTitle(stringResource(R.string.change_language).uppercase()); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("" to "Auto", "en" to "English", "ar" to "العربية", "iw" to "עברית").forEach { (k, n) -> Pill(n, repo.prefs.language == k) { repo.prefs.language = k; act.recreate() } } } }
                    "layout" -> { SectionTitle(stringResource(R.string.change_layout).uppercase()); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("large" to R.string.layout_large, "normal" to R.string.layout_normal, "compact" to R.string.layout_compact).forEach { (k, r) -> Pill(stringResource(r), repo.prefs.layoutSize == k) { repo.prefs.layoutSize = k; panel = ""; panel = "layout" } } } }
                    "hideL" -> HideCategories(Kind.LIVE)
                    "hideM" -> HideCategories(Kind.MOVIE)
                    "hideS" -> HideCategories(Kind.SERIES)
                    "sort" -> { SectionTitle(stringResource(R.string.live_sort).uppercase()); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("default" to R.string.sort_default, "az" to R.string.sort_az, "number" to R.string.sort_number).forEach { (k, r) -> Pill(stringResource(r), repo.prefs.liveSort == k) { repo.prefs.liveSort = k; panel = ""; panel = "sort" } } } }
                    "format" -> { SectionTitle(stringResource(R.string.live_format).uppercase()); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("auto" to "Auto", "hls" to "HLS (m3u8)", "ts" to "MPEG-TS").forEach { (k, n) -> Pill(n, repo.prefs.liveFormat == k) { repo.prefs.liveFormat = k; panel = ""; panel = "format" } } }; Gap(8); Text(stringResource(R.string.format_hint), color = c.muted, fontSize = 12.sp) }
                    "player" -> { SectionTitle(stringResource(R.string.change_player).uppercase()); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("auto" to "Auto", "exo" to "ExoPlayer", "vlc" to "VLC").forEach { (k, n) -> Pill(n, repo.prefs.playerEngine == k) { repo.prefs.playerEngine = k; panel = ""; panel = "player" } } }; Gap(8); Text(stringResource(R.string.player_hint), color = c.muted, fontSize = 12.sp) }
                    "external" -> { SectionTitle(stringResource(R.string.external_players).uppercase()); ToggleRow(stringResource(R.string.external_hint), repo.prefs.playerEngine == "external") { repo.prefs.playerEngine = if (it) "external" else "auto"; panel = ""; panel = "external" } }
                    "auto" -> { var a by remember { mutableStateOf(repo.prefs.autoRefresh) }; ToggleRow(stringResource(R.string.automatic_hint), a) { a = it; repo.prefs.autoRefresh = it } }
                    "time" -> { SectionTitle(stringResource(R.string.time_format).uppercase()); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Pill("24h", repo.prefs.timeFormat == "24") { repo.prefs.timeFormat = "24"; panel = ""; panel = "time" }; Pill("12h", repo.prefs.timeFormat == "12") { repo.prefs.timeFormat = "12"; panel = ""; panel = "time" } } }
                    "subs" -> { SectionTitle(stringResource(R.string.subtitle_size).uppercase()); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(0.8f, 1f, 1.3f, 1.6f, 2f).forEach { sc -> Pill((sc * 100).toInt().toString() + "%", repo.prefs.subtitleScale == sc) { repo.prefs.subtitleScale = sc; panel = ""; panel = "subs" } } }
                        Gap(10); SectionTitle(stringResource(R.string.subtitle_lang).uppercase()); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("ar" to "العربية", "he" to "עברית", "en" to "English", "" to stringResource(R.string.none)).forEach { (k, n) -> Pill(n, repo.prefs.subLang == k) { repo.prefs.subLang = k; panel = ""; panel = "subs" } } }
                        Gap(10); SectionTitle(stringResource(R.string.subtitle_color).uppercase()); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("white", "yellow", "cyan").forEach { k -> Pill(k, repo.prefs.subColor == k) { repo.prefs.subColor = k; panel = ""; panel = "subs" } } } }
                    "device" -> { SectionTitle(stringResource(R.string.device_type).uppercase()); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("auto" to "Auto", "tv" to "TV", "phone" to "Phone").forEach { (k, n) -> Pill(n, repo.prefs.deviceType == k) { repo.prefs.deviceType = k; panel = ""; panel = "device" } } } }
                    "theme" -> { SectionTitle(stringResource(R.string.theme).uppercase()); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Pill("Gold", themeMode == "gold") { onTheme("gold") }; Pill(stringResource(R.string.theme_navy), themeMode == "navy") { onTheme("navy") }; Pill(stringResource(R.string.theme_light), themeMode == "light") { onTheme("light") }; Pill(stringResource(R.string.theme_amoled), themeMode == "amoled") { onTheme("amoled") } } }
                    "support" -> { SectionTitle(stringResource(R.string.support).uppercase())
                        var waQr by remember { mutableStateOf(false) }
                        if (waQr) WaQrDialog(brand.whatsapp, "") { waQr = false }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (brand.whatsapp.isNotEmpty()) Pill("WhatsApp", false) { if (tv.ninekpro.app.data.DeviceInfo.isTv || repo.prefs.deviceType == "tv") waQr = true else openWhatsApp(act, brand.whatsapp, "") }
                        if (brand.telegram.isNotEmpty()) Pill("Telegram", false) { try { act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(brand.telegram))) } catch (e: Exception) {} }
                        if (brand.website.isNotEmpty()) Pill("Web", false) { try { act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(brand.website))) } catch (e: Exception) {} } } }
                    "done" -> { Text("✓ " + stringResource(R.string.ok), color = c.text, fontSize = 16.sp) }
                    "report" -> { Text("✓ " + stringResource(R.string.report_sent), color = c.text, fontSize = 16.sp) }
                    "speed" -> SpeedTestPanel()
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Mac Address: " + repo.prefs.deviceMac(), color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("Device Key: " + repo.prefs.deviceKey(), color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("v" + BuildConfig.VERSION_NAME, color = c.muted, fontSize = 11.sp)
        }
    }
    if (add) AddPlaylistDialog(onClose = { add = false })
}

/** Hide Live / VOD / Series categories: a checkbox list, saved to the profile (synced). */
@Composable
fun HideCategories(kind: Kind) {
    val repo = LocalRepo.current; val c = LocalColors.current
    val pl = repo.activePlaylist ?: return
    val pv by repo.profileVersion.collectAsState()
    var cats by remember { mutableStateOf<List<Category>>(emptyList()) }
    LaunchedEffect(pl.id, kind) { try { cats = repo.content(pl, kind).categories } catch (e: Throwable) {} }
    val hidden = remember(pv) { repo.hidden() }
    Column(Modifier.fillMaxSize()) {
        SectionTitle(when (kind) { Kind.LIVE -> stringResource(R.string.hide_live); Kind.MOVIE -> stringResource(R.string.hide_vod); Kind.SERIES -> stringResource(R.string.hide_series) }.uppercase())
        LazyColumn(Modifier.fillMaxSize()) {
            items(cats, key = { it.id }) { cat ->
                val h = hidden.contains("cat:" + cat.id)
                Row(Modifier.fillMaxWidth().tvFocus(c.accent, 6).clickable { repo.setHidden("cat:" + cat.id, !h) }.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = h, onCheckedChange = { repo.setHidden("cat:" + cat.id, it) }); Text(cat.name, color = c.text, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun Tile(text: String, icon: ImageVector, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(8.dp)).glass(c, 8).tvFocus(c.accent, 8).clickable(onClick = onClick).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.text, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text(text, color = c.text, fontSize = 13.5.sp, maxLines = 1)
    }
}

@Composable
fun ToggleRow(text: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp)).glass(c, 8).tvFocus(c.accent, 8).clickable { onChange(!value) }.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = c.text, modifier = Modifier.weight(1f)); Switch(checked = value, onCheckedChange = onChange)
    }
}


/** Speed test: pings the current host, then downloads 8 s of the first live channel and reports Mbps + a plain verdict. */
@Composable
fun SpeedTestPanel() {
    val repo = LocalRepo.current; val c = LocalColors.current; val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }; var ping by remember { mutableStateOf(-1L) }; var mbps by remember { mutableStateOf(-1.0) }; var err by remember { mutableStateOf("") }; var host by remember { mutableStateOf("") }
    SectionTitle(stringResource(R.string.speed_test).uppercase())
    Text(host, color = c.muted, fontSize = 12.sp); Gap(8)
    if (ping >= 0) Text(stringResource(R.string.speed_ping) + ": " + ping + " ms", color = c.text, fontSize = 16.sp)
    if (mbps >= 0) { Text(stringResource(R.string.speed_download) + ": " + String.format(java.util.Locale.US, "%.1f", mbps) + " Mbps", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(stringResource(when { mbps < 3 -> R.string.speed_verdict_bad; mbps < 8 -> R.string.speed_verdict_sd; mbps < 25 -> R.string.speed_verdict_hd; else -> R.string.speed_verdict_4k }), color = if (mbps < 3) Color(0xFFFF6B7A) else c.accent, fontSize = 14.sp) }
    if (err.isNotEmpty()) Text(err, color = Color(0xFFFF6B7A), fontSize = 14.sp)
    if (running) Text(stringResource(R.string.speed_testing), color = c.muted, fontSize = 14.sp)
    Gap(10)
    BigButton(stringResource(R.string.speed_start), Icons.Default.Speed) { if (running) return@BigButton; running = true; err = ""; ping = -1; mbps = -1.0
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try { val pl = repo.activePlaylist ?: throw Exception("no playlist"); val h = repo.host(pl); host = h.replace(Regex("^https?://"), "")
                val t0 = System.currentTimeMillis(); repo.xtream.ping(h); ping = System.currentTimeMillis() - t0
                val item = repo.content(pl, Kind.LIVE).items.firstOrNull() ?: throw Exception("no channel"); val url = repo.streamUrl(pl, item, ts = true)
                val client = okhttp3.OkHttpClient.Builder().connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS).readTimeout(15, java.util.concurrent.TimeUnit.SECONDS).build()
                val resp = client.newCall(okhttp3.Request.Builder().url(url).header("User-Agent", "9KProTV/1.0").build()).execute()
                val body = resp.body ?: throw Exception("HTTP " + resp.code); val ins = body.byteStream(); val buf = ByteArray(64 * 1024); var total = 0L; val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < 8000) { val n = ins.read(buf); if (n < 0) break; total += n; if (System.currentTimeMillis() - start > 1500) mbps = total * 8.0 / 1e6 / ((System.currentTimeMillis() - start) / 1000.0) }
                try { ins.close(); resp.close() } catch (e: Exception) {}
                val secs = (System.currentTimeMillis() - start) / 1000.0; mbps = if (secs > 0) total * 8.0 / 1e6 / secs else 0.0
            } catch (e: Throwable) { err = "✕ " + (e.message ?: "error") }
            running = false
        } }
}
