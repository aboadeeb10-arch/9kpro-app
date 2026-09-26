package tv.ninekpro.app.data

import android.content.Context
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

/** Small key/value store (SharedPreferences) for everything the app keeps locally. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("ninekpro", Context.MODE_PRIVATE)
    private val appCtx = ctx.applicationContext

    var token: String
        get() = sp.getString("token", "") ?: ""
        set(v) = sp.edit().putString("token", v).apply()

    var accountJson: String
        get() = sp.getString("account", "") ?: ""
        set(v) = sp.edit().putString("account", v).apply()

    var brandJson: String
        get() = sp.getString("brand", "") ?: ""
        set(v) = sp.edit().putString("brand", v).apply()

    var theme: String
        get() = sp.getString("theme", "") ?: ""
        set(v) = sp.edit().putString("theme", v).apply()

    var language: String            // "", "en", "ar", "iw"
        get() = sp.getString("lang", "") ?: ""
        set(v) = sp.edit().putString("lang", v).apply()

    var subtitleScale: Float
        get() = sp.getFloat("subScale", 1.3f)
        set(v) = sp.edit().putFloat("subScale", v).apply()

    var wifiOnlyDownloads: Boolean
        get() = sp.getBoolean("wifiOnly", true)
        set(v) = sp.edit().putBoolean("wifiOnly", v).apply()

    var activePlaylistId: String
        get() = sp.getString("activePl", "") ?: ""
        set(v) = sp.edit().putString("activePl", v).apply()

    var showHidden: Boolean
        get() = sp.getBoolean("showHidden", false)
        set(v) = sp.edit().putBoolean("showHidden", v).apply()

    var pin: String
        get() = sp.getString("pin", "") ?: ""
        set(v) = sp.edit().putString("pin", v).apply()

    private fun str(k: String, d: String) = sp.getString(k, d) ?: d
    private fun put(k: String, v: String) = sp.edit().putString(k, v).apply()
    var liveFormat: String get() = str("liveFormat", "auto"); set(v) = put("liveFormat", v)      // auto | hls | ts
    var playerEngine: String get() = str("engine", "auto"); set(v) = put("engine", v)          // auto | exo | vlc | external
    var timeFormat: String get() = str("timeFmt", "24"); set(v) = put("timeFmt", v)            // 12 | 24
    var layoutSize: String get() = str("layout", "normal"); set(v) = put("layout", v)          // compact | normal | large
    var liveSort: String get() = str("liveSort", "default"); set(v) = put("liveSort", v)       // default | az | number
    var deviceType: String get() = str("devType", "auto"); set(v) = put("devType", v)          // auto | tv | phone
    var autoRefresh: Boolean get() = sp.getBoolean("autoRefresh", true); set(v) = sp.edit().putBoolean("autoRefresh", v).apply()
    var parentalOn: Boolean get() = sp.getBoolean("parentalOn", true); set(v) = sp.edit().putBoolean("parentalOn", v).apply()
    var subLang: String get() = str("subLang", "ar"); set(v) = put("subLang", v)
    var subColor: String get() = str("subColor", "white"); set(v) = put("subColor", v)

    /** Crash Fix: when the temporary line expires (ms), which playlist to go back to, and the trial playlist id. */
    var crashUntil: Long get() = sp.getLong("crashUntil", 0L); set(v) = sp.edit().putLong("crashUntil", v).apply()
    var crashMainPl: String get() = str("crashMainPl", ""); set(v) = put("crashMainPl", v)
    var crashPlId: String get() = str("crashPlId", ""); set(v) = put("crashPlId", v)

    var recSched: Set<String>
        get() = sp.getStringSet("rec_sched", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("rec_sched", v).apply()
    var epgReminders: Set<String>
        get() = sp.getStringSet("epg_reminders", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("epg_reminders", v).apply()
    var reminders: Set<Long>
        get() = (sp.getStringSet("reminders", emptySet()) ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()
        set(v) = sp.edit().putStringSet("reminders", v.map { it.toString() }.toSet()).apply()

    var lastSync: Long
        get() = sp.getLong("lastSync", 0L)
        set(v) = sp.edit().putLong("lastSync", v).apply()

    /** Playlists the user added himself (Xtream / M3U); panel playlists come from the account payload. */
    var manualPlaylists: List<Playlist>
        get() = readList("manualPl").map { Playlist.fromJson(it) }
        set(v) = sp.edit().putString("manualPl", JSONArray(v.map { it.toJson() }).toString()).apply()

    var panelPlaylists: List<Playlist>
        get() = readList("panelPl").map { Playlist.fromJson(it) }
        set(v) = sp.edit().putString("panelPl", JSONArray(v.map { it.toJson() }).toString()).apply()

    /** The customer's personal data: favorites, resume, order, hidden. Synced to the panel as-is. */
    var profile: JSONObject
        get() = try { JSONObject(sp.getString("profile", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }
        set(v) = sp.edit().putString("profile", v.toString()).apply()

    var profileDirty: Boolean
        get() = sp.getBoolean("profileDirty", false)
        set(v) = sp.edit().putBoolean("profileDirty", v).apply()

    private fun readList(k: String): List<JSONObject> = try {
        val a = JSONArray(sp.getString(k, "[]") ?: "[]"); (0 until a.length()).map { a.getJSONObject(it) }
    } catch (e: Exception) { emptyList() }

    /** Android 10+ hides the real Wi-Fi MAC; like every IPTV app we derive a stable per-device pseudo-MAC from ANDROID_ID. */
    fun deviceMac(): String {
        val cached = sp.getString("mac", "")
        if (!cached.isNullOrEmpty()) return cached
        val id = Settings.Secure.getString(appCtx.contentResolver, Settings.Secure.ANDROID_ID) ?: "0000000000000000"
        val hex = (id + "ninekpro").hashCode().toUInt().toString(16).padStart(8, '0') + id.take(4).padEnd(4, '0')
        val clean = hex.filter { it.isLetterOrDigit() }.lowercase().take(12).padEnd(12, '0')
        val mac = clean.chunked(2).joinToString(":")
        sp.edit().putString("mac", mac).apply()
        return mac
    }

    /** 6-digit device key shown next to the MAC (stable per device). */
    fun deviceKey(): String { val h = (deviceMac() + "key").hashCode().toUInt() % 900000u + 100000u; return h.toString() }

    fun clearSession() { sp.edit().remove("token").remove("account").remove("panelPl").remove("profile").remove("profileDirty").apply() }
}
