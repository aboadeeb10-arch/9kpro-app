package tv.ninekpro.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapters
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.io.File
import java.nio.ByteBuffer

/** libVLC bundled next to the app (resources/vlc). One player for the whole app: the Live preview and the full screen show the same frames, so going big never reloads. */
object Vlc {
    var ready = false; private set
    var error = ""; private set
    private var factory: MediaPlayerFactory? = null

    fun init() {
        if (ready) return
        try {
            val res = System.getProperty("compose.application.resources.dir")
            if (res != null) {
                val vlc = File(res, "vlc")
                val libDir = if (File(vlc, "lib").isDirectory) File(vlc, "lib") else vlc
                if (libDir.isDirectory) {
                    System.setProperty("jna.library.path", libDir.absolutePath)
                    val plugins = if (File(vlc, "plugins").isDirectory) File(vlc, "plugins") else File(libDir, "plugins")
                    if (plugins.isDirectory) System.setProperty("VLC_PLUGIN_PATH", plugins.absolutePath)
                }
            }
            val found = NativeDiscovery().discover()
            if (!found) { error = "libVLC not found"; return }
            factory = MediaPlayerFactory("--no-video-title-show", "--network-caching=1500", "--live-caching=1500", "--quiet", "--no-snapshot-preview", "--sub-source=", "--freetype-rel-fontsize=16")
            ready = true
        } catch (e: Throwable) { error = e.message ?: e.toString() }
    }
    fun newPlayer(): EmbeddedMediaPlayer = factory!!.mediaPlayers().newEmbeddedMediaPlayer()
    fun release() { try { factory?.release() } catch (e: Throwable) {} }
}

/** Compose-facing player state: the current frame plus playback status. */
class VlcPlayer {
    var frame by mutableStateOf<ImageBitmap?>(null); private set
    var playing by mutableStateOf(false); private set
    var buffering by mutableStateOf(0f); private set
    var time by mutableStateOf(0L); private set
    var length by mutableStateOf(0L); private set
    var error by mutableStateOf(false); private set
    var ended by mutableStateOf(false); private set
    var url by mutableStateOf(""); private set
    var muted by mutableStateOf(false); private set
    var volume by mutableStateOf(100); private set
    var videoW = 0; var videoH = 0
    private var player: EmbeddedMediaPlayer? = null
    private var pixels = ByteArray(0)
    private var bw = 0; private var bh = 0
    private var lastFrameAt = 0L

