package tv.ninekpro.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tv.ninekpro.desktop.data.Kind
import tv.ninekpro.desktop.data.SpeedEngine

@Composable private fun Gap(h: Int) { Spacer(Modifier.height(h.dp)) }
private val GOOD = Color(0xFF22C55E); private val WARN = Color(0xFFF59E0B); private val BAD = Color(0xFFFF6B7A)
private fun speedColor(mbps: Double, accent: Color) = when { mbps < 0 -> accent; mbps < 3 -> BAD; mbps < 8 -> WARN; else -> GOOD }
private fun fmt1(v: Double) = if (v >= 100) String.format(java.util.Locale.US, "%.0f", v) else String.format(java.util.Locale.US, "%.1f", v)

/** Speedometer: 240° arc, 0–100 Mbps linear then compressed to 200; animated needle + colour by verdict. */
@Composable
fun SpeedGauge(mbps: Double, color: Color, size: Int = 200, label: String, sub: String) {
    val c = LocalColors.current
    val target = if (mbps < 0) 0f else if (mbps <= 100) (mbps / 100.0 * 0.8).toFloat() else (0.8 + (mbps - 100).coerceAtMost(100.0) / 100.0 * 0.2).toFloat()
    val frac by animateFloatAsState(target, tween(450), label = "gauge")
    val track = c.line.copy(alpha = 0.9f); val muted = c.muted; val textCol = c.text
    Box(Modifier.size(size.dp, (size * 0.78).dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size.dp)) {
            val stroke = size.toFloat() * 0.055f; val pad = stroke
            val arc = Size(this.size.width - pad * 2, this.size.width - pad * 2); val tl = Offset(pad, pad)
            drawArc(track, 150f, 240f, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            if (frac > 0f) drawArc(Brush.sweepGradient(0f to color.copy(alpha = 0.55f), 0.55f to color, 1f to color, center = Offset(this.size.width / 2, this.size.width / 2)), 150f, 240f * frac, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            // ticks 0 · 25 · 50 · 75 · 100 · 200
            val cx = this.size.width / 2; val cy = this.size.width / 2; val r = arc.width / 2
            listOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f).forEach { f -> val a = Math.toRadians((150f + 240f * f).toDouble()); val p1 = Offset(cx + (r - stroke * 1.1f) * Math.cos(a).toFloat(), cy + (r - stroke * 1.1f) * Math.sin(a).toFloat()); val p2 = Offset(cx + (r - stroke * 1.8f) * Math.cos(a).toFloat(), cy + (r - stroke * 1.8f) * Math.sin(a).toFloat()); drawLine(muted.copy(alpha = 0.6f), p1, p2, stroke * 0.25f) }
            // needle
            val a = Math.toRadians((150f + 240f * frac).toDouble()); val nr = r - stroke * 2.2f
            drawLine(textCol, Offset(cx, cy), Offset(cx + nr * Math.cos(a).toFloat(), cy + nr * Math.sin(a).toFloat()), stroke * 0.35f, StrokeCap.Round)
            drawCircle(color, stroke * 0.7f, Offset(cx, cy)); drawCircle(textCol, stroke * 0.3f, Offset(cx, cy))
        }
        Column(Modifier.padding(top = (size * 0.32).dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (mbps < 0) "—" else fmt1(mbps), color = c.text, fontSize = (size * 0.17).sp, fontWeight = FontWeight.ExtraBold)
            Text(label, color = c.muted, fontSize = (size * 0.06).sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            if (sub.isNotEmpty()) Text(sub, color = color, fontSize = (size * 0.055).sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StatCard(icon: ImageVector, title: String, value: String, sub: String, color: Color, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Column(modifier.clip(RoundedCornerShape(12.dp)).glass(c, 12).padding(12.dp, 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = color, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(title, color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp) }
        Spacer(Modifier.height(4.dp))
        Text(value, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        if (sub.isNotEmpty()) Text(sub, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Step(n: Int, text: String, phase: Int) {
    val c = LocalColors.current
    val done = phase > n; val active = phase == n
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(Modifier.size(18.dp).clip(RoundedCornerShape(9.dp)).background(if (done) GOOD else if (active) c.accent else c.line), contentAlignment = Alignment.Center) { Text(if (done) "✓" else n.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(8.dp)); Text(text, color = if (active) c.text else c.muted, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
    }
}

/** Settings → Speed test (desktop). Gauge + three stat cards + plain verdict; runs the 3-step [SpeedEngine] and cancels when you leave. */
@Composable
fun SpeedTestScreen() {
    Column(Modifier.fillMaxSize()) { BackRow(T("speed_test")); Box(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) { SpeedTestPanel() } }
}

@Composable
fun SpeedTestPanel() {
    val repo = LocalRepo.current; val c = LocalColors.current; val scope = rememberCoroutineScope()
    var r by remember { mutableStateOf(SpeedEngine.Result()) }
    var host by remember { mutableStateOf("") }; var err by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    val running = r.phase in SpeedEngine.PING..SpeedEngine.STREAM
    fun start() { if (running) return; err = ""; r = SpeedEngine.Result(phase = SpeedEngine.PING)
        job = scope.launch {
            try { val pl = repo.activePlaylist ?: throw Exception("no playlist"); val h = repo.host(pl); host = h.replace(Regex("^https?://"), "").trimEnd('/')
                val urls = ArrayList<String>(); try { for (it in repo.content(pl, Kind.LIVE).items.take(3)) urls.add(repo.streamUrl(pl, it, ts = true)) } catch (e: Throwable) {}
                SpeedEngine.run(repo.xtream.baseUrl(h) + "/player_api.php", urls) { r = it }
            } catch (e: Throwable) { if (e !is kotlinx.coroutines.CancellationException) { err = "✕ " + (e.message ?: "error"); r = r.copy(phase = SpeedEngine.DONE) } }
        } }
    val gaugeColor = if (r.down < 0 && r.phase < SpeedEngine.DOWN) c.accent else speedColor(r.down, c.accent)
    val verdict = when { r.phase != SpeedEngine.DONE -> ""; r.down < 0 -> T("speed_no_internet"); r.down < 3 -> T("speed_verdict_bad"); r.down < 8 -> T("speed_verdict_sd"); r.down < 25 -> T("speed_verdict_hd"); else -> T("speed_verdict_4k") }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SpeedGauge(r.down, gaugeColor, 210, "Mbps", when (r.phase) { SpeedEngine.PING -> T("speed_step_ping"); SpeedEngine.DOWN -> T("speed_step_down"); SpeedEngine.STREAM -> T("speed_step_stream"); else -> "" })
            Gap(4)
            SmallBtn(Icons.Default.Speed, if (r.phase == SpeedEngine.DONE) T("speed_again") else T("speed_start"), Modifier.width(210.dp), accent = !running) { start() }
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(T("speed_test").uppercase(), color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            if (host.isNotEmpty()) Text(host, color = c.muted, fontSize = 11.sp)
            Gap(8)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(Icons.Default.Timer, T("speed_ping"), if (r.ping < 0) (if (r.serverUp == false) "✕" else "—") else r.ping.toString() + " ms",
                    when { r.serverUp == false -> T("speed_fail"); r.jitter > 0 -> T("speed_jitter") + " " + r.jitter + " ms"; else -> "" }, when { r.serverUp == false -> BAD; r.ping < 0 -> c.accent; r.ping < 150 -> GOOD; r.ping < 400 -> WARN; else -> BAD }, Modifier.weight(1f))
                StatCard(Icons.Default.Cloud, T("speed_internet"), if (r.down < 0) (if (r.downError) "✕" else "—") else fmt1(r.down) + " Mbps",
                    if (r.downError) T("speed_no_internet") else "", if (r.downError) BAD else speedColor(r.down, c.accent), Modifier.weight(1f))
                StatCard(Icons.Default.Dns, T("speed_server"), if (r.stream < 0) (if (r.streamOk == false) "✕" else "—") else fmt1(r.stream) + " Mbps",
                    when (r.streamOk) { true -> T("speed_server_ok"); false -> T("speed_server_bad"); null -> "" }, when (r.streamOk) { true -> GOOD; false -> BAD; null -> c.accent }, Modifier.weight(1f))
            }
            Gap(10)
            if (verdict.isNotEmpty()) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(gaugeColor.copy(alpha = 0.16f)).padding(12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(gaugeColor)); Spacer(Modifier.width(8.dp)); Text(verdict, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            if (err.isNotEmpty()) Text(err, color = BAD, fontSize = 13.sp)
            if (r.phase == SpeedEngine.IDLE) Text(T("speed_hint"), color = c.muted, fontSize = 12.sp)
            else { Gap(6); Step(1, T("speed_step_ping"), r.phase); Step(2, T("speed_step_down"), r.phase); Step(3, T("speed_step_stream"), r.phase) }
        }
    }
}
