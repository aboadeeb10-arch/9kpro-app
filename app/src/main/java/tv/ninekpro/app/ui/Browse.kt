package tv.ninekpro.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import tv.ninekpro.app.R
import tv.ninekpro.app.data.Category
import tv.ninekpro.app.data.Episode
import tv.ninekpro.app.data.Item
import tv.ninekpro.app.data.Kind
import tv.ninekpro.app.data.Playlist
import tv.ninekpro.app.data.Repo
import tv.ninekpro.app.data.ResumeEntry
import tv.ninekpro.app.data.SeriesInfo
import tv.ninekpro.app.data.XtreamApi

@Composable
fun kindTitle(k: Kind) = when (k) { Kind.LIVE -> stringResource(R.string.live); Kind.MOVIE -> stringResource(R.string.movies); Kind.SERIES -> stringResource(R.string.series) }

/** Loads a playlist's content for one kind, with error/retry. */
@Composable
fun <T> Loaded(key0: Any?, load: suspend () -> T, isEmpty: (T) -> Boolean = { false }, content: @Composable (T) -> Unit) {
    // Refresh / a playlist changed by the panel bumps contentVersion → the screen loads again even though its playlist id is the same
    val cv by LocalRepo.current.contentVersion.collectAsState(); val key = key0 to cv
    var data by remember(key) { mutableStateOf<T?>(null) }; var err by remember(key) { mutableStateOf("") }; var tick by remember { mutableStateOf(0) }
    var waiting by remember(key) { mutableStateOf(0) }
    LaunchedEffect(key, tick) { err = ""
        try { var d = load(); var n = 0
            while (isEmpty(d) && n < 12) { n++; waiting = n; kotlinx.coroutines.delay(5000); d = load() }   // new line: server answers empty for a while
            waiting = 0; data = d } catch (e: Throwable) { err = (e.message ?: e.javaClass.simpleName) } }
    val d = data
    when {
        d != null -> content(d)
        waiting > 0 -> Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) { androidx.compose.material3.CircularProgressIndicator(color = LocalColors.current.accent); Gap(10); Text(stringResource(R.string.preparing_line) + "  " + waiting + "/12", color = LocalColors.current.muted, fontSize = 13.sp) }
        err.isNotEmpty() -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(stringResource(R.string.error_network), color = LocalColors.current.muted); Text(err, color = LocalColors.current.muted, fontSize = 11.sp); Gap(10); BigButton(stringResource(R.string.retry)) { tick++ } }
        else -> Loading()
    }
}

/** Filter/sort of a list. Small lists are computed right away in composition (no flicker); `big` ones run on a background thread and return null (→ show Loading()) until ready. Recomputed when a key changes. */
@Composable
fun <T> heavyList(big: Boolean, vararg keys: Any?, compute: () -> List<T>): List<T>? =
    if (big) produceState<List<T>?>(null, *keys) { value = null; value = withContext(Dispatchers.Default) { compute() } }.value
    else remember(*keys) { compute() }

// ------------------------------------------------------------ categories
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoriesScreen(kind: Kind) {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current; val pv by repo.profileVersion.collectAsState()
    val pl = repo.activePlaylist ?: run { nav.reset(Screen.Playlists); return }
    Column(Modifier.fillMaxSize()) {
        TopBar(kindTitle(kind)) { Pill(stringResource(R.string.all), false) { nav.push(Screen.Items(kind, null)) } }
        Loaded(pl.id + kind.name, { repo.content(pl, kind) }) { content ->
            val listId = "cats:${pl.id}:${kind.name}"
            val hidden = remember(pv) { repo.hidden() }; val show = repo.prefs.showHidden
            val cats = remember(content, pv) { repo.applyOrder(listId, content.categories) { it.id } }.filter { show || !hidden.contains("cat:" + it.id) }
            val counts = remember(content) { content.items.groupingBy { it.categoryId }.eachCount() }
            val locked = remember(pv) { repo.locked() }
            var gate by remember { mutableStateOf<Category?>(null) }
            PinGate(gate != null, { gate = null }) { val g = gate; gate = null; if (g != null) nav.push(Screen.Items(kind, g)) }
            LazyColumn(Modifier.restoreFocus(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(cats, key = { it.id }) { cat ->
                    var menu by remember { mutableStateOf(false) }
                    val isLocked = locked.contains("cat:" + cat.id)
                    // TV remote: the ⋮ button is a SIBLING of the clickable area (a button nested inside a clickable row can't be reached)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (hidden.contains("cat:" + cat.id)) c.card.copy(alpha = 0.4f) else c.card), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).tvFocus(c.accent, 12).combinedClickable(onClick = { if (isLocked) gate = cat else nav.push(Screen.Items(kind, cat)) }, onLongClick = { menu = true }).padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (isLocked) { Icon(Icons.Default.Lock, null, tint = c.muted, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
                            Text(cat.name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text((counts[cat.id] ?: 0).toString(), color = c.muted, fontSize = 12.sp)
                        }
                        Box(Modifier.padding(end = 6.dp)) { IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp).tvFocus(c.accent, 16)) { Icon(Icons.Default.MoreVert, stringResource(R.string.more), tint = c.muted) }
                            OrderMenu(menu, { menu = false }, repo, listId, cats.map { it.id }, cat.id, "cat:" + cat.id, hidden.contains("cat:" + cat.id)) }
                    }
                }
            }
        }
    }
}

