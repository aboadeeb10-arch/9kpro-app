package tv.ninekpro.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.ninekpro.desktop.data.AsyncImage
import tv.ninekpro.desktop.data.Episode
import tv.ninekpro.desktop.data.Item
import tv.ninekpro.desktop.data.Repo

sealed class Screen {
    object Login : Screen()
    object Main : Screen()
    data class Movie(val item: Item) : Screen()
    data class Series(val item: Item) : Screen()
    /** Full-screen player. live = channel list context for zapping; startMs for resume. */
    data class Play(val url: String, val title: String, val item: Item? = null, val episode: Episode? = null, val live: Boolean = false, val startMs: Long = 0L, val sub: String = "", val resumeKey: String = "", val image: String = "") : Screen()
    object Settings : Screen()
    object Search : Screen()
    object Guide : Screen()
    object Playlists : Screen()
    object Speed : Screen()
}

class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Main)
    var mainTab by mutableStateOf(0)
    val current: Screen get() = stack.last()
    fun push(s: Screen) { stack.add(s) }
    fun pop(): Boolean { if (stack.size > 1) { stack.removeAt(stack.size - 1); return true }; return false }
    fun reset(s: Screen) { stack.clear(); stack.add(s) }
    fun replace(s: Screen) { stack.removeAt(stack.size - 1); stack.add(s) }
}

val LocalRepo = compositionLocalOf<Repo> { error("repo") }
val LocalNav = compositionLocalOf<Nav> { error("nav") }
/** Window-level actions (full screen toggle, toast). */
class WindowActions(val setFullscreen: (Boolean) -> Unit, val isFullscreen: () -> Boolean, val toast: (String) -> Unit, val exit: () -> Unit)
val LocalWindow = compositionLocalOf<WindowActions> { error("window") }

@Composable
fun BrandLogo(url: String, modifier: Modifier = Modifier) {
    if (url.startsWith("http")) AsyncImage(url, modifier, contentScale = ContentScale.Fit) { Image(painterResource("logo.png"), null, modifier, contentScale = ContentScale.Fit) }
    else Image(painterResource("logo.png"), null, modifier, contentScale = ContentScale.Fit)
}

@Composable
fun PBtn(text: String, icon: ImageVector? = null, primary: Boolean = false, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(modifier.clip(RoundedCornerShape(10.dp)).background(if (primary) c.accent else c.card).hoverGlow(c.accent, 10, 1.03f).clickable(enabled = enabled) { onClick() }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icon != null) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
fun Loading(text: String = "") { val c = LocalColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = c.accent, modifier = Modifier.size(34.dp)); if (text.isNotEmpty()) { Spacer(Modifier.height(10.dp)); Text(text, color = c.muted, fontSize = 13.sp) } } }

@Composable
fun Poster(item: Item, w: Int = 150, onClick: () -> Unit) {
    val c = LocalColors.current; val repo = LocalRepo.current
    Column(Modifier.width(w.dp).hoverGlow(c.accent, 10, 1.05f).clickable { onClick() }.padding(4.dp)) {
        Box(Modifier.fillMaxWidth().height((w * 1.45f).dp).clip(RoundedCornerShape(8.dp)).background(c.card)) {
            AsyncImage(item.icon, Modifier.fillMaxWidth().height((w * 1.45f).dp)) { Box(Modifier.fillMaxWidth().height((w * 1.45f).dp), contentAlignment = Alignment.Center) { Text(item.name.take(2).uppercase(), color = c.muted, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold) } }
            val r = repo.resumeOf(item.key)
            if (r != null && r.durationMs > 0) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(r.positionMs.toFloat() / r.durationMs).height(3.dp).background(c.accent))
            if (item.rating.isNotEmpty() && item.rating != "0") Text("★ " + item.rating.take(3), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(item.name, color = c.text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
        if (item.year.isNotEmpty()) Text(item.year, color = c.muted, fontSize = 11.sp)
    }
}

fun fmtTime(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); val h = s / 3600; val m = (s % 3600) / 60; val x = s % 60; return if (h > 0) "%d:%02d:%02d".format(h, m, x) else "%d:%02d".format(m, x) }
fun clockText(fmt24: Boolean): String = java.text.SimpleDateFormat(if (fmt24) "HH:mm" else "h:mm a", java.util.Locale.getDefault()).format(java.util.Date())
fun dateText(): String = java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.getDefault()).format(java.util.Date())
fun hm(secs: Long, fmt24: Boolean): String = java.text.SimpleDateFormat(if (fmt24) "HH:mm" else "h:mm a", java.util.Locale.getDefault()).format(java.util.Date(secs * 1000))
