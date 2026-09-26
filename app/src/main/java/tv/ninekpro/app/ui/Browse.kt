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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.launch
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
fun <T> Loaded(key: Any?, load: suspend () -> T, isEmpty: (T) -> Boolean = { false }, content: @Composable (T) -> Unit) {
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
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(cats, key = { it.id }) { cat ->
                    var menu by remember { mutableStateOf(false) }
                    val isLocked = locked.contains("cat:" + cat.id)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (hidden.contains("cat:" + cat.id)) c.card.copy(alpha = 0.4f) else c.card)
                        .tvFocus(c.accent, 12).combinedClickable(onClick = { if (isLocked) gate = cat else nav.push(Screen.Items(kind, cat)) }, onLongClick = { menu = true }).padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isLocked) { Icon(Icons.Default.Lock, null, tint = c.muted, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
                        Text(cat.name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text((counts[cat.id] ?: 0).toString(), color = c.muted, fontSize = 12.sp)
                        Box { IconButton(onClick = { menu = true }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.MoreVert, null, tint = c.muted) }
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
        text = { Column { Field(v, { v = it.filter { ch -> ch.isDigit() }.take(6) }, "PIN", password = true); if (bad) Text(stringResource(R.string.wrong_pin), color = Color(0xFFFF6B7A), fontSize = 12.sp) } })
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
            val base = if (category == null) content.items else content.items.filter { it.categoryId == category.id }
            val sorted = when (sort) { "az" -> base.sortedBy { it.name.lowercase() }; "new" -> base.sortedByDescending { it.added }; "rating" -> base.sortedByDescending { it.rating.toDoubleOrNull() ?: 0.0 }; else -> repo.applyOrder(listId, base) { it.key } }
            val items = sorted.filter { show || !hidden.contains(it.key) }
            ItemGrid(items, kind, pl, listId, hidden, sort == "default")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemGrid(items: List<Item>, kind: Kind, pl: Playlist, listId: String, hidden: Set<String>, canOrder: Boolean) {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current
    val scope = rememberCoroutineScope()
    if (items.isEmpty()) { Empty(stringResource(R.string.no_items)); return }
    val orderedKeys = items.map { it.key }
    if (kind == Kind.LIVE) {
        val ev by repo.epgVersion.collectAsState()
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(items, key = { it.key }) { it ->
                var menu by remember { mutableStateOf(false) }
                val fav = repo.isFavorite(it.key)
                val nn = remember(ev, it.key) { repo.nowNext(pl, it) }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (hidden.contains(it.key)) c.card.copy(alpha = 0.4f) else c.card)
                    .tvFocus(c.accent, 10).combinedClickable(onClick = { repo.liveContext = items; scope.launch { playItem(repo, nav, pl, it) } }, onLongClick = { menu = true }).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Poster(it.icon, Modifier.size(52.dp, 40.dp), contentScaleFit = true); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text((if (it.num > 0) it.num.toString() + "  " else "") + it.name + (if (it.archive) "  ⏪" else ""), color = c.text, fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        nn.first?.let { e -> Text(e.title + (nn.second?.let { n -> "  ·  " + n.title } ?: ""), color = c.muted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    IconButton(onClick = { repo.toggleFavorite(it.key) }, modifier = Modifier.size(32.dp).tvFocus(c.accent, 16)) { Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (fav) Color(0xFFFF6B7A) else c.muted, modifier = Modifier.size(18.dp)) }
                    Box { IconButton(onClick = { menu = true }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.MoreVert, null, tint = c.muted) }
                        if (canOrder) OrderMenu(menu, { menu = false }, repo, listId, orderedKeys, it.key, it.key, hidden.contains(it.key)) }
                }
            }
        }
    } else {
        val cols = (if (tv.ninekpro.app.data.DeviceInfo.isTv || repo.prefs.deviceType == "tv") 6 else 5) + when (repo.prefs.layoutSize) { "large" -> -1; "compact" -> 2; else -> 0 }
        LazyVerticalGrid(columns = GridCells.Fixed(cols), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        if (last != null) BigButton(stringResource(R.string.resume), Icons.Default.PlayArrow) { resumePlay(repo, nav, last) }
                        else info.seasons.values.firstOrNull()?.firstOrNull()?.let { first -> BigButton(stringResource(R.string.play), Icons.Default.PlayArrow) { scope.launch { playEpisode(repo, nav, pl, info, first, item.name) } } }
                    }
                }
                if (info.seasons.size > 1) LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(info.seasons.keys.toList()) { s -> Pill(stringResource(R.string.season, s), s == season) { season = s } } }
                Gap(8)
                val eps = info.seasons[season] ?: emptyList()
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { SectionTitle(stringResource(R.string.episodes).uppercase()); Spacer(Modifier.weight(1f))
                    Pill(stringResource(R.string.download_season), false) { scope.launch { eps.forEach { ep -> val u = repo.episodeUrl(pl, ep); tv.ninekpro.app.data.Downloads.enqueue(nav, repo, u, item.name + " S" + ep.season + "E" + ep.num, ep.ext) } } } }
                eps.forEach { ep ->
                    val r = repo.resumeOf(ep.key)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(c.card).tvFocus(c.accent, 10).clickable { scope.launch { playEpisode(repo, nav, pl, info, ep, item.name) } }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box { Poster(ep.icon.ifEmpty { item.icon }, Modifier.size(96.dp, 56.dp)); if (r != null && r.durationMs > 0) Box(Modifier.align(Alignment.BottomStart).width((96 * (r.positionMs.toFloat() / r.durationMs).coerceIn(0.02f, 1f)).dp).height(3.dp).background(c.accent)) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) { Text("E" + ep.num + "  " + ep.title, color = c.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis); if (ep.durationSecs > 0) Text(fmtTime(ep.durationSecs * 1000L), color = c.muted, fontSize = 11.sp) }
                        IconButton(onClick = { scope.launch { val u = repo.episodeUrl(pl, ep); tv.ninekpro.app.data.Downloads.enqueue(nav, repo, u, item.name + " S" + ep.season + "E" + ep.num, ep.ext) } }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Download, null, tint = c.muted, modifier = Modifier.size(18.dp)) }
                    }
                }
                Gap(24)
            }
            }
        }
    }
}

