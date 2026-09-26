package tv.ninekpro.desktop.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Everything the app keeps locally, in one JSON file inside the user's app-data folder (survives updates). */
object AppDirs {
    val os: String = System.getProperty("os.name").lowercase()
    val isWindows get() = os.contains("win")
    val isMac get() = os.contains("mac")
    val data: File by lazy {
        val home = System.getProperty("user.home")
        val d = when {
            isWindows -> File(System.getenv("LOCALAPPDATA") ?: "$home/AppData/Local", "9KProTV/data")
            isMac -> File(home, "Library/Application Support/9KProTV")
            else -> File(home, ".ninekpro")
        }
        d.mkdirs(); d
    }
    val cache: File by lazy { File(data, "cache").apply { mkdirs() } }
    val images: File by lazy { File(cache, "img").apply { mkdirs() } }
}

class Prefs {
    private val file = File(AppDirs.data, "prefs.json")
    private val o: JSONObject = try { JSONObject(file.readText()) } catch (e: Exception) { JSONObject() }
    private val lock = Any()
    private fun save() { synchronized(lock) { try { val t = File(file.path + ".tmp"); t.writeText(o.toString()); java.nio.file.Files.move(t.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING) } catch (e: Exception) { try { file.writeText(o.toString()) } catch (e2: Exception) {} } } }
    private fun str(k: String, d: String = "") = o.optString(k, d)
    private fun put(k: String, v: Any?) { synchronized(lock) { o.put(k, v ?: JSONObject.NULL) }; save() }

    var token: String get() = str("token"); set(v) = put("token", v)
    var accountJson: String get() = str("account"); set(v) = put("account", v)
    var brandJson: String get() = str("brand"); set(v) = put("brand", v)
    var theme: String get() = str("theme"); set(v) = put("theme", v)
    var language: String get() = str("lang"); set(v) = put("lang", v)          // "", en, ar, iw
    var activePlaylistId: String get() = str("activePl"); set(v) = put("activePl", v)
    var pin: String get() = str("pin"); set(v) = put("pin", v)
    var liveFormat: String get() = str("liveFormat", "auto"); set(v) = put("liveFormat", v)
    var timeFormat: String get() = str("timeFmt", "24"); set(v) = put("timeFmt", v)
    var liveSort: String get() = str("liveSort", "default"); set(v) = put("liveSort", v)
    var autoRefresh: Boolean get() = o.optBoolean("autoRefresh", true); set(v) = put("autoRefresh", v)
    var parentalOn: Boolean get() = o.optBoolean("parentalOn", true); set(v) = put("parentalOn", v)
    var subLang: String get() = str("subLang", "ar"); set(v) = put("subLang", v)
    var subScale: Float get() = o.optDouble("subScale", 1.2).toFloat(); set(v) = put("subScale", v.toDouble())
    var volume: Int get() = o.optInt("volume", 100); set(v) = put("volume", v)
    var crashUntil: Long get() = o.optLong("crashUntil", 0L); set(v) = put("crashUntil", v)
    var crashMainPl: String get() = str("crashMainPl"); set(v) = put("crashMainPl", v)
    var crashPlId: String get() = str("crashPlId"); set(v) = put("crashPlId", v)
    var lastSync: Long get() = o.optLong("lastSync", 0L); set(v) = put("lastSync", v)
    var pendingUpdate: String get() = str("pendingUpdate"); set(v) = put("pendingUpdate", v)   // "version|path" downloaded, applied on next start
    var windowW: Int get() = o.optInt("winW", 1280); set(v) = put("winW", v)
    var windowH: Int get() = o.optInt("winH", 760); set(v) = put("winH", v)

    var manualPlaylists: List<Playlist>
        get() = readList("manualPl").map { Playlist.fromJson(it) }
        set(v) = put("manualPl", JSONArray(v.map { it.toJson() }).toString())
    var panelPlaylists: List<Playlist>
        get() = readList("panelPl").map { Playlist.fromJson(it) }
        set(v) = put("panelPl", JSONArray(v.map { it.toJson() }).toString())
    var profile: JSONObject
        get() = try { JSONObject(str("profile", "{}")) } catch (e: Exception) { JSONObject() }
        set(v) = put("profile", v.toString())
    var profileDirty: Boolean get() = o.optBoolean("profileDirty", false); set(v) = put("profileDirty", v)

    private fun readList(k: String): List<JSONObject> = try { val a = JSONArray(str(k, "[]")); (0 until a.length()).map { a.getJSONObject(it) } } catch (e: Exception) { emptyList() }

    /** Stable per-computer ID in MAC form (the panel treats it exactly like a phone/TV MAC). Derived once from the machine, then kept. */
    fun deviceMac(): String {
        val cached = str("mac"); if (cached.isNotEmpty()) return cached
        val parts = StringBuilder()
        try { parts.append(java.net.InetAddress.getLocalHost().hostName) } catch (e: Exception) {}
        parts.append('|').append(System.getProperty("user.name") ?: "")
        try {
            val nics = java.net.NetworkInterface.getNetworkInterfaces().toList().filter { !it.isLoopback && !it.isVirtual && it.hardwareAddress != null }.sortedBy { it.name }
            nics.firstOrNull()?.hardwareAddress?.let { parts.append('|').append(it.joinToString(":") { b -> "%02x".format(b) }) }
        } catch (e: Exception) {}
        if (parts.length < 8) parts.append(java.util.UUID.randomUUID().toString())
        val h = MessageDigest.getInstance("SHA-1").digest(parts.toString().toByteArray()).joinToString("") { "%02x".format(it) }
        val mac = ("d0" + h.take(10)).chunked(2).joinToString(":")
        put("mac", mac); return mac
    }
    fun deviceKey(): String { val h = (deviceMac() + "key").hashCode().toUInt() % 900000u + 100000u; return h.toString() }
    fun clearSession() { synchronized(lock) { listOf("token", "account", "panelPl", "profile", "profileDirty").forEach { o.remove(it) } }; save() }
}
