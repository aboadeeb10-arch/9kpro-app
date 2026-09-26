package tv.ninekpro.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import tv.ninekpro.app.data.Category
import tv.ninekpro.app.data.Episode
import tv.ninekpro.app.data.Item
import tv.ninekpro.app.data.Kind
import tv.ninekpro.app.data.Playlist
import tv.ninekpro.app.data.Repo

/** Screens are a simple stack; the phone Back button and the TV remote pop it. */
sealed class Screen {
    object Splash : Screen()
    object Playlists : Screen()
    data class Main(val tab: Int = 0) : Screen()                      // 0 Home · 1 Live · 2 Movies · 3 Series
    data class Items(val kind: Kind, val category: Category?) : Screen() // narrow-screen fallback list
    data class Series(val item: Item) : Screen()
    data class Movie(val item: Item) : Screen()
    object Multi : Screen()
    /** startMs: -1 = ask "Resume / Start over" when there is a saved position · 0 = start over · >0 = resume there. */
    data class Play(val url: String, val title: String, val key: String, val kind: Kind, val image: String = "", val item: Item? = null, val episode: Episode? = null, val playlist: Playlist? = null, val seriesName: String = "", val startMs: Long = -1L) : Screen()
    object Settings : Screen()
    object Downloads : Screen()
    data class Search(val q: String) : Screen()
    object Favorites : Screen()
    object Continue : Screen()
    object Guide : Screen()
}

class Nav {
    val stack = mutableStateListOf<Screen>()
    /** Which top tab the Main screen shows (Home 0 · Live 1 · Movies 2 · Series 3); Back on a tab goes to Home. */
    var mainTab by mutableStateOf(0)
    val current: Screen get() = stack.last()
    private fun tabOf(s: Screen) { if (s is Screen.Main) mainTab = s.tab }
    fun push(s: Screen) { tabOf(s); stack.add(s) }
    fun pop(): Boolean { if (stack.size <= 1) return false; stack.removeAt(stack.size - 1); return true }
    fun reset(s: Screen) { tabOf(s); stack.clear(); stack.add(s) }
    fun replace(s: Screen) { tabOf(s); if (stack.isNotEmpty()) stack.removeAt(stack.size - 1); stack.add(s) }
}

val LocalRepo = staticCompositionLocalOf<Repo> { error("no repo") }
val LocalNav = staticCompositionLocalOf<Nav> { error("no nav") }
val LocalColors = staticCompositionLocalOf<SmileColors> { error("no colors") }
val LocalActivity = staticCompositionLocalOf<Activity> { error("no activity") }

@Composable
fun App(repo: Repo, activity: Activity) {
    val nav = remember { Nav().apply { reset(Screen.Splash) } }
    val brand by repo.brand.collectAsState()
    var themeMode by remember { mutableStateOf(repo.prefs.theme.ifEmpty { brand.theme }) }
    val colors = smileColors(brand, themeMode)
    var exitAsk by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        repo.checkCrashFix()
        // Instant start: if lists are cached, go to Home right away and refresh the session in the background.
        val quick = repo.loggedIn && repo.activePlaylist != null
        if (quick) { nav.reset(Screen.Main(if (tv.ninekpro.app.Launch.openSports) 4 else 0)); tv.ninekpro.app.Launch.openSports = false; repo.preloadAll()
            val rk = tv.ninekpro.app.Launch.openResume; if (rk.isNotEmpty()) { tv.ninekpro.app.Launch.openResume = ""; try { val r = repo.resumeOf(rk); val pl = repo.activePlaylist; if (r != null && pl != null) resumePlay(repo, nav, r) } catch (e: Throwable) {} }
            val ck = tv.ninekpro.app.Launch.openChannel; if (ck.isNotEmpty()) { tv.ninekpro.app.Launch.openChannel = ""; try { val pl = repo.activePlaylist; if (pl != null) { val it = repo.content(pl, Kind.LIVE).items.firstOrNull { x -> x.key == ck }; if (it != null) { repo.liveContext = emptyList(); playItem(repo, nav, pl, it) } } } catch (e: Throwable) {} } }
        repo.refreshSession()
        if (repo.prefs.token.isEmpty()) repo.deviceHello()
        if (!quick) { delay(200); if (nav.current is Screen.Splash) nav.reset(if (repo.loggedIn && repo.activePlaylist != null) Screen.Main(0) else Screen.Playlists) }
        repo.preloadAll()
    }

    // Back never closes the app by surprise: screen → previous screen → Home tab → "Exit?" question.
    // Phones: the login screen follows the phone's rotation; after login the app is landscape. TV: always landscape.
    LaunchedEffect(nav.current is Screen.Playlists) {
        try { activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE } catch (e: Exception) {}
    }
    BackHandler(enabled = true) {
        when {
            nav.stack.size > 1 -> nav.pop()
            nav.current is Screen.Main && nav.mainTab != 0 -> nav.mainTab = 0
            nav.current is Screen.Splash -> {}
            else -> exitAsk = true
        }
    }
    if (exitAsk) androidx.compose.material3.AlertDialog(onDismissRequest = { exitAsk = false },
        confirmButton = { BigButton(androidx.compose.ui.res.stringResource(tv.ninekpro.app.R.string.exit)) { exitAsk = false; activity.finish() } },
        dismissButton = { BigButton(androidx.compose.ui.res.stringResource(tv.ninekpro.app.R.string.cancel), filled = false) { exitAsk = false } },
        title = { Text(androidx.compose.ui.res.stringResource(tv.ninekpro.app.R.string.exit_q)) })

    CompositionLocalProvider(LocalRepo provides repo, LocalNav provides nav, LocalColors provides colors, LocalActivity provides activity) {
        SmileTheme(colors) {
            Box(Modifier.fillMaxSize().background(colors.gradient)) {
                when (val s = nav.current) {
                    is Screen.Splash -> SplashScreen()
                    is Screen.Playlists -> PlaylistScreen()
                    is Screen.Main -> MainScreen()
                    is Screen.Items -> ItemsScreen(s.kind, s.category)
                    is Screen.Series -> SeriesScreen(s.item)
                    is Screen.Movie -> MovieScreen(s.item)
                    is Screen.Multi -> MultiScreen()
                    is Screen.Play -> PlayerScreen(s)
                    is Screen.Settings -> SettingsScreen(themeMode) { themeMode = it; repo.prefs.theme = it }
                    is Screen.Downloads -> DownloadsScreen()
                    is Screen.Search -> SearchScreen(s.q)
                    is Screen.Favorites -> FavoritesScreen()
                    is Screen.Continue -> ContinueScreen()
                    is Screen.Guide -> GuideScreen()
                }
            }
        }
    }
}

@Composable
fun SplashScreen() {
    val repo = LocalRepo.current; val c = LocalColors.current
    val brand by repo.brand.collectAsState()
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        BrandLogo(brand.logo, Modifier.size(200.dp, 120.dp))
        Gap(60)
        CircularProgressIndicator(color = c.accent, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
    }
}
