package tv.ninekpro.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun qrBitmap(text: String, size: Int = 160): ImageBitmap? = try {
    val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size, mapOf(com.google.zxing.EncodeHintType.MARGIN to 1))
    val img = java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_RGB)
    for (y in 0 until size) for (x in 0 until size) img.setRGB(x, y, if (m.get(x, y)) 0x000000 else 0xFFFFFF)
    img.toComposeImageBitmap()
} catch (e: Throwable) { null }

@Composable
fun LoginScreen() {
    val repo = LocalRepo.current; val nav = LocalNav.current; val c = LocalColors.current; val win = LocalWindow.current
    val brand by repo.brand.collectAsState(); val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf("") }; var pass by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }; var err by remember { mutableStateOf("") }
    val mac = remember { repo.prefs.deviceMac() }; val key = remember { repo.prefs.deviceKey() }
    val lang = LocalLang.current
    fun go() { scope.launch { busy = true; err = ""; val e = repo.login(user.trim(), pass); busy = false
        if (e == null) { nav.reset(Screen.Main); scope.launch { repo.prepareActive() } } else err = when (e) { "wrong_login" -> Strings.get(lang, "error_login"); "expired" -> Strings.get(lang, "error_expired"); "blocked", "device_blocked" -> Strings.get(lang, "error_blocked"); "network" -> Strings.get(lang, "error_network"); else -> e } } }
    // anonymous registration: the seller can attach a playlist to this Device ID from the panel; poll until it arrives
    LaunchedEffect(Unit) { repo.deviceHello(); while (true) { if (repo.prefs.token.isNotEmpty()) { repo.refreshSession(); if (repo.playlists.value.isNotEmpty()) { nav.reset(Screen.Main); scope.launch { repo.prepareActive() }; break } }; delay(20_000) } }

    Row(Modifier.fillMaxSize().padding(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Column(Modifier.width(380.dp).glass(c, 16).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BrandLogo(brand.logo, Modifier.height(64.dp).fillMaxWidth())
            Spacer(Modifier.height(6.dp)); Text(T("login_title"), color = c.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(user, { user = it }, label = { Text(T("username")) }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(c))
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(pass, { pass = it }, label = { Text(T("password")) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), colors = fieldColors(c), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { go() }))
            if (err.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Text(err, color = Color(0xFFFF6B7A), fontSize = 13.sp) }
            Spacer(Modifier.height(16.dp))
            if (busy) Loading() else PBtn(T("sign_in"), primary = true, modifier = Modifier.fillMaxWidth()) { go() }
            Spacer(Modifier.height(14.dp)); Text(T("need_subscription"), color = c.muted, fontSize = 12.sp)
            if (Vlc.error.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Text("Player: " + Vlc.error, color = Color(0xFFFF6B7A), fontSize = 11.sp) }
        }
        Spacer(Modifier.width(24.dp))
        Column(Modifier.width(300.dp).glass(c, 16).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(T("device_id"), color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(mac.uppercase(), color = c.text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp)); Text(T("device_key") + ": " + key, color = c.muted, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            PBtn(T("copy")) { try { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(mac.uppercase() + "  " + key), null); win.toast(Strings.get(lang, "copied")) } catch (e: Throwable) {} }
            Spacer(Modifier.height(10.dp)); Text(T("mac_id_hint"), color = c.muted, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (brand.whatsapp.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                val link = "https://wa.me/" + brand.whatsapp.filter { it.isDigit() } + "?text=" + java.net.URLEncoder.encode("9K Pro TV PC: " + mac.uppercase() + " key " + key, "UTF-8")
                val qr = remember(link) { qrBitmap(link) }
                if (qr != null) Image(qr, null, Modifier.size(150.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).padding(6.dp))
                Spacer(Modifier.height(6.dp)); Text(T("scan_whatsapp"), color = c.muted, fontSize = 11.5.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(8.dp)); PBtn(T("whatsapp_support")) { try { java.awt.Desktop.getDesktop().browse(java.net.URI(link)) } catch (e: Throwable) {} }
            }
        }
    }
}

@Composable
fun fieldColors(c: SmileColors) = OutlinedTextFieldDefaults.colors(focusedTextColor = c.text, unfocusedTextColor = c.text, focusedBorderColor = c.accent, unfocusedBorderColor = c.line, focusedLabelColor = c.accent, unfocusedLabelColor = c.muted, cursorColor = c.accent)
