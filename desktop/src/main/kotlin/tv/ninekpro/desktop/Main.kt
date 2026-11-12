package tv.ninekpro.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.ninekpro.desktop.data.Crash
import tv.ninekpro.desktop.data.DlStore
import tv.ninekpro.desktop.data.Kind
import tv.ninekpro.desktop.data.Repo
import tv.ninekpro.desktop.ui.*

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
fun main() {
    Updater.applyPendingOnStart()
    val repo = Repo()
    Crash.install(repo.panel)                       // uncaught errors → crash.log + panel
    Vlc.hwDecode = repo.prefs.hwDecode; Vlc.deinterlace = repo.prefs.deinterlace; Vlc.netCache = repo.prefs.netCache
    Vlc.initAsync()                                 // libVLC loads while the window is already on screen
    try { DlStore.init(repo.prefs) } catch (e: Throwable) { Crash.report(e, "DlStore.init") }
    VlcPlayer.shared.onError = { url -> repo.notePlayError(VlcPlayer.shared.title, url) }
    application {
        val p0 = repo.prefs
        val state = rememberWindowState(size = DpSize(p0.windowW.dp, p0.windowH.dp), placement = if (p0.maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
            position = if (p0.windowX != Int.MIN_VALUE && p0.windowY != Int.MIN_VALUE) WindowPosition(p0.windowX.dp, p0.windowY.dp) else WindowPosition.PlatformDefault)
        val nav = remember { Nav().apply { reset(if (repo.loggedIn) Screen.Main else Screen.Login) } }
        var toast by remember { mutableStateOf("") }
        var visible by remember { mutableStateOf(!p0.startInTray) }
        val brand by repo.brand.collectAsState()
        var themeMode by remember { mutableStateOf(repo.prefs.theme.ifEmpty { "gold" }) }
        var lang by remember { mutableStateOf(Strings.lang(repo.prefs.language)) }
        val scope = rememberCoroutineScope()
        fun saveWindow() { try { if (state.placement == WindowPlacement.Floating) { repo.prefs.windowW = state.size.width.value.toInt(); repo.prefs.windowH = state.size.height.value.toInt(); val pos = state.position; if (pos is WindowPosition.Absolute) { repo.prefs.windowX = pos.x.value.toInt(); repo.prefs.windowY = pos.y.value.toInt() } }; repo.prefs.maximized = state.placement == WindowPlacement.Maximized } catch (e: Throwable) {} }
        fun quit() { saveWindow(); repo.syncNow(); VlcPlayer.shared.release(); Vlc.release(); exitApplication() }
        fun playLast() { val pl = repo.activePlaylist ?: return; val key = repo.lastChannelKey(); scope.launch { try { val live = repo.content(pl, Kind.LIVE).items; val it = live.firstOrNull { x -> x.key == key } ?: live.firstOrNull() ?: return@launch; val u = repo.streamUrl(pl, it, repo.prefs.liveFormat == "ts"); repo.liveContext = live; if (nav.current !is Screen.Play) nav.push(Screen.Play(u, it.name, it, live = true, image = it.icon)) } catch (e: Throwable) {} } }
        val win = remember { WindowActions(
            setFullscreen = { fs -> state.placement = if (fs) WindowPlacement.Fullscreen else WindowPlacement.Floating },
            isFullscreen = { state.placement == WindowPlacement.Fullscreen },
            toast = { toast = it }, exit = { quit() }) }
        LaunchedEffect(toast) { if (toast.isNotEmpty()) { delay(3000); toast = "" } }
        LaunchedEffect(Unit) {
            repo.startHeartbeat()
            if (repo.loggedIn) { repo.refreshSession(); repo.preloadAll() } else { repo.refreshSession() }
            Updater.checkInBackground(repo.prefs)
        }
        // sleep timer: stop playback when the time is up
        val sleepUntil by repo.sleepUntil.collectAsState()
        LaunchedEffect(sleepUntil) { if (sleepUntil > 0) { while (System.currentTimeMillis() < sleepUntil) delay(5000); repo.sleepUntil.value = 0L; VlcPlayer.shared.pause(true); Mini.on = false; toast = Strings.get(lang, "sleep_done") } }
        val kicked by repo.kicked.collectAsState(); LaunchedEffect(kicked) { if (kicked) { VlcPlayer.shared.stop(); nav.reset(Screen.Login); repo.kicked.value = false } }
        // mini player: main window goes to the task bar, a small always-on-top window keeps playing
        LaunchedEffect(Mini.on) { if (Mini.on) { state.isMinimized = true } else if (visible) { state.isMinimized = false } }

        Tray(icon = painterResource("icon.png"), tooltip = brand.name.ifEmpty { "9K Pro TV" }, onAction = { visible = true; state.isMinimized = false }, menu = {
            Item(Strings.get(lang, "tray_open")) { visible = true; state.isMinimized = false }
            Item(Strings.get(lang, "tray_last")) { visible = true; state.isMinimized = false; playLast() }
            Separator()
            Item(Strings.get(lang, "tray_exit")) { quit() }
        })

        Window(onCloseRequest = { if (repo.prefs.trayOnClose) { saveWindow(); visible = false; VlcPlayer.shared.pause(true) } else quit() }, visible = visible,
            title = brand.name.ifEmpty { "9K Pro TV" }, state = state, icon = painterResource("icon.png"),
            onKeyEvent = { e ->
                val h = Keys.handler
                if (h != null && h(e)) true
                else if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { if (state.placement == WindowPlacement.Fullscreen) { state.placement = WindowPlacement.Floating; true } else if (nav.stack.size > 1) { nav.pop(); true } else false }
                else if (e.type == KeyEventType.KeyDown && e.key == Key.F11) { state.placement = if (state.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen; true }
                else if (e.type == KeyEventType.KeyDown && e.isCtrlPressed && e.key == Key.M && repo.loggedIn) { if (nav.current !is Screen.Multi) nav.push(Screen.Multi); true }
                else false
            }) {
            val colors = smileColors(brand, themeMode)
            CompositionLocalProvider(LocalRepo provides repo, LocalNav provides nav, LocalWindow provides win, LocalLang provides lang, LocalLayoutDirection provides (if (Strings.isRtl(lang)) LayoutDirection.Rtl else LayoutDirection.Ltr)) {
                SmileTheme(colors) {
                    Box(Modifier.fillMaxSize().background(colors.gradient)) {
                        val scr = nav.current
                        when (scr) {
                            is Screen.Login -> LoginScreen()
                            is Screen.Main -> MainScreen()
                            is Screen.Play -> PlayerScreen(scr)
                            is Screen.Movie -> MovieScreen(scr.item)
                            is Screen.Series -> SeriesScreen(scr.item)
                            is Screen.Settings -> SettingsScreen(onTheme = { themeMode = it; repo.prefs.theme = it }, onLang = { lang = Strings.lang(it); repo.prefs.language = it })
                            is Screen.Search -> SearchScreen()
                            is Screen.Guide -> GuideScreen()
                            is Screen.Playlists -> PlaylistsScreen()
                            is Screen.Speed -> SpeedTestScreen()
                            is Screen.Multi -> MultiScreen()
                            is Screen.Downloads -> DownloadsScreen()
                        }
                        if (toast.isNotEmpty()) Text(toast, color = Color.White, fontSize = 14.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp).background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 10.dp))
                    }
                }
            }
        }

        if (Mini.on) {
            val miniState = rememberWindowState(size = DpSize(480.dp, 270.dp), position = WindowPosition(Alignment.BottomEnd))
            val p = VlcPlayer.shared
            var hover by remember { mutableStateOf(false) }
            Window(onCloseRequest = { Mini.on = false }, state = miniState, undecorated = true, alwaysOnTop = true, resizable = true, title = brand.name.ifEmpty { "9K Pro TV" }, icon = painterResource("icon.png")) {
                WindowDraggableArea {
                    Box(Modifier.fillMaxSize().background(Color.Black).onPointerEvent(PointerEventType.Enter) { hover = true }.onPointerEvent(PointerEventType.Exit) { hover = false }
                        .combinedClickable(onDoubleClick = { Mini.on = false }) { }) {
                        VideoView(Modifier.fillMaxSize())
                        if (hover) Row(Modifier.align(Alignment.TopStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(p.title.ifEmpty { brand.name }, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                            Icon(if (p.playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp).clickable { p.togglePause() }); Spacer(Modifier.width(8.dp))
                            Icon(if (p.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, null, tint = Color.White, modifier = Modifier.size(20.dp).clickable { p.toggleMute() }); Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.OpenInFull, null, tint = Color.White, modifier = Modifier.size(18.dp).clickable { Mini.on = false }); Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(20.dp).clickable { Mini.on = false; p.stop(); repo.nowPlaying = null; if (nav.current is Screen.Play) nav.pop() })
                        }
                    }
                }
            }
        }
    }
}
