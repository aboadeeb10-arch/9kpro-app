package tv.ninekpro.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tv.ninekpro.desktop.data.Kind

@Composable
fun SearchScreen() {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current; val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }; val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    val pl = repo.activePlaylist
    val res = remember(q) { if (pl == null) emptyList() else repo.searchCached(pl, q) }
    val pv by repo.profileVersion.collectAsState()
    Column(Modifier.fillMaxSize()) {
        BackRow(T("search"))
        OutlinedTextField(q, { q = it }, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().focusRequester(fr), singleLine = true, colors = fieldColors(c), placeholder = { Text(T("search")) })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (q.length < 2 && pl != null) {
                val favKeys = repo.favorites(); val all = Kind.values().flatMap { repo.cachedContent(pl, it)?.items ?: emptyList() }.associateBy { it.key }
                val favs = favKeys.mapNotNull { all[it] }
                if (favs.isNotEmpty()) { SectionTitle(T("favorites")); Row(Modifier.fillMaxWidth()) { } ; LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(favs, key = { it.key }) { it -> if (it.kind == Kind.LIVE) ChannelChip(it) { scope.launch { val u = repo.streamUrl(pl, it, repo.prefs.liveFormat == "ts"); repo.liveContext = favs.filter { f -> f.kind == Kind.LIVE }; nav.push(Screen.Play(u, it.name, it, live = true, image = it.icon)) } } else Poster(it, 130) { nav.push(if (it.kind == Kind.MOVIE) Screen.Movie(it) else Screen.Series(it)) } } } }
            } else {
                for (k in Kind.values()) { val l = res.filter { it.kind == k }; if (l.isEmpty()) continue
                    SectionTitle(when (k) { Kind.LIVE -> T("live"); Kind.MOVIE -> T("movies"); Kind.SERIES -> T("series") })
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(l, key = { it.key }) { it -> if (k == Kind.LIVE) ChannelChip(it) { scope.launch { val u = repo.streamUrl(pl!!, it, repo.prefs.liveFormat == "ts"); repo.liveContext = l; nav.push(Screen.Play(u, it.name, it, live = true, image = it.icon)) } } else Poster(it, 130) { nav.push(if (k == Kind.MOVIE) Screen.Movie(it) else Screen.Series(it)) } } } }
                if (res.isEmpty() && q.length >= 2) Text(T("no_items"), color = c.muted)
            }
        }
    }
}
