package tv.ninekpro.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypted downloads + recordings. Files are AES-128-CTR scrambled on disk (unplayable outside 9K Pro TV);
 * the key lives in the app's private prefs. CTR = any position can be decrypted directly → seeking works.
 * The index (dl_index.json) lists every item: movie/episode downloads and live recordings.
 */
object DlStore {
    const val STATUS_RUNNING = 1; const val STATUS_DONE = 2; const val STATUS_FAILED = 3; const val STATUS_PAUSED = 4
    data class Row(val id: Long, val title: String, val kind: String, val status: Int, val bytes: Long, val total: Long, val path: String, val modified: Long, val url: String, val error: String = "", val durationMin: Int = 0) {
        val pct: Int get() = if (total > 0) (bytes * 100 / total).toInt().coerceIn(0, 100) else 0
        val isRecording get() = kind == "rec"
        val uri get() = "stv://" + id
    }

    private fun dir(ctx: Context) = File(ctx.filesDir, "dl").apply { mkdirs() }
    private fun indexFile(ctx: Context) = File(ctx.filesDir, "dl_index.json")
    private val lock = Any()

    fun key(ctx: Context): ByteArray {
        val sp = ctx.getSharedPreferences("ninekpro", Context.MODE_PRIVATE)
        var k = sp.getString("dl_key", "") ?: ""
        if (k.length != 32) { val b = ByteArray(16); SecureRandom().nextBytes(b); k = b.joinToString("") { String.format("%02x", it) }; sp.edit().putString("dl_key", k).apply() }
        return k.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    /** AES-CTR cipher positioned at byte offset `pos` of the stream (counter block = pos / 16, then skip pos % 16 bytes). */
    fun cipherAt(ctx: Context, id: Long, pos: Long): Cipher {
        val iv = ByteArray(16); val base = id xor 0x5A11E7A5B0C4D2E1L
        for (i in 0 until 8) iv[i] = (base shr (56 - i * 8)).toByte()
        val block = pos / 16
        for (i in 0 until 8) iv[8 + i] = (block shr (56 - i * 8)).toByte()
        val c = Cipher.getInstance("AES/CTR/NoPadding"); c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key(ctx), "AES"), IvParameterSpec(iv))
        val skip = (pos % 16).toInt(); if (skip > 0) c.update(ByteArray(skip))
        return c
    }

    fun list(ctx: Context): List<Row> = synchronized(lock) { try { val a = JSONArray(indexFile(ctx).takeIf { it.exists() }?.readText() ?: "[]"); (0 until a.length()).map { fromJson(a.getJSONObject(it)) }.sortedByDescending { it.modified } } catch (e: Exception) { emptyList() } }
    fun get(ctx: Context, id: Long): Row? = list(ctx).firstOrNull { it.id == id }
    fun file(ctx: Context, id: Long) = File(dir(ctx), "$id.stv")
    fun put(ctx: Context, r: Row) = synchronized(lock) { val l = list(ctx).filter { it.id != r.id } + r; indexFile(ctx).writeText(JSONArray(l.map { toJson(it) }).toString()) }
    fun update(ctx: Context, id: Long, f: (Row) -> Row) { val r = get(ctx, id) ?: return; put(ctx, f(r)) }
    fun remove(ctx: Context, id: Long) = synchronized(lock) { try { file(ctx, id).delete() } catch (e: Exception) {}; val l = list(ctx).filter { it.id != id }; indexFile(ctx).writeText(JSONArray(l.map { toJson(it) }).toString()) }
    fun newId() = System.currentTimeMillis()

    private fun toJson(r: Row) = JSONObject().put("id", r.id).put("title", r.title).put("kind", r.kind).put("status", r.status).put("bytes", r.bytes).put("total", r.total).put("path", r.path).put("modified", r.modified).put("url", r.url).put("error", r.error).put("durationMin", r.durationMin)
    private fun fromJson(o: JSONObject) = Row(o.optLong("id"), o.optString("title"), o.optString("kind", "movie"), o.optInt("status"), o.optLong("bytes"), o.optLong("total"), o.optString("path"), o.optLong("modified"), o.optString("url"), o.optString("error"), o.optInt("durationMin"))
}