/** Asks for the parental PIN; `onOk` runs when correct (or when no PIN is set). */
@Composable
fun PinGate(show: Boolean, onDismiss: () -> Unit, onOk: () -> Unit) {
    val repo = LocalRepo.current; val c = LocalColors.current
    if (!show) return
    if (!repo.prefs.parentalOn || System.currentTimeMillis() < repo.unlockedUntil) { onOk(); return }
    if (repo.pin.isEmpty()) repo.pin = "0000"
    var v by remember { mutableStateOf("") }; var bad by remember { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(onDismissRequest = onDismiss, confirmButton = { BigButton(stringResource(R.string.ok)) { if (v == repo.pin) { repo.unlockedUntil = System.currentTimeMillis() + 30 * 60_000L; onOk() } else bad = true } },
        dismissButton = { BigButton(stringResource(R.string.cancel), filled = false) { onDismiss() } }, title = { Text(stringResource(R.string.enter_pin)) },
        text = { Column { Field(v, { v = it.filter { ch -> ch.isDigit() }.take(6) }, "PIN", password = true, keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done, onImeAction = { if (v == repo.pin) { repo.unlockedUntil = System.currentTimeMillis() + 30 * 60_000L; onOk() } else bad = true }); if (bad) Text(stringResource(R.string.wrong_pin), color = Color(0xFFFF6B7A), fontSize = 12.sp) } })
}

@Composable
fun OrderMenu(open: Boolean, close: () -> Unit, repo: Repo, listId: String, orderedKeys: List<String>, key: String, hideKey: String, isHidden: Boolean, onMove: (() -> Unit)? = null, extra: @Composable () -> Unit = {}) {
    val isLocked = repo.locked().contains(hideKey)
    DropdownMenu(expanded = open, onDismissRequest = close) {
        extra()
        if (onMove != null) DropdownMenuItem(text = { Text("↕  " + stringResource(R.string.move)) }, onClick = { onMove(); close() })
        DropdownMenuItem(text = { Text(stringResource(if (isLocked) R.string.unlock else R.string.lock_pin)) }, onClick = { if (repo.pin.isEmpty()) repo.pin = "0000"; repo.setLocked(hideKey, !isLocked); close() })
        DropdownMenuItem(text = { Text(stringResource(R.string.move_top)) }, onClick = { repo.move(listId, orderedKeys, key, Int.MIN_VALUE); close() })
        DropdownMenuItem(text = { Text(stringResource(R.string.move_up)) }, onClick = { repo.move(listId, orderedKeys, key, -1); close() })
        DropdownMenuItem(text = { Text(stringResource(R.string.move_down)) }, onClick = { repo.move(listId, orderedKeys, key, 1); close() })
        DropdownMenuItem(text = { Text(stringResource(if (isHidden) R.string.unhide else R.string.hide)) }, onClick = { repo.setHidden(hideKey, !isHidden); close() })
    }
}

// ------------------------------------------------------------ items
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemsScreen(kind: Kind, category: Category?) {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current; val pv by repo.profileVersion.collectAsState()
    val pl = repo.activePlaylist ?: run { nav.reset(Screen.Playlists); return }
    var sort by remember { mutableStateOf("default") }
    Column(Modifier.fillMaxSize()) {
        TopBar(category?.name ?: (kindTitle(kind) + " · " + stringResource(R.string.all))) {
            var m by remember { mutableStateOf(false) }
            Box { Pill(stringResource(R.string.sort), false) { m = true }
                DropdownMenu(expanded = m, onDismissRequest = { m = false }) {
                    listOf("default" to R.string.sort_default, "az" to R.string.sort_az, "new" to R.string.sort_newest, "rating" to R.string.sort_rating).forEach { (k, r) -> DropdownMenuItem(text = { Text(stringResource(r)) }, onClick = { sort = k; m = false }) }
                } }
        }
        Loaded(pl.id + kind.name, { repo.content(pl, kind) }) { content ->
            val listId = "items:${pl.id}:${kind.name}:${category?.id ?: "all"}"
            val hidden = remember(pv) { repo.hidden() }; val show = repo.prefs.showHidden
            val items = heavyList(content.items.size > 2000, content, category?.id, pv, sort) {
                val base = if (category == null) content.items else content.items.filter { it.categoryId == category.id }
                val sorted = when (sort) { "az" -> base.sortedBy { it.name.lowercase() }; "new" -> base.sortedByDescending { it.added }; "rating" -> base.sortedByDescending { it.rating.toDoubleOrNull() ?: 0.0 }; else -> repo.applyOrder(listId, base) { it.key } }
                sorted.filter { show || !hidden.contains(it.key) }
            }
            if (items == null) Loading() else ItemGrid(items, kind, pl, listId, hidden, sort == "default")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemGrid(items: List<Item>, kind: Kind, pl: Playlist, listId: String, hidden: Set<String>, canOrder: Boolean, state: LazyGridState = rememberLazyGridState()) {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current
    val scope = rememberCoroutineScope()
    if (items.isEmpty()) { Empty(stringResource(R.string.no_items)); return }
    val orderedKeys = items.map { it.key }
    if (kind == Kind.LIVE) {
        val ev by repo.epgVersion.collectAsState()
        LazyColumn(Modifier.restoreFocus(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(items, key = { _, it -> it.key }) { idx, it ->
                var menu by remember { mutableStateOf(false) }
                val fav = repo.isFavorite(it.key)
                val nn = remember(ev, it.key) { repo.nowNext(pl, it) }
                // TV remote: ♥ and ⋮ are SIBLINGS of the clickable area (buttons nested inside a clickable row can't be reached)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (hidden.contains(it.key)) c.card.copy(alpha = 0.4f) else c.card), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).tvFocus(c.accent, 10).combinedClickable(onClick = { repo.liveContext = items; scope.launch { playItem(repo, nav, pl, it) } }, onLongClick = { menu = true }).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Poster(it.icon, Modifier.size(52.dp, 40.dp), contentScaleFit = true); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(((idx + 1).toString() + "  ") + it.name + (if (it.archive) "  ⏪" else ""), color = c.text, fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            nn.first?.let { e -> Text(e.title + (nn.second?.let { n -> "  ·  " + n.title } ?: ""), color = c.muted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                    IconButton(onClick = { repo.toggleFavorite(it.key) }, modifier = Modifier.size(32.dp).tvFocus(c.accent, 16)) { Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (fav) Color(0xFFFF6B7A) else c.muted, modifier = Modifier.size(18.dp)) }
                    Box(Modifier.padding(end = 4.dp)) { IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp).tvFocus(c.accent, 16)) { Icon(Icons.Default.MoreVert, stringResource(R.string.more), tint = c.muted) }
                        if (canOrder) OrderMenu(menu, { menu = false }, repo, listId, orderedKeys, it.key, it.key, hidden.contains(it.key)) }
                }
            }
        }
    } else {
        val cols = (if (tv.ninekpro.app.data.DeviceInfo.isTv || repo.prefs.deviceType == "tv") 6 else 5) + when (repo.prefs.layoutSize) { "large" -> -1; "compact" -> 2; else -> 0 }
        LazyVerticalGrid(columns = GridCells.Fixed(cols), state = state, modifier = Modifier.restoreFocus(), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items.size, key = { items[it].key }) { i ->
                val it = items[i]; var menu by remember { mutableStateOf(false) }
                Column(Modifier.clip(RoundedCornerShape(8.dp)).tvFocus(c.accent, 8).combinedClickable(onClick = { if (kind == Kind.SERIES) nav.push(Screen.Series(it)) else nav.push(Screen.Movie(it)) }, onLongClick = { menu = true })) {
                    Box { Poster(it.icon, Modifier.fillMaxWidth().aspectRatio(0.68f))
                        if (it.rating.isNotEmpty() && it.rating != "0") Text("★ " + it.rating.take(3), color = Color.White, fontSize = 10.sp, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(Color(0x99000000), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 2.dp))
                        if (repo.isFavorite(it.key)) Icon(Icons.Default.Favorite, null, tint = Color(0xFFFF6B7A), modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(16.dp))
                        repo.resumeOf(it.key)?.let { r -> if (r.durationMs > 0) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth((r.positionMs.toFloat() / r.durationMs).coerceIn(0.02f, 1f)).height(4.dp).background(c.accent)) }
                    }
                    Text(it.name, color = c.text, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(4.dp))
                    Box { DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.favorites)) }, onClick = { repo.toggleFavorite(it.key); menu = false })
                        if (kind == Kind.MOVIE) DropdownMenuItem(text = { Text(stringResource(R.string.download)) }, onClick = { menu = false; scope.launch { val u = repo.streamUrl(pl, it); tv.ninekpro.app.data.Downloads.enqueue(nav, repo, u, it.name, it.ext) } })
                        if (canOrder) { DropdownMenuItem(text = { Text(stringResource(R.string.move_top)) }, onClick = { repo.move(listId, orderedKeys, it.key, Int.MIN_VALUE); menu = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.move_up)) }, onClick = { repo.move(listId, orderedKeys, it.key, -1); menu = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.move_down)) }, onClick = { repo.move(listId, orderedKeys, it.key, 1); menu = false }) }
                        DropdownMenuItem(text = { Text(stringResource(if (hidden.contains(it.key)) R.string.unhide else R.string.hide)) }, onClick = { repo.setHidden(it.key, !hidden.contains(it.key)); menu = false })
                    } }
                }
            }
        }
    }
}

