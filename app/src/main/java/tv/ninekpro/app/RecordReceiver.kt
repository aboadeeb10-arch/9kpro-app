package tv.ninekpro.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import tv.ninekpro.app.data.DlStore

/** Scheduled recording from the guide: the alarm fires at the programme start and starts DlService for its duration. */
class RecordReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val url = intent.getStringExtra("url") ?: return; val title = intent.getStringExtra("title") ?: "Recording"; val dur = intent.getIntExtra("durationMin", 60)
        try { DlService.start(ctx, DlStore.newId(), url, title, "rec", dur) } catch (e: Exception) {}
        try { val p = ctx.getSharedPreferences("ninekpro", Context.MODE_PRIVATE); val key = intent.getStringExtra("key") ?: ""; val s = (p.getStringSet("rec_sched", emptySet()) ?: emptySet()).filter { !it.startsWith(key + "|") }.toSet(); p.edit().putStringSet("rec_sched", s).apply() } catch (e: Exception) {}
    }
    companion object {
        fun id(chKey: String, start: Long) = ((chKey.hashCode().toLong() shl 20) xor start xor 0x7EC0L).toInt()
        fun schedule(ctx: Context, chKey: String, start: Long, stop: Long, url: String, title: String): Boolean {
            val at = start * 1000; if (stop * 1000 < System.currentTimeMillis()) return false
            val dur = ((stop - start) / 60).toInt().coerceIn(5, 360)
            val p = PendingIntent.getBroadcast(ctx, id(chKey, start), Intent(ctx, RecordReceiver::class.java).putExtra("url", url).putExtra("title", title).putExtra("durationMin", dur).putExtra("key", chKey + "|" + start), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            return try { if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, maxOf(at, System.currentTimeMillis() + 2000), p) else am.set(AlarmManager.RTC_WAKEUP, at, p); true } catch (e: Exception) { false }
        }
        fun cancel(ctx: Context, chKey: String, start: Long) { try { (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(PendingIntent.getBroadcast(ctx, id(chKey, start), Intent(ctx, RecordReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)) } catch (e: Exception) {} }
    }
}
