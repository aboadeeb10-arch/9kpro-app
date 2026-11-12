package tv.ninekpro.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import tv.ninekpro.app.data.DlStore

/** Exact alarms (Android 12+ needs the "Alarms & reminders" permission; below that they are always allowed). */
object Alarms {
    fun exactAllowed(ctx: Context): Boolean = try { val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager; if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true } catch (e: Exception) { false }
    /** Settings page where the customer can allow exact alarms (Android 12+), null below. */
    fun settingsIntent(ctx: Context): Intent? = if (Build.VERSION.SDK_INT >= 31) Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(android.net.Uri.parse("package:" + ctx.packageName)) else null
    /** Arms the alarm: exact when allowed, else inexact (may fire minutes late). Returns true = exact, false = inexact fallback. Throws when nothing could be armed. */
    fun set(ctx: Context, at: Long, p: PendingIntent): Boolean {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val exact = exactAllowed(ctx)
        if (exact) { try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p); return true } catch (e: SecurityException) { /* permission revoked meanwhile → inexact */ } }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        return false
    }
}

/** One stored line of prefs.recSched: "chKey|start|stop|url|title" (url and title URL-encoded so '|' inside them is safe). Old lines are "chKey|start". */
data class RecSched(val chKey: String, val start: Long, val stop: Long, val url: String, val title: String) {
    fun encode(): String = chKey + "|" + start + "|" + stop + "|" + java.net.URLEncoder.encode(url, "UTF-8") + "|" + java.net.URLEncoder.encode(title, "UTF-8")
}

/** Scheduled recording from the guide: the alarm fires at the programme start and starts DlService for its duration. */
class RecordReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val url = intent.getStringExtra("url") ?: return; val title = intent.getStringExtra("title") ?: "Recording"; val dur = intent.getIntExtra("durationMin", 60)
        try { DlService.start(ctx, DlStore.newId(), url, title, "rec", dur) } catch (e: Exception) {}
        try { val p = ctx.getSharedPreferences("ninekpro", Context.MODE_PRIVATE); val key = intent.getStringExtra("key") ?: ""; val s = (p.getStringSet("rec_sched", emptySet()) ?: emptySet()).filter { !it.startsWith(key + "|") }.toSet(); p.edit().putStringSet("rec_sched", s).apply() } catch (e: Exception) {}
    }
    companion object {
        const val MAX_MIN = 300   // Android 15 stops a dataSync service after 6 h a day: never record longer than 5 h
        fun id(chKey: String, start: Long) = ((chKey.hashCode().toLong() shl 20) xor start xor 0x7EC0L).toInt()
        /** Parses a stored line; url/title are empty for old two-part lines. */
        fun parse(s: String): RecSched? {
            val p = s.split("|"); if (p.size < 2) return null
            val start = p[1].toLongOrNull() ?: return null
            if (p.size < 5) return RecSched(p[0], start, 0L, "", "")
            val dec = { x: String -> try { java.net.URLDecoder.decode(x, "UTF-8") } catch (e: Exception) { x } }
            return RecSched(p[0], start, p[2].toLongOrNull() ?: 0L, dec(p[3]), dec(p[4]))
        }
        /** Arms the recording alarm. true = exact; false = armed inexact (no exact-alarm permission) or nothing armed (past / error). Use Alarms.exactAllowed to tell the two apart. */
        fun schedule(ctx: Context, chKey: String, start: Long, stop: Long, url: String, title: String): Boolean {
            val at = start * 1000; if (stop * 1000 < System.currentTimeMillis()) return false
            val dur = ((stop - start) / 60).toInt().coerceIn(5, MAX_MIN)
            val p = PendingIntent.getBroadcast(ctx, id(chKey, start), Intent(ctx, RecordReceiver::class.java).putExtra("url", url).putExtra("title", title).putExtra("durationMin", dur).putExtra("key", chKey + "|" + start), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            return try { Alarms.set(ctx, maxOf(at, System.currentTimeMillis() + 2000), p) } catch (e: Exception) { false }
        }
        fun cancel(ctx: Context, chKey: String, start: Long) { try { (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(PendingIntent.getBroadcast(ctx, id(chKey, start), Intent(ctx, RecordReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)) } catch (e: Exception) {} }
    }
}
