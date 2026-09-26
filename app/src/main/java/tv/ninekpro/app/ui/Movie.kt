package tv.ninekpro.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Slideshow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.json.JSONObject
import tv.ninekpro.app.R
import tv.ninekpro.app.data.Downloads
import tv.ninekpro.app.data.Item
import tv.ninekpro.app.data.TmdbInfo

/** Movie details: backdrop, poster, plot, cast, year, rating · Play / Resume · Trailer · Download · Favorite. Info from the panel, filled by TMDB when missing. */
@Composable
fun MovieScreen(item: Item) {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current; val act = LocalActivity.current
    val pl = repo.playlists.collectAsState().value.firstOrNull { it.id == item.playlistId } ?: repo.activePlaylist ?: return
    val pv by repo.profileVersion.collectAsState()
    var info by remember { mutableStateOf<JSONObject?>(null) }; var tm by remember { mutableStateOf<TmdbInfo?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(item.key) {
        launch { try { info = repo.vodInfo(pl, item.id).optJSONObject("info") } catch (e: Throwable) {} }
        launch { tm = repo.tmdbFor(item.name, item.year, false) }
    }
    val i = info
    val plot = (i?.optString("plot") ?: "").ifEmpty { i?.optString("description") ?: "" }.ifEmpty { tm?.overview ?: item.plot }
    val cast = (i?.optString("cast") ?: "").ifEmpty { tm?.cast ?: "" }
    val genre = (i?.optString("genre") ?: "").ifEmpty { tm?.genres ?: "" }
    val year = item.year.ifEmpty { i?.optString("releasedate")?.take(4) ?: "" }.ifEmpty { tm?.year ?: "" }
    val rating = item.rating.takeIf { it.isNotEmpty() && it != "0" } ?: (i?.optString("rating")?.takeIf { it.isNotEmpty() && it != "0" } ?: tm?.rating ?: "")
    val poster = item.icon.ifEmpty { i?.optString("movie_image") ?: "" }.ifEmpty { tm?.poster ?: "" }
    val backdrop = (i?.optJSONArray("backdrop_path")?.optString(0) ?: "").ifEmpty { tm?.backdrop ?: "" }.ifEmpty { poster }
    val trailer = (i?.optString("youtube_trailer") ?: "").let { if (it.isNotEmpty() && it != "null") (if (it.startsWith("http")) it else "https://www.youtube.com/watch?v=$it") else "" }.ifEmpty { tm?.trailer ?: "" }
    val duration = (i?.optString("duration") ?: "").ifEmpty { tm?.runtime?.takeIf { it > 0 }?.let { "$it min" } ?: "" }
    val resume = remember(pv) { repo.resumeOf(item.key) }
    val fav = remember(pv) { repo.isFavorite(item.key) }

    Box(Modifier.fillMaxSize()) {
        if (backdrop.isNotEmpty()) AsyncImage(model = backdrop, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), alpha = 0.35f)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, c.bg3.copy(alpha = 0.9f), c.bg3))))
        Column(Modifier.fillMaxSize()) {
            TopBar(item.name)
            Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
                Poster(poster, Modifier.width(170.dp).height(250.dp))
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(tm?.title?.ifEmpty { item.name } ?: item.name, color = c.text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (tm?.tagline?.isNotEmpty() == true) Text(tm!!.tagline, color = c.muted, fontSize = 12.5.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Gap(8)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Fact(stringResource(R.string.rating), if (rating.isNotEmpty()) "★ " + rating + (tm?.votes?.takeIf { it > 0 }?.let { " (" + (if (it >= 1000) (it / 1000).toString() + "k" else it.toString()) + ")" } ?: "") else "")
                        Fact(stringResource(R.string.year), year); Fact(stringResource(R.string.length), duration); Fact(stringResource(R.string.director), tm?.director ?: "")
                    }
                    if (genre.isNotEmpty()) { Gap(6); Text(genre, color = c.accent, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold) }
                    Gap(10); Text(plot, color = c.text.copy(alpha = 0.9f), fontSize = 13.5.sp, maxLines = 7, overflow = TextOverflow.Ellipsis)
                    val people = (tm?.people ?: emptyList()).ifEmpty { cast.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(12).map { tv.ninekpro.app.data.Person(it, "", "") } }
                    if (people.isNotEmpty()) { Gap(12); SectionTitle(stringResource(R.string.cast).uppercase()); CastRow(people) }
                    Gap(16)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BigButton(if (resume != null) stringResource(R.string.resume) + " " + fmtTime(resume.positionMs) else stringResource(R.string.play), Icons.Default.PlayArrow) { scope.launch { playItem(repo, nav, pl, item, startMs = resume?.positionMs ?: 0L) } }
                        if (resume != null) BigButton(stringResource(R.string.start_over), null, filled = false) { repo.removeResume(item.key); scope.launch { playItem(repo, nav, pl, item, startMs = 0L) } }
                        if (trailer.isNotEmpty()) BigButton(stringResource(R.string.trailer), Icons.Default.Slideshow, filled = false) { try { act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(trailer))) } catch (e: Exception) {} }
                        BigButton(stringResource(R.string.download), Icons.Default.Download, filled = false) { scope.launch { val u = repo.streamUrl(pl, item); Downloads.enqueue(nav, repo, u, item.name, item.ext) } }
                        BigButton(if (fav) stringResource(R.string.remove_favorite) else stringResource(R.string.add_favorite), if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, filled = false) { repo.toggleFavorite(item.key) }
                    }
                }
            }
        }
    }
}
