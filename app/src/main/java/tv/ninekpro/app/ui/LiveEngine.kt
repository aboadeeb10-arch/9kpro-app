package tv.ninekpro.app.ui

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import tv.ninekpro.app.data.DeviceInfo

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
        // Weak boxes (DeviceInfo.lite): smaller buffer so the stream starts and does not starve the RAM.
        val fastStart = if (DeviceInfo.lite) DefaultLoadControl.Builder().setBufferDurationsMs(10_000, 20_000, 700, 1_500).setPrioritizeTimeOverSizeThresholds(false).build()
            else DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 50_000, 700, 1_500).setPrioritizeTimeOverSizeThresholds(true).build()
        val p = ExoPlayer.Builder(app).setRenderersFactory(DefaultRenderersFactory(app).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER))
            .setLoadControl(fastStart).setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setAudioAttributes(AudioAttributes.DEFAULT, true).setHandleAudioBecomingNoisy(true).setWakeMode(C.WAKE_MODE_NETWORK)   // phones: pause when headphones unplug / another app takes the sound
            .build().apply { playWhenReady = true }
        player = p; applyPrefs(subLang); return p
    }

    /** Subtitle preference ("" = none) — re-applied on every zap so a "None" picked on one channel does not stick for all later ones. */
    fun applyPrefs(subLang: String) {
        val p = player ?: return
        try { p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setPreferredTextLanguage(subLang.ifEmpty { null }).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, subLang.isEmpty()).build() } catch (e: Throwable) {}
    }

    /** Load a channel into the shared player (prefs re-applied first). */
    fun load(k: String, item: MediaItem, subLang: String) {
        val p = player ?: return
        key = k; applyPrefs(subLang); p.setMediaItem(item); p.prepare(); p.play()
    }

    /** Is this channel already playing (or loading) in the shared player? */
    fun isLoaded(k: String): Boolean { val p = player ?: return false; return key == k && p.playbackState != androidx.media3.common.Player.STATE_IDLE && p.playerError == null }

    /** Stop and free the stream (leaving Live TV, or the app going away). */
    fun stop() { try { player?.stop(); player?.clearMediaItems() } catch (e: Throwable) {}; key = ""; handoff = false }
    fun release() { stop(); try { player?.release() } catch (e: Throwable) {}; player = null }
}
