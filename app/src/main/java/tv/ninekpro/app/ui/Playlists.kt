package tv.ninekpro.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import tv.ninekpro.app.BuildConfig
import tv.ninekpro.app.R
import tv.ninekpro.app.data.DeviceInfo
import tv.ninekpro.app.data.Playlist

/**
 * Login / playlists screen. Simple: logo, username + password, MAC + device key under it.
 * TV: a WhatsApp QR code on the right (number from the panel). Phone: a "Chat on WhatsApp" button, and the screen follows the phone's rotation.
 * Playlists already on the device are listed under the form with "Add playlist".
 */
@Composable
fun PlaylistScreen() {
    val repo = LocalRepo.current; val c = LocalColors.current
    val brand by repo.brand.collectAsState(); val playlists by repo.playlists.collectAsState()
    val nav = LocalNav.current; val scope = rememberCoroutineScope()
    var preparing by remember { mutableStateOf(false) }
    val prog by repo.crashFixProgress.collectAsState()
    val go: () -> Unit = { if (!preparing) { preparing = true; scope.launch { repo.prepareActive(); preparing = false; nav.reset(Screen.Main(0)) } } }
    if (preparing) androidx.compose.material3.AlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text(stringResource(R.string.loading)) },
        text = { Row(verticalAlignment = Alignment.CenterVertically) { androidx.compose.material3.CircularProgressIndicator(color = c.accent, modifier = Modifier.size(28.dp)); Spacer(Modifier.width(14.dp)); Text(stringResource(R.string.loading_playlist) + (if (prog > 1) "  " + prog + "/12" else ""), fontSize = 14.sp) } })
    Box(Modifier.fillMaxSize()) {
        var showForm by remember { mutableStateOf(playlists.isEmpty()) }
        Row(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            // left: logo · login form · MAC
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Column(Modifier.widthIn(max = 400.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    BrandLogo(brand.logo, Modifier.fillMaxWidth(0.55f).height(64.dp))
                    Gap(10)
                    if (showForm || playlists.isEmpty()) LoginForm(onDone = { go() })
                    else BigButton(stringResource(R.string.add_playlist), Icons.Default.Add, filled = false, modifier = Modifier.fillMaxWidth()) { showForm = true }
                    Gap(10)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).glass(c, 10).padding(12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(stringResource(R.string.mac_address), color = c.muted, fontSize = 10.5.sp); Text(repo.prefs.deviceMac(), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                        Column(horizontalAlignment = Alignment.End) { Text(stringResource(R.string.device_key), color = c.muted, fontSize = 10.5.sp); Text(repo.prefs.deviceKey(), color = c.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
            // right: playlists already on the device
            if (playlists.isNotEmpty()) { Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f).fillMaxHeight().padding(vertical = 8.dp)) {
                    SectionTitle(stringResource(R.string.playlists).uppercase())
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f)) {
                        items(playlists, key = { it.id }) { pl ->
                            val active = pl.id == repo.prefs.activePlaylistId
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).glass(c, 10, active).tvFocus(c.accent, 10).clickable { repo.prefs.activePlaylistId = pl.id; go() }.padding(12.dp, 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text((if (pl.fromPanel && pl.user.isNotEmpty() && !pl.protect) pl.user + " · " else "") + pl.name + (if (pl.trial) " ⚡" else ""), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (active) stringResource(R.string.connected) else if (pl.fromPanel) stringResource(R.string.protected_pl) else pl.kind.uppercase(), color = if (active) c.accent else c.muted, fontSize = 11.sp)
                                }
                                if (!pl.fromPanel) IconButton(onClick = { repo.removeManualPlaylist(pl.id) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Delete, null, tint = c.muted, modifier = Modifier.size(15.dp)) }
                                Text("›", color = c.muted, fontSize = 20.sp)
                            }
                        }
                    }
                }
            }
        }
        Text("v" + BuildConfig.VERSION_NAME, color = c.muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp))
    }
}

