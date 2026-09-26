package tv.ninekpro.app

import android.content.Context
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.images.WebImage

/** Google Cast: default media receiver; the player's Cast button sends the current stream (HLS for live, the file URL for movies). */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions = CastOptions.Builder()
        .setReceiverApplicationId(com.google.android.gms.cast.CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
        .setCastMediaOptions(CastMediaOptions.Builder().setNotificationOptions(null).build()).build()
    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}

object CastHelper {
    fun available(ctx: Context): Boolean = try { !tv.ninekpro.app.data.DeviceInfo.isTv && GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ctx) == ConnectionResult.SUCCESS } catch (e: Throwable) { false }
    fun context(ctx: Context): CastContext? = try { CastContext.getSharedInstance(ctx.applicationContext) } catch (e: Throwable) { null }
    fun connected(ctx: Context): Boolean = try { context(ctx)?.sessionManager?.currentCastSession?.isConnected == true } catch (e: Throwable) { false }
    /** Send a stream to the connected Chromecast. Returns false when nothing is connected. */
    fun play(ctx: Context, url: String, title: String, image: String, live: Boolean, positionMs: Long = 0L): Boolean {
        return try {
            val s = context(ctx)?.sessionManager?.currentCastSession ?: return false; val rmc = s.remoteMediaClient ?: return false
            val md = MediaMetadata(if (live) MediaMetadata.MEDIA_TYPE_TV_SHOW else MediaMetadata.MEDIA_TYPE_MOVIE); md.putString(MediaMetadata.KEY_TITLE, title); if (image.isNotEmpty()) try { md.addImage(WebImage(android.net.Uri.parse(image))) } catch (e: Exception) {}
            val type = when { url.contains(".m3u8") -> "application/x-mpegURL"; url.endsWith(".mkv") -> "video/x-matroska"; url.endsWith(".ts") -> "video/mp2t"; else -> "video/mp4" }
            val info = MediaInfo.Builder(url).setStreamType(if (live) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED).setContentType(type).setMetadata(md).build()
            rmc.load(MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).setCurrentTime(positionMs).build()); true
        } catch (e: Throwable) { false }
    }
    fun stop(ctx: Context) { try { context(ctx)?.sessionManager?.endCurrentSession(true) } catch (e: Throwable) {} }
}
