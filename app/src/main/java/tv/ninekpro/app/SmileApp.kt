package tv.ninekpro.app

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import tv.ninekpro.app.data.DeviceInfo
import tv.ninekpro.app.data.Repo

class SmileApp : Application() {
    var repo: Repo? = null
    /** If something failed while starting, the error is shown on screen instead of a silent close. */
    var initError: String? = null

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
            r.startHeartbeat()
        } catch (e: Throwable) {
            Crash.record(e, "SmileApp.onCreate")
            initError = e.toString()
        }
    }

    companion object { fun repo(ctx: Context): Repo? = (ctx.applicationContext as SmileApp).repo }
}
