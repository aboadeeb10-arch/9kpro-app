package tv.ninekpro.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Phone home-screen widget: the last 4 things watched (or favourites) as one-tap shortcuts + Open. */
class SmileWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) { ids.forEach { draw(ctx, mgr, it) } }
    companion object {
        fun refresh(ctx: Context) { try { val mgr = AppWidgetManager.getInstance(ctx); val ids = mgr.getAppWidgetIds(android.content.ComponentName(ctx, SmileWidget::class.java)); ids.forEach { draw(ctx, mgr, it) } } catch (e: Throwable) {} }
        fun draw(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_smile)
            val app = (ctx.applicationContext as? SmileApp)?.repo
            val items = ArrayList<Triple<String, String, String>>() // title · extra name · extra value
            try { app?.resumeList()?.take(4)?.forEach { items.add(Triple(it.title, "resume_key", it.key)) } } catch (e: Throwable) {}
            try { if (items.size < 4 && app != null) { val pl = app.activePlaylist; if (pl != null) { val favs = app.favorites().toSet(); val cached = app.cachedLive(pl); cached.filter { favs.contains(it.key) }.take(4 - items.size).forEach { items.add(Triple(it.name, "chkey", it.key)) } } } } catch (e: Throwable) {}
            val slots = intArrayOf(R.id.w1, R.id.w2, R.id.w3, R.id.w4)
            slots.forEachIndexed { i, sid ->
                val it = items.getOrNull(i)
                rv.setTextViewText(sid, it?.first ?: ""); rv.setViewVisibility(sid, if (it != null) android.view.View.VISIBLE else android.view.View.GONE)
                if (it != null) rv.setOnClickPendingIntent(sid, PendingIntent.getActivity(ctx, 100 + i, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(it.second, it.third), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            rv.setOnClickPendingIntent(R.id.wopen, PendingIntent.getActivity(ctx, 99, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            mgr.updateAppWidget(id, rv)
        }
    }
}
