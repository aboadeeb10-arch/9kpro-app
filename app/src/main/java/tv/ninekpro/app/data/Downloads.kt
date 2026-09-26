package tv.ninekpro.app.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import tv.ninekpro.app.ui.Nav

/** Movie / episode downloads through the system DownloadManager (background, notification, resumable). Files live in the app's private Movies folder. */
object Downloads {
    data class Row(val id: Long, val title: String, val status: Int, val bytes: Long, val total: Long, val localUri: String, val modified: Long = 0L) { val pct: Int get() = if (total > 0) (bytes * 100 / total).toInt().coerceIn(0, 100) else 0 }

    fun enqueue(nav: Nav, repo: Repo, url: String, title: String, ext: String) {
        enqueue(AppCtx.ctx, repo, url, title, ext)
    }

    fun enqueue(ctx: Context, repo: Repo, url: String, title: String, ext: String) {
        // v0.8.1: encrypted download through DlService (file unplayable outside 9K Pro TV); the DownloadManager path below is kept only for old rows
        try { val safe = title.replace(Regex("[^A-Za-z0-9 _.\\-\\u0600-\\u06FF\\u0590-\\u05FF]"), "_").take(80); tv.ninekpro.app.DlService.start(ctx, DlStore.newId(), url, safe, "movie"); Toast.makeText(ctx, "⬇ " + safe, Toast.LENGTH_SHORT).show(); return } catch (e: Exception) { Toast.makeText(ctx, "Download failed: " + e.message, Toast.LENGTH_SHORT).show(); return }
        try {
            val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val safe = title.replace(Regex("[^A-Za-z0-9 _.\\-\\u0600-\\u06FF\\u0590-\\u05FF]"), "_").take(80)
            val req = DownloadManager.Request(Uri.parse(url)).setTitle(safe).setDescription("9K Pro TV")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_MOVIES, safe + "." + ext.ifEmpty { "mp4" })
                .addRequestHeader("User-Agent", "9KProTV/1.0")
            if (repo.prefs.wifiOnlyDownloads) req.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
            dm.enqueue(req)
            Toast.makeText(ctx, "⬇ " + safe, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(ctx, "Download failed: " + e.message, Toast.LENGTH_SHORT).show() }
    }

    fun list(ctx: Context): List<Row> {
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val out = ArrayList<Row>()
        try {
            dm.query(DownloadManager.Query()).use { c ->
                val iId = c.getColumnIndex(DownloadManager.COLUMN_ID); val iT = c.getColumnIndex(DownloadManager.COLUMN_TITLE); val iS = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val iB = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR); val iTot = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES); val iU = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI); val iM = c.getColumnIndex(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP)
                while (c.moveToNext()) out.add(Row(c.getLong(iId), c.getString(iT) ?: "", c.getInt(iS), c.getLong(iB), c.getLong(iTot), c.getString(iU) ?: "", if (iM >= 0) c.getLong(iM) else 0L))
            }
        } catch (e: Exception) { }
        return out
    }

    fun remove(ctx: Context, id: Long) { try { (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(id) } catch (e: Exception) { } }
}

object AppCtx { lateinit var ctx: Context }
