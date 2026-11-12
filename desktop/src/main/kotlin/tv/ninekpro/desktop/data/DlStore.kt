package tv.ninekpro.desktop.data

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread

/**
 * Downloads (movies / episodes) and live recordings on this PC — same idea as the Android app:
 * files are AES-CTR encrypted with a per-install key (they cannot be copied to another player), stored in <app-data>/downloads/<id>.stv,
 * and played back through a tiny localhost HTTP server that decrypts on the fly and supports Range requests (seeking works).
 */
object DlStore {
    data class Entry(val id: String, val title: String, val kind: String, val image: String, val size: Long, val total: Long, val done: Boolean, val created: Long, val extra: String, val mime: String, val error: String = "") {
        fun toJson(): JSONObject = JSONObject().put("id", id).put("title", title).put("kind", kind).put("image", image).put("size", size).put("total", total).put("done", done).put("t", created).put("extra", extra).put("mime", mime).put("error", error)
        companion object { fun fromJson(o: JSONObject) = Entry(o.optString("id"), o.optString("title"), o.optString("kind", "movie"), o.optString("image"), o.optLong("size"), o.optLong("total"), o.optBoolean("done"), o.optLong("t"), o.optString("extra"), o.optString("mime", "video/mp4"), o.optString("error")) }
    }

    val list = MutableStateFlow<List<Entry>>(emptyList())
    /** id → 0..1 while downloading; recordings report elapsed seconds as a negative number (-secs). */
    val progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val dir: File by lazy { File(AppDirs.data, "downloads").apply { mkdirs() } }
    private val index: File by lazy { File(dir, "index.json") }
    private lateinit var key: ByteArray
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val stopFlags = HashMap<String, Boolean>()
    private var server: HttpServer? = null
    private var port = 0
    private val lock = Any()

    fun init(prefs: Prefs) {
        var k = prefs.dlKey
        if (k.length != 64) { val b = ByteArray(32); SecureRandom().nextBytes(b); k = b.joinToString("") { "%02x".format(it) }; prefs.dlKey = k }
        key = k.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        load()
        // files of downloads that were interrupted by a crash / exit: keep the row, mark it failed so the user can delete or retry
        list.value = list.value.map { if (!it.done && it.error.isEmpty()) it.copy(error = "interrupted") else it }; save()
        thread(isDaemon = true) { startServer() }
    }

    private fun load() { try { val a = JSONArray(index.readText()); list.value = (0 until a.length()).map { Entry.fromJson(a.getJSONObject(it)) } } catch (e: Exception) { list.value = emptyList() } }
    private fun save() { synchronized(lock) { try { val t = File(index.path + ".tmp"); t.writeText(JSONArray(list.value.map { it.toJson() }).toString()); java.nio.file.Files.move(t.toPath(), index.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING) } catch (e: Exception) {} } }
    private fun update(id: String, f: (Entry) -> Entry) { synchronized(lock) { list.value = list.value.map { if (it.id == id) f(it) else it } }; save() }
    private fun setProgress(id: String, v: Float?) { synchronized(lock) { val m = HashMap(progress.value); if (v == null) m.remove(id) else m[id] = v; progress.value = m } }

    fun file(id: String) = File(dir, "$id.stv")
    private fun iv(id: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(("iv:" + id).toByteArray()).copyOf(16)
    private fun cipher(id: String, mode: Int, blockOffset: Long = 0L): Cipher {
        val base = BigInteger(1, iv(id)).add(BigInteger.valueOf(blockOffset))
        var ivb = base.toByteArray(); if (ivb.size > 16) ivb = ivb.copyOfRange(ivb.size - 16, ivb.size) else if (ivb.size < 16) ivb = ByteArray(16 - ivb.size) + ivb
        return Cipher.getInstance("AES/CTR/NoPadding").apply { init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(ivb)) }
    }
    fun has(extra: String): Entry? = list.value.firstOrNull { it.extra == extra && it.done }
    fun active(): List<Entry> = list.value.filter { !it.done && it.error.isEmpty() }
    fun recordingOf(itemKey: String): Entry? = list.value.firstOrNull { it.kind == "rec" && !it.done && it.error.isEmpty() && it.extra == itemKey }
    fun usedBytes(): Long = list.value.sumOf { it.size }

    /** Movie / episode: whole-file download. `extra` = the same item/episode JSON the resume list uses (lets the UI show "downloaded" on the title). */
    fun download(url: String, title: String, kind: String, image: String, extra: String): String {
        val id = "d" + System.currentTimeMillis().toString(36) + (1000..9999).random()
        val mime = if (url.substringAfterLast('.', "").lowercase().startsWith("mkv")) "video/x-matroska" else "video/mp4"
        synchronized(lock) { list.value = list.value + Entry(id, title, kind, image, 0, 0, false, System.currentTimeMillis(), extra, mime) }; save(); setProgress(id, 0f)
        thread(isDaemon = true, name = "dl-$id") { transfer(id, url, 0L) }
        return id
    }
    /** Live channel: copies the raw MPEG-TS stream for `minutes` (or until stopRecording). `extra` = channel key. */
    fun record(url: String, title: String, image: String, extra: String, minutes: Int): String {
        val id = "r" + System.currentTimeMillis().toString(36) + (1000..9999).random()
        synchronized(lock) { list.value = list.value + Entry(id, title, "rec", image, 0, 0, false, System.currentTimeMillis(), extra, "video/mp2t") }; save(); setProgress(id, -0f)
        thread(isDaemon = true, name = "rec-$id") { transfer(id, url, minutes * 60_000L) }
        return id
    }
    fun stopRecording(id: String) { synchronized(lock) { stopFlags[id] = true } }
    fun delete(id: String) { synchronized(lock) { stopFlags[id] = true; list.value = list.value.filter { it.id != id } }; save(); setProgress(id, null); try { file(id).delete() } catch (e: Exception) {} }

