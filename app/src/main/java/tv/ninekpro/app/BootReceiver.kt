package tv.ninekpro.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Alarms die with a reboot (and an app update): re-arm every scheduled recording, programme reminder and match reminder from prefs. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val a = intent.action ?: return
        if (a != Intent.ACTION_BOOT_COMPLETED && a != Intent.ACTION_MY_PACKAGE_REPLACED && a != "android.intent.action.QUICKBOOT_POWERON") return
        try { rearm(ctx) } catch (e: Exception) {}
    }
    companion object {
        fun rearm(ctx: Context) {
            val p = ctx.getSharedPreferences("ninekpro", Context.MODE_PRIVATE); val now = System.currentTimeMillis()
            // recordings: only lines in the new format carry url/title; old two-part lines cannot be re-armed and are dropped
            val recs = (p.getStringSet("rec_sched", emptySet()) ?: emptySet()).mapNotNull { RecordReceiver.parse(it) }.filter { it.url.isNotEmpty() && it.stop * 1000 > now }
            for (r in recs) try { RecordReceiver.schedule(ctx, r.chKey, r.start, r.stop, r.url, r.title) } catch (e: Exception) {}
            p.edit().putStringSet("rec_sched", recs.map { it.encode() }.toSet()).apply()
            // programme reminders "id|chKey|start|title|channel"
            val progs = (p.getStringSet("epg_reminders", emptySet()) ?: emptySet()).filter { s -> val x = RemindReceiver.parseProg(s); x != null && (x[1].toLongOrNull() ?: 0L) * 1000 > now }
            for (s in progs) try { val x = RemindReceiver.parseProg(s) ?: continue; RemindReceiver.scheduleProgramme(ctx, x[0], x[1].toLong(), x[2], x[3]) } catch (e: Exception) {}
            p.edit().putStringSet("epg_reminders", progs.toSet()).apply()
            // match reminders "id|kickoff|title"
            val ms = (p.getStringSet("reminder_matches", emptySet()) ?: emptySet()).filter { s -> (s.split("|").getOrNull(1)?.toLongOrNull() ?: 0L) * 1000 > now }
            for (s in ms) try { val x = s.split("|"); val id = x[0].toLongOrNull() ?: continue; val ts = x[1].toLongOrNull() ?: continue; val t = try { java.net.URLDecoder.decode(x.getOrElse(2) { "" }, "UTF-8") } catch (e: Exception) { "" }; RemindReceiver.schedule(ctx, id, ts, t) } catch (e: Exception) {}
            p.edit().putStringSet("reminder_matches", ms.toSet()).apply()
        }
    }
}
