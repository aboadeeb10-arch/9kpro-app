package tv.ninekpro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun TopBar(title: String, actions: @Composable () -> Unit = {}) {
    val nav = LocalNav.current; val c = LocalColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (nav.stack.size > 1) IconButton(onClick = { nav.pop() }, modifier = Modifier.tvFocus(c.accent, 24)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = c.text) }
        Text(title, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

@Composable
fun Loading() { val c = LocalColors.current; Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) } }

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    Box(Modifier.clip(RoundedCornerShape(999.dp)).then(if (selected) Modifier.background(c.accent) else Modifier.glass(c, 999)).tvFocus(c.accent, 999).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(text, color = if (selected) Color.White else c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun BigButton(text: String, icon: ImageVector? = null, filled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(modifier.clip(RoundedCornerShape(10.dp)).then(if (filled) Modifier.background(c.accent) else Modifier.glass(c, 10)).tvFocus(c.accent, 10).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icon != null) { Icon(icon, null, tint = if (filled) Color.White else c.text, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = if (filled) Color.White else c.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, label: String, password: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth()) {
    val c = LocalColors.current
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, modifier = modifier,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = c.text, unfocusedTextColor = c.text, focusedBorderColor = c.accent, unfocusedBorderColor = c.muted, focusedLabelColor = c.accent, unfocusedLabelColor = c.muted, cursorColor = c.accent))
}

/** Poster / logo image. Rounded, thin border, fade-in; on weak devices images are decoded small to keep scrolling smooth. */
@Composable
fun Poster(url: String, modifier: Modifier = Modifier, contentScaleFit: Boolean = false) {
    val c = LocalColors.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(c.card2).then(if (contentScaleFit) Modifier else Modifier.border(1.dp, c.line.copy(alpha = 0.6f), RoundedCornerShape(10.dp)))) {
        if (url.isNotEmpty()) {
            val req = remember(url) { coil.request.ImageRequest.Builder(ctx).data(url).crossfade(150).size(if (tv.ninekpro.app.data.DeviceInfo.lite) 220 else 420).build() }
            AsyncImage(model = req, contentDescription = null, modifier = Modifier.matchParentSize(),
                contentScale = if (contentScaleFit) androidx.compose.ui.layout.ContentScale.Fit else androidx.compose.ui.layout.ContentScale.Crop)
        }
    }
}

@Composable
fun SectionTitle(text: String) { val c = LocalColors.current; Text(text, color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }

@Composable
fun Banner(text: String, color: Color, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.22f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = c.text, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) { Spacer(Modifier.width(8.dp)); Box(Modifier.clip(RoundedCornerShape(8.dp)).background(color).tvFocus(c.accent, 8).clickable(onClick = onAction).padding(horizontal = 10.dp, vertical = 6.dp)) { Text(action, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) } }
    }
}

fun fmtTime(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60; return if (h > 0) String.format("%d:%02d:%02d", h, m, sec) else String.format("%02d:%02d", m, sec) }

@Composable
fun Gap(h: Int) { Spacer(Modifier.height(h.dp)) }

@Composable
fun Empty(text: String) { val c = LocalColors.current; Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(text, color = c.muted, fontSize = 15.sp) } }

/** Pause playback when the app goes to the background (Android Home button), resume when it comes back. */
@Composable
fun PauseInBackground(onStop: () -> Unit, onStart: () -> Unit = {}) {
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev -> if (ev == androidx.lifecycle.Lifecycle.Event.ON_STOP) onStop() else if (ev == androidx.lifecycle.Lifecycle.Event.ON_START) onStart() }
        owner.lifecycle.addObserver(obs); onDispose { owner.lifecycle.removeObserver(obs) }
    }
}

/** Cast row: round photos + name + role (TMDB). */
@Composable
fun CastRow(people: List<tv.ninekpro.app.data.Person>) {
    val c = LocalColors.current
    if (people.isEmpty()) return
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(people) { p ->
            Column(Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(60.dp).clip(RoundedCornerShape(30.dp)).background(c.card2)) { if (p.photo.isNotEmpty()) AsyncImage(model = p.photo, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(60.dp)) else Text(p.name.take(1), color = c.muted, fontSize = 22.sp, modifier = Modifier.align(Alignment.Center)) }
                Text(p.name, color = c.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                if (p.role.isNotEmpty()) Text(p.role, color = c.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

/** Small facts chip: label above value (Rating · Year · Length · Director …). */
@Composable
fun Fact(label: String, value: String) {
    val c = LocalColors.current
    if (value.isEmpty()) return
    Column(Modifier.clip(RoundedCornerShape(8.dp)).background(c.card2.copy(alpha = 0.8f)).padding(10.dp, 6.dp)) { Text(label, color = c.muted, fontSize = 9.5.sp, letterSpacing = 0.8.sp); Text(value, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

private val logoCache = HashMap<String, androidx.compose.ui.graphics.ImageBitmap?>()
/** Brand logo from the panel (base64 "data:" image or URL); falls back to the bundled 9K Pro TV logo. */
@Composable
fun BrandLogo(logo: String, modifier: Modifier = Modifier, alpha: Float = 1f) {
    if (logo.startsWith("data:")) {
        val bmp = remember(logo) { logoCache.getOrPut(logo) { try { val b = android.util.Base64.decode(logo.substringAfter("base64,"), android.util.Base64.DEFAULT); android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size)?.let { it.asImageBitmap() } } catch (e: Throwable) { null } } }
        if (bmp != null) { androidx.compose.foundation.Image(bmp, contentDescription = null, modifier = modifier, alpha = alpha, contentScale = androidx.compose.ui.layout.ContentScale.Fit); return }
    } else if (logo.isNotEmpty()) { AsyncImage(model = logo, contentDescription = null, modifier = modifier, alpha = alpha, contentScale = androidx.compose.ui.layout.ContentScale.Fit); return }
    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(tv.ninekpro.app.R.drawable.logo_ninekpro), contentDescription = null, modifier = modifier, alpha = alpha, contentScale = androidx.compose.ui.layout.ContentScale.Fit)
}
