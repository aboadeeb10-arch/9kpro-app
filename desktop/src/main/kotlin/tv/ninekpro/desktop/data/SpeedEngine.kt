// Speed test engine (desktop 1.0.8)
package tv.ninekpro.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Speed test in three steps, each independent (one failing never hides the others):
 *  1. server answer  — 4 quick requests to the IPTV host, best = ping, spread = jitter
 *  2. internet speed — 4 parallel downloads from a public speed server for ~7 s (real line speed, not the channel bitrate)
 *  3. server stream  — pulls a live channel for ~5 s; tells whether the server actually delivers video and how fast
 * Progress is pushed through [onUpdate] several times a second so the gauge moves while testing.
 */
object SpeedEngine {
    const val IDLE = 0; const val PING = 1; const val DOWN = 2; const val STREAM = 3; const val DONE = 4

    data class Result(val phase: Int = IDLE, val ping: Long = -1, val jitter: Long = -1, val down: Double = -1.0, val stream: Double = -1.0,
                      val serverUp: Boolean? = null, val streamOk: Boolean? = null, val downError: Boolean = false, val progress: Float = 0f)

    private val DOWN_URLS = listOf(
        "https://speed.cloudflare.com/__down?bytes=250000000",
        "https://proof.ovh.net/files/100Mb.dat",
        "http://ipv4.download.thinkbroadband.com/100MB.zip")

    private val client = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).retryOnConnectionFailure(true).build()
    private const val UA = "9KProTV/1.0"

    suspend fun run(pingUrl: String, streamUrls: List<String>, onUpdate: (Result) -> Unit): Result = withContext(Dispatchers.IO) {
        var r = Result(phase = PING); onUpdate(r)
        // ---- 1. server answer
        val times = ArrayList<Long>()
        repeat(4) { if (currentCoroutineContext().isActive) { val t = timeOne(pingUrl); if (t >= 0) times.add(t); r = r.copy(ping = times.minOrNull() ?: -1, progress = (it + 1) / 4f * 0.15f); onUpdate(r) } }
        r = r.copy(ping = times.minOrNull() ?: -1, jitter = if (times.size >= 2) times.max() - times.min() else -1, serverUp = times.isNotEmpty())
        // ---- 2. internet speed
        r = r.copy(phase = DOWN, progress = 0.15f); onUpdate(r)
        var got = false
        for (u in DOWN_URLS) { if (!currentCoroutineContext().isActive) break
            val ok = download(u, 4, 7000L) { mbps, frac -> r = r.copy(down = mbps, progress = 0.15f + 0.6f * frac); onUpdate(r) }
            if (ok) { got = true; break } }
        if (!got) r = r.copy(down = -1.0, downError = true)
        // ---- 3. server stream
        r = r.copy(phase = STREAM, progress = 0.75f); onUpdate(r)
        var sOk = false
        for (u in streamUrls.take(3)) { if (!currentCoroutineContext().isActive) break
            val ok = download(u, 1, 5000L, warm = 500L) { mbps, frac -> r = r.copy(stream = mbps, progress = 0.75f + 0.25f * frac); onUpdate(r) }
            if (ok && r.stream > 0.2) { sOk = true; break } }
        r = r.copy(phase = DONE, streamOk = sOk, progress = 1f); onUpdate(r); r
    }

    private fun timeOne(url: String): Long = try {
        val t0 = System.nanoTime()
        client.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { it.code }
        (System.nanoTime() - t0) / 1_000_000
    } catch (e: Exception) { -1 }

    /** Pulls [url] over [streams] parallel connections for [ms]; rate is measured after a [warm]-up so TCP slow-start doesn't drag it down. Returns false when nothing arrived. */
    private suspend fun download(url: String, streams: Int, ms: Long, warm: Long = 1000L, onRate: (Double, Float) -> Unit): Boolean = coroutineScope {
        val total = AtomicLong(0); val start = System.currentTimeMillis(); var warmBytes = 0L; var warmAt = 0L
        val jobs = (1..streams).map { async(Dispatchers.IO) {
            try { client.newCall(Request.Builder().url(url).header("User-Agent", UA).header("Cache-Control", "no-cache").build()).execute().use { resp ->
                if (!resp.isSuccessful) return@async false
                val ins = resp.body?.byteStream() ?: return@async false; val buf = ByteArray(128 * 1024)
                while (currentCoroutineContext().isActive && System.currentTimeMillis() - start < ms) { val n = ins.read(buf); if (n < 0) break; total.addAndGet(n.toLong()) }
                true } } catch (e: Exception) { total.get() > 0 } } }
        val ticker = async(Dispatchers.IO) {
            while (currentCoroutineContext().isActive && System.currentTimeMillis() - start < ms) {
                kotlinx.coroutines.delay(200); val now = System.currentTimeMillis(); val el = now - start
                if (el >= warm) { if (warmAt == 0L) { warmAt = now; warmBytes = total.get() } else { val secs = (now - warmAt) / 1000.0; if (secs > 0.3) onRate((total.get() - warmBytes) * 8.0 / 1e6 / secs, (el.toFloat() / ms).coerceIn(0f, 1f)) } }
                else onRate(total.get() * 8.0 / 1e6 / (el / 1000.0).coerceAtLeast(0.05), (el.toFloat() / ms).coerceIn(0f, 1f))
                if (jobs.all { it.isCompleted } && total.get() == 0L) break
            } }
        val any = jobs.map { it.await() }.any { it }; ticker.cancel()
        val now = System.currentTimeMillis(); val secs = if (warmAt > 0) (now - warmAt) / 1000.0 else (now - start) / 1000.0; val bytes = if (warmAt > 0) total.get() - warmBytes else total.get()
        if (total.get() > 0) onRate(if (secs > 0.3) bytes * 8.0 / 1e6 / secs else total.get() * 8.0 / 1e6 / ((now - start) / 1000.0).coerceAtLeast(0.05), 1f)
        any && total.get() > 0
    }
}
