package tv.ninekpro.app.ui

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/**
 * One ExoPlayer shared by the Live TV preview (small screen) and the full-screen player.
 * Tapping the preview only hands the running stream to the big screen; Back hands it back — no reload
 * until the channel changes. Stopped when the Live tab is left.
 */
@androidx.annotation.OptIn(UnstableApi::class)
object LiveEngine {
    @Volatile private var player: ExoPlayer? = null
    /** Key of the channel currently loaded in the shared player ("" = nothing). */
    @Volatile var key: String = ""
    /** True while the full screen was opened from the preview: the stream must survive the hand-over in both directions. */
    @Volatile var handoff: Boolean = false

    fun get(ctx: Context, subLang: String = ""): ExoPlayer {
        player?.let { return it }
        val app = ctx.applicationContext
        val http = DefaultHttpDataSource.Factory().setUserAgent("9KProTV/1.0").setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(15000).setReadTimeoutMs(20000)
        val fastStart = DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 50_000, 700, 1_500).setPrioritizeTimeOverSizeThresholds(true).build()
        val p = ExoPlayer.Builder(app).setRenderersFactory(DefaultRenderersFactory(app).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER))
            .setLoadControl(fastStart).setMediaSourceFactory(DefaultMediaSourceFactory(http)).build().apply {
                playWhenReady = true
                trackSelectionParameters = trackSelectionParameters.buildUpon().setPreferredTextLanguage(subLang.ifEmpty { null }).build()
            }
        player = p; return p
    }

    /** Is this channel already playing (or loading) in the shared player? */
    fun isLoaded(k: String): Boolean { val p = player ?: return false; return key == k && p.playbackState != androidx.media3.common.Player.STATE_IDLE && p.playerError == null }

    /** Stop and free the stream (leaving Live TV, or the app going away). */
    fun stop() { try { player?.stop(); player?.clearMediaItems() } catch (e: Throwable) {}; key = ""; handoff = false }
    fun release() { stop(); try { player?.release() } catch (e: Throwable) {}; player = null }
}
