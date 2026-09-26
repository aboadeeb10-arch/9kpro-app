package tv.ninekpro.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.ninekpro.desktop.data.AppDirs
import tv.ninekpro.desktop.data.AppInfo
import tv.ninekpro.desktop.data.Prefs
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Silent updates. On start: if a newer installer was already downloaded, install it and relaunch (a few seconds).
 * In the background: read /9KProTV-desktop.version ("1.0.1 sha"), download the matching installer for this OS/arch into the app-data folder.
 */
object Updater {
    var status by mutableStateOf("")
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()
    private fun parts(s: String): List<Int> { return s.split(".").map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0) }
    private fun newer(a: String, b: String): Boolean { val x = parts(a); val y = parts(b); for (i in 0 until 3) { if (x[i] != y[i]) return x[i] > y[i] }; return false }
    private val arch: String get() { val a = System.getProperty("os.arch").lowercase(); return if (a.contains("aarch64") || a.contains("arm")) "arm" else "intel" }
    private fun installerName(): String = if (AppDirs.isWindows) "9KProTV-Setup.msi" else if (AppDirs.isMac) "9KProTV-mac-$arch.dmg" else ""

    fun checkInBackground(prefs: Prefs) { thread(isDaemon = true) { try { check(prefs, false) } catch (e: Throwable) { status = "" } } }
    fun checkNow(prefs: Prefs, toast: (String) -> Unit) { thread(isDaemon = true) { try { toast(check(prefs, true)) } catch (e: Throwable) { toast(e.message ?: "error") } } }

    private fun check(prefs: Prefs, verbose: Boolean): String {
        val name = installerName(); if (name.isEmpty()) return "n/a"
        val v = client.newCall(Request.Builder().url(AppInfo.PANEL_URL + "/9KProTV-desktop.version?t=" + System.currentTimeMillis()).build()).execute().use { it.body?.string() ?: "" }.trim().split(" ").firstOrNull() ?: ""
        if (v.isEmpty() || !newer(v, AppInfo.VERSION)) { status = ""; return "✓ " + AppInfo.VERSION }
        val pending = prefs.pendingUpdate.split("|"); if (pending.size == 2 && pending[0] == v && File(pending[1]).length() > 1_000_000) { status = "v$v ready"; return "v$v ready — restarts on next start" }
        status = "downloading v$v"
        val dir = File(AppDirs.data, "update").apply { mkdirs() }; val f = File(dir, name); val tmp = File(dir, "$name.part")
        client.newCall(Request.Builder().url(AppInfo.DOWNLOADS_URL + name).build()).execute().use { r -> if (!r.isSuccessful) throw IllegalStateException("HTTP " + r.code); tmp.outputStream().use { o -> r.body!!.byteStream().copyTo(o) } }
        if (tmp.length() < 1_000_000) { tmp.delete(); throw IllegalStateException("bad download") }
        f.delete(); tmp.renameTo(f); prefs.pendingUpdate = "$v|" + f.absolutePath; status = "v$v ready"
        return "v$v ready — restarts on next start"
    }

    /** Called first thing in main(): installs a downloaded update and relaunches. */
    fun applyPendingOnStart() {
        try {
            val prefs = Prefs(); val p = prefs.pendingUpdate.split("|"); if (p.size != 2) return
            val f = File(p[1]); if (!newer(p[0], AppInfo.VERSION) || !f.exists()) { prefs.pendingUpdate = ""; f.delete(); return }
            prefs.pendingUpdate = ""
            if (AppDirs.isWindows) {
                val exe = File(System.getProperty("java.home")).parentFile?.let { File(it, "9KProTV.exe") }
                val relaunch = if (exe != null && exe.exists()) "start \"\" \"${exe.absolutePath}\"" else "start \"\" \"%LOCALAPPDATA%\\9KProTV\\9KProTV.exe\""
                val cmd = "timeout /t 2 /nobreak >nul & msiexec /i \"${f.absolutePath}\" /qn /norestart & del \"${f.absolutePath}\" & $relaunch"
                ProcessBuilder("cmd", "/c", cmd).start()
                System.exit(0)
            } else if (AppDirs.isMac) {
                // this app bundle: .../9KProTV.app/Contents/runtime/Contents/Home → up to the .app
                var app = File(System.getProperty("java.home")); while (app.parentFile != null && !app.name.endsWith(".app")) app = app.parentFile
                if (!app.name.endsWith(".app")) return
                val sh = File(AppDirs.data, "update/apply.sh")
                sh.writeText("#!/bin/sh\nsleep 2\nM=/tmp/9kpro-upd\nhdiutil attach -nobrowse -quiet -mountpoint \"\$M\" \"${f.absolutePath}\" || exit 1\nSRC=\$(ls -d \"\$M\"/*.app | head -1)\nrm -rf \"${app.absolutePath}.new\"\nditto \"\$SRC\" \"${app.absolutePath}.new\" && rm -rf \"${app.absolutePath}\" && mv \"${app.absolutePath}.new\" \"${app.absolutePath}\"\nhdiutil detach -quiet \"\$M\"\nrm -f \"${f.absolutePath}\"\nxattr -dr com.apple.quarantine \"${app.absolutePath}\" 2>/dev/null\nopen -n \"${app.absolutePath}\"\n")
                sh.setExecutable(true)
                ProcessBuilder("/bin/sh", sh.absolutePath).start()
                System.exit(0)
            }
        } catch (e: Throwable) { }
    }
}
