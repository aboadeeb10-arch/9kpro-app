package tv.ninekpro.app.ui

import android.app.PictureInPictureParams
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import tv.ninekpro.app.R
import tv.ninekpro.app.data.EpgEntry
import tv.ninekpro.app.data.Item
import tv.ninekpro.app.data.Kind
import tv.ninekpro.app.data.ResumeEntry

/** Entry point: picks the engine (Settings → Change Player). Auto = ExoPlayer, falling back to VLC when ExoPlayer gives up. */
@Composable
fun PlayerScreen(s: Screen.Play) {
    val repo = LocalRepo.current; val act = LocalActivity.current
    val engine = repo.prefs.playerEngine
    var useVlc by remember { mutableStateOf(engine == "vlc" && !s.url.startsWith("stv://")) }
    if (engine == "external") { LaunchedEffect(Unit) { try { act.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(s.url), "video/*")) } catch (e: Exception) {} }; LocalNav.current.pop(); return }
    if (useVlc) VlcPlayerScreen(s, onBackToExo = if (engine == "auto") { { useVlc = false } } else null) else ExoScreen(s) { useVlc = true }
}

/** HTTP status behind a playback error (0 = none): ExoPlayer wraps it as HttpDataSource.InvalidResponseCodeException somewhere in the cause chain. */
@OptIn(UnstableApi::class)
fun httpCodeOf(e: Throwable?): Int {
    var t = e; var n = 0
    while (t != null && n < 8) { if (t is HttpDataSource.InvalidResponseCodeException) return t.responseCode; t = t.cause; n++ }
    return 0
}
/** True for "could not reach the server" errors (DNS, connect, timeout). */
fun isConnectError(e: PlaybackException): Boolean {
    if (e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED || e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT) return true
    var t: Throwable? = e; var n = 0
    while (t != null && n < 8) { if (t is java.net.SocketTimeoutException || t is java.net.UnknownHostException || t is java.net.ConnectException || t is java.net.SocketException) return true; t = t.cause; n++ }
    return false
}
/** Plain-words message for the error screen (item 32); the raw code stays in small text for support. */
fun playerErrorText(act: android.content.Context, e: PlaybackException): String {
    val code = httpCodeOf(e)
    return when {
        code == 401 || code == 403 -> act.getString(R.string.err_line)
        code == 404 -> act.getString(R.string.err_not_found)
        isConnectError(e) -> act.getString(R.string.err_connect)
        else -> act.getString(R.string.err_generic)
    }
}

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExoScreen(s: Screen.Play, onFallbackToVlc: () -> Unit) {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current; val act = LocalActivity.current
    val scope = rememberCoroutineScope()
    var controls by remember { mutableStateOf(s.kind != Kind.LIVE) }
    var playing by remember { mutableStateOf(true) }
    var pos by remember { mutableStateOf(0L) }; var dur by remember { mutableStateOf(0L) }
    var buffered by remember { mutableStateOf(0L) }
    // Smooth seeking (movies / series / catch-up, never live): while scrubbing the player keeps playing the old spot, only `scrub` moves;
    // the seek happens once, on commit (finger up / key up / OK). Shared by the bar, the remote keys and the full-screen drag.
    var scrub by remember { mutableStateOf<Long?>(null) }
    var keyScrub by remember { mutableStateOf(false) }       // the running scrub was started by Left/Right (KeyUp commits it)
    var barFocused by remember { mutableStateOf(false) }     // TV: the seek bar is the focused element of the control bar
    var scrubUntil by remember { mutableStateOf(0L) }        // after a commit the preview strip stays on screen until this time
    var scrubTick by remember { mutableStateOf(0) }          // bumped when scrubUntil passes, so the strip recomposes away
    val barFocus = remember { FocusRequester() }
    var err by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf<Tracks?>(null) }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var speed by remember { mutableStateOf(1f) }
    var subUri by remember { mutableStateOf<Uri?>(null) }
    var nextCountdown by remember { mutableStateOf(-1) }
    var url by remember { mutableStateOf(s.url) }
    var attempts by remember { mutableStateOf(0) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var cur by remember { mutableStateOf(s) }
    var infoUntil by remember { mutableStateOf(if (s.kind == Kind.LIVE) System.currentTimeMillis() + 4000 else 0L) }
    var numBuf by remember { mutableStateOf("") }; var numAt by remember { mutableStateOf(0L) }
    var sideList by remember { mutableStateOf(false) }
    var nn by remember { mutableStateOf<Pair<EpgEntry?, EpgEntry?>>(null to null) }
    var clock by remember { mutableStateOf(clockText(repo.prefs.timeFormat == "24")) }
    val ev by repo.epgVersion.collectAsState()
    var cuMenu by remember { mutableStateOf(false) }
    val resumeEntry = remember { if (s.kind != Kind.LIVE && s.startMs < 0 && !s.key.startsWith("cu:")) repo.resumeOf(s.key)?.takeIf { it.positionMs > 30_000 } else null }
    var ask by remember { mutableStateOf(resumeEntry != null) }
    var startAt by remember { mutableStateOf(if (s.startMs > 0) s.startMs else 0L) }
    var gestureText by remember { mutableStateOf("") }; var gestureUntil by remember { mutableStateOf(0L) }
    var streamInfo by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf("") }
    var recId by remember { mutableStateOf(0L) }
    val castOk = remember { tv.ninekpro.app.CastHelper.available(act) }
    fun flash(t: String) { gestureText = t; gestureUntil = System.currentTimeMillis() + 900 }
    var errMsg by remember { mutableStateOf("") }          // plain-words error (item 32); `err` keeps the raw code for support
    var retryMsg by remember { mutableStateOf("") }        // "Trying another server…" while retries remain
    var hasNext by remember { mutableStateOf(false) }      // there is an episode after this one (from the cached series info)
    var finished by remember { mutableStateOf(false) }     // the stream reached its end (no resume save, next episode / back)
    var cuAltTried by remember { mutableStateOf(false) }   // catch-up: the alternative /timeshift/ URL was already tried
    val isTv = tv.ninekpro.app.data.DeviceInfo.isTv
    // Phones: picture-in-picture when the user leaves the app while a stream plays (MainActivity owns the window state).
    val mainAct = act as? tv.ninekpro.app.MainActivity
    val pipFlow = remember { mainAct?.pipMode ?: MutableStateFlow(false) }
    val pip by pipFlow.collectAsState()
    fun inPip(): Boolean { if (pipFlow.value) return true; if (Build.VERSION.SDK_INT < 24) return false; return try { act.isInPictureInPictureMode } catch (e: Throwable) { false } }

    // Live TV uses the one shared player (LiveEngine) so preview ↔ full screen never reloads; everything else gets its own.
    val shared = s.kind == Kind.LIVE
    val player = remember {
        if (shared) LiveEngine.get(act, repo.prefs.subLang) else {
        val http = DefaultHttpDataSource.Factory().setUserAgent("9KProTV/1.0").setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(15000).setReadTimeoutMs(20000)
        // Fast start: begin playing after ~0.7 s of data instead of ExoPlayer's 2.5 s default, keep filling the buffer while playing. Weak boxes (lite): smaller buffer.
        val fastStart = if (tv.ninekpro.app.data.DeviceInfo.lite) DefaultLoadControl.Builder().setBufferDurationsMs(10_000, 20_000, 700, 1_500).setPrioritizeTimeOverSizeThresholds(false).build()
            else DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 50_000, 700, 1_500).setPrioritizeTimeOverSizeThresholds(true).build()
        ExoPlayer.Builder(act).setRenderersFactory(DefaultRenderersFactory(act).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER))
            .setLoadControl(fastStart).setMediaSourceFactory(DefaultMediaSourceFactory(tv.ninekpro.app.data.CryptDataSource.RoutingFactory(act, http)))
            .setAudioAttributes(AudioAttributes.DEFAULT, true).setHandleAudioBecomingNoisy(true).setWakeMode(C.WAKE_MODE_NETWORK)   // phones: pause when headphones unplug / another app takes the sound
            .build().apply {
                playWhenReady = true
                trackSelectionParameters = trackSelectionParameters.buildUpon().setPreferredTextLanguage(repo.prefs.subLang.ifEmpty { null }).build()
            } }
    }
    fun buildItem(u: String, sub: Uri?): MediaItem {
        val b = MediaItem.Builder().setUri(u)
        if (sub != null) b.setSubtitleConfigurations(listOf(MediaItem.SubtitleConfiguration.Builder(sub).setMimeType(if (sub.toString().lowercase().endsWith(".vtt")) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).setLanguage("und").build()))
        return b.build()
    }
    fun load(u: String, startMs: Long) {
        err = ""; errMsg = ""; retryMsg = ""; finished = false
        if (shared) { LiveEngine.key = cur.key; LiveEngine.applyPrefs(repo.prefs.subLang) }   // zap: subtitle pref re-applied so "None" does not stick for all later channels
        player.setMediaItem(buildItem(u, subUri), if (startMs > 0) startMs else C.TIME_UNSET); player.prepare(); player.play()
    }
    fun refreshNowNext() { val pl = cur.playlist; val it = cur.item; if (pl != null && it != null && cur.kind == Kind.LIVE) scope.launch { nn = try { repo.nowNextLive(pl, it) } catch (e: Throwable) { null to null } } }
    fun zap(target: Item?) {
        val pl = cur.playlist ?: return; val t = target ?: return
        scope.launch { try { attempts = 0; val u = repo.streamUrl(pl, t, ts = repo.prefs.liveFormat == "ts"); cur = Screen.Play(u, t.name, t.key, Kind.LIVE, t.icon, item = t, playlist = pl); url = u; load(u, 0); repo.setLastChannel(t.key); repo.liveUiSel = t
            repo.nowPlaying = JSONObject().put("kind", "live").put("id", t.id).put("name", t.name); infoUntil = System.currentTimeMillis() + 4000; refreshNowNext() } catch (e: Exception) { err = e.message ?: "" } }
    }
    fun zapDelta(d: Int) { val l = repo.liveContext; val i = l.indexOfFirst { it.key == cur.key }; if (l.isEmpty()) return; zap(l[((if (i < 0) 0 else i) + d + l.size) % l.size]) }

    LaunchedEffect(ask) {
        if (ask) return@LaunchedEffect
        if (shared && LiveEngine.handoff && LiveEngine.isLoaded(s.key)) player.play() else load(url, startAt)   // came from the preview: keep the running stream
        repo.nowPlaying = JSONObject().put("kind", s.kind.name.lowercase()).put("id", s.key.substringAfterLast(':')).put("name", s.title)
        refreshNowNext()
    }
    // Is there an episode after the one playing? (cached series info — no network once the series screen loaded it). Last episode: no Next button, no countdown.
    LaunchedEffect(cur.episode?.id) {
        val ep = cur.episode; val pl = cur.playlist
        hasNext = try { if (ep != null && pl != null) { val all = repo.seriesInfo(pl, ep.seriesId).seasons.values.flatten(); val i = all.indexOfFirst { it.id == ep.id }; i >= 0 && i + 1 < all.size } else false } catch (e: Exception) { false }
    }
    /** End of an episode / the countdown: next episode, or back to the series screen after the last one. */
    fun goNext() { if (finished) return; finished = true; nextCountdown = -1; if (cur.episode != null && hasNext) playNext(repo, nav, cur) { nav.pop() } else nav.pop() }
    // PiP window: enabled while a stream is actually playing (never on TV); controls, sheets and the side list fold away inside the small window.
    LaunchedEffect(err, ask) { if (!isTv) mainAct?.setPipEnabled(err.isEmpty() && !ask) }
    LaunchedEffect(pip) { if (pip) { controls = false; sheet = ""; sideList = false; cuMenu = false } }
    DisposableEffect(Unit) {
        act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onTracksChanged(t: Tracks) { tracks = t }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && cur.kind != Kind.LIVE && !cur.key.startsWith("cu:") && cur.episode != null) goNext()
            }
            override fun onPlayerError(e: PlaybackException) {
                scope.launch {
                    attempts++
                    val pl = cur.playlist; val item = cur.item
                    val code = httpCodeOf(e)
                    // 401/403 = the line itself is refused: no other server or engine will help → say so at once.
                    if (code == 401 || code == 403) {
                        // live "auto": some panels refuse only the m3u8 link — the .ts link gets one try first
                        if (cur.kind == Kind.LIVE && pl != null && item != null && attempts == 1 && repo.prefs.liveFormat == "auto" && url.contains(".m3u8")) { try { url = repo.streamUrl(pl, item, ts = true); load(url, 0); return@launch } catch (x: Exception) {} }
                        // catch-up: some panels only accept the /timeshift/… form — try it once before giving up
                        if (cur.key.startsWith("cu:") && !cuAltTried) { cuAltTried = true; val alt = try { repo.xtream.catchupAlt(url) } catch (x: Exception) { null }; if (alt != null) { url = alt; load(url, 0); return@launch } }
                        err = e.errorCodeName + " · HTTP " + code; errMsg = playerErrorText(act, e); retryMsg = ""; return@launch
                    }
                    if (code == 404 && cur.key.startsWith("cu:") && !cuAltTried) { cuAltTried = true; val alt = try { repo.xtream.catchupAlt(url) } catch (x: Exception) { null }; if (alt != null) { url = alt; load(url, 0); return@launch } }
                    if (pl != null && attempts <= 3) {
                        retryMsg = act.getString(R.string.err_trying)
                        try {
                            delay(1500)   // a short pause between attempts: hammering a busy server only makes it worse
                            if (cur.kind == Kind.LIVE && item != null && attempts == 1 && repo.prefs.liveFormat == "auto") { url = repo.streamUrl(pl, item, ts = true); load(url, 0); return@launch }
                            repo.forgetHost(pl)
                            url = if (item != null) repo.streamUrl(pl, item, ts = attempts > 1) else if (cur.episode != null) repo.episodeUrl(pl, cur.episode!!) else url
                            load(url, player.currentPosition); return@launch
                        } catch (x: Exception) { }
                    }
                    retryMsg = ""
                    // 404 = the stream does not exist on this server: VLC cannot help either
                    if (code != 404 && repo.prefs.playerEngine == "auto" && attempts <= 4) { LiveEngine.handoff = false; onFallbackToVlc(); return@launch }
                    err = e.errorCodeName + (if (code > 0) " · HTTP " + code else ""); errMsg = playerErrorText(act, e)
                }
            }
        }
        player.addListener(l)
        onDispose {
            act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            mainAct?.setPipEnabled(false)
            // save under the key of what is actually playing (cur), never the screen we were opened with
            val p = cur
            if (p.kind != Kind.LIVE && !p.key.startsWith("cu:")) { if (finished) repo.removeResume(p.key) else if (player.duration > 0) repo.saveResume(ResumeEntry(p.key, p.title, p.kind.name, p.image, player.currentPosition, player.duration, System.currentTimeMillis(), if (p.episode != null && p.playlist != null) episodeExtra(p.playlist, p.episode, p.seriesName) else if (p.item != null && p.playlist != null) itemExtra(p.playlist, p.item) else "")) }
            repo.nowPlaying = null; repo.syncNow()
            player.removeListener(l)
            if (!shared) player.release() else if (!LiveEngine.handoff) LiveEngine.stop()   // opened from the preview → keep playing for it
        }
    }
    // Controls stay while the user is touching the remote: every key press / tap pushes the hide time out (TV boxes: the bar used to vanish 4 s after it appeared, before the focus reached it).
    var lastInteract by remember { mutableStateOf(System.currentTimeMillis()) }
    val focus = remember { FocusRequester() }; val ctlFocus = remember { FocusRequester() }
    /** Known duration, or 0 (ExoPlayer reports C.TIME_UNSET = a huge negative number while it is unknown). */
    fun knownDur(): Long { val d: Long = player.duration; return if (d <= 0L) 0L else d }
    fun scrubTo(p: Long) { val d = knownDur(); scrub = if (d > 0L) p.coerceIn(0L, d) else p.coerceAtLeast(0L); lastInteract = System.currentTimeMillis() }
    /** Seek once to the scrub position; the preview strip stays 1.5 s so the user sees where playback landed. */
    fun commitScrub() { val p: Long = scrub ?: return; scrub = null; keyScrub = false; player.seekTo(p); scrubUntil = System.currentTimeMillis() + 1500; lastInteract = System.currentTimeMillis() }
    fun cancelScrub() { scrub = null; keyScrub = false; scrubUntil = 0L }
    /** Remote Left/Right: first press ±10 s; holding the key accelerates (repeat 1–5 → 10 s each, 6–15 → 30 s, 16+ → 60 s). */
    fun keyScrubStep(dir: Int, repeat: Int) {
        val step: Long = if (repeat <= 5) 10_000L else if (repeat <= 15) 30_000L else 60_000L
        val base: Long = scrub ?: player.currentPosition
        keyScrub = true
        scrubTo(base + dir * step)
    }
    LaunchedEffect(scrubUntil) { if (scrubUntil > 0L) { val w = scrubUntil - System.currentTimeMillis(); if (w > 0L) delay(w + 30); scrubTick++ } }
    LaunchedEffect(controls) { barFocused = false; if (controls) { lastInteract = System.currentTimeMillis(); delay(80); try { ctlFocus.requestFocus() } catch (e: Exception) {} } else { delay(80); try { focus.requestFocus() } catch (e: Exception) {} } }
    // TV: when the side channel list or a bottom sheet closes, the focus used to be lost (no key worked) → give it back to the video box.
    LaunchedEffect(sideList, sheet) { if (!sideList && sheet.isEmpty() && !controls) { delay(80); try { focus.requestFocus() } catch (e: Exception) {} } }
    LaunchedEffect(Unit) {
        var lastSave = 0L; var saveKey = cur.key
        while (true) {
            pos = player.currentPosition; dur = player.duration.coerceAtLeast(0); buffered = player.bufferedPosition.coerceAtLeast(0); clock = clockText(repo.prefs.timeFormat == "24")
            if (controls && playing && sheet.isEmpty() && scrub == null && System.currentTimeMillis() - lastInteract > 5000) controls = false
            val p = cur
            if (p.key != saveKey) { saveKey = p.key; lastSave = System.currentTimeMillis() }   // the screen now plays something else: never save the old position under the new key
            if (p.kind != Kind.LIVE && !p.key.startsWith("cu:") && !finished && dur > 0 && System.currentTimeMillis() - lastSave > 15000) { lastSave = System.currentTimeMillis()
                repo.saveResume(ResumeEntry(p.key, p.title, p.kind.name, p.image, pos, dur, lastSave, if (p.episode != null && p.playlist != null) episodeExtra(p.playlist, p.episode, p.seriesName) else if (p.item != null && p.playlist != null) itemExtra(p.playlist, p.item) else "")) }
            if (p.episode != null && hasNext && !finished && dur > 0 && dur - pos < 12_000 && nextCountdown < 0) nextCountdown = 10
            if (nextCountdown > 0) { nextCountdown--; if (nextCountdown == 0) goNext() }
            if (gestureText.isNotEmpty() && System.currentTimeMillis() > gestureUntil) gestureText = ""
            if (numBuf.isNotEmpty() && System.currentTimeMillis() - numAt > 1500) { val n = numBuf.toIntOrNull(); numBuf = ""; val t = repo.liveContext.getOrNull((n ?: 0) - 1); if (t != null) zap(t) }
            delay(1000)
        }
    }
    val subPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) { try { act.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}; subUri = uri; load(url, player.currentPosition) } }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // Going to the background pauses — except into the PiP window, where the stream keeps playing.
    PauseInBackground(onStop = { try { if (!inPip()) player.pause() } catch (e: Exception) {} }, onStart = { try { if (!ask && err.isEmpty() && !finished) player.play() } catch (e: Exception) {} })
    val subColor = when (repo.prefs.subColor) { "yellow" -> Color.Yellow; "cyan" -> Color.Cyan; else -> Color.White }

    Box(Modifier.fillMaxSize().background(Color.Black).focusRequester(focus).onPreviewKeyEvent { ev -> if (ev.type == KeyEventType.KeyDown) lastInteract = System.currentTimeMillis(); false }.onKeyEvent { ev ->
        val seekKey: Boolean = ev.key == Key.DirectionLeft || ev.key == Key.DirectionRight || ev.key == Key.MediaRewind || ev.key == Key.MediaFastForward
        // Left/Right released while a key scrub is running → seek now (the preview strip stays 1.5 s)
        if (ev.type == KeyEventType.KeyUp) { if (seekKey && keyScrub && scrub != null) { commitScrub(); return@onKeyEvent true }; return@onKeyEvent false }
        if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
        val seekable: Boolean = cur.kind != Kind.LIVE
        // Left/Right scrub (instead of moving focus) only when the bar is hidden, or when the seek bar itself is the focused control
        val scrubKeys: Boolean = seekable && !sideList && (!controls || barFocused)
        val repeat: Int = try { ev.nativeKeyEvent.repeatCount } catch (e: Throwable) { 0 }
        when (ev.key) {
            Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> { if (scrub != null) { commitScrub(); true } else if (sideList) false else if (!controls) { if (cur.kind == Kind.LIVE) { infoUntil = System.currentTimeMillis() + 5000; controls = true } else controls = true; true } else false }
            Key.MediaPlay -> { player.play(); true }
            Key.MediaPause -> { player.pause(); true }
            Key.DirectionLeft, Key.MediaRewind -> { if (sideList) false else if (cur.kind == Kind.LIVE && !controls) { sideList = true; true } else if (scrubKeys) { keyScrubStep(-1, repeat); true } else false }
            Key.DirectionRight, Key.MediaFastForward -> { if (sideList) { sideList = false; true } else if (scrubKeys) { keyScrubStep(1, repeat); true } else false }
            // bar visible (VOD): Up from any button lands on the seek bar, Down from the bar goes back to Play; the keys stay consumed as before
            Key.DirectionUp -> { if (sideList) false else if (cur.kind == Kind.LIVE && !controls) { zapDelta(1); true } else if (!controls) { controls = true; true } else { if (seekable && dur > 0L && !barFocused) { try { barFocus.requestFocus() } catch (e: Exception) {} }; true } }
            Key.DirectionDown -> { if (sideList) false else if (cur.kind == Kind.LIVE && !controls) { zapDelta(-1); true } else if (!controls) { controls = true; true } else { if (barFocused) { try { ctlFocus.requestFocus() } catch (e: Exception) {} }; true } }
            Key.ChannelUp -> { zapDelta(1); true }
            Key.ChannelDown -> { zapDelta(-1); true }
            Key.Menu -> { controls = true; true }
            Key.Back, Key.Escape -> { if (scrub != null) { cancelScrub(); true } else if (sideList) { sideList = false; true } else if (sheet.isNotEmpty()) { sheet = ""; true } else if (controls && tv.ninekpro.app.data.DeviceInfo.isTv) { controls = false; true } else false }
            Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine -> {
                if (cur.kind != Kind.LIVE) false else { val d = listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine).indexOf(ev.key); numBuf = (numBuf + d).takeLast(4); numAt = System.currentTimeMillis(); infoUntil = numAt + 2500; true }
            }
            else -> false
        }
    }.focusable()) {   // the box itself must be focusable: when the bar hides, focus returns here so the remote keeps working (TV: "no button brings the bar back")
        AndroidView(factory = { ctx -> PlayerView(ctx).apply { useController = false; this.player = player; setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS); setKeepContentOnPlayerReset(true)
            subtitleView?.setStyle(CaptionStyleCompat(subColor.hashCode(), 0x00000000, 0, CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.Black.hashCode(), null)); subtitleView?.setFractionalTextSize(0.0533f * repo.prefs.subtitleScale); playerViewRef = this } },
            onRelease = { it.player = null }, update = { it.resizeMode = resizeMode }, modifier = Modifier.fillMaxSize()
                .pointerInput(cur.kind) { detectTapGestures(onTap = { if (cur.kind == Kind.LIVE) { infoUntil = System.currentTimeMillis() + 5000 }; lastInteract = System.currentTimeMillis(); controls = !controls },
                    onDoubleTap = { off -> if (cur.kind != Kind.LIVE) { val fwd = off.x > size.width / 2; player.seekTo((player.currentPosition + if (fwd) 10_000 else -10_000).coerceAtLeast(0)); flash(if (fwd) "⏩ +10s" else "⏪ −10s") } }) }
                .pointerInput(cur.kind) {
                    // phone gestures: vertical swipe = channel up/down (live) or brightness (left) / volume (right) (VOD); horizontal drag = seek (VOD)
                    var dx = 0f; var dy = 0f; var axis = 0; var startX = 0f; var dragBase = 0L
                    val am = act.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                    // horizontal drag (VOD) = preview on the seek bar, one seek when the finger lifts (full width ≈ 3 min)
                    detectDragGestures(onDragStart = { o -> dx = 0f; dy = 0f; axis = 0; startX = o.x; dragBase = player.currentPosition }, onDragEnd = {
                        if (cur.kind == Kind.LIVE) { if (axis == 2 && kotlin.math.abs(dy) > 90) zapDelta(if (dy < 0) 1 else -1) }
                        else if (axis == 1 && kotlin.math.abs(dx) > 30) commitScrub() else cancelScrub()
                    }, onDragCancel = { if (cur.kind != Kind.LIVE) cancelScrub() }) { change, drag ->
                        change.consume(); dx += drag.x; dy += drag.y
                        if (axis == 0 && (kotlin.math.abs(dx) > 24 || kotlin.math.abs(dy) > 24)) axis = if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) 1 else 2
                        if (axis == 1 && cur.kind != Kind.LIVE) scrubTo(dragBase + (dx / size.width * 180_000f).toLong())
                        if (axis == 2 && cur.kind != Kind.LIVE && !tv.ninekpro.app.data.DeviceInfo.isTv) {
                            if (startX < size.width / 2) { val lp = act.window.attributes; val b = (if (lp.screenBrightness < 0) 0.6f else lp.screenBrightness) - drag.y / size.height; lp.screenBrightness = b.coerceIn(0.05f, 1f); act.window.attributes = lp; flash("☀ " + (lp.screenBrightness * 100).toInt() + "%") }
                            else { val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC); val curV = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC); val step = (-drag.y / size.height * max * 1.5f); val nv = (curV + step).toInt().coerceIn(0, max); if (nv != curV) am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, nv, 0); flash("🔊 " + (nv * 100 / max) + "%") }
                        }
                    }
                })
        // tap zone on the left edge opens the channel list (phones)
        if (cur.kind == Kind.LIVE && !sideList) Box(Modifier.align(Alignment.CenterStart).width(36.dp).fillMaxHeight().clickable { sideList = true })

        if (gestureText.isNotEmpty() && System.currentTimeMillis() < gestureUntil) Box(Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).background(Color(0xAA000000)).padding(horizontal = 18.dp, vertical = 10.dp)) { Text(gestureText, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        if (ask && resumeEntry != null) androidx.compose.material3.AlertDialog(onDismissRequest = { nav.pop() }, title = { Text(stringResource(R.string.resume_q)) }, text = { Text(cur.title + "\n" + fmtTime(resumeEntry.positionMs) + (if (resumeEntry.durationMs > 0) " / " + fmtTime(resumeEntry.durationMs) else "")) },
            confirmButton = { BigButton(stringResource(R.string.resume)) { startAt = resumeEntry.positionMs; ask = false } },
            dismissButton = { BigButton(stringResource(R.string.start_over), filled = false) { startAt = 0L; repo.removeResume(s.key); ask = false } })
        if (streamInfo) androidx.compose.material3.AlertDialog(onDismissRequest = { streamInfo = false }, confirmButton = { BigButton(stringResource(R.string.ok)) { streamInfo = false } }, title = { Text(stringResource(R.string.stream_info)) }, text = {
            val v = player.videoFormat; val a = player.audioFormat
            Column { Text("Video: " + (if (v != null) v.width.toString() + "×" + v.height + (if (v.frameRate > 0) " · " + v.frameRate.toInt() + " fps" else "") + " · " + (v.sampleMimeType ?: "") + (if (v.bitrate > 0) " · " + (v.bitrate / 1000) + " kbps" else "") else "—"), fontSize = 13.sp)
                Text("Audio: " + (if (a != null) (a.sampleMimeType ?: "") + " · " + a.channelCount + "ch · " + a.sampleRate + " Hz" else "—"), fontSize = 13.sp)
                Text("Buffer: " + (player.totalBufferedDuration / 1000) + " s", fontSize = 13.sp)
                Text("Server: " + (try { val hh = Uri.parse(url).host ?: "@"; ((cur.playlist?.hosts?.indexOfFirst { x -> x.contains(hh) } ?: -1) + 1).let { n -> if (n > 0) n.toString() else "—" } } catch (e: Exception) { "—" }), fontSize = 13.sp)
                Text("Engine: ExoPlayer · attempts " + attempts, fontSize = 13.sp) } })
        if (retryMsg.isNotEmpty() && err.isEmpty()) Box(Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).background(Color(0xAA000000)).padding(horizontal = 18.dp, vertical = 10.dp)) { Text(retryMsg, color = Color.White, fontSize = 15.sp) }
        if (err.isNotEmpty()) Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(errMsg.ifEmpty { stringResource(R.string.err_generic) }, color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 12.dp)); Gap(6); Text(err, color = Color.Gray, fontSize = 11.sp); Gap(12)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { BigButton(stringResource(R.string.retry)) { attempts = 0; cuAltTried = false; load(url, player.currentPosition) }; BigButton("VLC", filled = false) { LiveEngine.handoff = false; onFallbackToVlc() }; BigButton(stringResource(R.string.err_back), filled = false) { nav.pop() } } }
        if (nextCountdown > 0 && hasNext) Box(Modifier.align(Alignment.BottomEnd).padding(24.dp).clip(RoundedCornerShape(12.dp)).background(c.accent).clickable { goNext() }.padding(horizontal = 16.dp, vertical = 10.dp)) { Text(stringResource(R.string.next_episode) + " · " + nextCountdown, color = Color.White, fontWeight = FontWeight.Bold) }

        // receiver-style info bar for live channels
        if (cur.kind == Kind.LIVE && (numBuf.isNotEmpty() || System.currentTimeMillis() < infoUntil) && !controls && !sideList && !pip) {
            val fmt = java.text.SimpleDateFormat(if (repo.prefs.timeFormat == "24") "HH:mm" else "h:mm a", java.util.Locale.getDefault())
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xD9060C1C)).padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (numBuf.isNotEmpty()) numBuf else (repo.liveContext.indexOfFirst { it.key == cur.key }.takeIf { it >= 0 }?.let { (it + 1).toString() } ?: ""), color = c.accent, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
                Poster(cur.image, Modifier.size(56.dp, 38.dp), contentScaleFit = true); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(cur.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    nn.first?.let { e -> Text(fmt.format(java.util.Date(e.start * 1000)) + "  " + e.title, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    nn.second?.let { e -> Text(fmt.format(java.util.Date(e.start * 1000)) + "  " + e.title, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                Text(clock, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        // side channel list (left key / left edge tap)
        if (sideList && !pip) {
            val list = repo.liveContext; val st = rememberLazyListState(initialFirstVisibleItemIndex = list.indexOfFirst { it.key == cur.key }.coerceAtLeast(0))
            Box(Modifier.fillMaxHeight().width(300.dp).background(Color(0xE6070D20)).clickable(enabled = false) {}) {
                LazyColumn(state = st, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(list, key = { _, it -> it.key }) { idx, ch ->
                        Row(Modifier.fillMaxWidth().background(if (ch.key == cur.key) c.selected else Color.Transparent).tvFocus(c.accent, 0).clickable { zap(ch); sideList = false }.padding(12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text((idx + 1).toString(), color = c.muted, fontSize = 12.sp, modifier = Modifier.width(34.dp)); Poster(ch.icon, Modifier.size(30.dp, 20.dp), contentScaleFit = true); Spacer(Modifier.width(8.dp))
                            Text(ch.name, color = if (ch.key == cur.key) c.accent else Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.6f).clickable { sideList = false })
        }

        // live: the bar brings the groups + channels panel with it (same dark style), so the channel can be changed without leaving the big screen
        if (controls && !pip && !sideList && cur.kind == Kind.LIVE && cur.playlist != null) {
            val pl = cur.playlist!!
            var content by remember(pl.id) { mutableStateOf(repo.cachedContent(pl, Kind.LIVE)) }
            LaunchedEffect(pl.id) { if (content == null) content = try { repo.content(pl, Kind.LIVE) } catch (e: Throwable) { null } }
            val pv by repo.profileVersion.collectAsState()
            var grp by remember { mutableStateOf(repo.liveUiCat) }
            val cnt = content
            if (cnt != null) {
                val hidden = remember(pv) { repo.hidden() }; val favs = remember(pv) { repo.favorites().toSet() }
                val locked = remember(pv, cnt) { val l = repo.locked().toMutableSet(); if (repo.prefs.parentalOn) cnt.categories.filter { repo.isAdultName(it.name) }.forEach { l.add("cat:" + it.id) }; if (!repo.prefs.parentalOn) l.clear(); l as Set<String> }
                val groups = remember(cnt, pv) { repo.applyOrder("cats:${pl.id}:LIVE", cnt.categories) { it.id }.filter { (repo.prefs.showHidden || !hidden.contains("cat:" + it.id)) && !locked.contains("cat:" + it.id) } }
                val chans = remember(cnt, grp, pv) {
                    val base = when (grp) { "__all" -> cnt.items; "__fav" -> cnt.items.filter { favs.contains(it.key) }; else -> cnt.items.filter { it.categoryId == grp } }.filter { !locked.contains("cat:" + it.categoryId) }
                    val sorted = when (repo.prefs.liveSort) { "az" -> base.sortedBy { it.name.lowercase() }; "number" -> base.sortedBy { it.num }; else -> repo.applyOrder("items:${pl.id}:LIVE:$grp", base) { it.key } }
                    sorted.filter { repo.prefs.showHidden || !hidden.contains(it.key) } }
                val gst = rememberLazyListState(); val cst = rememberLazyListState(initialFirstVisibleItemIndex = chans.indexOfFirst { it.key == cur.key }.coerceAtLeast(0))
                val touch = Modifier.onFocusChanged { if (it.isFocused) lastInteract = System.currentTimeMillis() }
                val allS = stringResource(R.string.all); val favS = stringResource(R.string.favorites)
                val groupRows = remember(groups, allS, favS) { listOf("__all" to allS, "__fav" to favS) + groups.map { it.id to it.name } }
                Row(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.62f).fillMaxHeight().padding(start = 10.dp, top = 74.dp, bottom = 96.dp)) {
                    LazyColumn(state = gst, modifier = Modifier.weight(0.42f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(Color(0xD9060C1C)).padding(vertical = 4.dp)) {
                        items(groupRows, key = { it.first }) { (id, name) ->
                            val on = id == grp
                            Text(name, color = if (on) c.accent else Color.White, fontSize = 13.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().background(if (on) c.selected else Color.Transparent).then(touch).tvFocus(c.accent, 0).clickable { grp = id; repo.liveUiCat = id; lastInteract = System.currentTimeMillis() }.padding(12.dp, 9.dp))
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    LazyColumn(state = cst, modifier = Modifier.weight(0.58f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(Color(0xD9060C1C)).padding(vertical = 4.dp)) {
                        itemsIndexed(chans, key = { _, it -> it.key }) { idx, ch ->
                            val on = ch.key == cur.key
                            Row(Modifier.fillMaxWidth().background(if (on) c.selected else Color.Transparent).then(touch).tvFocus(c.accent, 0).clickable { repo.liveContext = chans; lastInteract = System.currentTimeMillis(); zap(ch) }.padding(10.dp, 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text((idx + 1).toString(), color = c.muted, fontSize = 12.sp, modifier = Modifier.width(32.dp)); Poster(ch.icon, Modifier.size(30.dp, 20.dp), contentScaleFit = true); Spacer(Modifier.width(8.dp))
                                Text(ch.name, color = if (on) c.accent else Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        // bar hidden: scrubbing with the remote (or a drag) shows just the seek strip at the bottom, and keeps it 1.5 s after the seek
        if (!controls && !pip && cur.kind != Kind.LIVE && dur > 0 && (scrub != null || (scrubTick >= 0 && System.currentTimeMillis() < scrubUntil))) {
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000)))).padding(14.dp, 40.dp, 14.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(fmtTime(scrub ?: pos), color = Color.White, fontSize = 12.sp); Spacer(Modifier.width(10.dp))
                SeekBar(pos, dur, buffered, scrub, false, Modifier.weight(1f), onScrub = { p -> scrubTo(p) }, onCommit = { p -> scrub = p; commitScrub() })
                Spacer(Modifier.width(10.dp)); Text(fmtTime(dur), color = Color.White, fontSize = 12.sp)
            }
        }

        if (controls && !pip) {
            // ---- top: back · title · now/next · clock
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xD9000000), Color.Transparent))).padding(10.dp, 8.dp, 16.dp, 22.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.pop() }, modifier = Modifier.tvFocus(c.accent, 24)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(cur.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (cur.kind == Kind.LIVE) nn.first?.let { e -> Text(stringResource(R.string.now) + ": " + e.title + (nn.second?.let { n -> "   ·   " + stringResource(R.string.next) + ": " + n.title } ?: ""), color = Color.White.copy(alpha = 0.8f), fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                Text(clock, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            // ---- bottom bar: seek (VOD) + one row of buttons; each opens a bottom sheet
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))).padding(14.dp, 26.dp, 14.dp, 10.dp)) {
                if (cur.kind != Kind.LIVE && dur > 0) Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(fmtTime(scrub ?: pos), color = Color.White, fontSize = 12.sp); Spacer(Modifier.width(10.dp))
                    // focusable on TV (Up from Play lands here): Left/Right then scrub instead of moving between buttons
                    SeekBar(pos, dur, buffered, scrub, barFocused, Modifier.weight(1f).focusRequester(barFocus).onFocusChanged { barFocused = it.isFocused }.focusable(),
                        onScrub = { p -> scrubTo(p) }, onCommit = { p -> scrub = p; commitScrub() })
                    Spacer(Modifier.width(10.dp)); Text(fmtTime(dur), color = Color.White, fontSize = 12.sp)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    PBtn(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) stringResource(R.string.pause) else stringResource(R.string.play), big = true, modifier = Modifier.focusRequester(ctlFocus)) { if (player.isPlaying) player.pause() else player.play() }
                    if (cur.kind != Kind.LIVE) { PBtn(Icons.Default.Replay10, "-10s") { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) }; PBtn(Icons.Default.Forward10, "+10s") { player.seekTo(player.currentPosition + 10_000) } }
                    if (cur.episode != null && hasNext) PBtn(Icons.Default.SkipNext, stringResource(R.string.next_episode)) { goNext() }
                    if (cur.kind == Kind.LIVE) { PBtn(Icons.AutoMirrored.Filled.List, stringResource(R.string.channels)) { sideList = true; controls = false }
                        // Guide replaces the player (Back from a guide-picked channel must not stack two players) and the shared stream stops — no sound under the guide.
                        PBtn(Icons.Default.CalendarViewWeek, stringResource(R.string.guide)) { LiveEngine.handoff = false; nav.replace(Screen.Guide) }
                        if (cur.item?.archive == true) PBtn(Icons.Default.History, stringResource(R.string.catchup)) { sheet = "catchup" }
                        // Record always the .ts stream: recording an HLS link only saves the playlist text (same as Guide.kt scheduling).
                        PBtn(Icons.Default.FiberManualRecord, if (recId > 0) stringResource(R.string.stop_recording) else stringResource(R.string.record)) { if (recId > 0) { tv.ninekpro.app.DlService.stop(act, recId); recId = 0; flash("■ " + act.getString(R.string.recording_saved)) } else { val id = tv.ninekpro.app.data.DlStore.newId(); val recTitle = cur.title + " · " + clockText(true); scope.launch { try { val u = if (cur.playlist != null && cur.item != null) repo.streamUrl(cur.playlist!!, cur.item!!, ts = true) else url; tv.ninekpro.app.DlService.start(act, id, u, recTitle, "rec"); recId = id; flash("● " + act.getString(R.string.recording)) } catch (e: Exception) { flash(act.getString(R.string.err_generic)) } } } } }
                    Spacer(Modifier.weight(1f))
                    PBtn(Icons.Default.ClosedCaption, stringResource(R.string.subtitles)) { sheet = "subs" }
                    PBtn(Icons.Default.Audiotrack, stringResource(R.string.audio)) { sheet = "audio" }
                    PBtn(Icons.Default.AspectRatio, stringResource(R.string.aspect)) { sheet = "aspect" }
                    if (cur.kind != Kind.LIVE) PBtn(Icons.Default.Speed, stringResource(R.string.speed)) { sheet = "speed" }
                    if (cur.kind == Kind.LIVE && cur.item != null) PBtn(Icons.Default.Videocam, stringResource(R.string.live_format)) { sheet = "format" }
                    if (castOk) PBtn(Icons.Default.Cast, "Cast") { if (tv.ninekpro.app.CastHelper.connected(act)) { scope.launch { try { val u = if (cur.kind == Kind.LIVE && cur.item != null && cur.playlist != null) repo.streamUrl(cur.playlist!!, cur.item!!, ts = false) else url; if (tv.ninekpro.app.CastHelper.play(act, u, cur.title, cur.image, cur.kind == Kind.LIVE, if (cur.kind == Kind.LIVE) 0L else player.currentPosition)) { player.pause(); flash("📺 Cast") } else flash("Cast: not connected") } catch (e: Exception) { flash("Cast failed") } } } else sheet = "cast" }
                    PBtn(Icons.Default.MoreHoriz, stringResource(R.string.more)) { sheet = "more" }
                }
            }
        }

        // ---- bottom sheets
        if (sheet.isNotEmpty() && !pip) androidx.compose.material3.ModalBottomSheet(onDismissRequest = { sheet = "" }, containerColor = Color(0xF20A1428), dragHandle = null) {
            Column(Modifier.fillMaxWidth().padding(18.dp, 14.dp, 18.dp, 24.dp)) {
                when (sheet) {
                    "subs" -> SubtitleSheet(tracks, player, playerViewRef, cur, { u -> subUri = u; load(url, player.currentPosition); sheet = "" }) { sheet = ""; subPicker.launch(arrayOf("*/*")) }
                    "audio" -> { SheetTitle(stringResource(R.string.audio))
                        val gs = tracks?.groups?.filter { it.type == C.TRACK_TYPE_AUDIO } ?: emptyList()
                        if (gs.isEmpty()) Text(stringResource(R.string.none), color = c.muted)
                        gs.forEach { g -> for (i in 0 until g.length) { val f = g.getTrackFormat(i); SheetRow((f.language ?: (stringResource(R.string.audio) + " " + (i + 1))) + (if (f.label != null) " · " + f.label else "") + (if (f.channelCount > 0) " · " + f.channelCount + "ch" else ""), g.isTrackSelected(i)) { player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i)).build(); sheet = "" } } } }
                    "aspect" -> { SheetTitle(stringResource(R.string.aspect)); listOf("Fit" to AspectRatioFrameLayout.RESIZE_MODE_FIT, "Fill" to AspectRatioFrameLayout.RESIZE_MODE_FILL, "Zoom" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM, "16:9" to AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH).forEach { (n, m) -> SheetRow(n, resizeMode == m) { resizeMode = m; sheet = "" } } }
                    "speed" -> { SheetTitle(stringResource(R.string.speed)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { sp -> Pill(sp.toString() + "×", speed == sp) { speed = sp; player.setPlaybackSpeed(sp); sheet = "" } } } }
                    "format" -> { SheetTitle(stringResource(R.string.live_format)); SheetRow("HLS (m3u8)", url.contains(".m3u8")) { sheet = ""; scope.launch { url = repo.streamUrl(cur.playlist!!, cur.item!!, ts = false); load(url, 0) } }; SheetRow("MPEG-TS", !url.contains(".m3u8")) { sheet = ""; scope.launch { url = repo.streamUrl(cur.playlist!!, cur.item!!, ts = true); load(url, 0) } } }
                    "catchup" -> { SheetTitle(stringResource(R.string.catchup)); val now = System.currentTimeMillis() / 1000; val list = cur.item?.let { repo.epgFor(cur.playlist!!, it) }?.filter { it.start < now }?.takeLast(40)?.reversed() ?: emptyList()
                        if (list.isEmpty()) Text(stringResource(R.string.no_epg), color = c.muted); val fmt = java.text.SimpleDateFormat("EEE HH:mm", java.util.Locale.getDefault())
                        LazyColumn(Modifier.heightIn(max = 300.dp)) { items(list) { e -> SheetRow(fmt.format(java.util.Date(e.start * 1000)) + "   " + e.title, false) { sheet = ""; scope.launch { try { val u = repo.catchupUrl(cur.playlist!!, cur.item!!, e); LiveEngine.handoff = false; nav.push(Screen.Play(u, e.title, "cu:" + cur.item!!.id + ":" + e.start, Kind.MOVIE, cur.image, startMs = 0L)) } catch (x: Exception) {} } } } } }
                    "cast" -> { SheetTitle("Cast")
                        Text("Pick your TV, then press Cast again to send the stream.", color = c.muted, fontSize = 13.sp)
                        AndroidView(factory = { ctx -> androidx.mediarouter.app.MediaRouteButton(ctx).apply { try { com.google.android.gms.cast.framework.CastButtonFactory.setUpMediaRouteButton(ctx, this) } catch (e: Throwable) {}; setAlwaysVisible(true) } }, modifier = Modifier.padding(vertical = 8.dp).size(56.dp))
                        SheetRow("Disconnect", false) { sheet = ""; tv.ninekpro.app.CastHelper.stop(act) } }
                    "more" -> { SheetTitle(stringResource(R.string.more))
                        SheetRow(stringResource(R.string.stream_info), false) { sheet = ""; streamInfo = true }
                        SheetRow("VLC", false) { sheet = ""; onFallbackToVlc() }
                        SheetRow(stringResource(R.string.external_player), false) { sheet = ""; try { act.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*")) } catch (e: Exception) {} }
                        if (Build.VERSION.SDK_INT >= 26 && !tv.ninekpro.app.data.DeviceInfo.isTv) SheetRow("Picture-in-picture", false) { sheet = ""; try { act.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) } catch (e: Exception) {} } }
                }
            }
        }
    }
}

/**
 * Seek bar drawn by hand (the Material Slider seeked on every drag tick and stuttered): track · buffered part (white 45 %) · played part (accent) ·
 * round thumb that grows with a white ring when `focused` on TV. Touch: tap = commit at that spot; horizontal drag = `onScrub` previews only,
 * `onCommit` once on release. While `scrub` is set a bubble above the thumb shows the target time and the difference ("+1:20" / "−0:40").
 */
@Composable
fun SeekBar(pos: Long, dur: Long, buffered: Long, scrub: Long?, focused: Boolean, modifier: Modifier = Modifier, onScrub: (Long) -> Unit, onCommit: (Long) -> Unit) {
    val c = LocalColors.current
    val density = LocalDensity.current
    val shown: Long = scrub ?: pos
    val frac: Float = if (dur > 0L) (shown.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
    val bufFrac: Float = if (dur > 0L) (buffered.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
    val active: Boolean = focused || scrub != null
    val padDp = 12.dp                                   // room for the biggest thumb + ring at both ends
    val padPx: Float = with(density) { padDp.toPx() }
    var widthPx by remember { mutableStateOf(0) }
    var bubbleW by remember { mutableStateOf(0) }
    var bubbleH by remember { mutableStateOf(0) }
    val accent: Color = c.accent
    Box(modifier.height(28.dp).onSizeChanged { widthPx = it.width }) {
        Canvas(Modifier.fillMaxSize()
            .pointerInput(dur) {
                if (dur <= 0L) return@pointerInput
                detectTapGestures(onTap = { o -> val len = (size.width - 2 * padPx).coerceAtLeast(1f); onCommit((((o.x - padPx) / len).coerceIn(0f, 1f) * dur.toFloat()).toLong()) })
            }
            .pointerInput(dur) {
                if (dur <= 0L) return@pointerInput
                var last = 0L
                fun at(x: Float): Long { val len = (size.width - 2 * padPx).coerceAtLeast(1f); return (((x - padPx) / len).coerceIn(0f, 1f) * dur.toFloat()).toLong() }
                detectHorizontalDragGestures(onDragStart = { o -> last = at(o.x); onScrub(last) }, onDragEnd = { onCommit(last) }, onDragCancel = { onCommit(last) }) { change, _ ->
                    change.consume(); last = at(change.position.x); onScrub(last)
                }
            }) {
            val w: Float = size.width; val cy: Float = size.height / 2f
            val trackH: Float = (if (active) 5.dp else 4.dp).toPx()
            val x0: Float = padPx; val len: Float = (w - 2 * padPx).coerceAtLeast(1f)
            val top: Float = cy - trackH / 2f
            drawRoundRect(Color.White.copy(alpha = 0.25f), Offset(x0, top), Size(len, trackH), CornerRadius(trackH / 2f))
            if (bufFrac > 0f) drawRoundRect(Color.White.copy(alpha = 0.45f), Offset(x0, top), Size(len * bufFrac, trackH), CornerRadius(trackH / 2f))
            if (frac > 0f) drawRoundRect(accent, Offset(x0, top), Size(len * frac, trackH), CornerRadius(trackH / 2f))
            val tx: Float = x0 + len * frac
            val r: Float = (if (active) 9.dp else 6.dp).toPx()
            if (focused) { drawCircle(Color.White.copy(alpha = 0.30f), r + 5.dp.toPx(), Offset(tx, cy)) }   // glow, like tvFocus
            drawCircle(accent, r, Offset(tx, cy))
            if (focused) drawCircle(Color.White, r, Offset(tx, cy), style = Stroke(width = 2.5.dp.toPx()))   // white ring
        }
        if (scrub != null) {
            val diff: Long = scrub - pos
            val gapPx: Int = with(density) { 6.dp.roundToPx() }
            Column(Modifier.align(Alignment.TopStart)
                .offset { val tx = padPx + (widthPx - 2 * padPx).coerceAtLeast(1f) * frac; IntOffset((tx - bubbleW / 2f).roundToInt().coerceIn(0, (widthPx - bubbleW).coerceAtLeast(0)), -(bubbleH + gapPx)) }
                .onSizeChanged { bubbleW = it.width; bubbleH = it.height }
                .clip(RoundedCornerShape(8.dp)).background(Color(0xE6000000)).padding(horizontal = 10.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(fmtTime(scrub), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text((if (diff >= 0L) "+" else "−") + fmtTime(abs(diff)), color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
            }
        }
    }
}

/** One player-bar button: icon over a tiny label, focusable on TV. */
@Composable
fun PBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, big: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalColors.current
    Column(modifier.clip(RoundedCornerShape(10.dp)).tvFocus(c.accent, 10).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp).width(if (big) 60.dp else 54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(if (big) 34.dp else 24.dp)); Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
@Composable fun SheetTitle(t: String) { Text(t, color = LocalColors.current.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp)) }
@Composable
fun SheetRow(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (selected) c.accent.copy(alpha = 0.18f) else Color.Transparent).tvFocus(c.accent, 8).clickable(onClick = onClick).padding(12.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = if (selected) c.accent else Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis); if (selected) Text("✓", color = c.accent)
    }
}

/** Subtitles sheet: embedded tracks · load a file · search online (OpenSubtitles through the panel) · size · colour. */
@OptIn(UnstableApi::class)
@Composable
fun SubtitleSheet(tracks: Tracks?, player: ExoPlayer, pv: PlayerView?, cur: Screen.Play, onUrl: (Uri) -> Unit, onPickFile: () -> Unit) {
    val repo = LocalRepo.current; val c = LocalColors.current; val act = LocalActivity.current; val scope = rememberCoroutineScope()
    val brand by repo.brand.collectAsState()
    var tab by remember { mutableStateOf(0) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Pill(stringResource(R.string.subtitles), tab == 0) { tab = 0 }; if (brand.opensubsOn) Pill(stringResource(R.string.search_online), tab == 1) { tab = 1 }; Pill(stringResource(R.string.subtitle_settings), tab == 2) { tab = 2 } }
    Gap(8)
    when (tab) {
        0 -> { SheetRow(stringResource(R.string.none), false) { player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build() }
            tracks?.groups?.filter { it.type == C.TRACK_TYPE_TEXT }?.forEach { g -> for (i in 0 until g.length) { val f = g.getTrackFormat(i); SheetRow((f.language ?: "?") + (if (f.label != null) " · " + f.label else ""), g.isTrackSelected(i)) { player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i)).build() } } }
            SheetRow("📂 " + stringResource(R.string.pick_file), false) { onPickFile() }
            if (!brand.opensubsOn) Text(stringResource(R.string.subs_off_hint), color = c.muted, fontSize = 11.5.sp, modifier = Modifier.padding(top = 6.dp)) }
        1 -> {
            val (cleanTitle, year) = remember(cur.title) { repo.tmdb.cleanName(cur.seriesName.ifEmpty { cur.title }) }
            var q by remember { mutableStateOf(cleanTitle) }; var lang by remember { mutableStateOf(repo.prefs.subLang.ifEmpty { "ar" }.let { if (it == "he") "he" else it }) }
            var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }; var busy by remember { mutableStateOf(false) }; var msg by remember { mutableStateOf("") }
            fun search() { busy = true; msg = ""; scope.launch(kotlinx.coroutines.Dispatchers.IO) { val r = repo.panel.subsSearch(q, lang, cur.episode?.season ?: 0, cur.episode?.num ?: 0, year); val a = r.json.optJSONArray("rows"); val l = ArrayList<JSONObject>(); if (a != null) for (i in 0 until a.length()) l.add(a.getJSONObject(i)); rows = l; busy = false; if (!r.ok) msg = r.error + " " + r.json.optString("detail") else if (l.isEmpty()) msg = act.getString(R.string.no_items) } }
            LaunchedEffect(Unit) { search() }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Field(q, { q = it }, stringResource(R.string.search), modifier = Modifier.weight(1f))
                listOf("ar" to "AR", "he" to "HE", "en" to "EN").forEach { (k, n) -> Pill(n, lang == k) { lang = k; search() } }
                Pill("🔍", false) { search() }
            }
            Gap(6)
            if (busy) Loading() else if (msg.isNotEmpty()) Text(msg, color = c.muted, fontSize = 12.sp)
            LazyColumn(Modifier.heightIn(max = 260.dp)) { items(rows) { r -> SheetRow(r.optString("name") + "   · " + r.optString("lang").uppercase() + " · ⬇ " + r.optInt("downloads") + (if (r.optBoolean("hi")) " · HI" else ""), false) {
                scope.launch(kotlinx.coroutines.Dispatchers.IO) { val g = repo.panel.subsGet(r.optLong("file_id")); val u = g.json.optString("url")
                    if (g.ok && u.isNotEmpty()) scope.launch { onUrl(Uri.parse(u)) } else scope.launch { msg = g.error + " " + g.json.optString("detail") } } } } }
        }
        else -> { SheetTitle(stringResource(R.string.subtitle_size)); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(0.8f, 1f, 1.3f, 1.6f, 2f).forEach { sc -> Pill((sc * 100).toInt().toString() + "%", repo.prefs.subtitleScale == sc) { repo.prefs.subtitleScale = sc; pv?.subtitleView?.setFractionalTextSize(0.0533f * sc) } } }
            Gap(10); SheetTitle(stringResource(R.string.subtitle_color)); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("white", "yellow", "cyan").forEach { k -> Pill(k, repo.prefs.subColor == k) { repo.prefs.subColor = k; val col = when (k) { "yellow" -> Color.Yellow; "cyan" -> Color.Cyan; else -> Color.White }; pv?.subtitleView?.setStyle(CaptionStyleCompat(col.hashCode(), 0x00000000, 0, CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.Black.hashCode(), null)) } } }
            // language: saved AND applied to the running player (it used to only write the pref); "" = no subtitles
            var subLangSel by remember { mutableStateOf(repo.prefs.subLang) }
            Gap(10); SheetTitle(stringResource(R.string.subtitle_lang)); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("ar" to "العربية", "he" to "עברית", "en" to "English", "" to stringResource(R.string.none)).forEach { (k, n) -> Pill(n, subLangSel == k) { subLangSel = k; repo.prefs.subLang = k
                try { player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setPreferredTextLanguage(k.ifEmpty { null }).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, k.isEmpty()).build() } catch (e: Exception) {} } } } }
    }
}

/** Play the episode after `s` (replacing the player screen); `onNone` runs when this was the last one. */
fun playNext(repo: tv.ninekpro.app.data.Repo, nav: Nav, s: Screen.Play, onNone: () -> Unit = {}) {
    val ep = s.episode ?: return; val pl = s.playlist ?: return
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
        try {
            val info = repo.seriesInfo(pl, ep.seriesId); val all = info.seasons.values.flatten(); val i = all.indexOfFirst { it.id == ep.id }
            if (i >= 0 && i + 1 < all.size) playEpisode(repo, nav, pl, info, all[i + 1], s.seriesName, replace = true) else onNone()
        } catch (e: Exception) { }
    }
}
