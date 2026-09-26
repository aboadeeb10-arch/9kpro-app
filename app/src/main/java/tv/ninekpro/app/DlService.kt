package tv.ninekpro.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.ninekpro.app.data.DlStore
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.crypto.CipherOutputStream

/**
 * Foreground service that downloads movies/episodes and records live channels, writing AES-CTR encrypted files.
 * Intents: ACTION_START {id,url,title,kind('movie'|'rec'),durationMin}, ACTION_STOP {id}. Several jobs run in parallel threads.
 */
class DlService : Service() {
    companion object {
        const val ACTION_START = "tv.ninekpro.app.DL_START"; const val ACTION_STOP = "tv.ninekpro.app.DL_STOP"; const val CH = "downloads"
        private val running = java.util.concurrent.ConcurrentHashMap<Long, Thread>()
        fun isRunning(id: Long) = running.containsKey(id)
        fun start(ctx: Context, id: Long, url: String, title: String, kind: String, durationMin: Int = 0) {
            DlStore.put(ctx, DlStore.Row(id, title, kind, DlStore.STATUS_RUNNING, 0, 0, DlStore.file(ctx, id).path, System.currentTimeMillis(), url, "", durationMin))
            val i = Intent(ctx, DlService::class.java).setAction(ACTION_START).putExtra("id", id).putExtra("url", url).putExtra("title", title).putExtra("kind", kind).putExtra("durationMin", durationMin)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }
        fun stop(ctx: Context, id: Long) { ctx.startService(Intent(ctx, DlService::class.java).setAction(ACTION_STOP).putExtra("id", id)) }
    }
    private val nm by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CH, "Downloads & recordings", NotificationManager.IMPORTANCE_LOW))
        if (intent?.action == ACTION_STOP) { val id = intent.getLongExtra("id", 0L); running.remove(id)?.interrupt(); if (running.isEmpty()) { stopForeground(true); stopSelf() }; return START_NOT_STICKY }
        if (intent?.action != ACTION_START) return START_NOT_STICKY
        val id = intent.getLongExtra("id", 0L); val url = intent.getStringExtra("url") ?: ""; val title = intent.getStringExtra("title") ?: ""; val kind = intent.getStringExtra("kind") ?: "movie"; val durMin = intent.getIntExtra("durationMin", 0)
        startForeground(9001, notif(title, kind == "rec", 0))
        val t = Thread { run(id, url, title, kind, durMin) }; running[id] = t; t.start()
        return START_NOT_STICKY
    }
    private fun notif(title: String, rec: Boolean, pct: Int): Notification {
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CH) else @Suppress("DEPRECATION") Notification.Builder(this)
        b.setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle((if (rec) "● REC  " else "⬇ ") + title).setContentText(if (rec) "Recording…" else "$pct%").setOngoing(true).setContentIntent(open).setOnlyAlertOnce(true)
        if (!rec) b.setProgress(100, pct, pct == 0)
        return b.build()
    }
    private fun run(id: Long, url: String, title: String, kind: String, durMin: Int) {
        val ctx = applicationContext; val rec = kind == "rec"; val f = DlStore.file(ctx, id)
        try {
            val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(if (rec) 60 else 40, TimeUnit.SECONDS).build()
            val resp = client.newCall(Request.Builder().url(url).header("User-Agent", "9KProTV/1.0").build()).execute()
            if (!resp.isSuccessful) throw Exception("HTTP " + resp.code)
            val body = resp.body ?: throw Exception("empty"); val total = if (rec) 0L else body.contentLength()
            val out = CipherOutputStream(FileOutputStream(f), DlStore.cipherAt(ctx, id, 0)); val ins = body.byteStream()
            val buf = ByteArray(256 * 1024); var done = 0L; var lastUi = 0L; val start = System.currentTimeMillis(); val endAt = if (rec && durMin > 0) start + durMin * 60_000L else Long.MAX_VALUE
            try {
                while (!Thread.currentThread().isInterrupted && System.currentTimeMillis() < endAt) {
                    val n = ins.read(buf); if (n < 0) break
                    out.write(buf, 0, n); done += n
                    if (System.currentTimeMillis() - lastUi > 1500) { lastUi = System.currentTimeMillis(); DlStore.update(ctx, id) { it.copy(bytes = done, total = total, modified = System.currentTimeMillis()) }; try { nm.notify(9001, notif(title, rec, if (total > 0) (done * 100 / total).toInt() else 0)) } catch (e: Exception) {} }
                }
            } finally { try { out.close() } catch (e: Exception) {}; try { ins.close(); resp.close() } catch (e: Exception) {} }
            val complete = rec || total <= 0 || done >= total
            DlStore.update(ctx, id) { it.copy(bytes = done, total = if (total > 0) total else done, status = if (complete) DlStore.STATUS_DONE else DlStore.STATUS_FAILED, modified = System.currentTimeMillis(), error = if (complete) "" else "incomplete") }
        } catch (e: InterruptedException) { DlStore.update(ctx, id) { it.copy(status = if (rec) DlStore.STATUS_DONE else DlStore.STATUS_FAILED, total = if (rec) it.bytes else it.total, modified = System.currentTimeMillis()) } }
        catch (e: Throwable) { if (Thread.currentThread().isInterrupted) DlStore.update(ctx, id) { it.copy(status = if (rec) DlStore.STATUS_DONE else DlStore.STATUS_FAILED, total = if (rec) it.bytes else it.total) } else DlStore.update(ctx, id) { it.copy(status = DlStore.STATUS_FAILED, error = (e.message ?: "error").take(120), modified = System.currentTimeMillis()) } }
        running.remove(id)
        if (running.isEmpty()) { stopForeground(true); stopSelf() }
    }
}
