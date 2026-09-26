@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package tv.ninekpro.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject
import tv.ninekpro.desktop.data.*

@Composable
fun VodPane(kind: Kind) {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current
    val pl = repo.activePlaylist
    var content by remember { mutableStateOf<XtreamApi.Content?>(null) }; var err by remember { mutableStateOf("") }
    val lv by repo.loadedVersion.collectAsState(); val pv by repo.profileVersion.collectAsState()
    var cat by remember(kind) { mutableStateOf(repo.vodUiCat[kind] ?: "__all") }; var sort by remember { mutableStateOf("newest") }; var reorder by remember { mutableStateOf(false) }
    LaunchedEffect(cat) { repo.vodUiCat[kind] = cat }
    val saved = repo.vodUiScroll[kind]
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(saved?.first ?: 0, saved?.second ?: 0)
    DisposableEffect(kind) { onDispose { repo.vodUiScroll[kind] = gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset } }
    LaunchedEffect(pl?.id, lv, kind) { if (pl != null) { try { content = repo.content(pl, kind) } catch (e: Throwable) { err = e.message ?: "error" } } }
    val lang = LocalLang.current
    val realCats = remember(content, pv) { val l = content?.categories ?: emptyList(); val locked = repo.locked(); val hid = repo.hidden()
        repo.applyOrder("cats:${pl?.id}:${kind.name}", l.filter { !hid.contains("cat:" + it.id) && !(repo.prefs.parentalOn && (repo.isAdultName(it.name) || locked.contains("cat:" + it.id)) && repo.unlockedUntil < System.currentTimeMillis()) }) { it.id } }
    val contCount = remember(content, pv) { content?.let { repo.continueItems(kind, it).size } ?: 0 }
    val cats = remember(realCats, contCount) { listOf(Category("__all", Strings.get(lang, "all"), kind)) + (if (contCount > 0) listOf(Category("__cont", Strings.get(lang, "continue_watching"), kind)) else emptyList()) + listOf(Category("__fav", Strings.get(lang, "favorites"), kind)) + realCats }
    val items = remember(content, cat, sort, pv) { val all = content?.items ?: emptyList()
        if (cat == "__cont") return@remember content?.let { repo.continueItems(kind, it) } ?: emptyList()
        val base = when (cat) { "__all" -> all; "__fav" -> { val f = repo.favorites().toSet(); all.filter { f.contains(it.key) } }; else -> all.filter { it.categoryId == cat } }
        when (sort) { "az" -> base.sortedBy { it.name.lowercase() }; "rating" -> base.sortedByDescending { it.rating.toFloatOrNull() ?: 0f }; "newest" -> base.sortedByDescending { it.added }; else -> base } }
    val catsListId = "cats:${pl?.id}:${kind.name}"; val keysState = androidx.compose.runtime.rememberUpdatedState(realCats.map { it.id })
    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) {
        if (reorder) Column(Modifier.width(230.dp).fillMaxHeight().glass(c, 12).padding(6.dp)) {
            Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) { Text("↕ " + T("reorder_hint"), color = c.accent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); PBtn(T("done"), primary = true) { reorder = false } }
            LazyColumn(Modifier.fillMaxSize()) { items(realCats, key = { it.id }) { ct -> var acc by remember { mutableStateOf(0f) }
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(c.card).pointerInput(ct.id) { detectDragGestures(onDragEnd = { acc = 0f }) { ch, drag -> ch.consume(); acc += drag.y; if (acc > 26) { repo.move(catsListId, keysState.value, ct.id, 1); acc = 0f } else if (acc < -26) { repo.move(catsListId, keysState.value, ct.id, -1); acc = 0f } } }.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DragHandle, null, tint = c.accent, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(ct.name, color = c.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } } }
        } else LazyColumn(Modifier.width(230.dp).fillMaxHeight().glass(c, 12).padding(6.dp)) {
            items(cats, key = { it.id }) { ct -> val s = ct.id == cat; val real = !ct.id.startsWith("__")
                Text(ct.name, color = if (s) Color.White else c.text, fontSize = 13.5.sp, fontWeight = if (s) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(8.dp)).background(if (s) c.accent.copy(alpha = 0.35f) else Color.Transparent).hoverGlow(c.accent, 8, 1f)
                        .combinedClickable(onClick = { cat = ct.id }, onLongClick = { if (real) reorder = true }).padding(horizontal = 10.dp, vertical = 8.dp)) } }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(cats.firstOrNull { it.id == cat }?.name ?: "", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(items.size.toString(), color = c.muted, fontSize = 12.sp); Spacer(Modifier.width(12.dp))
                listOf("newest" to "sort_newest", "az" to "sort_az", "rating" to "sort_rating", "default" to "sort_default").forEach { (k, t) -> Text(T(t), color = if (sort == k) c.accent else c.muted, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { sort = k }.padding(horizontal = 6.dp, vertical = 4.dp)) }
            }
            if (content == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { if (err.isNotEmpty()) Text(err, color = c.muted) else Loading(T("loading")) }
            else if (items.isEmpty()) Text(T("no_items"), color = c.muted, modifier = Modifier.padding(20.dp))
            else LazyVerticalGrid(GridCells.Adaptive(150.dp), state = gridState, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(items, key = { it.key }) { it -> Poster(it, 150) { nav.push(if (kind == Kind.MOVIE) Screen.Movie(it) else Screen.Series(it)) } } }
        }
    }
}

