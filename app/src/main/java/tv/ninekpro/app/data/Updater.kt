package tv.ninekpro.app.data

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * In-app update for the sideload flavour (the Play flavour never calls this — Google Play updates itself).
 * Flow: [start] downloads the APK from the panel with DownloadManager into <external-files>/Download/9KProTV-<code>.apk,
 * [progress] is polled every 500 ms, and on completion [install] opens the system package installer through the FileProvider.
 * API 26+: the "install unknown apps" permission must be granted first — Settings opens, the file stays pending, and the next
 * press of Update installs it. API 23–25: the installer opens straight away (the system asks about unknown sources itself).
 */
object Updater {
    const val IDLE: Int = -1
    const val FAILED: Int = -2

    /** -1 idle · 0..99 downloading · 100 downloaded (pending install) · -2 failed. */
    val progress: MutableStateFlow<Int> = MutableStateFlow(IDLE)
    /** A fully downloaded APK that is newer than the running app and not installed yet. */
    val pendingInfo: MutableStateFlow<UpdateInfo?> = MutableStateFlow(null)
    /** True after the user was sent to Settings to allow installs from this app (API 26+). */
    val askPermission: MutableStateFlow<Boolean> = MutableStateFlow(false)
    /** True after the system installer was opened for the pending file. */
    val installing: MutableStateFlow<Boolean> = MutableStateFlow(false)
    /** How many downloads failed in a row (the banner falls back to the browser link after two). */
    val failures: MutableStateFlow<Int> = MutableStateFlow(0)

    @Volatile private var pendingFile: File? = null
    private var job: Job? = null
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private const val PREFS: String = "updater"

    private fun dir(ctx: Context): File? = try { ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) } catch (e: Exception) { null }
    private fun prefs(ctx: Context): android.content.SharedPreferences = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun fail() { progress.value = FAILED; failures.value = failures.value + 1 }

    /** App start: a download that finished earlier (and was never installed) for a newer version becomes [pendingInfo]; stale files are deleted. */
    fun restore(ctx: Context, currentCode: Int) {
        val app = ctx.applicationContext
        val sp = prefs(app)
        val path: String = sp.getString("ready", "") ?: ""
        val code: Int = sp.getInt("readyCode", 0)
        val f: File? = if (path.isNotEmpty()) File(path) else null
        if (f != null && f.exists() && f.length() > 0L && code > currentCode) {
            pendingFile = f
            pendingInfo.value = UpdateInfo(code, sp.getString("readyName", "") ?: "", sp.getString("readyUrl", "") ?: "")
            progress.value = 100
        } else {
            sp.edit().remove("ready").remove("readyCode").remove("readyName").remove("readyUrl").apply()
            cleanDir(app, null)
        }
    }

    private fun cleanDir(ctx: Context, keep: File?) {
        try {
            val files = dir(ctx)?.listFiles() ?: return
            for (f in files) if (f.name.endsWith(".apk") && (keep == null || f.absolutePath != keep.absolutePath)) f.delete()
        } catch (e: Exception) {}
    }

    /** Update button: installs the pending file if there is one, otherwise starts (or restarts) the download. */
    fun start(ctx: Context, info: UpdateInfo) {
        val app = ctx.applicationContext
        val pf = pendingFile
        if (pf != null && pf.exists() && progress.value == 100) { install(app, pf); return }
        if (progress.value in 0..99) return   // already downloading
        val d = dir(app)
        if (d == null) { fail(); return }
        val file = File(d, "9KProTV-" + info.code + ".apk")
        prefs(app).edit().remove("ready").remove("readyCode").remove("readyName").remove("readyUrl").apply()
        pendingFile = null; pendingInfo.value = null; installing.value = false; askPermission.value = false
        cleanDir(app, null)
        try { if (file.exists()) file.delete() } catch (e: Exception) {}
        val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (dm == null) { fail(); return }
        val id: Long = try {
            val req = DownloadManager.Request(Uri.parse(info.url))
                .setTitle("9K Pro TV update")
                .setDescription("9K Pro TV " + info.name)
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, file.name)
            dm.enqueue(req)
        } catch (e: Exception) { fail(); return }
        progress.value = 0
        job?.cancel()
        job = scope.launch { poll(app, dm, id, file, info) }
    }

    private suspend fun poll(app: Context, dm: DownloadManager, id: Long, file: File, info: UpdateInfo) {
        while (true) {
            delay(500)
            val st: Pair<Int, Int> = withContext(Dispatchers.IO) { query(dm, id) }
            when (st.first) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    if (!file.exists() || file.length() <= 0L) { fail(); return }
                    prefs(app).edit().putString("ready", file.absolutePath).putInt("readyCode", info.code).putString("readyName", info.name).putString("readyUrl", info.url).apply()
                    pendingFile = file; pendingInfo.value = info; failures.value = 0
                    progress.value = 100
                    install(app, file)
                    return
                }
                DownloadManager.STATUS_FAILED, -1 -> { fail(); return }
                else -> progress.value = st.second.coerceIn(0, 99)
            }
        }
    }

    /** (status, percent) of one DownloadManager row; status -1 = the row is gone (cancelled from the notification). */
    private fun query(dm: DownloadManager, id: Long): Pair<Int, Int> {
        val c: Cursor = (try { dm.query(DownloadManager.Query().setFilterById(id)) } catch (e: Exception) { null }) ?: return Pair(-1, 0)
        try {
            if (!c.moveToFirst()) return Pair(-1, 0)
            val status: Int = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done: Long = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total: Long = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val pct: Int = if (total > 0L) (done * 100L / total).toInt() else 0
            return Pair(status, pct)
        } catch (e: Exception) {
            return Pair(-1, 0)
        } finally {
            try { c.close() } catch (e: Exception) {}
        }
    }

    /**
     * Opens the system installer for [file]. Returns true when the installer was opened.
     * API 26+ without the "install unknown apps" permission: opens that Settings page instead, sets [askPermission] and keeps the file pending.
     */
    fun install(ctx: Context, file: File): Boolean {
        val app = ctx.applicationContext
        if (!file.exists()) { pendingFile = null; pendingInfo.value = null; progress.value = IDLE; return false }
        pendingFile = file
        if (Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()) {
            askPermission.value = true
            try { app.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + app.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
            return false
        }
        askPermission.value = false
        return try {
            // API 24+: content:// through the FileProvider (file:// is forbidden there); API 23: the installer wants a plain file:// path
            val uri: Uri = if (Build.VERSION.SDK_INT >= 24) FileProvider.getUriForFile(app, app.packageName + ".fileprovider", file) else Uri.fromFile(file)
            val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            app.startActivity(i)
            installing.value = true
            true
        } catch (e: Exception) { fail(); false }
    }

    /** Opens the APK link in the browser — the last resort when DownloadManager keeps failing. */
    fun openInBrowser(ctx: Context, info: UpdateInfo) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
    }
}
