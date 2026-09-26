package tv.ninekpro.desktop.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Tiny image loader: memory LRU (600) → disk cache → network. Decoded with Skia; posters are downscaled to ≤ 600 px. */
object Images {
    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    private val mem = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) { override fun removeEldestEntry(e: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 600 }
    private val failed = HashSet<String>()
    private fun key(url: String) = MessageDigest.getInstance("MD5").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }

    suspend fun load(url: String): ImageBitmap? {
        if (url.isBlank() || !url.startsWith("http")) return null
        synchronized(mem) { mem[url]?.let { return it }; if (failed.contains(url)) return null }
        return withContext(Dispatchers.IO) {
            try {
                val f = File(AppDirs.images, key(url))
                val bytes = if (f.exists() && f.length() > 0) f.readBytes() else {
                    client.newCall(Request.Builder().url(url).header("User-Agent", "9KProTV/1.0").build()).execute().use { r -> if (!r.isSuccessful) throw IllegalStateException("http"); r.body!!.bytes() }.also { try { f.writeBytes(it) } catch (e: Exception) {} }
                }
                val img = org.jetbrains.skia.Image.makeFromEncoded(bytes)
                val bmp = if (img.width > 600 || img.height > 600) {
                    val s = 600f / maxOf(img.width, img.height); val w = (img.width * s).toInt().coerceAtLeast(1); val h = (img.height * s).toInt().coerceAtLeast(1)
                    val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(w, h)
                    surface.canvas.drawImageRect(img, org.jetbrains.skia.Rect.makeWH(w.toFloat(), h.toFloat()))
                    surface.makeImageSnapshot().toComposeImageBitmap()
                } else img.toComposeImageBitmap()
                synchronized(mem) { mem[url] = bmp }; bmp
            } catch (e: Throwable) { synchronized(mem) { failed.add(url) }; null }
        }
    }
}

@Composable
fun AsyncImage(url: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop, placeholder: @Composable () -> Unit = {}) {
    var bmp by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) { bmp = Images.load(url) }
    val b = bmp
    if (b != null) androidx.compose.foundation.Image(b, null, modifier = modifier, contentScale = contentScale, alignment = Alignment.Center) else androidx.compose.foundation.layout.Box(modifier) { placeholder() }
}
