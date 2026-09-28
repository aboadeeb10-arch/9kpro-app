package tv.ninekpro.desktop.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AppInfo {
    const val PANEL_URL = "https://9kpro-panel.vercel.app"
    const val VERSION = "1.0.7"
    /** Installers are release assets of the public downloads repo (no size limit). */
    const val DOWNLOADS_URL = "https://github.com/aboadeeb10-arch/9kpro-downloads/releases/download/desktop/"
    val platform: String get() = if (AppDirs.isWindows) "windows" else if (AppDirs.isMac) "mac" else "linux"
    val model: String get() = (System.getProperty("os.name") + " " + System.getProperty("os.version")).trim()
}

/** Talks to IPTV Command → /api/myapp. Every call is a POST {op:...}; device calls carry the Bearer token. */
class PanelApi(private val prefs: Prefs) {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val url = AppInfo.PANEL_URL + "/api/myapp"
    private val json = "application/json; charset=utf-8".toMediaType()

    class PanelResult(val ok: Boolean, val error: String, val json: JSONObject)

    private fun meta(o: JSONObject): JSONObject = o.put("mac", prefs.deviceMac()).put("model", AppInfo.model).put("platform", AppInfo.platform).put("app_version", AppInfo.VERSION + "-desktop")

    fun post(op: String, body: JSONObject = JSONObject(), auth: Boolean = true, tokenOverride: String = ""): PanelResult {
        return try {
            val b = meta(body).put("op", op)
            val rb = Request.Builder().url(url).post(b.toString().toRequestBody(json))
            val tk = tokenOverride.ifEmpty { prefs.token }
            if (auth && tk.isNotEmpty()) rb.header("Authorization", "Bearer " + tk)
            client.newCall(rb.build()).execute().use { r ->
                val txt = r.body?.string() ?: "{}"
                val j = try { JSONObject(txt) } catch (e: Exception) { JSONObject().put("ok", false).put("error", "bad_json") }
                PanelResult(j.optBoolean("ok"), j.optString("error"), j)
            }
        } catch (e: Exception) { PanelResult(false, "network", JSONObject().put("detail", e.message ?: "")) }
    }

    fun brand(): Brand? { val r = post("brand", auth = false); return if (r.ok) Brand.fromJson(r.json.optJSONObject("brand")) else null }
    fun login(username: String, password: String) = post("login", JSONObject().put("username", username).put("password", password), auth = false)
    fun codeLogin(code: String) = post("code_login", JSONObject().put("code", code), auth = false)
    fun refresh() = post("refresh")
    fun logout() = post("logout")
    fun syncSet(items: JSONObject) = post("sync_set", JSONObject().put("items", items))
    fun heartbeat(playing: JSONObject?, secs: Int, log: Boolean, error: String = "") =
        post("heartbeat", JSONObject().put("playing", playing ?: JSONObject.NULL).put("secs", secs).put("log", log).put("error", error))
    fun crashTrial() = post("crash_trial")
    fun crashEnd() = post("crash_end")
    fun matches(date: String) = post("matches", JSONObject().put("date", date))
    fun messagesSeen(ids: List<Int>) = post("messages_seen", JSONObject().put("ids", org.json.JSONArray(ids)))
}
