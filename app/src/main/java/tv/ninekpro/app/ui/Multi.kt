package tv.ninekpro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import tv.ninekpro.app.R
import tv.ninekpro.app.data.DeviceInfo
import tv.ninekpro.app.data.Item
import tv.ninekpro.app.data.Kind
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/** Multi screen: 2 or 4 live windows; the highlighted window has sound. Pick a window, then a channel from the list on the right. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun MultiScreen() {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current; val act = LocalActivity.current
    val pl = repo.activePlaylist ?: return
    var count by remember { mutableStateOf(2) }
    var focus by remember { mutableStateOf(0) }
    val slots = remember { mutableListOf<Item?>(null, null, null, null) }
    var tick by remember { mutableStateOf(0) }
    // Players are built lazily per window (2 or 4) with a small buffer — four full players at once killed weak boxes.
    val players = remember { arrayOfNulls<ExoPlayer>(4) }
    val errs = remember { mutableStateListOf("", "", "", "") }
    fun playerAt(i: Int): ExoPlayer {
        players[i]?.let { return it }
        val http = DefaultHttpDataSource.Factory().setUserAgent("9KProTV/1.0").setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(15000).setReadTimeoutMs(20000)
        val lc = DefaultLoadControl.Builder().setBufferDurationsMs(if (DeviceInfo.lite) 5_000 else 8_000, if (DeviceInfo.lite) 10_000 else 20_000, 700, 1_500).setPrioritizeTimeOverSizeThresholds(false).build()
        // Only window 0 asks for audio focus: several players requesting focus would silence each other.
        val p = ExoPlayer.Builder(act).setLoadControl(lc).setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setAudioAttributes(AudioAttributes.DEFAULT, i == 0).setHandleAudioBecomingNoisy(true).setWakeMode(C.WAKE_MODE_NETWORK).build()
        p.volume = if (i == focus) 1f else 0f
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { errs[i] = error.errorCodeName.removePrefix("ERROR_CODE_").replace('_', ' ').lowercase() }
            override fun onPlaybackStateChanged(playbackState: Int) { if (playbackState == Player.STATE_READY) errs[i] = "" }
        })
        players[i] = p; return p
    }
    fun releaseAt(i: Int) { try { players[i]?.release() } catch (e: Exception) {}; players[i] = null; slots[i] = null; errs[i] = "" }
    DisposableEffect(Unit) { onDispose { for (i in 0 until 4) releaseAt(i) } }
    PauseInBackground(onStop = { players.forEach { try { it?.pause() } catch (e: Exception) {} } }, onStart = { players.forEach { try { it?.play() } catch (e: Exception) {} } })
    LaunchedEffect(focus, tick) { players.forEachIndexed { i, p -> p?.volume = if (i == focus) 1f else 0f } }
    LaunchedEffect(count) { if (count == 2) { releaseAt(2); releaseAt(3); if (focus > 1) focus = 0; tick++ } }
    var channels by remember { mutableStateOf<List<Item>>(emptyList()) }
    LaunchedEffect(pl.id) { try { channels = repo.content(pl, Kind.LIVE).items } catch (e: Throwable) {} }
    var q by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(0.72f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill("‹", false) { nav.pop() }; Text(stringResource(R.string.multi_screen), color = c.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Pill("2", count == 2) { count = 2 }; Pill("4", count == 4) { count = 4 }
            }
            val cells: @Composable (Int, Modifier) -> Unit = { i, m ->
                Box(m.padding(3.dp).background(Color.Black).border(2.dp, if (focus == i) c.accent else c.line).clickable { focus = i }) {
                    AndroidView(factory = { ctx -> PlayerView(ctx).apply { useController = false; player = playerAt(i); setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS) } }, onRelease = { it.player = null }, modifier = Modifier.fillMaxSize())
                    val it = slots[i]; Text(it?.name ?: stringResource(R.string.pick_channel), color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomStart).background(Color(0x88000000)).padding(6.dp, 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (errs[i].isNotEmpty()) Column(Modifier.align(Alignment.Center).background(Color(0xAA000000)).padding(10.dp, 6.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(stringResource(R.string.err_generic), color = Color.White, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(errs[i], color = Color.Gray, fontSize = 10.sp, maxLines = 1) }
                }
            }
            if (count == 2) Row(Modifier.fillMaxSize()) { cells(0, Modifier.weight(1f).fillMaxHeight()); cells(1, Modifier.weight(1f).fillMaxHeight()) }
            else Column(Modifier.fillMaxSize()) { Row(Modifier.weight(1f)) { cells(0, Modifier.weight(1f).fillMaxHeight()); cells(1, Modifier.weight(1f).fillMaxHeight()) }; Row(Modifier.weight(1f)) { cells(2, Modifier.weight(1f).fillMaxHeight()); cells(3, Modifier.weight(1f).fillMaxHeight()) } }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
        Column(Modifier.weight(0.28f).fillMaxHeight()) {
            Field(q, { q = it }, stringResource(R.string.search), modifier = Modifier.fillMaxWidth().padding(8.dp))
            val list = remember(q, channels) { if (q.length < 2) channels.take(300) else channels.filter { it.name.contains(q, true) }.take(300) }
            LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.key }) { ch ->
                    Row(Modifier.fillMaxWidth().tvFocus(c.accent, 6).clickable { slots[focus] = ch; tick++; val p = playerAt(focus); errs[focus] = ""; scope.launch { try { val u = repo.streamUrl(pl, ch); p.setMediaItem(MediaItem.fromUri(u)); p.prepare(); p.playWhenReady = true } catch (e: Exception) { errs[focus] = e.message ?: "error" } } }.padding(10.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Poster(ch.icon, Modifier.width(28.dp).fillMaxHeight(), contentScaleFit = true); Text("  " + ch.name, color = c.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
