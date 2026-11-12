package tv.ninekpro.desktop.data

import java.io.File
import kotlin.concurrent.thread

/** Uncaught errors → <app-data>/crash.log and the panel (My App → Stats → crashes), like the Android app. Max one report per 2 minutes. */
object Crash {
    private var panel: PanelApi? = null
    private var lastSent = 0L
    fun install(p: PanelApi) {
        panel = p
        Thread.setDefaultUncaughtExceptionHandler { t, e -> report(e, "thread " + t.name) }
        // a report that could not be sent last time (the app died first) goes now
        try { val f = File(AppDirs.data, "crash.pending"); if (f.exists()) { val txt = f.readText(); f.delete(); thread(isDaemon = true) { try { p.crash(txt) } catch (x: Throwable) {} } } } catch (e: Throwable) {}
    }
    fun report(e: Throwable, where: String = "") {
        try {
            val sw = java.io.StringWriter(); e.printStackTrace(java.io.PrintWriter(sw))
            val txt = (if (where.isNotEmpty()) "[$where] " else "") + "9K Pro TV PC " + AppInfo.VERSION + " · " + AppInfo.model + "\n" + sw.toString()
            try { val log = File(AppDirs.data, "crash.log"); if (log.length() > 400_000) log.delete(); log.appendText("\n==== " + java.util.Date() + "\n" + txt) } catch (x: Throwable) {}
            val now = System.currentTimeMillis(); if (now - lastSent < 120_000) return; lastSent = now
            val p = panel ?: return
            try { File(AppDirs.data, "crash.pending").writeText(txt) } catch (x: Throwable) {}
            thread(isDaemon = true) { try { if (p.crash(txt).ok) File(AppDirs.data, "crash.pending").delete() } catch (x: Throwable) {} }
        } catch (x: Throwable) {}
    }
}