// ------------------------------------------------------------ playback helpers
fun itemExtra(pl: Playlist, it: Item): String = JSONObject().put("pl", pl.id).put("kind", it.kind.name).put("id", it.id).put("name", it.name).put("icon", it.icon).put("ext", it.ext).put("cat", it.categoryId).put("url", it.directUrl).toString()
fun episodeExtra(pl: Playlist, ep: Episode, seriesName: String): String = JSONObject().put("pl", pl.id).put("kind", "EP").put("id", ep.id).put("series", ep.seriesId).put("ext", ep.ext).put("title", ep.title).put("s", ep.season).put("n", ep.num).put("icon", ep.icon).put("sname", seriesName).toString()

suspend fun playItem(repo: Repo, nav: Nav, pl: Playlist, it: Item, startMs: Long = -1L) {
    if (it.kind == Kind.LIVE) repo.setLastChannel(it.key)
    val url = repo.streamUrl(pl, it, ts = it.kind == Kind.LIVE && repo.prefs.liveFormat == "ts")
    nav.push(Screen.Play(url, it.name, it.key, it.kind, it.icon, item = it, playlist = pl, startMs = startMs))
}

suspend fun playEpisode(repo: Repo, nav: Nav, pl: Playlist, info: SeriesInfo, ep: Episode, seriesName: String, replace: Boolean = false, startMs: Long = -1L) {
    val url = repo.episodeUrl(pl, ep)
    val title = seriesName + " · S" + ep.season + "E" + ep.num + (if (ep.title.isNotEmpty()) " · " + ep.title else "")
    val s = Screen.Play(url, title, ep.key, Kind.SERIES, ep.icon.ifEmpty { "" }, episode = ep, playlist = pl, seriesName = seriesName, startMs = if (replace) 0L else startMs)
    if (replace) nav.replace(s) else nav.push(s)
}

