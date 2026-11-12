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
 * A download that breaks (network) continues from the bytes already on disk with a Range request; the CTR cipher is positioned the same way the player reads.
 */
class DlService : Service() {
    companion object {
        const val ACTION_START = "tv.ninekpro.app.DL_START"; const val ACTION_STOP = "tv.ninekpro.app.DL_STOP"; const val CH = "downloads"
        private val running = java.util.concurrent.ConcurrentHashMap<Long, Thread>()
        /** Ids the user stopped — checked besides the thread interrupt flag, which OkHttp may swallow while reading. */
        private val cancelled: MutableSet<Long> = java.util.concurrent.ConcurrentHashMap.newKeySet()
        fun isRunning(id: Long) = running.containsKey(id)
        /** Starts a download / recording. The row is written RUNNING first; when Android refuses the background start (Android 12+ limits) it is turned into a FAILED row so the customer sees why. */
        fun start(ctx: Context, id: Long, url: String, title: String, kind: String, durationMin: Int = 0) {
            // Android 13+: the progress notification is hidden until the user allows notifications — ask the first time a download/recording starts.
            if (Build.VERSION.SDK_INT >= 33 && ctx is android.app.Activity && ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                try { ctx.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 9) } catch (e: Exception) {}
            val old = DlStore.get(ctx, id)   // resuming a failed download keeps its bytes
            DlStore.put(ctx, DlStore.Row(id, title, kind, DlStore.STATUS_RUNNING, old?.bytes ?: 0L, old?.total ?: 0L, DlStore.file(ctx, id).path, System.currentTimeMillis(), url, "", durationMin))
            val i = Intent(ctx, DlService::class.java).setAction(ACTION_START).putExtra("id", id).putExtra("url", url).putExtra("title", title).putExtra("kind", kind).putExtra("durationMin", durationMin)
            try { if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i) }
            catch (e: Exception) { DlStore.update(ctx, id) { it.copy(status = DlStore.STATUS_FAILED, error = "background start blocked", modified = System.currentTimeMillis()) }; throw e }
        }
        fun stop(ctx: Context, id: Long) { cancelled.add(id); try { ctx.startService(Intent(ctx, DlService::class.java).setAction(ACTION_STOP).putExtra("id", id)) } catch (e: Exception) { running.remove(id)?.interrupt() } }
    }
    private val nm by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    /** Set when Android 15+ ends the service (6 h of dataSync a day): the jobs stop and their rows say why. */
    @Volatile private var timeLimit = false
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CH, "Downloads & recordings", NotificationManager.IMPORTANCE_LOW))
        if (intent?.action == ACTION_STOP) { val id = intent.getLongExtra("id", 0L); cancelled.add(id); running.remove(id)?.interrupt(); if (running.isEmpty()) { stopForeground(true); stopSelf() }; return START_NOT_STICKY }
        if (intent?.action != ACTION_START) return START_NOT_STICKY
        val id = intent.getLongExtra("id", 0L); val url = intent.getStringExtra("url") ?: ""; val title = intent.getStringExtra("title") ?: ""; val kind = intent.getStringExtra("kind") ?: "movie"; val durMin = intent.getIntExtra("durationMin", 0)
        try { startForeground(9001, notif(title, kind == "rec", 0)) } catch (e: Exception) { DlStore.update(applicationContext, id) { it.copy(status = DlStore.STATUS_FAILED, error = "background start blocked", modified = System.currentTimeMillis()) }; stopSelf(); return START_NOT_STICKY }
        val t = Thread { run(id, url, title, kind, durMin) }; running[id] = t; t.start()
        return START_NOT_STICKY
    }
    /** Android 15+: the system is about to stop a dataSync service that used its daily 6 h. Finish cleanly: recordings are kept as DONE, downloads become FAILED "time limit" (resumable). */
    override fun onTimeout(startId: Int, fgsType: Int) {
        timeLimit = true
        for (t in running.values) try { t.interrupt() } catch (e: Exception) {}
        running.clear()
        try { stopForeground(true) } catch (e: Exception) {}
        stopSelf()
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
        var done = if (!rec && f.exists()) f.length() else 0L   // bytes already on disk from an earlier try (download only; a recording always starts fresh)
        var total = 0L
        val start = System.currentTimeMillis(); val endAt = if (rec && durMin > 0) start + durMin * 60_000L else Long.MAX_VALUE
        cancelled.remove(id)
        val stopped = { Thread.currentThread().isInterrupted || cancelled.contains(id) }
        try {
            val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(if (rec) 60 else 40, TimeUnit.SECONDS).build()
            var tries = 0; var finished = false
            while (!finished) {
                try {
                    val rb = Request.Builder().url(url).header("User-Agent", "9KProTV/1.0"); if (done > 0) rb.header("Range", "bytes=$done-")
                    val resp = client.newCall(rb.build()).execute()
                    if (!resp.isSuccessful) { resp.close(); throw Exception("HTTP " + resp.code) }
                    val body = resp.body ?: run { resp.close(); throw Exception("empty") }
                    // 206 = the server continues where we stopped; 200 after a Range request = it does not support ranges → start over
                    if (done > 0 && resp.code != 206) done = 0L
                    if (!rec) total = if (body.contentLength() >= 0) done + body.contentLength() else 0L
                    val out = CipherOutputStream(FileOutputStream(f, done > 0), DlStore.cipherAt(ctx, id, done)); val ins = body.byteStream()
                    val buf = ByteArray(256 * 1024); var lastUi = 0L
                    try {
                        while (!stopped() && System.currentTimeMillis() < endAt) {
                            val n = ins.read(buf); if (n < 0) break
                            out.write(buf, 0, n); done += n
                            if (System.currentTimeMillis() - lastUi > 1500) { lastUi = System.currentTimeMillis(); DlStore.update(ctx, id) { it.copy(bytes = done, total = total, modified = System.currentTimeMillis()) }; try { nm.notify(9001, notif(title, rec, if (total > 0) (done * 100 / total).toInt() else 0)) } catch (e: Exception) {} }
                        }
                    } finally { try { out.close() } catch (e: Exception) {}; try { ins.close(); resp.close() } catch (e: Exception) {} }
                    finished = true
                } catch (e: java.io.IOException) {
                    // network broke mid-way: a download continues from `done` (up to 5 tries, 3 s apart); a recording ends here with what it has
                    tries++
                    if (rec || stopped() || timeLimit || tries > 5) throw e
                    try { Thread.sleep(3000) } catch (ie: InterruptedException) { throw e }
                }
            }
            val complete = rec || total <= 0 || done >= total
            DlStore.update(ctx, id) { it.copy(bytes = done, total = if (total > 0) total else done, status = if (complete) DlStore.STATUS_DONE else DlStore.STATUS_FAILED, modified = System.currentTimeMillis(), error = if (complete) "" else "incomplete") }
        } catch (e: Throwable) {
            val interrupted = e is InterruptedException || stopped() || timeLimit
            if (interrupted) DlStore.update(ctx, id) { it.copy(bytes = done, status = if (rec) DlStore.STATUS_DONE else DlStore.STATUS_FAILED, total = if (rec) done else it.total, error = if (rec) "" else if (timeLimit) "time limit" else "stopped", modified = System.currentTimeMillis()) }
            else DlStore.update(ctx, id) { it.copy(bytes = done, status = DlStore.STATUS_FAILED, error = (e.message ?: "error").take(120), modified = System.currentTimeMillis()) }
        }
        running.remove(id); cancelled.remove(id)
        if (running.isEmpty()) { try { stopForeground(true) } catch (e: Exception) {}; stopSelf() }
    }
}