@Composable
fun BackRow(title: String) { val nav = LocalNav.current; val c = LocalColors.current
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { RoundBtn(Icons.AutoMirrored.Filled.ArrowBack) { nav.pop() }; Spacer(Modifier.width(12.dp)); Text(title, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis) } }

/** Resume/Start over prompt then play. */
@Composable
fun ResumeAsk(key: String, onPick: (Long) -> Unit, onDismiss: () -> Unit) { val repo = LocalRepo.current; val r = repo.resumeOf(key)
    if (r == null || r.positionMs <= 0) { LaunchedEffect(Unit) { onPick(0L) }; return }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(T("resume_q")) }, text = { Text(fmtTime(r.positionMs) + " / " + fmtTime(r.durationMs)) },
        confirmButton = { TextButton({ onPick(r.positionMs) }) { Text(T("resume")) } }, dismissButton = { TextButton({ onPick(0L) }) { Text(T("start_over")) } }) }

@Composable
fun MovieScreen(item: Item) {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current; val scope = rememberCoroutineScope()
    val pl = repo.activePlaylist
    var info by remember { mutableStateOf<JSONObject?>(null) }; var tm by remember { mutableStateOf<TmdbInfo?>(null) }; var ask by remember { mutableStateOf(false) }
    val pv by repo.profileVersion.collectAsState()
    LaunchedEffect(item.key) { launch { try { info = pl?.let { repo.vodInfo(it, item.id).optJSONObject("info") } } catch (e: Throwable) {} }; launch { tm = repo.tmdbFor(item.name, item.year, false) } }
    val i = info
    val plot = (i?.optString("plot") ?: "").ifEmpty { i?.optString("description") ?: "" }.ifEmpty { tm?.overview ?: item.plot }
    val cast = (i?.optString("cast") ?: "").ifEmpty { i?.optString("actors") ?: "" }.ifEmpty { tm?.cast ?: "" }
    val genre = (i?.optString("genre") ?: "").ifEmpty { tm?.genres ?: "" }
    val year = item.year.ifEmpty { i?.optString("releasedate")?.take(4) ?: "" }.ifEmpty { tm?.year ?: "" }
    val rating = item.rating.takeIf { it.isNotEmpty() && it != "0" } ?: (i?.optString("rating")?.takeIf { it.isNotEmpty() && it != "0" } ?: tm?.rating?.takeIf { it != "0.0" } ?: "")
    val poster = item.icon.ifEmpty { i?.optString("movie_image") ?: "" }.ifEmpty { tm?.poster ?: "" }
    val backdrop = (i?.optJSONArray("backdrop_path")?.optString(0) ?: "").ifEmpty { tm?.backdrop ?: "" }.ifEmpty { poster }
    val trailer = (i?.optString("youtube_trailer") ?: "").let { if (it.isNotEmpty() && it != "null") (if (it.startsWith("http")) it else "https://www.youtube.com/watch?v=$it") else "" }.ifEmpty { tm?.trailer ?: "" }
    val duration = (i?.optString("duration") ?: "").ifEmpty { tm?.runtime?.takeIf { it > 0 }?.let { "$it min" } ?: "" }
    val director = (i?.optString("director") ?: "").ifEmpty { tm?.director ?: "" }
    fun play(startMs: Long) { scope.launch { val u = repo.streamUrl(pl ?: return@launch, item); nav.push(Screen.Play(u, item.name, item, startMs = startMs, resumeKey = item.key, image = poster)) } }
    Box(Modifier.fillMaxSize()) {
        if (backdrop.startsWith("http")) AsyncImage(backdrop, Modifier.fillMaxSize().alpha(0.28f))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, c.bg3.copy(alpha = 0.9f), c.bg3))))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            BackRow(item.name)
            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                AsyncImage(poster, Modifier.width(220.dp).height(320.dp).clip(RoundedCornerShape(12.dp)).background(c.card))
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f)) {
                    Text(tm?.title?.ifEmpty { item.name } ?: item.name, color = c.text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    if (tm?.tagline?.isNotEmpty() == true) Text(tm!!.tagline, color = c.muted, fontSize = 13.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Fact(T("rating"), if (rating.isNotEmpty()) "★ " + rating + (tm?.votes?.takeIf { it > 0 }?.let { " (" + (if (it >= 1000) (it / 1000).toString() + "k" else it.toString()) + ")" } ?: "") else "")
                        Fact(T("year"), year); Fact(T("length"), duration); Fact(T("director"), director)
                    }
                    if (genre.isNotEmpty()) { Spacer(Modifier.height(6.dp)); Text(genre, color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val r = repo.resumeOf(item.key)
                        PBtn(if (r != null && r.positionMs > 0) T("resume") else T("play"), Icons.Default.PlayArrow, primary = true) { if (r != null && r.positionMs > 0) ask = true else play(0L) }
                        val fav = repo.isFavorite(item.key); PBtn(if (fav) T("remove_favorite") else T("add_favorite"), if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder) { repo.toggleFavorite(item.key) }
                        if (trailer.isNotEmpty()) PBtn(T("trailer"), Icons.Default.OndemandVideo) { try { java.awt.Desktop.getDesktop().browse(java.net.URI(trailer)) } catch (e: Throwable) {} }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (plot.isNotEmpty()) Text(plot, color = c.text.copy(alpha = 0.92f), fontSize = 14.sp, lineHeight = 20.sp)
                    else if (info == null && tm == null) Text(T("loading"), color = c.muted, fontSize = 13.sp)
                    val people = tm?.people ?: emptyList()
                    if (people.isNotEmpty()) { Spacer(Modifier.height(14.dp)); Text(T("cast"), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(6.dp)); CastRow(people) }
                    else if (cast.isNotEmpty()) { Spacer(Modifier.height(10.dp)); Text(T("cast") + ": " + cast, color = c.muted, fontSize = 12.5.sp) }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
    if (ask) ResumeAsk(item.key, onPick = { ask = false; play(it) }, onDismiss = { ask = false })
}

@Composable
fun Fact(label: String, value: String) { if (value.isEmpty()) return; val c = LocalColors.current
    Column(Modifier.clip(RoundedCornerShape(8.dp)).background(c.card).padding(horizontal = 10.dp, vertical = 6.dp)) { Text(label, color = c.muted, fontSize = 10.5.sp); Text(value, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) } }

/** Actors with photos (TMDB). */
@Composable
fun CastRow(people: List<Person>) { val c = LocalColors.current
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(people, key = { it.name + it.role }) { p ->
        Column(Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(36.dp)).background(c.card)) { if (p.photo.isNotEmpty()) AsyncImage(p.photo, Modifier.fillMaxSize()) else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(p.name.take(1), color = c.muted, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold) } }
            Spacer(Modifier.height(4.dp)); Text(p.name, color = c.text, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 13.sp)
            if (p.role.isNotEmpty()) Text(p.role, color = c.muted, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center) } } } }

