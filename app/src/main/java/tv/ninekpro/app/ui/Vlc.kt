package tv.ninekpro.app.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import tv.ninekpro.app.R
import tv.ninekpro.app.data.Kind
import tv.ninekpro.app.data.ResumeEntry

/** VLC engine (libVLC): plays anything ExoPlayer can't. Used when Settings → Change Player = VLC, or automatically after ExoPlayer fails. `onBackToExo` (engine = auto) offers ExoPlayer again when VLC fails too. */
@Composable
fun VlcPlayerScreen(s: Screen.Play, onBackToExo: (() -> Unit)? = null) {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current; val act = LocalActivity.current
    val scope = rememberCoroutineScope()
    var controls by remember { mutableStateOf(true) }; var playing by remember { mutableStateOf(true) }
    var pos by remember { mutableStateOf(0L) }; var dur by remember { mutableStateOf(0L) }; var err by remember { mutableStateOf("") }
    var finished by remember { mutableStateOf(false) }
    // Weak boxes (DeviceInfo.lite) are allowed to drop late frames — otherwise the picture stutters and audio runs ahead.
    val libVlc = remember { val o = arrayListOf("--network-caching=1500", "--http-reconnect"); if (!tv.ninekpro.app.data.DeviceInfo.lite) { o.add("--no-drop-late-frames"); o.add("--no-skip-frames") }; o.add("--sub-text-scale=" + (100 * repo.prefs.subtitleScale).toInt()); LibVLC(act, o) }
    val player = remember { MediaPlayer(libVlc) }
    PauseInBackground(onStop = { try { player.pause() } catch (e: Exception) {} }, onStart = { try { if (err.isEmpty() && !finished) player.play() } catch (e: Exception) {} })
    var layoutRef by remember { mutableStateOf<VLCVideoLayout?>(null) }

    /** (Re)load the stream; startMs > 0 seeks there once playing. */
    fun start(startMs: Long) {
        err = ""; finished = false
        try {
            val m = Media(libVlc, Uri.parse(s.url)); m.setHWDecoderEnabled(true, false); m.addOption(":network-caching=1500")
            player.media = m; m.release()
            player.play(); if (startMs > 0) scope.launch { delay(800); try { player.time = startMs } catch (e: Exception) {} }
        } catch (e: Exception) { err = e.message ?: "vlc" }
    }
    LaunchedEffect(Unit) {
        // libVLC reports stream failure and the end of the stream only through events: a dead link used to leave a black screen forever.
        try { player.setEventListener { ev ->
            when (ev.type) {
                MediaPlayer.Event.EncounteredError -> { if (err.isEmpty()) err = "VLC: EncounteredError" }
                MediaPlayer.Event.EndReached -> {
                    if (s.kind == Kind.LIVE || s.key.startsWith("cu:")) scope.launch { delay(1500); if (err.isEmpty()) start(0) }   // live: the link dropped → reconnect
                    else if (!finished) { finished = true; if (s.episode != null) playNext(repo, nav, s) { nav.pop() } else nav.pop() }
                }
                else -> {}
            }
        } } catch (e: Exception) {}
        val r = if (s.kind != Kind.LIVE && !s.key.startsWith("cu:")) repo.resumeOf(s.key) else null
        start(if (s.startMs > 0) s.startMs else if (s.startMs == 0L) 0L else (r?.positionMs ?: 0L))
        repo.nowPlaying = org.json.JSONObject().put("kind", s.kind.name.lowercase()).put("id", s.key.substringAfterLast(':')).put("name", s.title)
    }
    // Remote-control handling (TV boxes): any key keeps the bar open; focus lands on Play when it appears and returns to the video when it hides.
    var lastInteract by remember { mutableStateOf(System.currentTimeMillis()) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }; val ctlFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { try { focus.requestFocus() } catch (e: Exception) {} }
    LaunchedEffect(controls) { if (controls) { lastInteract = System.currentTimeMillis(); delay(80); try { ctlFocus.requestFocus() } catch (e: Exception) {} } else { delay(80); try { focus.requestFocus() } catch (e: Exception) {} } }
    LaunchedEffect(Unit) {
        var lastSave = 0L
        while (true) {
            pos = player.time; dur = player.length.coerceAtLeast(0); playing = player.isPlaying
            if (controls && playing && System.currentTimeMillis() - lastInteract > 5000) controls = false
            if (s.kind != Kind.LIVE && !s.key.startsWith("cu:") && dur > 0 && System.currentTimeMillis() - lastSave > 15000) { lastSave = System.currentTimeMillis(); repo.saveResume(ResumeEntry(s.key, s.title, s.kind.name, s.image, pos, dur, lastSave, if (s.episode != null && s.playlist != null) episodeExtra(s.playlist, s.episode, s.seriesName) else if (s.item != null && s.playlist != null) itemExtra(s.playlist, s.item) else "")) }
            delay(1000)
        }
    }
    DisposableEffect(Unit) {
        act.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            act.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            try { if (finished) repo.removeResume(s.key) else if (s.kind != Kind.LIVE && !s.key.startsWith("cu:") && player.length > 0) repo.saveResume(ResumeEntry(s.key, s.title, s.kind.name, s.image, player.time, player.length, System.currentTimeMillis(), if (s.episode != null && s.playlist != null) episodeExtra(s.playlist, s.episode, s.seriesName) else if (s.item != null && s.playlist != null) itemExtra(s.playlist, s.item) else "")) } catch (e: Exception) {}
            repo.nowPlaying = null; repo.syncNow()
            try { player.setEventListener(null) } catch (e: Exception) {}
            try { player.stop(); player.detachViews(); player.release(); libVlc.release() } catch (e: Exception) {}
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black).focusRequester(focus)
        .onPreviewKeyEvent { ev -> if (ev.type == KeyEventType.KeyDown) lastInteract = System.currentTimeMillis(); false }
        .onKeyEvent { ev ->
            if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
            when (ev.key) {
                Key.DirectionCenter, Key.Enter, Key.MediaPlayPause, Key.DirectionUp, Key.DirectionDown, Key.Menu -> if (!controls) { controls = true; true } else false
                Key.MediaPlay -> { player.play(); true }
                Key.MediaPause -> { player.pause(); true }
                Key.DirectionLeft, Key.MediaRewind -> if (!controls && s.kind != Kind.LIVE) { player.time = (player.time - 10_000).coerceAtLeast(0); true } else false
                Key.DirectionRight, Key.MediaFastForward -> if (!controls && s.kind != Kind.LIVE) { player.time = player.time + 10_000; true } else false
                Key.Back, Key.Escape -> if (controls && tv.ninekpro.app.data.DeviceInfo.isTv) { controls = false; true } else false
                else -> false
            }
        }
        .clickable { lastInteract = System.currentTimeMillis(); controls = !controls }) {
        AndroidView(factory = { ctx -> VLCVideoLayout(ctx).also { v -> layoutRef = v; player.attachViews(v, null, false, false) } }, modifier = Modifier.fillMaxSize())
        if (err.isNotEmpty()) Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.err_generic), color = Color.White); Gap(6); Text(err, color = Color.Gray, fontSize = 11.sp); Gap(12)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { BigButton(stringResource(R.string.retry)) { start(if (s.kind == Kind.LIVE) 0L else pos) }; if (onBackToExo != null) BigButton(stringResource(R.string.err_back_to_exo), filled = false) { onBackToExo() } }
        }
        if (controls) Column(Modifier.fillMaxSize().background(Color(0x66000000))) {
            TopBar(s.title + "  ·  VLC") {
                var m by remember { mutableStateOf(false) }
                Box { IconButton(onClick = { m = true }) { Icon(Icons.Default.Tune, null, tint = Color.White) }
                    DropdownMenu(expanded = m, onDismissRequest = { m = false }) {
                        Text(stringResource(R.string.audio), color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(12.dp, 4.dp))
                        (player.audioTracks ?: emptyArray()).forEach { t -> DropdownMenuItem(text = { Text(t.name + (if (player.audioTrack == t.id) " ✓" else "")) }, onClick = { player.audioTrack = t.id; m = false }) }
                        Text(stringResource(R.string.subtitles), color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(12.dp, 4.dp))
                        DropdownMenuItem(text = { Text(stringResource(R.string.none)) }, onClick = { player.spuTrack = -1; m = false })
                        (player.spuTracks ?: emptyArray()).forEach { t -> DropdownMenuItem(text = { Text(t.name + (if (player.spuTrack == t.id) " ✓" else "")) }, onClick = { player.spuTrack = t.id; m = false }) }
                        Text(stringResource(R.string.aspect), color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(12.dp, 4.dp))
                        listOf("Fit" to null, "16:9" to "16:9", "4:3" to "4:3", "Fill" to "fill").forEach { (n, a) -> DropdownMenuItem(text = { Text(n) }, onClick = { if (a == "fill") { player.aspectRatio = null; player.scale = 0f; player.videoScale = MediaPlayer.ScaleType.SURFACE_FILL } else { player.aspectRatio = a; player.scale = 0f; player.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT }; m = false }) }
                    } }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (s.kind != Kind.LIVE) IconButton(onClick = { player.time = (player.time - 10_000).coerceAtLeast(0) }, modifier = Modifier.size(56.dp).tvFocus(c.accent, 28)) { Icon(Icons.Default.Replay10, null, tint = Color.White, modifier = Modifier.size(34.dp)) }
                IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }, modifier = Modifier.size(72.dp).focusRequester(ctlFocus).tvFocus(c.accent, 36)) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(48.dp)) }
                if (s.kind != Kind.LIVE) IconButton(onClick = { player.time = player.time + 10_000 }, modifier = Modifier.size(56.dp).tvFocus(c.accent, 28)) { Icon(Icons.Default.Forward10, null, tint = Color.White, modifier = Modifier.size(34.dp)) }
            }
            Spacer(Modifier.weight(1f))
            if (s.kind != Kind.LIVE && dur > 0) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(fmtTime(pos), color = Color.White, fontSize = 12.sp); Spacer(Modifier.width(8.dp))
                Slider(value = (pos.toFloat() / dur).coerceIn(0f, 1f), onValueChange = { player.time = (it * dur).toLong() }, modifier = Modifier.weight(1f).height(24.dp), colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent))
                Spacer(Modifier.width(8.dp)); Text(fmtTime(dur), color = Color.White, fontSize = 12.sp)
            } else Gap(24)
        }
    }
}
