package tv.ninekpro.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import tv.ninekpro.app.data.Match

/** Match reminder: 5 minutes before kick-off a notification opens the app on the Sports tab (channel chooser for that match). */
class RemindReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: return; val id = intent.getLongExtra("id", 0L); val chkey = intent.getStringExtra("chkey") ?: ""
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CH, "Match reminders", NotificationManager.IMPORTANCE_HIGH))
        val open = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP); if (chkey.isNotEmpty()) open.putExtra("chkey", chkey) else open.putExtra("match", id)
        val pi = PendingIntent.getActivity(ctx, id.toInt(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, CH) else @Suppress("DEPRECATION") Notification.Builder(ctx)
        b.setSmallIcon(android.R.drawable.ic_media_play).setContentTitle((if (chkey.isNotEmpty()) "📺 " else "⚽ ") + title).setContentText(ctx.getString(if (chkey.isNotEmpty()) R.string.prog_soon else R.string.match_soon)).setContentIntent(pi).setAutoCancel(true)
        try { nm.notify(id.toInt(), b.build()) } catch (e: Exception) {}
        try { val p = ctx.getSharedPreferences("ninekpro", Context.MODE_PRIVATE); val s = (p.getStringSet("reminders", emptySet()) ?: emptySet()).toMutableSet(); s.remove(id.toString()); p.edit().putStringSet("reminders", s).apply()
            val e = (p.getStringSet("epg_reminders", emptySet()) ?: emptySet()).filter { !it.startsWith(id.toString() + "|") }.toSet(); p.edit().putStringSet("epg_reminders", e).apply()
            val m = (p.getStringSet("reminder_matches", emptySet()) ?: emptySet()).filter { !it.startsWith(id.toString() + "|") }.toSet(); p.edit().putStringSet("reminder_matches", m).apply() } catch (e: Exception) {}
    }
    companion object {
        const val CH = "match"
        private fun pi(ctx: Context, title: String, id: Long) = PendingIntent.getBroadcast(ctx, id.toInt(), Intent(ctx, RemindReceiver::class.java).putExtra("id", id).putExtra("title", title), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun matchTitle(m: Match) = m.home + " – " + m.away
        /** Stored line of prefs.reminderMatches so BootReceiver can re-arm after a reboot: "id|kickoffSecs|title" (title URL-encoded). */
        fun matchLine(id: Long, ts: Long, title: String): String = id.toString() + "|" + ts + "|" + java.net.URLEncoder.encode(title, "UTF-8")
        fun schedule(ctx: Context, m: Match): Boolean = schedule(ctx, m.id, m.ts, matchTitle(m))
        /** true = exact alarm; false = inexact fallback or nothing armed (kick-off already too close). */
        fun schedule(ctx: Context, id: Long, ts: Long, title: String): Boolean {
            val at = ts * 1000 - 5 * 60_000L; if (at < System.currentTimeMillis()) return false
            return try { Alarms.set(ctx, at, pi(ctx, title, id)) } catch (e: Exception) { false }
        }
        fun cancel(ctx: Context, id: Long) { try { (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi(ctx, "", id)) } catch (e: Exception) {} }
        /** EPG programme reminder: fires 5 minutes before the programme and opens that channel. id = hash of channel key + start. */
        fun progId(chKey: String, start: Long): Long = (chKey.hashCode().toLong() shl 20) xor start
        /** Stored line of prefs.epgReminders: "id|chKey|start|title|channel" (title and channel URL-encoded; old lines are "id|chKey|start|title" raw). */
        fun progLine(chKey: String, start: Long, title: String, channel: String): String = progId(chKey, start).toString() + "|" + chKey + "|" + start + "|" + java.net.URLEncoder.encode(title, "UTF-8") + "|" + java.net.URLEncoder.encode(channel, "UTF-8")
        /** Parses a stored programme-reminder line → (chKey, start, title, channel). */
        fun parseProg(s: String): Array<String>? {
            val p = s.split("|"); if (p.size < 4) return null
            if (p.size >= 5) { val dec = { x: String -> try { java.net.URLDecoder.decode(x, "UTF-8") } catch (e: Exception) { x } }; return arrayOf(p[1], p[2], dec(p[3]), dec(p[4])) }
            return arrayOf(p[1], p[2], p.drop(3).joinToString("|"), "")
        }
        /** true = exact alarm; false = inexact fallback or nothing armed (programme already too close). */
        fun scheduleProgramme(ctx: Context, chKey: String, start: Long, title: String, channel: String): Boolean {
            val at = start * 1000 - 5 * 60_000L; if (at < System.currentTimeMillis()) return false
            val id = progId(chKey, start)
            val p = PendingIntent.getBroadcast(ctx, id.toInt(), Intent(ctx, RemindReceiver::class.java).putExtra("id", id).putExtra("title", title + " · " + channel).putExtra("chkey", chKey), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            return try { Alarms.set(ctx, at, p) } catch (e: Exception) { false }
        }
        fun cancelProgramme(ctx: Context, chKey: String, start: Long) { val id = progId(chKey, start); try { (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(PendingIntent.getBroadcast(ctx, id.toInt(), Intent(ctx, RemindReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)) } catch (e: Exception) {} }
    }
}