    private fun ensure(): EmbeddedMediaPlayer? {
        player?.let { return it }
        if (!Vlc.ready) return null
        val p = Vlc.newPlayer()
        val fmtCb = object : BufferFormatCallback {
            override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                videoW = sourceWidth; videoH = sourceHeight
                var w = sourceWidth; var h = sourceHeight
                if (w > 1920) { h = h * 1920 / w; w = 1920 }   // 4K sources are scaled by VLC to 1080p frames (4× less copying)
                bw = w; bh = h; pixels = ByteArray(w * h * 4)
                return RV32BufferFormat(w, h)
            }
            override fun allocatedBuffers(buffers: Array<out ByteBuffer>) {}
        }
        val renderCb = RenderCallback { _: MediaPlayer, nativeBuffers: Array<out ByteBuffer>, _: BufferFormat ->
            try {
                val now = System.nanoTime(); if (now - lastFrameAt < 12_000_000L) return@RenderCallback   // ≤ ~80 fps to the UI
                lastFrameAt = now
                val b = nativeBuffers[0]; b.rewind(); if (pixels.size < b.remaining()) pixels = ByteArray(b.remaining()); b.get(pixels, 0, minOf(pixels.size, b.remaining()))
                val img = org.jetbrains.skia.Image.makeRaster(ImageInfo(bw, bh, ColorType.BGRA_8888, ColorAlphaType.OPAQUE), pixels, bw * 4)
                frame = img.toComposeImageBitmap()
            } catch (e: Throwable) { }
        }
        p.videoSurface().set(CallbackVideoSurface(fmtCb, renderCb, true, VideoSurfaceAdapters.getVideoSurfaceAdapter()))
        p.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mp: MediaPlayer) { playing = true; error = false; ended = false }
            override fun paused(mp: MediaPlayer) { playing = false }
            override fun stopped(mp: MediaPlayer) { playing = false }
            override fun finished(mp: MediaPlayer) { playing = false; ended = true }
            override fun error(mp: MediaPlayer) { playing = false; error = true }
            override fun buffering(mp: MediaPlayer, newCache: Float) { buffering = if (newCache >= 100f) 0f else newCache }
            override fun timeChanged(mp: MediaPlayer, newTime: Long) { time = newTime }
            override fun lengthChanged(mp: MediaPlayer, newLength: Long) { length = newLength }
        })
        p.audio().setVolume(volume)
        player = p; return p
    }

    fun play(u: String, startMs: Long = 0L, subtitle: String = "") {
        val p = ensure() ?: return
        if (u == url && (playing || buffering > 0f)) { if (startMs > 0) p.controls().setTime(startMs); return }
        url = u; error = false; ended = false; time = 0; length = 0; buffering = 1f
        val opts = ArrayList<String>()
        if (startMs > 0) opts.add(":start-time=" + (startMs / 1000))
        if (subtitle.isNotEmpty()) opts.add(":sub-file=$subtitle")
        opts.add(":http-user-agent=9KProTV/1.0")
        p.media().play(u, *opts.toTypedArray())
    }
    /** Set by the Live pane right before opening full screen so the pane's dispose does not stop the stream. */
    @Volatile var handoff = false
    fun stop() { url = ""; playing = false; buffering = 0f; error = false; frame = null; try { player?.controls()?.stop() } catch (e: Throwable) {} }
    fun togglePause() { try { player?.controls()?.pause() } catch (e: Throwable) {} }
    fun pause(b: Boolean) { try { player?.controls()?.setPause(b) } catch (e: Throwable) {} }
    fun seekTo(ms: Long) { try { player?.controls()?.setTime(ms.coerceAtLeast(0)) } catch (e: Throwable) {} }
    fun seekBy(ms: Long) { seekTo(time + ms) }
    fun volumeTo(v: Int) { volume = v.coerceIn(0, 150); try { player?.audio()?.setVolume(volume) } catch (e: Throwable) {} }
    fun toggleMute() { muted = !muted; try { player?.audio()?.isMute = muted } catch (e: Throwable) {} }
    fun audioTracks(): List<Pair<Int, String>> = try { player?.audio()?.trackDescriptions()?.map { it.id() to it.description() } ?: emptyList() } catch (e: Throwable) { emptyList() }
    fun subTracks(): List<Pair<Int, String>> = try { player?.subpictures()?.trackDescriptions()?.map { it.id() to it.description() } ?: emptyList() } catch (e: Throwable) { emptyList() }
    fun audioTrack(): Int = try { player?.audio()?.track() ?: -1 } catch (e: Throwable) { -1 }
    fun subTrack(): Int = try { player?.subpictures()?.track() ?: -1 } catch (e: Throwable) { -1 }
    fun setAudioTrack(id: Int) { try { player?.audio()?.setTrack(id) } catch (e: Throwable) {} }
    fun setSubTrack(id: Int) { try { player?.subpictures()?.setTrack(id) } catch (e: Throwable) {} }
    fun setAspect(a: String) { try { player?.video()?.setAspectRatio(if (a == "auto") null else a) } catch (e: Throwable) {} }
    fun setRate(r: Float) { try { player?.controls()?.setRate(r) } catch (e: Throwable) {} }
    fun release() { try { player?.release() } catch (e: Throwable) {}; player = null }

    companion object { val shared = VlcPlayer() }
}