    private fun transfer(id: String, url: String, maxMs: Long) {
        val f = file(id); var written = 0L; val startedAt = System.currentTimeMillis()
        try {
            client.newCall(Request.Builder().url(url).header("User-Agent", "9KProTV/1.0").build()).execute().use { r ->
                if (!r.isSuccessful) throw IllegalStateException("HTTP " + r.code)
                val total = if (maxMs > 0) 0L else (r.body?.contentLength() ?: -1L).coerceAtLeast(0L)
                update(id) { it.copy(total = total) }
                val out = CipherOutputStream(f.outputStream().buffered(1 shl 16), cipher(id, Cipher.ENCRYPT_MODE))
                val buf = ByteArray(1 shl 16); var lastUi = 0L
                r.body!!.byteStream().use { ins ->
                    while (true) {
                        if (synchronized(lock) { stopFlags[id] == true }) break
                        if (maxMs > 0 && System.currentTimeMillis() - startedAt > maxMs) break
                        val n = ins.read(buf); if (n < 0) break
                        out.write(buf, 0, n); written += n
                        val now = System.currentTimeMillis()
                        if (now - lastUi > 700) { lastUi = now; setProgress(id, if (maxMs > 0) -((now - startedAt) / 1000f) else if (total > 0) (written.toFloat() / total).coerceIn(0f, 0.999f) else 0f); update(id) { it.copy(size = written) } }
                    }
                }
                out.close()
            }
            val gone = synchronized(lock) { list.value.none { it.id == id } }
            if (gone) { f.delete(); return }
            if (written < 200_000L) throw IllegalStateException("empty")
            update(id) { it.copy(size = written, total = if (it.total > 0) it.total else written, done = true) }
        } catch (e: Throwable) {
            val gone = synchronized(lock) { list.value.none { it.id == id } }
            if (!gone) update(id) { it.copy(size = written, error = (e.message ?: "failed").take(80)) }
        } finally { setProgress(id, null); synchronized(lock) { stopFlags.remove(id) } }
    }

    /** URL the player opens: a Range-capable loopback stream that decrypts the file on the fly. */
    fun playUrl(id: String): String { if (server == null) startServer(); return "http://127.0.0.1:$port/f/$id" }

    @Synchronized private fun startServer() {
        if (server != null) return
        try {
            val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            s.executor = Executors.newCachedThreadPool { r -> Thread(r, "dl-http").apply { isDaemon = true } }
            s.createContext("/f") { ex ->
                try {
                    val id = ex.requestURI.path.substringAfterLast('/').filter { it.isLetterOrDigit() }
                    val e = list.value.firstOrNull { it.id == id }; val f = file(id)
                    if (e == null || !f.exists()) { ex.sendResponseHeaders(404, -1); ex.close(); return@createContext }
                    val len = f.length(); var from = 0L; var to = len - 1
                    val range = ex.requestHeaders.getFirst("Range")
                    if (range != null && range.startsWith("bytes=")) { val p = range.removePrefix("bytes=").split("-"); from = p[0].toLongOrNull() ?: 0L; to = p.getOrNull(1)?.toLongOrNull() ?: (len - 1); if (to >= len) to = len - 1 }
                    if (from < 0 || from > to) { ex.sendResponseHeaders(416, -1); ex.close(); return@createContext }
                    val h = ex.responseHeaders; h.set("Content-Type", e.mime); h.set("Accept-Ranges", "bytes"); h.set("Cache-Control", "no-store")
                    if (range != null) h.set("Content-Range", "bytes $from-$to/$len")
                    ex.sendResponseHeaders(if (range != null) 206 else 200, to - from + 1)
                    val blockStart = (from / 16) * 16; val skip = (from - blockStart).toInt()
                    val c = cipher(id, Cipher.DECRYPT_MODE, from / 16)
                    RandomAccessFile(f, "r").use { raf ->
                        raf.seek(blockStart); var remaining = to - blockStart + 1; var first = true
                        val buf = ByteArray(1 shl 16); val os = ex.responseBody
                        while (remaining > 0) {
                            val n = raf.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt()); if (n <= 0) break
                            val dec = c.update(buf, 0, n) ?: ByteArray(0)
                            if (first) { first = false; if (dec.size > skip) os.write(dec, skip, dec.size - skip) } else os.write(dec)
                            remaining -= n
                        }
                        os.flush()
                    }
                } catch (e: Throwable) { } finally { try { ex.close() } catch (e: Throwable) {} }
            }
            s.start(); server = s; port = s.address.port
        } catch (e: Throwable) { }
    }
}