fun resumePlay(repo: Repo, nav: Nav, r: ResumeEntry) {
    val x = try { JSONObject(r.extra) } catch (e: Exception) { return }
    val pl = repo.playlists.value.firstOrNull { it.id == x.optString("pl") } ?: return
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
        try {
            if (x.optString("kind") == "EP") {
                val info = repo.seriesInfo(pl, x.optString("series"))
                val ep = info.seasons.values.flatten().firstOrNull { it.id == x.optString("id") } ?: return@launch
                playEpisode(repo, nav, pl, info, ep, x.optString("sname"), startMs = r.positionMs)
            } else {
                val it = Item(x.optString("id"), x.optString("name"), Kind.valueOf(x.optString("kind")), x.optString("cat"), x.optString("icon"), x.optString("ext"), playlistId = pl.id, directUrl = x.optString("url"))
                playItem(repo, nav, pl, it, startMs = if (it.kind == Kind.LIVE) -1L else r.positionMs)
            }
        } catch (e: Exception) { }
    }
}

// ------------------------------------------------------------ series
@Composable
fun SeriesScreen(item: Item) {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current; val pv by repo.profileVersion.collectAsState()
    val pl = repo.playlists.collectAsState().value.firstOrNull { it.id == item.playlistId } ?: repo.activePlaylist ?: return
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        TopBar(item.name) { IconButton(onClick = { repo.toggleFavorite(item.key) }) { val f = repo.isFavorite(item.key); Icon(if (f) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (f) Color(0xFFFF6B7A) else c.text) } }
        Loaded(item.key, { repo.seriesInfo(pl, item.id) }) { info ->
            var season by remember { mutableStateOf(info.seasons.keys.firstOrNull() ?: 0) }
            var tm by remember { mutableStateOf<tv.ninekpro.app.data.TmdbInfo?>(null) }
            LaunchedEffect(item.key) { tm = repo.tmdbFor(item.name, info.year.ifEmpty { item.year }, true) }
            val backdrop = info.backdrop.ifEmpty { tm?.backdrop ?: "" }
            Box(Modifier.fillMaxSize()) {
            if (backdrop.isNotEmpty()) coil.compose.AsyncImage(model = backdrop, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize(), alpha = 0.3f)
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, c.bg3.copy(alpha = 0.9f), c.bg3))))
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Row(Modifier.padding(12.dp)) {
                    Poster(item.icon, Modifier.size(110.dp, 160.dp)); Spacer(Modifier.width(12.dp))
                    Column { Text(tm?.title?.ifEmpty { item.name } ?: item.name, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        Gap(6)
                        val rt = info.rating.takeIf { it.isNotEmpty() && it != "0" } ?: tm?.rating?.takeIf { it != "0.0" } ?: ""
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Fact(stringResource(R.string.rating), if (rt.isNotEmpty()) "★ " + rt else ""); Fact(stringResource(R.string.year), info.year.ifEmpty { tm?.year ?: "" })
                            val tmi = tm
                            Fact(stringResource(R.string.seasons), if (tmi != null && tmi.seasons > 0) tmi.seasons.toString() + " · " + tmi.episodes + " ep" else info.seasons.size.toString())
                            Fact(stringResource(R.string.director), tm?.director ?: "") }
                        val gn = info.genre.ifEmpty { tm?.genres ?: "" }; if (gn.isNotEmpty()) { Gap(6); Text(gn, color = c.accent, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold) }
                        Gap(6); Text(info.plot.ifEmpty { item.plot }.ifEmpty { tm?.overview ?: "" }, color = c.text.copy(alpha = 0.85f), fontSize = 12.5.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                        val people = (tm?.people ?: emptyList()).ifEmpty { info.cast.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(12).map { tv.ninekpro.app.data.Person(it, "", "") } }
                        if (people.isNotEmpty()) { Gap(10); SectionTitle(stringResource(R.string.cast).uppercase()); CastRow(people) }
                        Gap(8)
                        val last = repo.resumeList().firstOrNull { r -> r.extra.contains("\"series\":\"" + item.id + "\"") }
                        // TV: Play / Resume is highlighted as soon as the series page opens — one OK press starts it
                        val playFocus = remember { androidx.compose.ui.focus.FocusRequester() }
                        LaunchedEffect(Unit) { delay(120); try { playFocus.requestFocus() } catch (e: Exception) {} }
                        if (last != null) BigButton(stringResource(R.string.resume), Icons.Default.PlayArrow, modifier = Modifier.focusRequester(playFocus)) { resumePlay(repo, nav, last) }
                        else info.seasons.values.firstOrNull()?.firstOrNull()?.let { first -> BigButton(stringResource(R.string.play), Icons.Default.PlayArrow, modifier = Modifier.focusRequester(playFocus)) { scope.launch { playEpisode(repo, nav, pl, info, first, item.name) } } }
                    }
                }
                if (info.seasons.size > 1) LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(info.seasons.keys.toList()) { s -> Pill(stringResource(R.string.season, s), s == season) { season = s } } }
                Gap(8)
                val eps = info.seasons[season] ?: emptyList()
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { SectionTitle(stringResource(R.string.episodes).uppercase()); Spacer(Modifier.weight(1f))
                    Pill(stringResource(R.string.download_season), false) { scope.launch { eps.forEach { ep -> val u = repo.episodeUrl(pl, ep); tv.ninekpro.app.data.Downloads.enqueue(nav, repo, u, item.name + " S" + ep.season + "E" + ep.num, ep.ext) } } } }
                eps.forEach { ep ->
                    val r = repo.resumeOf(ep.key)
                    // TV remote: the Download button sits NEXT TO the clickable area, not inside it
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(c.card), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).tvFocus(c.accent, 10).clickable { scope.launch { playEpisode(repo, nav, pl, info, ep, item.name) } }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box { Poster(ep.icon.ifEmpty { item.icon }, Modifier.size(96.dp, 56.dp)); if (r != null && r.durationMs > 0) Box(Modifier.align(Alignment.BottomStart).width((96 * (r.positionMs.toFloat() / r.durationMs).coerceIn(0.02f, 1f)).dp).height(3.dp).background(c.accent)) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) { Text("E" + ep.num + "  " + ep.title, color = c.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis); if (ep.durationSecs > 0) Text(fmtTime(ep.durationSecs * 1000L), color = c.muted, fontSize = 11.sp) }
                        }
                        IconButton(onClick = { scope.launch { val u = repo.episodeUrl(pl, ep); tv.ninekpro.app.data.Downloads.enqueue(nav, repo, u, item.name + " S" + ep.season + "E" + ep.num, ep.ext) } }, modifier = Modifier.padding(end = 4.dp).size(36.dp).tvFocus(c.accent, 18)) { Icon(Icons.Default.Download, stringResource(R.string.download), tint = c.muted, modifier = Modifier.size(18.dp)) }
                    }
                }
                Gap(24)
            }
            }
        }
    }
}

