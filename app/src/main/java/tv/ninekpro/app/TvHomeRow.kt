package tv.ninekpro.app

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.ChannelLogoUtils
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import tv.ninekpro.app.data.Repo

/** Android TV home screen row ("9K Pro TV"): favourite channels + continue watching. Cards open the app straight into that channel / movie. */
object TvHomeRow {
    fun update(ctx: Context, repo: Repo) {
        if (Build.VERSION.SDK_INT < 26 || !tv.ninekpro.app.data.DeviceInfo.isTv) return
        try {
            val sp = ctx.getSharedPreferences("ninekpro", Context.MODE_PRIVATE); var chId = sp.getLong("tv_channel_id", 0L)
            val name = repo.brand.value.name.ifEmpty { "9K Pro TV" }
            if (chId <= 0) {
                val ch = Channel.Builder().setType(TvContractCompat.Channels.TYPE_PREVIEW).setDisplayName(name).setAppLinkIntent(Intent(ctx, MainActivity::class.java)).build()
                val uri = ctx.contentResolver.insert(TvContractCompat.Channels.CONTENT_URI, ch.toContentValues()) ?: return
                chId = ContentUris.parseId(uri); sp.edit().putLong("tv_channel_id", chId).apply()
                try { val bmp = android.graphics.BitmapFactory.decodeResource(ctx.resources, R.drawable.logo_ninekpro); if (bmp != null) ChannelLogoUtils.storeChannelLogo(ctx, chId, bmp) } catch (e: Exception) {}
                try { TvContractCompat.requestChannelBrowsable(ctx, chId) } catch (e: Exception) {}
            }
            ctx.contentResolver.delete(TvContractCompat.buildPreviewProgramsUriForChannel(chId), null, null)
            val pl = repo.activePlaylist ?: return
            val live = repo.cachedLive(pl)
            var n = 0
            // continue watching (movies / episodes)
            repo.resumeList().take(6).forEach { r ->
                val i = Intent(ctx, MainActivity::class.java).putExtra("resume_key", r.key)
                val p = PreviewProgram.Builder().setChannelId(chId).setType(if (r.kind == "SERIES") TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE else TvContractCompat.PreviewPrograms.TYPE_MOVIE).setTitle(r.title).setDescription("▶ " + (r.positionMs / 60000) + " min").setPosterArtUri(if (r.image.isNotEmpty()) Uri.parse(r.image) else null).setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3).setIntent(i).setWeight(100 - n).build()
                ctx.contentResolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI, p.toContentValues()); n++
            }
            // favourite channels
            val favs = repo.favorites().toSet()
            live.filter { favs.contains(it.key) }.take(10).forEach { ch ->
                val i = Intent(ctx, MainActivity::class.java).putExtra("chkey", ch.key)
                val p = PreviewProgram.Builder().setChannelId(chId).setType(TvContractCompat.PreviewPrograms.TYPE_CHANNEL).setTitle(ch.name).setDescription(name).setPosterArtUri(if (ch.icon.isNotEmpty()) Uri.parse(ch.icon) else null).setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9).setIntent(i).setWeight(50 - n).build()
                ctx.contentResolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI, p.toContentValues()); n++
            }
        } catch (e: Throwable) { }
    }
}
