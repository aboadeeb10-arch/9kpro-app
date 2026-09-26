package tv.ninekpro.app.ui

import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import tv.ninekpro.app.R
import tv.ninekpro.app.data.Kind
import tv.ninekpro.app.data.ResumeEntry

/** VLC engine (libVLC): plays anything ExoPlayer can't. Used when Settings → Change Player = VLC, or automatically after ExoPlayer fails. */
@Composable
fun VlcPlayerScreen(s: Screen.Play) {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current; val act = LocalActivity.current
    var controls by remember { mutableStateOf(true) }; var playing by remember { mutableStateOf(true) }
    var pos by remember { mutableStateOf(0L) }; var dur by remember { mutableStateOf(0L) }; var err by remember { mutableStateOf("") }
    val libVlc = remember { LibVLC(act, arrayListOf("--network-caching=1500", "--http-reconnect", "--no-drop-late-frames", "--no-skip-frames", "--sub-text-scale=" + (100 * repo.prefs.subtitleScale).toInt())) }
    val player = remember { MediaPlayer(libVlc) }
    PauseInBackground(onStop = { try { player.pause() } catch (e: Exception) {} }, onStart = { try { player.play() } catch (e: Exception) {} })
    var layoutRef by remember { mutableStateOf<VLCVideoLayout?>(null) }

    LaunchedEffect(Unit) {
        try {
            val m = Media(libVlc, Uri.parse(s.url)); m.setHWDecoderEnabled(true, false); m.addOption(":network-caching=1500")
            player.media = m; m.release()
            val r = if (s.kind != Kind.LIVE) repo.resumeOf(s.key) else null
            player.play(); if (r != null && r.positionMs > 0) { delay(800); player.time = r.positionMs }
            repo.nowPlaying = org.json.JSONObject().put("kind", s.kind.name.lowercase()).put("id", s.key.substringAfterLast(':')).put("name", s.title)
        } catch (e: Exception) { err = e.message ?: "vlc" }
    }
    LaunchedEffect(Unit) {
        var hideAt = System.currentTimeMillis() + 4000; var lastSave = 0L
        while (true) {
            pos = player.time; dur = player.length.coerceAtLeast(0); playing = player.isPlaying
            if (controls && playing && System.currentTimeMillis() > hideAt) controls = false
            if (!controls) hideAt = System.currentTimeMillis() + 4000
            if (s.kind != Kind.LIVE && !s.key.startsWith("cu:") && dur > 0 && System.currentTimeMillis() - lastSave > 15000) { lastSave = System.currentTimeMillis(); repo.saveResume(ResumeEntry(s.key, s.title, s.kind.name, s.image, pos, dur, lastSave, if (s.episode != null && s.playlist != null) episodeExtra(s.playlist, s.episode, s.seriesName) else if (s.item != null && s.playlist != null) itemExtra(s.playlist, s.item) else "")) }
            delay(1000)
        }
    }
    DisposableEffect(Unit) {
        act.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            act.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            try { if (s.kind != Kind.LIVE && !s.key.startsWith("cu:") && player.length > 0) repo.saveResume(ResumeEntry(s.key, s.title, s.kind.name, s.image, player.time, player.length, System.currentTimeMillis(), if (s.episode != null && s.playlist != null) episodeExtra(s.playlist, s.episode, s.seriesName) else if (s.item != null && s.playlist != null) itemExtra(s.playlist, s.item) else "")) } catch (e: Exception) {}
            repo.nowPlaying = null; repo.syncNow()
            try { player.stop(); player.detachViews(); player.release(); libVlc.release() } catch (e: Exception) {}
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black).clickable { controls = !controls }) {
        AndroidView(factory = { ctx -> VLCVideoLayout(ctx).also { v -> layoutRef = v; player.attachViews(v, null, false, false) } }, modifier = Modifier.fillMaxSize())
        if (err.isNotEmpty()) Text(err, color = Color.White, modifier = Modifier.align(Alignment.Center))
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
                if (s.kind != Kind.LIVE) IconButton(onClick = { player.time = (player.time - 10_000).coerceAtLeast(0) }, modifier = Modifier.size(56.dp)) { Icon(Icons.Default.Replay10, null, tint = Color.White, modifier = Modifier.size(34.dp)) }
                IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }, modifier = Modifier.size(72.dp)) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(48.dp)) }
                if (s.kind != Kind.LIVE) IconButton(onClick = { player.time = player.time + 10_000 }, modifier = Modifier.size(56.dp)) { Icon(Icons.Default.Forward10, null, tint = Color.White, modifier = Modifier.size(34.dp)) }
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