// ------------------------------------------------------------ search / favorites / continue
private const val RECENT_KEY = "recentSearches"; private const val RECENT_MAX = 8

/** Search across live / movies / series. Typing is debounced 250 ms and the search runs off the main thread; the last 8 queries are kept as "Recent searches". */
@Composable
fun SearchScreen(initial: String) {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current
    val pl = repo.activePlaylist ?: return
    var q by remember { mutableStateOf(initial.ifEmpty { repo.searchQ }) }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState(repo.searchScroll)
    DisposableEffect(Unit) { onDispose { repo.searchQ = q; repo.searchScroll = scroll.value } }
    LaunchedEffect(pl.id) { for (k in Kind.values()) try { repo.content(pl, k) } catch (e: Throwable) {} }
    // recent searches live in the app's SharedPreferences ("ninekpro"), newest first
    val sp = LocalContext.current.getSharedPreferences("ninekpro", android.content.Context.MODE_PRIVATE)
    var recent by remember { mutableStateOf((sp.getString(RECENT_KEY, "") ?: "").split("\n").filter { it.isNotBlank() }) }
    val saveRecent: (String) -> Unit = { s -> val t = s.trim(); if (t.length >= 2) { val l = (listOf(t) + recent.filter { it != t }).take(RECENT_MAX); recent = l; sp.edit().putString(RECENT_KEY, l.joinToString("\n")).apply() } }
    var res by remember { mutableStateOf<List<Item>>(emptyList()) }; var busy by remember { mutableStateOf(false) }
    LaunchedEffect(q, pl.id) {
        val s = q.trim()
        if (s.length < 2) { res = emptyList(); busy = false; return@LaunchedEffect }
        busy = true; delay(250)
        res = withContext(Dispatchers.Default) { try { repo.searchCached(pl, s) } catch (e: Throwable) { emptyList() } }
        busy = false
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.search))
        Field(q, { q = it; repo.searchQ = it }, stringResource(R.string.search), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), keyboardType = KeyboardType.Text, imeAction = ImeAction.Search, onImeAction = { saveRecent(q) })
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            if (q.trim().length < 2) {
                Text(stringResource(R.string.type_two_letters), color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                if (recent.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { SectionTitle(stringResource(R.string.recent_searches).uppercase()); Spacer(Modifier.weight(1f))
                        Pill(stringResource(R.string.clear_recent), false) { recent = emptyList(); sp.edit().remove(RECENT_KEY).apply() }; Spacer(Modifier.width(12.dp)) }
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(recent, key = { "rs:" + it }) { s -> Pill(s, false) { q = s; repo.searchQ = s } } }
                }
            } else if (busy && res.isEmpty()) Text(stringResource(R.string.searching), color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            Kind.values().forEach { k ->
                val list = res.filter { it.kind == k }.take(40)
                if (list.isNotEmpty()) { SectionTitle(kindTitle(k).uppercase() + "  ·  " + list.size)
                    LazyRow(Modifier.restoreFocus(), contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(list, key = { k.name + ":" + it.key }) { it -> PosterCard(it, if (k == Kind.LIVE) 120 else 110) { saveRecent(q); when (k) { Kind.LIVE -> { repo.liveContext = list; scope.launch { playItem(repo, nav, pl, it) } }; Kind.MOVIE -> nav.push(Screen.Movie(it)); Kind.SERIES -> nav.push(Screen.Series(it)) } } } } }
            }
            if (q.trim().length >= 2 && !busy && res.isEmpty()) Empty(stringResource(R.string.no_items))
            Gap(20)
        }
    }
}