@Composable
fun SeriesScreen(item: Item) {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current; val scope = rememberCoroutineScope()
    val pl = repo.activePlaylist
    var info by remember { mutableStateOf<SeriesInfo?>(null) }; var err by remember { mutableStateOf("") }; var season by remember { mutableStateOf(-1) }
    var askEp by remember { mutableStateOf<Episode?>(null) }; var tm by remember { mutableStateOf<TmdbInfo?>(null) }
    val pv by repo.profileVersion.collectAsState()
    LaunchedEffect(item.key) { launch { tm = repo.tmdbFor(item.name, item.year, true) }; if (pl != null) try { info = repo.seriesInfo(pl, item.id); season = info?.seasons?.keys?.firstOrNull() ?: -1 } catch (e: Throwable) { err = e.message ?: "error" } }
    fun play(ep: Episode, startMs: Long) { scope.launch { val u = repo.episodeUrl(pl ?: return@launch, ep); nav.push(Screen.Play(u, item.name + " · S" + ep.season + "E" + ep.num + " " + ep.title, item, episode = ep, startMs = startMs, resumeKey = ep.key, image = ep.icon.ifEmpty { item.icon })) } }
    val inf = info
    Box(Modifier.fillMaxSize()) {
        val bd = (inf?.backdrop ?: "").ifEmpty { tm?.backdrop ?: "" }
        if (bd.startsWith("http")) AsyncImage(bd, Modifier.fillMaxSize().alpha(0.22f))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, c.bg3))))
        Column(Modifier.fillMaxSize()) {
            BackRow(item.name)
            Row(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                Column(Modifier.width(240.dp)) {
                    AsyncImage(item.icon, Modifier.width(220.dp).height(320.dp).clip(RoundedCornerShape(12.dp)).background(c.card))
                    Spacer(Modifier.height(10.dp))
                    val fav = repo.isFavorite(item.key); PBtn(if (fav) T("remove_favorite") else T("add_favorite"), if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder) { repo.toggleFavorite(item.key) }
                    val sYear = (inf?.year ?: "").ifEmpty { item.year }.ifEmpty { tm?.year ?: "" }; val sGenre = (inf?.genre ?: "").ifEmpty { tm?.genres ?: "" }
                    val sRating = (inf?.rating?.takeIf { it.isNotEmpty() && it != "0" } ?: item.rating.takeIf { it.isNotEmpty() && it != "0" } ?: tm?.rating?.takeIf { it != "0.0" } ?: "")
                    val sPlot = (inf?.plot ?: "").ifEmpty { item.plot }.ifEmpty { tm?.overview ?: "" }
                    Spacer(Modifier.height(10.dp))
                    Text(listOf(sYear, sGenre, if (sRating.isNotEmpty()) "★ " + sRating else "", tm?.seasons?.takeIf { it > 0 }?.let { it.toString() + " " + T("seasons").lowercase() } ?: "").filter { it.isNotBlank() }.joinToString(" · "), color = c.muted, fontSize = 12.5.sp)
                    if (tm?.director?.isNotEmpty() == true) Text(T("director") + ": " + tm!!.director, color = c.muted, fontSize = 12.sp)
                    if (sPlot.isNotEmpty()) Text(sPlot, color = c.text, fontSize = 12.5.sp, lineHeight = 17.sp, maxLines = 12, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    val people = tm?.people ?: emptyList(); val sCast = (inf?.cast ?: "").ifEmpty { tm?.cast ?: "" }
                    if (people.isNotEmpty()) { Spacer(Modifier.height(10.dp)); Text(T("cast"), color = c.text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(4.dp)); CastRow(people) }
                    else if (sCast.isNotEmpty()) Text(T("cast") + ": " + sCast, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    if (inf == null) { if (err.isNotEmpty()) Text(err, color = c.muted) else Loading(T("loading")) }
                    else {
                        Row(Modifier.padding(bottom = 8.dp)) { inf.seasons.keys.forEach { sn -> Text(T("season", sn), color = if (sn == season) Color.White else c.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(8.dp)).background(if (sn == season) c.accent else c.card).hoverGlow(c.accent, 8, 1.02f).clickable { season = sn }.padding(horizontal = 12.dp, vertical = 7.dp)) } }
                        val eps = inf.seasons[season] ?: emptyList()
                        LazyColumn { items(eps, key = { it.key }) { ep ->
                            val r = repo.resumeOf(ep.key)
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).glass(c, 10).hoverGlow(c.accent, 10, 1.01f).clickable { if (r != null && r.positionMs > 0) askEp = ep else play(ep, 0L) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(150.dp, 84.dp).clip(RoundedCornerShape(8.dp)).background(c.card2)) { AsyncImage(ep.icon.ifEmpty { item.icon }, Modifier.fillMaxSize()); if (r != null && r.durationMs > 0) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(r.positionMs.toFloat() / r.durationMs).height(3.dp).background(c.accent)) }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) { Text("E" + ep.num + "  " + ep.title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (ep.durationSecs > 0) Text(fmtTime(ep.durationSecs * 1000L), color = c.muted, fontSize = 11.5.sp)
                                    if (ep.plot.isNotEmpty()) Text(ep.plot, color = c.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                Icon(Icons.Default.PlayArrow, null, tint = c.accent, modifier = Modifier.size(28.dp))
                            } } }
                    }
                }
            }
        }
    }
    val ae = askEp
    if (ae != null) ResumeAsk(ae.key, onPick = { askEp = null; play(ae, it) }, onDismiss = { askEp = null })
}
