package tv.ninekpro.app.data

import android.os.Build
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import tv.ninekpro.app.BuildConfig
import java.util.concurrent.TimeUnit

/** Talks to IPTV Command → /api/myapp. Every call is a POST {op:...}; device calls carry the Bearer token. */
class PanelApi(private val prefs: Prefs) {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val url = BuildConfig.PANEL_URL + "/api/myapp"
    private val json = "application/json; charset=utf-8".toMediaType()

    class PanelResult(val ok: Boolean, val error: String, val json: JSONObject)

    private fun meta(o: JSONObject): JSONObject = o.put("mac", prefs.deviceMac()).put("model", (Build.MANUFACTURER + " " + Build.MODEL).trim())
        .put("platform", if (DeviceInfo.isTv) "tv" else "phone").put("app_version", BuildConfig.VERSION_NAME)

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
    fun subsSearch(query: String, lang: String, season: Int = 0, episode: Int = 0, year: String = "") = post("subs_search", JSONObject().put("query", query).put("lang", lang).put("season", season).put("episode", episode).put("year", year))
    fun matches(date: String) = post("matches", JSONObject().put("date", date))
    fun weather() = post("weather")
    fun subsGet(fileId: Long) = post("subs_get", JSONObject().put("file_id", fileId))
    fun messagesSeen(ids: List<Int>) = post("messages_seen", JSONObject().put("ids", org.json.JSONArray(ids)))
}

object DeviceInfo {
    @Volatile var isTv: Boolean = false
    /** Weak device (< 2 GB RAM or Android "low RAM" flag): smaller images, no blur. */
    @Volatile var lite: Boolean = false
}
