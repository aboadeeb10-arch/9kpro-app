package tv.ninekpro.app

import android.app.Activity
import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import tv.ninekpro.app.data.DeviceInfo
import tv.ninekpro.app.data.Repo

class SmileApp : Application() {
    var repo: Repo? = null
    /** If something failed while starting, the error is shown on screen instead of a silent close. */
    var initError: String? = null
    private var started = 0

    override fun onCreate() {
        super.onCreate()
        Crash.install(this)
        try {
            val ui = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            DeviceInfo.isTv = ui.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION || packageManager.hasSystemFeature("android.software.leanback")
            try { val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager; val mi = android.app.ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
                DeviceInfo.lite = am.isLowRamDevice || mi.totalMem < 2L * 1024 * 1024 * 1024 } catch (e: Throwable) {}
            tv.ninekpro.app.data.AppCtx.ctx = this
            val r = Repo(this)
            repo = r
            // foreground = at least one activity started; the heartbeat slows down to every 20 min while in the background and not playing
            registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) { started++; r.foreground = true }
                override fun onActivityStopped(activity: Activity) { started = (started - 1).coerceAtLeast(0); r.foreground = started > 0 }
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
                override fun onActivityResumed(activity: Activity) {}
                override fun onActivityPaused(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            })
            r.startHeartbeat()
            try { if (r.loggedIn) r.preloadAll() } catch (e: Throwable) {}   // Live / Movies / Series start loading before the first screen is even drawn
        } catch (e: Throwable) {
            Crash.record(e, "SmileApp.onCreate")
            initError = e.toString()
        }
    }

    companion object { fun repo(ctx: Context): Repo? = (ctx.applicationContext as SmileApp).repo }
}