// ------------------------------------------------------------ search / favorites / continue
@Composable
fun SearchScreen(initial: String) {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current
    val pl = repo.activePlaylist ?: return
    var q by remember { mutableStateOf(initial) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pl.id) { for (k in Kind.values()) try { repo.content(pl, k) } catch (e: Throwable) {} }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.search))
        Field(q, { q = it }, stringResource(R.string.search), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
        val res = remember(q) { repo.searchCached(pl, q) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Kind.values().forEach { k ->
                val list = res.filter { it.kind == k }.take(40)
                if (list.isNotEmpty()) { SectionTitle(kindTitle(k).uppercase() + "  ·  " + list.size)
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(list, key = { it.key }) { it -> PosterCard(it, if (k == Kind.LIVE) 120 else 110) { when (k) { Kind.LIVE -> { repo.liveContext = list; scope.launch { playItem(repo, nav, pl, it) } }; Kind.MOVIE -> nav.push(Screen.Movie(it)); Kind.SERIES -> nav.push(Screen.Series(it)) } } } } }
            }
            if (q.length >= 2 && res.isEmpty()) Empty(stringResource(R.string.no_items))
            Gap(20)
        }
    }
}

@Composable
fun FavoritesScreen() {
    val repo = LocalRepo.current; val c = LocalColors.current; val pv by repo.profileVersion.collectAsState()
    val pl = repo.activePlaylist ?: return
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.favorites))
        Loaded(pl.id + "fav", { Kind.values().associateWith { k -> repo.content(pl, k) } }) { all ->
            val favs = remember(pv) { repo.favorites() }
            val items = favs.mapNotNull { key -> all.values.firstNotNullOfOrNull { c2 -> c2.items.firstOrNull { it.key == key } } }
            if (items.isEmpty()) Empty(stringResource(R.string.no_items))
            else Column(Modifier.verticalScroll(rememberScrollState())) {
                Kind.values().forEach { k -> val l = items.filter { it.kind == k }; if (l.isNotEmpty()) { SectionTitle(kindTitle(k).uppercase()); Box(Modifier.height(if (k == Kind.LIVE) (l.size * 56 + 24).dp else ((l.size + 2) / 3 * 200 + 24).dp)) { ItemGrid(l, k, pl, "fav:" + k.name, emptySet(), true) } } }
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
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(list, key = { it.key }) { r ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.card).tvFocus(c.accent, 10).clickable { resumePlay(repo, nav, r) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Poster(r.image, Modifier.size(72.dp, 50.dp)); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text(r.title, color = c.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis); if (r.durationMs > 0) Text(fmtTime(r.positionMs) + " / " + fmtTime(r.durationMs), color = c.muted, fontSize = 11.sp) }
                    IconButton(onClick = { repo.removeResume(r.key) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, null, tint = c.muted, modifier = Modifier.size(16.dp)) }
                }
            }
        }
    }
}
