package tv.ninekpro.app

import android.content.Context
import android.os.Build
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.TimeUnit

/**
 * Catches any crash, keeps the stack trace on the device and reports it to the panel (My App → Stats → App crashes).
 * If the app crashes twice in a row right after opening, MainActivity shows the trace on screen instead of the app,
 * so the customer (or the reseller) can read / send it — no more "it closes instantly" mysteries.
 */
object Crash {
    private const val SP = "ninekpro_crash"
    private lateinit var app: Context

    fun install(ctx: Context) {
        app = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { record(e, "thread " + t.name) } catch (_: Throwable) {}
            prev?.uncaughtException(t, e)
        }
    }

    fun record(e: Throwable, where: String) {
        val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
        val trace = (where + "\n" + sw.toString()).take(12000)
        val sp = app.getSharedPreferences(SP, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastAt = sp.getLong("at", 0L)
        val streak = if (now - lastAt < 90_000L) sp.getInt("streak", 0) + 1 else 1
        sp.edit().putString("trace", trace).putLong("at", now).putInt("streak", streak).putBoolean("unsent", true).commit()
        // Best effort: report right away (the process is about to die, so give it 3 s).
        try { val th = Thread { send(trace) }; th.start(); th.join(3000) } catch (_: Throwable) {}
    }

    /** The trace to show instead of the app: only when it crashed 2+ times in a row within 90 s of each other. */
    fun blocking(ctx: Context): String? {
        val sp = ctx.getSharedPreferences(SP, Context.MODE_PRIVATE)
        val at = sp.getLong("at", 0L); val streak = sp.getInt("streak", 0)
        return if (streak >= 2 && System.currentTimeMillis() - at < 10 * 60_000L) sp.getString("trace", null) else null
    }

    fun lastTrace(ctx: Context): String? = ctx.getSharedPreferences(SP, Context.MODE_PRIVATE).getString("trace", null)

    /** Called when the app reached its UI normally: clear the streak and send anything not yet reported. */
    fun onAppStarted(ctx: Context) {
        val sp = ctx.getSharedPreferences(SP, Context.MODE_PRIVATE)
        sp.edit().putInt("streak", 0).apply()
        if (sp.getBoolean("unsent", false)) { val t = sp.getString("trace", "") ?: ""; Thread { if (send(t)) sp.edit().putBoolean("unsent", false).apply() }.start() }
    }

    fun reset(ctx: Context) { ctx.getSharedPreferences(SP, Context.MODE_PRIVATE).edit().clear().commit() }

    fun send(trace: String): Boolean = try {
        val mac = try { tv.ninekpro.app.data.Prefs(app).deviceMac() } catch (_: Throwable) { "" }
        val body = JSONObject().put("op", "app_crash").put("mac", mac).put("model", (Build.MANUFACTURER + " " + Build.MODEL).trim())
            .put("android", Build.VERSION.RELEASE + " (" + Build.VERSION.SDK_INT + ")").put("app_version", BuildConfig.VERSION_NAME).put("trace", trace)
        val client = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS).build()
        client.newCall(Request.Builder().url(BuildConfig.PANEL_URL + "/api/myapp").post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()).execute().use { it.isSuccessful }
    } catch (_: Throwable) { false }
}
