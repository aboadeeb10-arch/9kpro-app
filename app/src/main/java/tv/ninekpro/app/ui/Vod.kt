package tv.ninekpro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.ninekpro.app.R
import tv.ninekpro.app.data.Kind

private const val ALL = "__all"; private const val CONT = "__cont"; private const val FAV = "__fav"

/** Movies / Series: categories on the left, poster grid on the right (two steps on narrow phones). */
@Composable
fun VodPane(kind: Kind) {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current
    val pl = repo.activePlaylist ?: run { nav.reset(Screen.Playlists); return }
    val pv by repo.profileVersion.collectAsState()
    var catId by remember { mutableStateOf(ALL) }; var showList by remember { mutableStateOf(false) }; var gate by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf("new") }; var reorder by remember { mutableStateOf(false) }
    Loaded(pl.id + kind.name, { repo.content(pl, kind) }, isEmpty = { it.items.isEmpty() && pl.kind == "xtream" }) { content ->
        val hidden = remember(pv) { repo.hidden() }; val favs = remember(pv) { repo.favorites().toSet() }
        val cats = remember(content, pv) { repo.applyOrder("cats:${pl.id}:${kind.name}", content.categories) { it.id } }.filter { repo.prefs.showHidden || !hidden.contains("cat:" + it.id) }
        val locked = remember(pv, content) { val l = repo.locked().toMutableSet(); if (repo.prefs.parentalOn) content.categories.filter { repo.isAdultName(it.name) }.forEach { l.add("cat:" + it.id) }; if (!repo.prefs.parentalOn) l.clear(); l as Set<String> }
        val counts = remember(content) { content.items.groupingBy { it.categoryId }.eachCount() }
        val listId = "items:${pl.id}:${kind.name}:$catId"
        val items = remember(content, catId, pv, sort) {
            if (catId == CONT) return@remember repo.continueItems(kind, content)
            val base = when (catId) { ALL -> content.items; FAV -> content.items.filter { favs.contains(it.key) }; else -> content.items.filter { it.categoryId == catId } }
            val sorted = when (sort) { "az" -> base.sortedBy { it.name.lowercase() }; "new" -> base.sortedByDescending { it.added }; "rating" -> base.sortedByDescending { it.rating.toDoubleOrNull() ?: 0.0 }; else -> repo.applyOrder(listId, base) { it.key } }
            sorted.filter { repo.prefs.showHidden || !hidden.contains(it.key) }
        }
        val contCount = remember(content, pv) { repo.continueItems(kind, content).size }
        PinGate(gate != null, { gate = null }) { val g = gate; gate = null; if (g != null) { catId = g; showList = true } }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > 700.dp
            androidx.activity.compose.BackHandler(enabled = !wide && showList) { showList = false }
            val catsListId = "cats:${pl.id}:${kind.name}"
            val keysState = rememberUpdatedState(cats.map { it.id })
            val density = LocalDensity.current
            val rowPx = with(density) { 46.dp.toPx() }
            val catList: @Composable (Modifier) -> Unit = { m ->
                if (reorder) Column(m) {
                    Row(Modifier.fillMaxWidth().background(c.accent.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("↕ " + stringResource(R.string.reorder_hint), color = c.accent, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 2)
                        Pill(stringResource(R.string.done), true) { reorder = false }
                    }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(cats, key = { it.id }) { cat ->
                            var acc by remember { mutableStateOf(0f) }
                            Row(Modifier.fillMaxWidth().background(c.card).padding(horizontal = 10.dp, vertical = 12.dp)
                                .pointerInput(cat.id) { detectDragGestures(onDragEnd = { acc = 0f }, onDragCancel = { acc = 0f }) { ch, drag -> ch.consume(); acc += drag.y
                                    if (acc > rowPx) { repo.move(catsListId, keysState.value, cat.id, 1); acc = 0f } else if (acc < -rowPx) { repo.move(catsListId, keysState.value, cat.id, -1); acc = 0f } } }, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DragHandle, null, tint = c.accent, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(10.dp))
                                Text(cat.name, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line.copy(alpha = 0.5f)))
                        }
                    }
                } else LazyColumn(m, contentPadding = PaddingValues(vertical = 4.dp)) {
                    item { CatRow(stringResource(R.string.all), content.items.size, catId == ALL) { catId = ALL; showList = true } }
                    if (contCount > 0) item { CatRow(stringResource(R.string.continue_watching), contCount, catId == CONT) { catId = CONT; showList = true } }
                    item { CatRow(stringResource(R.string.favorites), favs.count { it.contains(":" + kind.name + ":") }, catId == FAV) { catId = FAV; showList = true } }
                    items(cats, key = { it.id }) { cat ->
                        var menu by remember { mutableStateOf(false) }
                        val isLocked = locked.contains("cat:" + cat.id)
                        Box { CatRow((if (isLocked) "🔒 " else "") + cat.name, counts[cat.id] ?: 0, catId == cat.id, onLong = { menu = true }) { if (isLocked) gate = cat.id else { catId = cat.id; showList = true } }
                            OrderMenu(menu, { menu = false }, repo, catsListId, cats.map { it.id }, cat.id, "cat:" + cat.id, hidden.contains("cat:" + cat.id), onMove = { reorder = true }) }
                    }
                }
            }
            val grid: @Composable (Modifier) -> Unit = { m ->
                Column(m) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!wide) { Pill("‹", false) { showList = false }; Spacer(Modifier.width(8.dp)) }
                        Text(cats.firstOrNull { it.id == catId }?.name ?: (when (catId) { FAV -> stringResource(R.string.favorites); CONT -> stringResource(R.string.continue_watching); else -> stringResource(R.string.all) }), color = c.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        var m2 by remember { mutableStateOf(false) }
                        Box { Pill(stringResource(R.string.sort), false) { m2 = true }
                            DropdownMenu(expanded = m2, onDismissRequest = { m2 = false }) { listOf("default" to R.string.sort_default, "az" to R.string.sort_az, "new" to R.string.sort_newest, "rating" to R.string.sort_rating).forEach { (k, r) -> DropdownMenuItem(text = { Text(stringResource(r)) }, onClick = { sort = k; m2 = false }) } } }
                    }
                    Box(Modifier.fillMaxSize()) { ItemGrid(items, kind, pl, listId, hidden, sort == "default") }
                }
            }
            if (wide) Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(0.24f).fillMaxHeight()) { catList(Modifier.fillMaxSize()) }
                Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
                Box(Modifier.weight(0.76f).fillMaxHeight()) { grid(Modifier.fillMaxSize()) }
            } else { if (!showList) catList(Modifier.fillMaxSize()) else grid(Modifier.fillMaxSize()) }
        }
    }
}