/** Username + password (or a 6-digit code). The provider can also push the playlist to this MAC from the panel — no typing then. */
@Composable
fun LoginForm(onDone: () -> Unit) {
    val repo = LocalRepo.current; val c = LocalColors.current
    var useCode by remember { mutableStateOf(false) }
    var user by remember { mutableStateOf("") }; var pass by remember { mutableStateOf("") }; var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }; var err by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val errText = when (err) { "" -> ""; "wrong_login" -> stringResource(R.string.error_login); "blocked", "device_blocked" -> stringResource(R.string.error_blocked); "expired" -> stringResource(R.string.error_expired); "network" -> stringResource(R.string.error_network); else -> err }
    Column(Modifier.fillMaxWidth()) {
        if (!useCode) { Field(user, { user = it }, stringResource(R.string.username)); Gap(8); Field(pass, { pass = it }, stringResource(R.string.password), password = true) }
        else Field(code, { code = it.filter { ch -> ch.isDigit() }.take(6) }, stringResource(R.string.code))
        Gap(12)
        BigButton(if (busy) stringResource(R.string.loading) else stringResource(R.string.sign_in), modifier = Modifier.fillMaxWidth()) {
            if (busy) return@BigButton
            if (!useCode && (user.isBlank() || pass.isBlank())) return@BigButton
            if (useCode && code.length < 6) return@BigButton
            busy = true; err = ""; scope.launch { val e = if (useCode) repo.codeLogin(code) else repo.login(user.trim(), pass); busy = false; if (e == null) onDone() else err = e }
        }
        if (errText.isNotEmpty()) { Gap(8); Text(errText, color = Color(0xFFFF6B7A), fontSize = 13.sp) }
        Gap(8)
        Text(if (useCode) stringResource(R.string.use_password) else stringResource(R.string.use_code), color = c.accent, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(6.dp)).tvFocus(c.accent, 6).clickable { useCode = !useCode; err = "" }.padding(6.dp, 4.dp))
    }
}

/** Right side: WhatsApp support. TV = QR code to scan with the phone; phone = button that opens the chat. */
@Composable
private fun SupportCard(whatsapp: String, brandName: String) {
    val c = LocalColors.current; val act = LocalActivity.current
    val number = whatsapp.filter { it.isDigit() }
    val url = "https://wa.me/" + number
    Column(Modifier.widthIn(max = 340.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).glass(c, 16).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.whatsapp_support), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Gap(10)
        if (number.isEmpty()) Text(stringResource(R.string.no_whatsapp), color = c.muted, fontSize = 12.sp, textAlign = TextAlign.Center)
        else if (DeviceInfo.isTv) {
            val qr = remember(url) { qrBitmap(url, 640) }
            if (qr != null) Box(Modifier.size(200.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).padding(10.dp)) { Image(qr.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize()) }
            Gap(10); Text(stringResource(R.string.scan_whatsapp), color = c.text, fontSize = 13.sp, textAlign = TextAlign.Center)
            Gap(4); Text("+" + number, color = c.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        } else {
            BigButton(stringResource(R.string.chat_whatsapp), modifier = Modifier.fillMaxWidth()) { try { act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) {} }
            Gap(6); Text("+" + number, color = c.muted, fontSize = 13.sp)
        }
    }
}

/** WhatsApp as a scannable QR (for TV, where a tap can't open WhatsApp). Phone opens the chat directly and never sees this. */
@Composable
fun WaQrDialog(number: String, text: String, onClose: () -> Unit) {
    val c = LocalColors.current
    val url = "https://wa.me/" + number.filter { it.isDigit() } + (if (text.isNotEmpty()) "?text=" + Uri.encode(text) else "")
    val qr = remember(url) { qrBitmap(url, 640) }
    androidx.compose.material3.AlertDialog(onDismissRequest = onClose, confirmButton = { BigButton(stringResource(R.string.ok)) { onClose() } },
        title = { Text(stringResource(R.string.whatsapp_support)) },
        text = { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (qr != null) Box(Modifier.size(220.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).padding(10.dp)) { Image(qr.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize()) }
            Gap(10); Text(stringResource(R.string.scan_wa_renew), color = c.text, fontSize = 13.sp, textAlign = TextAlign.Center)
            Gap(4); Text("+" + number.filter { it.isDigit() }, color = c.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold) } })
}

/** QR code as a bitmap (ZXing). */
fun qrBitmap(text: String, size: Int): android.graphics.Bitmap? = try {
    val hints = mapOf(com.google.zxing.EncodeHintType.MARGIN to 0)
    val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size, hints)
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.RGB_565)
    for (x in 0 until size) for (y in 0 until size) bmp.setPixel(x, y, if (m[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    bmp
} catch (e: Throwable) { null }

/** "Add playlist" from Settings: same simple form in a dialog. */
@Composable
fun AddPlaylistDialog(onClose: () -> Unit) {
    val nav = LocalNav.current; val c = LocalColors.current
    Box(Modifier.fillMaxSize().background(Color(0xAA000000)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
        Column(Modifier.width(420.dp).clip(RoundedCornerShape(14.dp)).glass(c, 14).clickable(enabled = false) {}.padding(18.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.add_playlist), color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold); Gap(12)
            LoginForm(onDone = { onClose(); nav.reset(Screen.Main(0)) })
            Gap(10); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Pill(stringResource(R.string.cancel), false, onClose) }
        }
    }
}
