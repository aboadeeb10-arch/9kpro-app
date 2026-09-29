package tv.ninekpro.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import tv.ninekpro.desktop.data.Repo
import tv.ninekpro.desktop.ui.*

fun main() {
    Updater.applyPendingOnStart()
    Vlc.init()
    val repo = Repo()
    application {
        val state = rememberWindowState(size = DpSize(repo.prefs.windowW.dp, repo.prefs.windowH.dp))
        val nav = remember { Nav().apply { reset(if (repo.loggedIn) Screen.Main else Screen.Login) } }
        var toast by remember { mutableStateOf("") }
        var exitAsk by remember { mutableStateOf(false) }
        val brand by repo.brand.collectAsState()
        var themeMode by remember { mutableStateOf(repo.prefs.theme.ifEmpty { "gold" }) }
        var lang by remember { mutableStateOf(Strings.lang(repo.prefs.language)) }
        val win = remember { WindowActions(
            setFullscreen = { fs -> state.placement = if (fs) WindowPlacement.Fullscreen else WindowPlacement.Floating },
            isFullscreen = { state.placement == WindowPlacement.Fullscreen },
            toast = { toast = it }, exit = { exitApplication() }) }
        LaunchedEffect(toast) { if (toast.isNotEmpty()) { delay(3000); toast = "" } }
        LaunchedEffect(Unit) {
            repo.startHeartbeat()
            if (repo.loggedIn) { repo.refreshSession(); repo.preloadAll() } else { repo.refreshSession() }
            Updater.checkInBackground(repo.prefs)
        }
        val kicked by repo.kicked.collectAsState(); LaunchedEffect(kicked) { if (kicked) { VlcPlayer.shared.stop(); nav.reset(Screen.Login); repo.kicked.value = false } }
        Window(onCloseRequest = { repo.prefs.windowW = state.size.width.value.toInt(); repo.prefs.windowH = state.size.height.value.toInt(); repo.syncNow(); VlcPlayer.shared.release(); Vlc.release(); exitApplication() },
            title = brand.name.ifEmpty { "9K Pro TV" }, state = state, icon = painterResource("icon.png"),
            onKeyEvent = { e ->
                val h = Keys.handler
                if (h != null && h(e)) true
                else if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { if (state.placement == WindowPlacement.Fullscreen) { state.placement = WindowPlacement.Floating; true } else if (nav.stack.size > 1) { nav.pop(); true } else false }
                else if (e.type == KeyEventType.KeyDown && e.key == Key.F11) { state.placement = if (state.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen; true }
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
                        }
                        if (toast.isNotEmpty()) Text(toast, color = Color.White, fontSize = 14.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp).background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 10.dp))
                    }
                }
            }
        }
    }
}
