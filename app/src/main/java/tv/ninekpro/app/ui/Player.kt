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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
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
    if (useVlc) VlcPlayerScreen(s) else ExoScreen(s) { useVlc = true }
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

    // Live TV uses the one shared player (LiveEngine) so preview ↔ full screen never reloads; everything else gets its own.
    val shared = s.kind == Kind.LIVE
    val player = remember {
        if (shared) LiveEngine.get(act, repo.prefs.subLang) else {
        val http = DefaultHttpDataSource.Factory().setUserAgent("9KProTV/1.0").setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(15000).setReadTimeoutMs(20000)
        // Fast start: begin playing after ~0.7 s of data instead of ExoPlayer's 2.5 s default, keep filling the buffer while playing.
        val fastStart = androidx.media3.exoplayer.DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 50_000, 700, 1_500).setPrioritizeTimeOverSizeThresholds(true).build()
        ExoPlayer.Builder(act).setRenderersFactory(DefaultRenderersFactory(act).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER))
            .setLoadControl(fastStart).setMediaSourceFactory(DefaultMediaSourceFactory(tv.ninekpro.app.data.CryptDataSource.RoutingFactory(act, http))).build().apply {
                playWhenReady = true
                trackSelectionParameters = trackSelectionParameters.buildUpon().setPreferredTextLanguage(repo.prefs.subLang.ifEmpty { null }).build()
            } }
    }
    fun buildItem(u: String, sub: Uri?): MediaItem {
        val b = MediaItem.Builder().setUri(u)
        if (sub != null) b.setSubtitleConfigurations(listOf(MediaItem.SubtitleConfiguration.Builder(sub).setMimeType(if (sub.toString().lowercase().endsWith(".vtt")) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).setLanguage("und").build()))
        return b.build()
    }
    fun load(u: String, startMs: Long) { err = ""; if (shared) LiveEngine.key = cur.key; player.setMediaItem(buildItem(u, subUri), if (startMs > 0) startMs else C.TIME_UNSET); player.prepare(); player.play() }
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
    DisposableEffect(Unit) {
        act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onTracksChanged(t: Tracks) { tracks = t }
            override fun onPlayerError(e: PlaybackException) {
                scope.launch {
                    attempts++
                    val pl = cur.playlist; val item = cur.item
                    if (pl != null && attempts <= 3) try { android.widget.Toast.makeText(act, act.getString(R.string.trying_server, attempts + 1), android.widget.Toast.LENGTH_SHORT).show() } catch (x: Exception) {}
                    if (pl != null && attempts <= 3) {
                        try {
                            if (cur.kind == Kind.LIVE && item != null && attempts == 1 && repo.prefs.liveFormat == "auto") { url = repo.streamUrl(pl, item, ts = true); load(url, 0); return@launch }
                            repo.forgetHost(pl)
                            url = if (item != null) repo.streamUrl(pl, item, ts = attempts > 1) else if (cur.episode != null) repo.episodeUrl(pl, cur.episode!!) else url
                            load(url, player.currentPosition); return@launch
                        } catch (x: Exception) { }
                    }
                    if (repo.prefs.playerEngine == "auto" && attempts <= 4) { LiveEngine.handoff = false; onFallbackToVlc(); return@launch }
                    err = e.errorCodeName
                }
            }
        }
        player.addListener(l)
        onDispose {
            act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (s.kind != Kind.LIVE && !s.key.startsWith("cu:") && player.duration > 0) repo.saveResume(ResumeEntry(s.key, s.title, s.kind.name, s.image, player.currentPosition, player.duration, System.currentTimeMillis(), if (s.episode != null && s.playlist != null) episodeExtra(s.playlist, s.episode, s.seriesName) else if (s.item != null && s.playlist != null) itemExtra(s.playlist, s.item) else ""))
            repo.nowPlaying = null; repo.syncNow()
            player.removeListener(l)
            if (!shared) player.release() else if (!LiveEngine.handoff) LiveEngine.stop()   // opened from the preview → keep playing for it
        }
    }
    LaunchedEffect(Unit) {
        var hideAt = System.currentTimeMillis() + 4000; var lastSave = 0L
        while (true) {
            pos = player.currentPosition; dur = player.duration.coerceAtLeast(0); clock = clockText(repo.prefs.timeFormat == "24")
            if (controls && playing && System.currentTimeMillis() > hideAt) controls = false
            if (!controls) hideAt = System.currentTimeMillis() + 4000
            if (s.kind != Kind.LIVE && !s.key.startsWith("cu:") && dur > 0 && System.currentTimeMillis() - lastSave > 15000) { lastSave = System.currentTimeMillis()
                repo.saveResume(ResumeEntry(s.key, s.title, s.kind.name, s.image, pos, dur, lastSave, if (s.episode != null && s.playlist != null) episodeExtra(s.playlist, s.episode, s.seriesName) else if (s.item != null && s.playlist != null) itemExtra(s.playlist, s.item) else "")) }
            if (s.episode != null && dur > 0 && dur - pos < 12_000 && nextCountdown < 0) nextCountdown = 10
            if (nextCountdown > 0) { nextCountdown--; if (nextCountdown == 0) playNext(repo, nav, s) }
            if (gestureText.isNotEmpty() && System.currentTimeMillis() > gestureUntil) gestureText = ""
            if (numBuf.isNotEmpty() && System.currentTimeMillis() - numAt > 1500) { val n = numBuf.toIntOrNull(); numBuf = ""; val t = repo.liveContext.firstOrNull { it.num == n }; if (t != null) zap(t) }
            delay(1000)
        }
    }
    val subPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) { try { act.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}; subUri = uri; load(url, player.currentPosition) } }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    PauseInBackground(onStop = { try { player.pause() } catch (e: Exception) {} }, onStart = { try { if (!ask && err.isEmpty()) player.play() } catch (e: Exception) {} })
    val subColor = when (repo.prefs.subColor) { "yellow" -> Color.Yellow; "cyan" -> Color.Cyan; else -> Color.White }

    Box(Modifier.fillMaxSize().background(Color.Black).focusRequester(focus).onKeyEvent { ev ->
        if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
        when (ev.key) {
            Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> { if (sideList) false else if (!controls) { if (cur.kind == Kind.LIVE) { infoUntil = System.currentTimeMillis() + 5000; controls = true } else controls = true; true } else false }
            Key.MediaPlay -> { player.play(); true }
            Key.MediaPause -> { player.pause(); true }
            Key.DirectionLeft, Key.MediaRewind -> { if (sideList) false else if (cur.kind == Kind.LIVE && !controls) { sideList = true; true } else if (!controls && cur.kind != Kind.LIVE) { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)); true } else false }
            Key.DirectionRight, Key.MediaFastForward -> { if (sideList) { sideList = false; true } else if (!controls && cur.kind != Kind.LIVE) { player.seekTo(player.currentPosition + 10_000); true } else false }
            Key.DirectionUp -> { if (sideList) false else if (cur.kind == Kind.LIVE && !controls) { zapDelta(1); true } else { controls = true; true } }
            Key.DirectionDown -> { if (sideList) false else if (cur.kind == Kind.LIVE && !controls) { zapDelta(-1); true } else { controls = true; true } }
            Key.ChannelUp -> { zapDelta(1); true }
            Key.ChannelDown -> { zapDelta(-1); true }
            Key.Menu -> { controls = true; true }
            Key.Back, Key.Escape -> { if (sideList) { sideList = false; true } else false }
            Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine -> {
                if (cur.kind != Kind.LIVE) false else { val d = listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine).indexOf(ev.key); numBuf = (numBuf + d).takeLast(4); numAt = System.currentTimeMillis(); infoUntil = numAt + 2500; true }
            }
            else -> false
        }
    }) {
        AndroidView(factory = { ctx -> PlayerView(ctx).apply { useController = false; this.player = player; setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS); setKeepContentOnPlayerReset(true)
            subtitleView?.setStyle(CaptionStyleCompat(subColor.hashCode(), 0x00000000, 0, CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.Black.hashCode(), null)); subtitleView?.setFractionalTextSize(0.0533f * repo.prefs.subtitleScale); playerViewRef = this } },
            onRelease = { it.player = null }, update = { it.resizeMode = resizeMode }, modifier = Modifier.fillMaxSize()
                .pointerInput(cur.kind) { detectTapGestures(onTap = { if (cur.kind == Kind.LIVE) { infoUntil = System.currentTimeMillis() + 5000 }; controls = !controls },
                    onDoubleTap = { off -> if (cur.kind != Kind.LIVE) { val fwd = off.x > size.width / 2; player.seekTo((player.currentPosition + if (fwd) 10_000 else -10_000).coerceAtLeast(0)); flash(if (fwd) "⏩ +10s" else "⏪ −10s") } }) }
                .pointerInput(cur.kind) {
                    // phone gestures: vertical swipe = channel up/down (live) or brightness (left) / volume (right) (VOD); horizontal drag = seek (VOD)
                    var dx = 0f; var dy = 0f; var axis = 0; var startX = 0f
                    val am = act.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                    detectDragGestures(onDragStart = { o -> dx = 0f; dy = 0f; axis = 0; startX = o.x }, onDragEnd = {
                        if (cur.kind == Kind.LIVE) { if (axis == 2 && kotlin.math.abs(dy) > 90) zapDelta(if (dy < 0) 1 else -1) }
                        else if (axis == 1 && kotlin.math.abs(dx) > 30) { val delta = (dx / size.width * 180_000L).toLong(); player.seekTo((player.currentPosition + delta).coerceIn(0, player.duration.coerceAtLeast(0))); flash((if (delta >= 0) "+" else "−") + (kotlin.math.abs(delta) / 1000) + "s") }
                    }) { change, drag ->
                        change.consume(); dx += drag.x; dy += drag.y
                        if (axis == 0 && (kotlin.math.abs(dx) > 24 || kotlin.math.abs(dy) > 24)) axis = if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) 1 else 2
                        if (axis == 1 && cur.kind != Kind.LIVE) flash((if (dx >= 0) "+" else "−") + (kotlin.math.abs(dx / size.width * 180).toInt()) + "s")
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
                Text("Server: " + (try { Uri.parse(url).host ?: "" } catch (e: Exception) { "" }), fontSize = 13.sp)
                Text("Engine: ExoPlayer · attempts " + attempts, fontSize = 13.sp) } })
        if (err.isNotEmpty()) Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(stringResource(R.string.playing_error), color = Color.White); Gap(6); Text(err, color = Color.Gray, fontSize = 11.sp); Gap(12)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { BigButton(stringResource(R.string.retry)) { attempts = 0; load(url, player.currentPosition) }; BigButton("VLC", filled = false) { onFallbackToVlc() } } }
        if (nextCountdown > 0) Box(Modifier.align(Alignment.BottomEnd).padding(24.dp).clip(RoundedCornerShape(12.dp)).background(c.accent).clickable { nextCountdown = -1; playNext(repo, nav, s) }.padding(horizontal = 16.dp, vertical = 10.dp)) { Text(stringResource(R.string.next_episode) + " · " + nextCountdown, color = Color.White, fontWeight = FontWeight.Bold) }

        // receiver-style info bar for live channels
        if (cur.kind == Kind.LIVE && (numBuf.isNotEmpty() || System.currentTimeMillis() < infoUntil) && !controls && !sideList) {
            val fmt = java.text.SimpleDateFormat(if (repo.prefs.timeFormat == "24") "HH:mm" else "h:mm a", java.util.Locale.getDefault())
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xD9060C1C)).padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (numBuf.isNotEmpty()) numBuf else (cur.item?.num?.takeIf { it > 0 }?.toString() ?: ""), color = c.accent, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
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
        if (sideList) {
            val list = repo.liveContext; val st = rememberLazyListState(initialFirstVisibleItemIndex = list.indexOfFirst { it.key == cur.key }.coerceAtLeast(0))
            Box(Modifier.fillMaxHeight().width(300.dp).background(Color(0xE6070D20)).clickable(enabled = false) {}) {
                LazyColumn(state = st, modifier = Modifier.fillMaxSize()) {
                    items(list, key = { it.key }) { ch ->
                        Row(Modifier.fillMaxWidth().background(if (ch.key == cur.key) c.selected else Color.Transparent).tvFocus(c.accent, 0).clickable { zap(ch); sideList = false }.padding(12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (ch.num > 0) ch.num.toString() else "", color = c.muted, fontSize = 12.sp, modifier = Modifier.width(34.dp)); Poster(ch.icon, Modifier.size(30.dp, 20.dp), contentScaleFit = true); Spacer(Modifier.width(8.dp))
                            Text(ch.name, color = if (ch.key == cur.key) c.accent else Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.6f).clickable { sideList = false })
        }

        if (controls) {
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
                    Text(fmtTime(pos), color = Color.White, fontSize = 12.sp); Spacer(Modifier.width(10.dp))
                    Slider(value = (pos.toFloat() / dur).coerceIn(0f, 1f), onValueChange = { player.seekTo((it * dur).toLong()) }, modifier = Modifier.weight(1f).height(26.dp), colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = Color.White.copy(alpha = 0.25f)))
                    Spacer(Modifier.width(10.dp)); Text(fmtTime(dur), color = Color.White, fontSize = 12.sp)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    PBtn(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) stringResource(R.string.pause) else stringResource(R.string.play), big = true) { if (player.isPlaying) player.pause() else player.play() }
                    if (cur.kind != Kind.LIVE) { PBtn(Icons.Default.Replay10, "-10s") { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) }; PBtn(Icons.Default.Forward10, "+10s") { player.seekTo(player.currentPosition + 10_000) } }
                    if (s.episode != null) PBtn(Icons.Default.SkipNext, stringResource(R.string.next_episode)) { playNext(repo, nav, s) }
                    if (cur.kind == Kind.LIVE) { PBtn(Icons.AutoMirrored.Filled.List, stringResource(R.string.channels)) { sideList = true; controls = false }
                        PBtn(Icons.Default.CalendarViewWeek, stringResource(R.string.guide)) { nav.push(Screen.Guide) }
                        if (cur.item?.archive == true) PBtn(Icons.Default.History, stringResource(R.string.catchup)) { sheet = "catchup" }
                        PBtn(Icons.Default.FiberManualRecord, if (recId > 0) stringResource(R.string.stop_recording) else stringResource(R.string.record)) { if (recId > 0) { tv.ninekpro.app.DlService.stop(act, recId); recId = 0; flash("■ " + act.getString(R.string.recording_saved)) } else { val id = tv.ninekpro.app.data.DlStore.newId(); tv.ninekpro.app.DlService.start(act, id, url, cur.title + " · " + clockText(true), "rec"); recId = id; flash("● " + act.getString(R.string.recording)) } } }
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
        if (sheet.isNotEmpty()) androidx.compose.material3.ModalBottomSheet(onDismissRequest = { sheet = "" }, containerColor = Color(0xF20A1428), dragHandle = null) {
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
                        LazyColumn(Modifier.heightIn(max = 300.dp)) { items(list) { e -> SheetRow(fmt.format(java.util.Date(e.start * 1000)) + "   " + e.title, false) { sheet = ""; scope.launch { try { val u = repo.catchupUrl(cur.playlist!!, cur.item!!, e); nav.push(Screen.Play(u, e.title, "cu:" + cur.item!!.id + ":" + e.start, Kind.MOVIE, cur.image, startMs = 0L)) } catch (x: Exception) {} } } } } }
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

/** One player-bar button: icon over a tiny label, focusable on TV. */
@Composable
fun PBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, big: Boolean = false, onClick: () -> Unit) {
    val c = LocalColors.current
    Column(Modifier.clip(RoundedCornerShape(10.dp)).tvFocus(c.accent, 10).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp).width(if (big) 60.dp else 54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
            Gap(10); SheetTitle(stringResource(R.string.subtitle_lang)); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("ar" to "العربية", "he" to "עברית", "en" to "English").forEach { (k, n) -> Pill(n, repo.prefs.subLang == k) { repo.prefs.subLang = k } } } }
    }
}

fun playNext(repo: tv.ninekpro.app.data.Repo, nav: Nav, s: Screen.Play) {
    val ep = s.episode ?: return; val pl = s.playlist ?: return
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
        try {
            val info = repo.seriesInfo(pl, ep.seriesId); val all = info.seasons.values.flatten(); val i = all.indexOfFirst { it.id == ep.id }
            if (i >= 0 && i + 1 < all.size) playEpisode(repo, nav, pl, info, all[i + 1], s.seriesName, replace = true)
        } catch (e: Exception) { }
    }
}
