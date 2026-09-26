@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package tv.ninekpro.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import tv.ninekpro.desktop.data.AsyncImage
import tv.ninekpro.desktop.data.Item
import tv.ninekpro.desktop.data.Kind
import tv.ninekpro.desktop.data.ResumeEntry

/** Global key handler installed by the screen that owns the keyboard (the player). */
object Keys { var handler: ((KeyEvent) -> Boolean)? = null }

/** Draws the shared player's frames, letterboxed. */
@Composable
fun VideoView(modifier: Modifier = Modifier) {
    val p = VlcPlayer.shared
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        val f = p.frame
        if (f != null) Image(f, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        if (p.buffering > 0f && !p.error) Loading()
    }
}

@Composable
fun PlayerScreen(s: Screen.Play) {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current; val win = LocalWindow.current
    val p = VlcPlayer.shared; val scope = rememberCoroutineScope()
    var showUi by remember { mutableStateOf(true) }; var lastMove by remember { mutableStateOf(System.currentTimeMillis()) }
    var cur by remember { mutableStateOf(s) }
    var sideList by remember { mutableStateOf(false) }
    var numBuf by remember { mutableStateOf("") }
    var sheet by remember { mutableStateOf("") }   // "audio" | "subs" | "aspect" | ""
    var toast by remember { mutableStateOf("") }
    val pl = repo.activePlaylist
    val liveList = repo.liveContext
    val fmt24 = repo.prefs.timeFormat == "24"

    fun playItem(it: Item) { scope.launch { val u = try { repo.streamUrl(pl ?: return@launch, it, repo.prefs.liveFormat == "ts") } catch (e: Throwable) { "" }; if (u.isNotEmpty()) { cur = Screen.Play(u, it.name, it, live = true, image = it.icon); p.play(u); repo.setLastChannel(it.key); repo.nowPlaying = JSONObject().put("id", it.key).put("name", it.name).put("kind", "LIVE") } } }
    fun zap(delta: Int) { val it = cur.item ?: return; val i = liveList.indexOfFirst { x -> x.key == it.key }; if (i < 0 || liveList.isEmpty()) return; playItem(liveList[(i + delta + liveList.size) % liveList.size]) }
    fun zapNumber(n: Int) { val it = liveList.firstOrNull { x -> x.num == n } ?: liveList.getOrNull(n - 1) ?: return; playItem(it) }

    LaunchedEffect(s) {
        if (p.url != s.url) p.play(s.url, s.startMs, s.sub) else if (s.startMs > 0) p.seekTo(s.startMs)
        val it = s.item
        repo.nowPlaying = JSONObject().put("id", s.resumeKey.ifEmpty { it?.key ?: s.title }).put("name", s.title).put("kind", if (s.live) "LIVE" else it?.kind?.name ?: "VOD")
        if (s.live && it != null) repo.setLastChannel(it.key)
    }
    // auto-hide controls, resume saving, numeric zapping timeout
    LaunchedEffect(Unit) { while (true) { delay(500); if (showUi && System.currentTimeMillis() - lastMove > 3500 && sheet.isEmpty() && !sideList) showUi = false
        if (!cur.live && cur.resumeKey.isNotEmpty() && p.length > 0 && p.playing) repo.saveResume(ResumeEntry(cur.resumeKey, cur.title, cur.item?.kind?.name ?: "SERIES", cur.image, p.time, p.length, System.currentTimeMillis(), cur.episode?.let { "S" + it.season + "E" + it.num } ?: "")) } }
    LaunchedEffect(numBuf) { if (numBuf.isNotEmpty()) { delay(1500); val n = numBuf.toIntOrNull(); numBuf = ""; if (n != null) zapNumber(n) } }
    LaunchedEffect(toast) { if (toast.isNotEmpty()) { delay(2500); toast = "" } }
    LaunchedEffect(p.error) { if (p.error) { toast = Strings.get(Strings.lang(repo.prefs.language), "playing_error"); if (cur.live && pl != null) { repo.forgetHost(pl); delay(2000); cur.item?.let { playItem(it) } } } }
    LaunchedEffect(p.ended) { if (p.ended && !cur.live) { delay(800); if (nav.current == s || nav.current is Screen.Play) { repo.nowPlaying = null; nav.pop() } } }

    DisposableEffect(Unit) {
        Keys.handler = { e ->
            if (e.type != KeyEventType.KeyDown) false else {
                lastMove = System.currentTimeMillis(); showUi = true
                when (e.key) {
                    Key.Escape, Key.Back -> { if (sheet.isNotEmpty()) sheet = "" else if (sideList) sideList = false else { repo.nowPlaying = null; if (win.isFullscreen()) win.setFullscreen(false); nav.pop() }; true }
                    Key.F, Key.Enter -> { win.setFullscreen(!win.isFullscreen()); true }
                    Key.Spacebar, Key.P -> { p.togglePause(); true }
                    Key.M -> { p.toggleMute(); true }
                    Key.DirectionUp -> { if (cur.live) zap(1) else p.volumeTo(p.volume + 5); true }
                    Key.DirectionDown -> { if (cur.live) zap(-1) else p.volumeTo(p.volume - 5); true }
                    Key.DirectionRight -> { if (!cur.live) p.seekBy(10_000); true }
                    Key.DirectionLeft -> { if (!cur.live) p.seekBy(-10_000); true }
                    Key.PageUp -> { zap(1); true }
                    Key.PageDown -> { zap(-1); true }
                    Key.L -> { if (cur.live) sideList = !sideList; true }
                    Key.S -> { sheet = if (sheet == "subs") "" else "subs"; true }
                    Key.A -> { sheet = if (sheet == "audio") "" else "audio"; true }
                    else -> { val d = when (e.key) { Key.Zero, Key.NumPad0 -> "0"; Key.One, Key.NumPad1 -> "1"; Key.Two, Key.NumPad2 -> "2"; Key.Three, Key.NumPad3 -> "3"; Key.Four, Key.NumPad4 -> "4"; Key.Five, Key.NumPad5 -> "5"; Key.Six, Key.NumPad6 -> "6"; Key.Seven, Key.NumPad7 -> "7"; Key.Eight, Key.NumPad8 -> "8"; Key.Nine, Key.NumPad9 -> "9"; else -> "" }
                        if (d.isNotEmpty() && cur.live) { numBuf = (numBuf + d).takeLast(4); true } else false }
                }
            }
        }
        onDispose { Keys.handler = null; repo.nowPlaying = null; p.handoff = false; p.stop() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).onPointerEvent(PointerEventType.Move) { lastMove = System.currentTimeMillis(); showUi = true }
        .combinedClickable(onDoubleClick = { win.setFullscreen(!win.isFullscreen()) }) { if (sheet.isNotEmpty()) sheet = "" else showUi = !showUi }) {
        VideoView(Modifier.fillMaxSize())
        if (numBuf.isNotEmpty()) Text(numBuf, color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.TopEnd).padding(30.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(horizontal = 18.dp, vertical = 6.dp))
        if (toast.isNotEmpty()) Text(toast, color = Color.White, fontSize = 14.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 8.dp))
        // top bar
        if (showUi) Row(Modifier.align(Alignment.TopStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent))).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.15f)).hoverGlow(c.accent, 18).clickable { repo.nowPlaying = null; if (win.isFullscreen()) win.setFullscreen(false); nav.pop() }, contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(cur.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (cur.live && cur.item != null && pl != null) { val nn = repo.nowNext(pl, cur.item!!).first; if (nn != null) Text(hm(nn.start, fmt24) + " – " + hm(nn.stop, fmt24) + "  " + nn.title, color = Color.White.copy(alpha = 0.8f), fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
            Text(clockText(fmt24), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        // bottom bar
        if (showUi) Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))).padding(horizontal = 16.dp, vertical = 10.dp)) {
            if (!cur.live && p.length > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fmtTime(p.time), color = Color.White, fontSize = 12.sp); Spacer(Modifier.width(8.dp))
                    Slider(value = (p.time.toFloat() / p.length).coerceIn(0f, 1f), onValueChange = { p.seekTo((it * p.length).toLong()) }, modifier = Modifier.weight(1f).height(24.dp), colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = Color.White.copy(alpha = 0.25f)))
                    Spacer(Modifier.width(8.dp)); Text(fmtTime(p.length), color = Color.White, fontSize = 12.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (cur.live) { CBtn(Icons.Default.List) { sideList = !sideList }; CBtn(Icons.Default.KeyboardArrowUp) { zap(1) }; CBtn(Icons.Default.KeyboardArrowDown) { zap(-1) } }
                else { CBtn(Icons.Default.Replay10) { p.seekBy(-10_000) }; CBtn(if (p.playing) Icons.Default.Pause else Icons.Default.PlayArrow, big = true) { p.togglePause() }; CBtn(Icons.Default.Forward10) { p.seekBy(10_000) } }
                Spacer(Modifier.width(10.dp))
                CBtn(if (p.muted || p.volume == 0) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp) { p.toggleMute() }
                Slider(value = p.volume / 150f, onValueChange = { p.volumeTo((it * 150).toInt()); repo.prefs.volume = p.volume }, modifier = Modifier.width(110.dp).height(24.dp), colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.25f)))
                Spacer(Modifier.weight(1f))
                if (cur.item != null) { val fav = repo.isFavorite(cur.item!!.key); CBtn(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, tint = if (fav) Color(0xFFFF5C7A) else Color.White) { repo.toggleFavorite(cur.item!!.key) } }
                CBtn(Icons.Default.Subtitles) { sheet = if (sheet == "subs") "" else "subs" }
                CBtn(Icons.Default.Audiotrack) { sheet = if (sheet == "audio") "" else "audio" }
                CBtn(Icons.Default.AspectRatio) { sheet = if (sheet == "aspect") "" else "aspect" }
                CBtn(if (win.isFullscreen()) Icons.Default.FullscreenExit else Icons.Default.Fullscreen) { win.setFullscreen(!win.isFullscreen()) }
            }
        }
        // side channel list
        if (sideList && cur.live) {
            val ls = rememberLazyListState(); LaunchedEffect(Unit) { val i = liveList.indexOfFirst { it.key == cur.item?.key }; if (i > 0) ls.scrollToItem(i) }
            LazyColumn(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(300.dp).background(Color.Black.copy(alpha = 0.82f)).padding(vertical = 8.dp), state = ls) {
                items(liveList, key = { it.key }) { it ->
                    val sel = it.key == cur.item?.key
                    Row(Modifier.fillMaxWidth().background(if (sel) c.accent.copy(alpha = 0.35f) else Color.Transparent).hoverGlow(c.accent, 0, 1f).clickable { playItem(it) }.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (it.num > 0) it.num.toString() else "", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.width(34.dp))
                        AsyncImage(it.icon, Modifier.size(34.dp, 22.dp), contentScale = ContentScale.Fit); Spacer(Modifier.width(8.dp))
                        Column { Text(it.name, color = Color.White, fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val nn = pl?.let { p2 -> repo.nowNext(p2, it).first }; if (nn != null) Text(nn.title, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
        // track sheets
        if (sheet.isNotEmpty()) Column(Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).width(280.dp).background(Color.Black.copy(alpha = 0.88f), RoundedCornerShape(12.dp)).padding(10.dp)) {
            when (sheet) {
                "subs" -> { Text(T("subtitles"), color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(6.dp)); val cur2 = p.subTrack(); (listOf(-1 to T("none")) + p.subTracks().filter { it.first >= 0 }).forEach { (id, n) -> SheetRow(n, id == cur2) { p.setSubTrack(id); sheet = "" } } }
                "audio" -> { Text(T("audio"), color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(6.dp)); val cur2 = p.audioTrack(); p.audioTracks().filter { it.first >= 0 }.forEach { (id, n) -> SheetRow(n, id == cur2) { p.setAudioTrack(id); sheet = "" } } }
                "aspect" -> { Text(T("aspect"), color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(6.dp)); listOf("auto", "16:9", "4:3", "21:9", "1:1").forEach { a -> SheetRow(if (a == "auto") T("automatic") else a, false) { p.setAspect(a); sheet = "" } } }
            }
        }
    }
}

@Composable
private fun SheetRow(text: String, sel: Boolean, onClick: () -> Unit) { val c = LocalColors.current
    Text(text, color = if (sel) c.accent else Color.White, fontSize = 13.5.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).hoverGlow(c.accent, 8, 1f).clickable { onClick() }.padding(horizontal = 10.dp, vertical = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) }

@Composable
fun CBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, big: Boolean = false, tint: Color = Color.White, onClick: () -> Unit) { val c = LocalColors.current
    Box(Modifier.padding(horizontal = 3.dp).size(if (big) 46.dp else 38.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = if (big) 0.22f else 0.12f)).hoverGlow(c.accent, 50, 1.08f).clickable { onClick() }, contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(if (big) 28.dp else 22.dp)) } }

/** Resume / start over prompt, then opens the player. */
fun startPlayback(nav: Nav, s: Screen.Play) { nav.push(s) }
