@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package tv.ninekpro.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch
import tv.ninekpro.app.R
import tv.ninekpro.app.data.Category
import tv.ninekpro.app.data.Item
import tv.ninekpro.app.data.Kind
import tv.ninekpro.app.data.Playlist

private const val ALL = "__all"; private const val FAV = "__fav"; private const val LOCK = "__lock"

/** Live TV: categories | channels | preview player — the classic three-pane layout (two steps on narrow phones). */
@Composable
fun LivePane() {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current
    val pl = repo.activePlaylist ?: run { nav.reset(Screen.Playlists); return }
    val pv by repo.profileVersion.collectAsState(); val ev by repo.epgVersion.collectAsState()
    val scope = rememberCoroutineScope()
    var catId by remember { mutableStateOf(repo.liveUiCat) }
    var selected by remember { mutableStateOf(repo.liveUiSel) }
    var gate by remember { mutableStateOf<String?>(null) }
    var showList by remember { mutableStateOf(repo.liveUiList) }   // narrow screens: false = categories, true = channels
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = repo.liveUiScroll)
    LaunchedEffect(catId) { if (catId != repo.liveUiCat) { repo.liveUiCat = catId; repo.liveUiScroll = 0; listState.scrollToItem(0) } }
    LaunchedEffect(selected) { repo.liveUiSel = selected }
    LaunchedEffect(showList) { repo.liveUiList = showList }
    LaunchedEffect(listState) { androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex }.collect { repo.liveUiScroll = it } }

    Loaded(pl.id + "LIVE", { repo.content(pl, Kind.LIVE) }, isEmpty = { it.items.isEmpty() && pl.kind == "xtream" }) { content ->
        val hidden = remember(pv) { repo.hidden() }; val favs = remember(pv) { repo.favorites().toSet() }
        val cats = remember(content, pv) { repo.applyOrder("cats:${pl.id}:LIVE", content.categories) { it.id } }.filter { repo.prefs.showHidden || !hidden.contains("cat:" + it.id) }
        val locked = remember(pv, content) { val l = repo.locked().toMutableSet(); if (repo.prefs.parentalOn) content.categories.filter { repo.isAdultName(it.name) }.forEach { l.add("cat:" + it.id) }; if (!repo.prefs.parentalOn) l.clear(); l as Set<String> }
        val counts = remember(content) { content.items.groupingBy { it.categoryId }.eachCount() }
        val channels = remember(content, catId, pv) {
            val base = when (catId) { ALL -> content.items; FAV -> content.items.filter { favs.contains(it.key) }; LOCK -> content.items.filter { locked.contains("cat:" + it.categoryId) }; else -> content.items.filter { it.categoryId == catId } }
            val sorted = when (repo.prefs.liveSort) { "az" -> base.sortedBy { it.name.lowercase() }; "number" -> base.sortedBy { it.num }; else -> repo.applyOrder("items:${pl.id}:LIVE:$catId", base) { it.key } }
            sorted.filter { repo.prefs.showHidden || !hidden.contains(it.key) }
        }
        var nn by remember { mutableStateOf<Pair<tv.ninekpro.app.data.EpgEntry?, tv.ninekpro.app.data.EpgEntry?>>(null to null) }
        LaunchedEffect(selected?.key, ev) { val sel = selected; nn = if (sel == null) null to null else try { repo.nowNextLive(pl, sel) } catch (e: Throwable) { null to null } }
        val tfmt = remember { java.text.SimpleDateFormat(if (repo.prefs.timeFormat == "24") "HH:mm" else "h:mm a", java.util.Locale.getDefault()) }
        PinGate(gate != null, { gate = null }) { val g = gate; gate = null; if (g != null) { catId = g; showList = true } }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > 700.dp
            androidx.activity.compose.BackHandler(enabled = !wide && showList) { showList = false }
            val catList: @Composable (Modifier) -> Unit = { m ->
                LazyColumn(m, contentPadding = PaddingValues(vertical = 4.dp)) {
                    item { CatRow(stringResource(R.string.all), content.items.size, catId == ALL) { catId = ALL; showList = true } }
                    item { CatRow(stringResource(R.string.favorites), favs.count { k -> k.contains(":LIVE:") }, catId == FAV) { catId = FAV; showList = true } }
                    item { CatRow(stringResource(R.string.lock), locked.count { it.startsWith("cat:") }, catId == LOCK) { if (locked.isEmpty()) return@CatRow; gate = LOCK } }
                    items(cats, key = { it.id }) { cat ->
                        var menu by remember { mutableStateOf(false) }
                        val isLocked = locked.contains("cat:" + cat.id)
                        Box { CatRow((if (isLocked) "🔒 " else "") + cat.name, counts[cat.id] ?: 0, catId == cat.id, onLong = { menu = true }) { if (isLocked) gate = cat.id else { catId = cat.id; showList = true } }
                            OrderMenu(menu, { menu = false }, repo, "cats:${pl.id}:LIVE", cats.map { it.id }, cat.id, "cat:" + cat.id, hidden.contains("cat:" + cat.id)) }
                    }
                }
            }
            val chList: @Composable (Modifier) -> Unit = { m ->
                val now = System.currentTimeMillis() / 1000
                LazyColumn(m, state = listState, contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(channels, key = { it.key }) { ch ->
                        var menu by remember { mutableStateOf(false) }
                        val isSel = selected?.key == ch.key
                        val prog = remember(ev, ch.key) { repo.nowNext(pl, ch).first }
                        Row(Modifier.fillMaxWidth().background(if (isSel) c.selected else Color.Transparent).tvFocus(c.accent, 6)
                            .combinedClickable(onClick = { if (wide) { if (isSel) { repo.liveContext = channels; LiveEngine.handoff = true; scope.launch { playItem(repo, nav, pl, ch) } } else selected = ch } else { repo.liveContext = channels; scope.launch { playItem(repo, nav, pl, ch) } } }, onLongClick = { menu = true })
                            .padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (ch.num > 0) ch.num.toString() else "", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(32.dp))
                            Poster(ch.icon, Modifier.size(38.dp, 26.dp), contentScaleFit = true); Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ch.name, color = if (isSel) c.accent else c.text, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (prog != null) {
                                    Text(prog.title, color = c.muted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val f = if (prog.stop > prog.start) ((now - prog.start).toFloat() / (prog.stop - prog.start)).coerceIn(0.02f, 1f) else 0f
                                    Box(Modifier.padding(top = 3.dp).fillMaxWidth(0.85f).height(2.dp).clip(RoundedCornerShape(1.dp)).background(c.line)) { Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(c.accent)) }
                                }
                            }
                            if (favs.contains(ch.key)) Text("♥", color = Color(0xFFFF6B7A), fontSize = 12.sp)
                            // long-press quick menu: Favorite + Move (answer 8/30)
                            Box { DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(if (favs.contains(ch.key)) stringResource(R.string.remove_favorite) else stringResource(R.string.add_favorite)) }, onClick = { repo.toggleFavorite(ch.key); menu = false })
                                val lid = "items:${pl.id}:LIVE:$catId"; val keys = channels.map { it.key }
                                DropdownMenuItem(text = { Text(stringResource(R.string.move_top)) }, onClick = { repo.move(lid, keys, ch.key, Int.MIN_VALUE); menu = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.move_up)) }, onClick = { repo.move(lid, keys, ch.key, -1); menu = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.move_down)) }, onClick = { repo.move(lid, keys, ch.key, 1); menu = false })
                            } }
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line.copy(alpha = 0.4f)))
                    }
                }
            }
            if (wide) Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(0.23f).fillMaxHeight()) { catList(Modifier.fillMaxSize()) }
                Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
                Box(Modifier.weight(0.27f).fillMaxHeight()) { chList(Modifier.fillMaxSize()) }
                Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
                Column(Modifier.weight(0.50f).fillMaxHeight()) {
                    val sel = selected
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black).tvFocus(c.accent, 0).clickable { sel?.let { repo.liveContext = channels; LiveEngine.handoff = true; scope.launch { playItem(repo, nav, pl, it) } } }) { if (sel != null) MiniPlayer(pl, sel) }
                    if (sel != null) {
                        Text(sel.name, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(14.dp, 10.dp, 14.dp, 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        nn.first?.let { e -> Text(tfmt.format(java.util.Date(e.start * 1000)) + " – " + tfmt.format(java.util.Date(e.stop * 1000)) + "   " + e.title, color = c.text.copy(alpha = 0.92f), fontSize = 14.sp, modifier = Modifier.padding(horizontal = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        nn.second?.let { e -> Text(tfmt.format(java.util.Date(e.start * 1000)) + " – " + tfmt.format(java.util.Date(e.stop * 1000)) + "   " + e.title, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        if (nn.first == null) Text(stringResource(R.string.no_epg), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        var cu by remember { mutableStateOf(false) }
                        if (sel?.archive == true) Box { PillOutline(stringResource(R.string.catchup)) { cu = true }
                            DropdownMenu(expanded = cu, onDismissRequest = { cu = false }) {
                                val now = System.currentTimeMillis() / 1000; val list = repo.epgFor(pl, sel).filter { it.start < now }.takeLast(30).reversed()
                                if (list.isEmpty()) DropdownMenuItem(text = { Text(stringResource(R.string.no_epg)) }, onClick = { cu = false })
                                val fmt = java.text.SimpleDateFormat("EEE HH:mm", java.util.Locale.getDefault())
                                list.forEach { e -> DropdownMenuItem(text = { Text(fmt.format(java.util.Date(e.start * 1000)) + "  " + e.title) }, onClick = { cu = false; scope.launch { try { val u = repo.catchupUrl(pl, sel, e); nav.push(Screen.Play(u, e.title, "cu:" + sel.id + ":" + e.start, Kind.MOVIE, sel.icon)) } catch (x: Exception) {} } }) }
                            } }
                        PillOutline(if (sel != null && favs.contains(sel.key)) stringResource(R.string.remove_favorite) else stringResource(R.string.add_favorite)) { sel?.let { repo.toggleFavorite(it.key) } }
                        PillOutline(stringResource(R.string.guide)) { repo.liveContext = channels; nav.push(Screen.Guide) }
                        PillOutline(stringResource(R.string.search)) { nav.push(Screen.Search("")) }
                    }
                }
            } else {
                if (!showList) catList(Modifier.fillMaxSize())
                else Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) { Pill("‹ " + stringResource(R.string.all), false) { showList = false }; Spacer(Modifier.width(8.dp)); Text(cats.firstOrNull { it.id == catId }?.name ?: stringResource(R.string.all), color = c.text, fontWeight = FontWeight.Bold) }
                    chList(Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
fun CatRow(name: String, count: Int, selected: Boolean, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth().background(if (selected) c.selected else Color.Transparent).tvFocus(c.accent, 6).combinedClickable(onClick = onClick, onLongClick = onLong).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = if (selected) c.accent else c.text, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(count.toString(), color = if (selected) c.accent else c.muted, fontSize = 12.sp)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.line.copy(alpha = 0.5f)))
}

@Composable
fun PillOutline(text: String, onClick: () -> Unit) {
    val c = LocalColors.current
    Box(Modifier.clip(RoundedCornerShape(999.dp)).glass(c, 999).tvFocus(c.accent, 999).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp)) { Text(text, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

/** Small preview player: the shared LiveEngine stream. Tap = same stream goes full screen (no reload); Back brings it back here still playing. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MiniPlayer(pl: Playlist, item: Item) {
    val repo = LocalRepo.current; val act = LocalActivity.current
    val player = remember { LiveEngine.get(act, repo.prefs.subLang) }
    LaunchedEffect(item.key) {
        if (LiveEngine.isLoaded(item.key)) { player.play(); return@LaunchedEffect }
        try { val u = repo.streamUrl(pl, item, ts = repo.prefs.liveFormat == "ts"); LiveEngine.key = item.key; player.setMediaItem(MediaItem.fromUri(u)); player.prepare(); player.play() } catch (e: Exception) { }
    }
    DisposableEffect(Unit) { LiveEngine.handoff = false; onDispose { if (!LiveEngine.handoff) LiveEngine.stop() } }
    PauseInBackground(onStop = { try { player.pause() } catch (e: Exception) {} }, onStart = { try { player.play() } catch (e: Exception) {} })
    AndroidView(factory = { ctx -> PlayerView(ctx).apply { useController = false; this.player = player; setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS); setKeepContentOnPlayerReset(true) } }, onRelease = { it.player = null }, modifier = Modifier.fillMaxSize())
}