/** Favourites: one scrolling column — a title + a poster row per kind (live favourites play with the row as their channel list). */
@Composable
fun FavoritesScreen() {
    val repo = LocalRepo.current; val nav = LocalNav.current; val pv by repo.profileVersion.collectAsState()
    val pl = repo.activePlaylist ?: return
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.favorites))
        Loaded(pl.id + "fav", { Kind.values().associateWith { k -> repo.content(pl, k) } }) { all ->
            val favs = remember(pv) { repo.favorites().distinct() }
            val byKey = remember(all) { HashMap<String, Item>().also { m -> all.values.forEach { c2 -> c2.items.forEach { m[it.key] = it } } } }
            val items = remember(favs, byKey) { favs.mapNotNull { byKey[it] } }
            if (items.isEmpty()) Empty(stringResource(R.string.no_items))
            else LazyColumn(Modifier.fillMaxSize().restoreFocus(), contentPadding = PaddingValues(vertical = 6.dp)) {
                Kind.values().forEach { k ->
                    val l = items.filter { it.kind == k }
                    if (l.isNotEmpty()) {
                        item(key = "title:" + k.name) { SectionTitle(kindTitle(k).uppercase() + "  ·  " + l.size) }
                        item(key = "row:" + k.name) {
                            LazyRow(Modifier.restoreFocus(), contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(l, key = { k.name + ":" + it.key }) { it -> PosterCard(it, if (k == Kind.LIVE) 120 else 110) { when (k) { Kind.LIVE -> { repo.liveContext = l; scope.launch { playItem(repo, nav, pl, it) } }; Kind.MOVIE -> nav.push(Screen.Movie(it)); Kind.SERIES -> nav.push(Screen.Series(it)) } } }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContinueScreen() {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current; val pv by repo.profileVersion.collectAsState()
    val list = remember(pv) { repo.resumeList() }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.continue_watching))
        if (list.isEmpty()) Empty(stringResource(R.string.no_items))
        LazyColumn(Modifier.restoreFocus(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(list, key = { it.key }) { r ->
                // TV remote: ✕ is a sibling of the clickable area, not nested inside it
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.card), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).tvFocus(c.accent, 10).clickable { resumePlay(repo, nav, r) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Poster(r.image, Modifier.size(72.dp, 50.dp)); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) { Text(r.title, color = c.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis); if (r.durationMs > 0) Text(fmtTime(r.positionMs) + " / " + fmtTime(r.durationMs), color = c.muted, fontSize = 11.sp) }
                    }
                    IconButton(onClick = { repo.removeResume(r.key) }, modifier = Modifier.padding(end = 4.dp).size(36.dp).tvFocus(c.accent, 18)) { Icon(Icons.Default.Close, stringResource(R.string.remove), tint = c.muted, modifier = Modifier.size(16.dp)) }
                }
            }
        }
    }
}
