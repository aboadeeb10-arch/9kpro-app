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
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import tv.ninekpro.app.R
import tv.ninekpro.app.data.Item
import tv.ninekpro.app.data.Kind
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
    val players = remember { List(4) { val http = DefaultHttpDataSource.Factory().setUserAgent("9KProTV/1.0").setAllowCrossProtocolRedirects(true); ExoPlayer.Builder(act).setMediaSourceFactory(DefaultMediaSourceFactory(http)).build() } }
    DisposableEffect(Unit) { onDispose { players.forEach { try { it.release() } catch (e: Exception) {} } } }
    PauseInBackground(onStop = { players.forEach { try { it.pause() } catch (e: Exception) {} } }, onStart = { players.forEach { try { it.play() } catch (e: Exception) {} } })
    LaunchedEffect(focus, tick) { players.forEachIndexed { i, p -> p.volume = if (i == focus) 1f else 0f } }
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
                    AndroidView(factory = { ctx -> PlayerView(ctx).apply { useController = false; player = players[i]; setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS) } }, modifier = Modifier.fillMaxSize())
                    val it = slots[i]; Text(it?.name ?: stringResource(R.string.pick_channel), color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomStart).background(Color(0x88000000)).padding(6.dp, 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    Row(Modifier.fillMaxWidth().tvFocus(c.accent, 6).clickable { slots[focus] = ch; tick++; val p = players[focus]; scope.launch { try { val u = repo.streamUrl(pl, ch); p.setMediaItem(MediaItem.fromUri(u)); p.prepare(); p.playWhenReady = true } catch (e: Exception) {} } }.padding(10.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Poster(ch.icon, Modifier.width(28.dp).fillMaxHeight(), contentScaleFit = true); Text("  " + ch.name, color = c.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
