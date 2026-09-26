package tv.ninekpro.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tv.ninekpro.desktop.Updater
import tv.ninekpro.desktop.data.AppInfo
import tv.ninekpro.desktop.data.Playlist

@Composable
fun SettingsScreen(onTheme: (String) -> Unit, onLang: (String) -> Unit) {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current; val win = LocalWindow.current; val lang = LocalLang.current
    var pick by remember { mutableStateOf("") }   // which chooser is open
    var pinAsk by remember { mutableStateOf(false) }; var pin by remember { mutableStateOf("") }
    var tick by remember { mutableStateOf(0) }
    val account by repo.account.collectAsState()
    fun s(k: String) = Strings.get(lang, k)
    fun choose(title: String, options: List<Pair<String, String>>, cur: String, on: (String) -> Unit) { pick = title; chooser = Triple(options, cur, on) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        BackRow(T("settings"))
        LazyVerticalGrid(GridCells.Adaptive(220.dp), Modifier.padding(horizontal = 16.dp).height(520.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { STile(Icons.Default.PlaylistPlay, s("playlists"), repo.activePlaylist?.name ?: "") { nav.push(Screen.Playlists) } }
            item { STile(Icons.Default.Language, s("change_language"), when (lang) { "ar" -> "العربية"; "iw" -> "עברית"; else -> "English" }) { choose("lang", listOf("" to s("automatic"), "en" to "English", "ar" to "العربية", "iw" to "עברית"), repo.prefs.language) { onLang(it) } } }
            item { STile(Icons.Default.Palette, s("theme"), repo.prefs.theme.ifEmpty { "gold" }) { choose("theme", listOf("gold" to "Gold", "navy" to s("theme_navy"), "light" to s("theme_light"), "amoled" to s("theme_amoled")), repo.prefs.theme.ifEmpty { "gold" }) { onTheme(it); tick++ } } }
            item { STile(Icons.Default.Hd, s("live_format"), repo.prefs.liveFormat.uppercase()) { choose("fmt", listOf("auto" to s("automatic"), "hls" to "HLS (m3u8)", "ts" to "MPEG-TS"), repo.prefs.liveFormat) { repo.prefs.liveFormat = it; tick++ } } }
            item { STile(Icons.Default.Sort, s("live_sort"), repo.prefs.liveSort) { choose("sort", listOf("default" to s("sort_default"), "az" to s("sort_az"), "number" to s("sort_number")), repo.prefs.liveSort) { repo.prefs.liveSort = it; tick++ } } }
            item { STile(Icons.Default.Schedule, s("time_format"), repo.prefs.timeFormat + "h") { choose("time", listOf("24" to "24h", "12" to "12h"), repo.prefs.timeFormat) { repo.prefs.timeFormat = it; tick++ } } }
            item { STile(Icons.Default.Lock, s("parental_onoff"), if (repo.prefs.parentalOn) "ON" else "OFF") { if (repo.prefs.pin.isEmpty()) pinAsk = true else { pin = ""; pinAsk = true } } }
            item { STile(Icons.Default.Refresh, s("update_now"), s("update_hint")) { repo.forceReload(); win.toast(Strings.get(lang, "refreshing")) } }
            item { STile(Icons.Default.History, s("clear_history"), "") { repo.prefs.profile = repo.prefs.profile.apply { remove("resume") }; repo.profileVersion.value++; win.toast("OK") } }
            item { STile(Icons.Default.SystemUpdate, s("app_version"), AppInfo.VERSION + " · " + (Updater.status.ifEmpty { "" })) { Updater.checkNow(repo.prefs) { win.toast(it) } } }
            item { STile(Icons.Default.Logout, s("logout"), account?.username ?: "") { repo.logout(); VlcPlayer.shared.stop(); nav.reset(Screen.Login) } }
            item { STile(Icons.Default.PowerSettingsNew, s("exit"), "") { win.exit() } }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().glass(c, 12).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(T("device_id"), color = c.muted, fontSize = 11.sp); Text(repo.prefs.deviceMac().uppercase(), color = c.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }
            Column(Modifier.weight(1f)) { Text(T("device_key"), color = c.muted, fontSize = 11.sp); Text(repo.prefs.deviceKey(), color = c.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }
            Column(Modifier.weight(1f)) { Text(T("app_version"), color = c.muted, fontSize = 11.sp); Text(AppInfo.VERSION + " " + AppInfo.platform, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            PBtn(T("copy")) { try { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(repo.prefs.deviceMac().uppercase() + "  " + repo.prefs.deviceKey()), null); win.toast(Strings.get(lang, "copied")) } catch (e: Throwable) {} }
        }
        Text(T("shortcuts"), color = c.muted, fontSize = 11.5.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
    }
    val ch = chooser
    if (pick.isNotEmpty() && ch != null) AlertDialog(onDismissRequest = { pick = "" }, confirmButton = {}, title = { Text(pick) }, text = {
        Column { ch.first.forEach { (v, n) -> Text(n, color = if (v == ch.second) c.accent else c.text, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().clickable { ch.third(v); pick = "" }.padding(10.dp)) } } })
    if (pinAsk) AlertDialog(onDismissRequest = { pinAsk = false }, title = { Text(if (repo.prefs.pin.isEmpty()) T("set_pin") else T("enter_pin")) },
        text = { OutlinedTextField(pin, { pin = it.filter { ch2 -> ch2.isDigit() }.take(4) }, singleLine = true, colors = fieldColors(c)) },
        confirmButton = { TextButton({ if (repo.prefs.pin.isEmpty()) { if (pin.length == 4) { repo.prefs.pin = pin; pinAsk = false } } else if (pin == repo.prefs.pin) { repo.prefs.parentalOn = !repo.prefs.parentalOn; if (!repo.prefs.parentalOn) repo.unlockedUntil = System.currentTimeMillis() + 30 * 60_000L; repo.profileVersion.value++; pinAsk = false; tick++ } else win.toast(Strings.get(lang, "wrong_pin")) }) { Text(T("ok")) } },
        dismissButton = { TextButton({ pinAsk = false }) { Text(T("cancel")) } })
}
private var chooser: Triple<List<Pair<String, String>>, String, (String) -> Unit>? by mutableStateOf(null)

@Composable
fun STile(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) { val c = LocalColors.current
    Row(Modifier.fillMaxWidth().height(74.dp).glass(c, 12).hoverGlow(c.accent, 12, 1.03f).clickable { onClick() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(26.dp)); Spacer(Modifier.width(10.dp))
        Column { Text(title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); if (sub.isNotEmpty()) Text(sub, color = c.muted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } } }

/** Playlists = the lines the panel gave this device + own Xtream/M3U ones; pick the active one, pick a server. */
@Composable
fun PlaylistsScreen() {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current; val win = LocalWindow.current; val scope = rememberCoroutineScope()
    val pls by repo.playlists.collectAsState(); val pv by repo.profileVersion.collectAsState()
    var add by remember { mutableStateOf(false) }; var user by remember { mutableStateOf("") }; var pass by remember { mutableStateOf("") }; var addErr by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        BackRow(T("playlists"))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            pls.forEach { pl ->
                val active = pl.id == repo.activePlaylist?.id
                Column(Modifier.fillMaxWidth().glass(c, 12, selected = active).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(pl.name.ifEmpty { pl.user }, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            Text(listOf(if (pl.protect) T("protected_pl") else pl.user, pl.panel, if (pl.exp.isNotEmpty()) T("expires") + " " + pl.exp else "", if (pl.trial) "Crash Fix" else "").filter { it.isNotBlank() }.joinToString(" · "), color = c.muted, fontSize = 12.5.sp) }
                        if (active) Text(T("connected"), color = c.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp) else PBtn(T("change_playlist")) { VlcPlayer.shared.stop(); repo.setActive(pl.id); scope.launch { repo.prepareActive() }; win.toast(String.format(Strings.get(Strings.lang(repo.prefs.language), "switched_to"), pl.name)) }
                        if (!pl.fromPanel) { Spacer(Modifier.width(8.dp)); PBtn(T("delete")) { repo.prefs.manualPlaylists = repo.prefs.manualPlaylists.filter { it.id != pl.id }; repo.playlists.value = repo.prefs.panelPlaylists + repo.prefs.manualPlaylists } }
                    }
                    if (pl.hosts.size > 1 && !pl.protect) { Spacer(Modifier.height(8.dp)); Text(T("servers"), color = c.muted, fontSize = 11.5.sp)
                        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { pl.hosts.forEachIndexed { i, h -> val cur = repo.currentHost(pl) == repo.xtream.baseUrl(h)
                            Text(String.format(Strings.get(Strings.lang(repo.prefs.language), "server_n"), i + 1), color = if (cur) Color.White else c.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (cur) c.accent else c.card).clickable { VlcPlayer.shared.stop(); repo.setHost(pl, h); win.toast("OK") }.padding(horizontal = 10.dp, vertical = 6.dp)) } } }
                }
            }
            // Only accounts made in the panel: "Add playlist" = sign in with another 9K Pro TV account (its lines are added, nothing replaced).
            PBtn(T("add_playlist"), Icons.Default.Add) { add = !add }
            if (add) Column(Modifier.fillMaxWidth().glass(c, 12).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(user, { user = it }, label = { Text(T("username")) }, singleLine = true, colors = fieldColors(c), modifier = Modifier.weight(1f)); OutlinedTextField(pass, { pass = it }, label = { Text(T("password")) }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), colors = fieldColors(c), modifier = Modifier.weight(1f)) }
                if (addErr.isNotEmpty()) Text(addErr, color = Color(0xFFFF6B7A), fontSize = 12.5.sp)
                PBtn(T("sign_in"), primary = true) { if (user.isNotBlank() && pass.isNotBlank()) scope.launch { addErr = ""; val e = repo.login(user.trim(), pass); if (e == null) { add = false; user = ""; pass = ""; scope.launch { repo.prepareActive() }; win.toast("OK") } else addErr = when (e) { "wrong_login" -> Strings.get(Strings.lang(repo.prefs.language), "error_login"); "expired" -> Strings.get(Strings.lang(repo.prefs.language), "error_expired"); "network" -> Strings.get(Strings.lang(repo.prefs.language), "error_network"); else -> e } } }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}
