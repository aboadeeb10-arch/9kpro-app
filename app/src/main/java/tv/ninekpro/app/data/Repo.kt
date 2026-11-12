package tv.ninekpro.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import tv.ninekpro.app.BuildConfig
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Movies / Series tab state kept across navigation (category, categories-or-items pane on narrow screens, scroll). */
class VodUi { @Volatile var cat: String = "__all"; @Volatile var showList: Boolean = false; @Volatile var scroll: Int = 0 }

/** A newer sideload APK on the panel: version code, version name and the download link. */
data class UpdateInfo(val code: Int, val name: String, val url: String)

/**
 * The one place the UI reads from. Holds the session (account/brand/playlists/messages), the content of each playlist,
 * and the customer's profile (favorites, resume, order, hidden) which is synced to the panel.
 */
/** An "M3U" link that is really an Xtream get.php link → use it as an Xtream playlist (player_api) — panels like Neo 4K answer 403 to get.php downloads. */
fun asXtream(pl: Playlist): Playlist {
    if (pl.kind != "m3u") return pl
    val u = pl.hosts.firstOrNull() ?: return pl
    return try { val x = android.net.Uri.parse(u); val user = x.getQueryParameter("username"); val pass = x.getQueryParameter("password")
        if (user.isNullOrEmpty() || pass.isNullOrEmpty() || x.host.isNullOrEmpty()) pl else pl.copy(kind = "xtream", hosts = listOf(x.scheme + "://" + x.host + (if (x.port > 0) ":" + x.port else "")) + pl.hosts.drop(1), user = user, pass = pass)
    } catch (e: Exception) { pl }
}
class Repo(private val ctx: Context) {
    val prefs = Prefs(ctx)
    val panel = PanelApi(prefs)
    val xtream = XtreamApi()
    val tmdb = Tmdb { brand.value.tmdb }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val brand = MutableStateFlow(if (prefs.brandJson.isNotEmpty()) Brand.fromJson(JSONObject(prefs.brandJson)) else Brand())
    val account = MutableStateFlow(Account.fromJson(if (prefs.accountJson.isNotEmpty()) JSONObject(prefs.accountJson) else null))
    val playlists = MutableStateFlow(prefs.panelPlaylists + prefs.manualPlaylists)
    val messages = MutableStateFlow<List<Message>>(emptyList())
    val crashTrialAvailable = MutableStateFlow(false)
    val profileVersion = MutableStateFlow(0)          // bump to redraw lists after favorites/order changes
    /** Bumped when the lists must be read again (Refresh, playlist changed by the panel, sign out): every open Live/Movies/Series screen reloads. */
    val contentVersion = MutableStateFlow(0)
    /** Last "hosts/account version" the panel sent (payload or heartbeat); a different one means the panel changed this customer's playlists → reload. */
    @Volatile private var lastHv = ""
    /** At least one activity is started (SmileApp counts them): heartbeat and host ping run at full rate only then, or while something plays. */
    @Volatile var foreground = false

    val loggedIn: Boolean get() = prefs.token.isNotEmpty() || prefs.manualPlaylists.isNotEmpty()

    // ---------------- session ----------------
    private fun safeId(s: String) = s.replace(Regex("[^A-Za-z0-9_.-]"), "_")
    /** Content-based playlist id: the same line keeps its id when the panel re-orders or removes other lines (ids used to be position-based: "p0_user"). */
    private fun plId(kind: String, panel: String, user: String, hosts: List<String>): String =
        if (kind == "m3u") "p_m3u_" + (hosts.firstOrNull() ?: "").hashCode() else "p_" + kind + "_" + safeId(panel) + "_" + (if (user.isNotEmpty()) safeId(user) else hosts.joinToString(",").hashCode().toString())
    /** What identifies one line across payloads: kind|panel|user (+ the link for M3U). */
    private fun ident(p: Playlist) = p.kind + "|" + p.panel + "|" + p.user + (if (p.kind == "m3u") "|" + (p.hosts.firstOrNull() ?: "") else "")
    /** Old position-based id "p<i>_<user or host hash>" → the new id of the same line; null when it is not an old id or the line is gone. */
    private fun legacyId(id: String, pls: List<Playlist>): String? {
        val m = Regex("^p(\\d+)_(.*)$").find(id) ?: return null
        val i = m.groupValues[1].toIntOrNull() ?: return null; val rest = m.groupValues[2]
        val c = pls.filter { it.user == rest || (it.user.isEmpty() && (it.hosts.firstOrNull()?.hashCode()?.toString() ?: "") == rest) }
        return (if (c.size == 1) c[0] else c.firstOrNull { pls.indexOf(it) == i } ?: c.firstOrNull())?.id
    }

    fun applyPayload(j: JSONObject) {
        j.optJSONObject("brand")?.let { prefs.brandJson = it.toString(); brand.value = Brand.fromJson(it) }
        j.optJSONObject("account")?.let { prefs.accountJson = it.toString(); account.value = Account.fromJson(it) }
        val lines = j.optJSONArray("lines")
        var oldToNew: Map<String, String> = emptyMap()
        if (lines != null) {
            val used = HashSet<String>()
            val pls = (0 until lines.length()).map { i ->
                val l = lines.getJSONObject(i)
                val hosts = l.optJSONArray("hosts")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
                val kind = if (l.optString("kind") == "m3u") "m3u" else "xtream"; val user = l.optString("user"); val panelName = l.optString("panel")
                var id = plId(kind, panelName, user, hosts); val b = id; var n = 2; while (!used.add(id)) id = b + "_" + n++   // the same line twice → _2, _3 …
                asXtream(Playlist(id = id, name = l.optString("name"), kind = kind, hosts = hosts, user = user,
                    pass = l.optString("pass"), panel = panelName, trial = l.optBoolean("trial"), exp = l.optString("exp"), fromPanel = true, protect = l.optBoolean("protect")))
            }
            val oldPls = prefs.panelPlaylists
            // one-time move from the old ids: map old → new for the same line and rewrite everything that stored an id (profile keys, active id, cache file names)
            val taken = HashSet<String>()   // two identical lines: the first old one maps to the first new one, the second to "_2"
            oldToNew = oldPls.mapNotNull { o -> val oi = ident(o); val nw = pls.firstOrNull { ident(it) == oi && taken.add(it.id) }; if (nw != null && nw.id != o.id) o.id to nw.id else null }.toMap()
            val res = { id: String -> oldToNew[id] ?: legacyId(id, pls) }
            // one-time move; a broken old profile must never stop the login / refresh that carries it
            val migrated = if (oldToNew.isNotEmpty() || !prefs.plIdV2) { val ch = try { migrateIds(res) } catch (e: Throwable) { false }; prefs.plIdV2 = true; ch } else false
            // the panel sends hosts healthiest-first (10-minute checks): follow it, so a customer on a dead host moves to a working one by himself
            for (pl in pls) { val best = pl.hosts.firstOrNull() ?: continue; val cur = hostFor[pl.id]; if (cur != null && cur != xtream.baseUrl(best)) { hostFor[pl.id] = xtream.baseUrl(best); cache.keys.filter { it.startsWith(pl.id + ":") }.forEach { cache.remove(it) } } }
            // Did the seller change this customer's playlists (another line, new password, other M3U link, line removed)? Then the old lists must go —
            // memory AND the 24 h disk cache — otherwise the TV keeps showing the previous playlist until the cache expires.
            val sig = { p: Playlist -> p.kind + "|" + p.user + "|" + p.pass + "|" + p.panel + (if (p.kind == "m3u") "|" + (p.hosts.firstOrNull() ?: "") else "") }
            val old = oldPls.associate { (oldToNew[it.id] ?: it.id) to sig(it) }
            val changedIds = pls.filter { old[it.id] != null && old[it.id] != sig(it) }.map { it.id } + (old.keys - pls.map { it.id }.toSet())
            prefs.panelPlaylists = pls
            playlists.value = pls + prefs.manualPlaylists
            if (changedIds.isNotEmpty() || migrated) {
                changedIds.forEach { dropCaches(it) }
                if (prefs.activePlaylistId.isEmpty() || playlists.value.none { it.id == prefs.activePlaylistId }) prefs.activePlaylistId = playlists.value.firstOrNull()?.id ?: ""
                // (9 Nov 2026) no resetUi() here: hosts reorders from the panel happen every few minutes and were throwing the customer back to the top of Movies/Series; the activePlaylist getter still resets when the active id really changes
                contentVersion.value++; preloadJob?.cancel(); preloadJob = null; preloadAll()
            }
        }
        j.optString("hv").let { if (it.isNotEmpty()) lastHv = it }
        j.optJSONArray("messages")?.let { a -> messages.value = (0 until a.length()).map { val m = a.getJSONObject(it); Message(m.optInt("id"), m.optString("title"), m.optString("text"), m.optString("kind"), m.optString("image"), m.optString("link")) } }
        crashTrialAvailable.value = j.optBoolean("crash_trial")
        // cloud profile: the panel copy wins only when the local one was never changed (fresh install); its keys may still carry old playlist ids
        j.optJSONObject("sync")?.let { s -> if (!prefs.profileDirty && s.length() > 0) {
            val pls = playlists.value.filter { it.fromPanel }; val map = oldToNew
            val moved = migrateProfile(s) { id -> map[id] ?: legacyId(id, pls) }
            prefs.profile = s; profCache = s; favCache = null; resumeCache = null; lockedCache = null; hiddenCache = null; profileVersion.value++
            if (moved) { prefs.profileDirty = true; scheduleSync() } } }
        if (prefs.activePlaylistId.isEmpty() && playlists.value.isNotEmpty()) prefs.activePlaylistId = playlists.value.first().id
    }

    /** "<pl>:KIND:id" keys and "cats:<pl>:KIND" / "items:<pl>:KIND:cat" list ids with the playlist part mapped through `res`. */
    private fun migKey(s: String, res: (String) -> String?): String {
        val parts = s.split(":"); val i = if (parts.size >= 2 && (parts[0] == "cats" || parts[0] == "items")) 1 else 0
        if (parts.size <= i) return s
        val n = res(parts[i]) ?: return s
        return (parts.take(i) + n + parts.drop(i + 1)).joinToString(":")
    }
    /** Rewrites the playlist ids inside a profile (favorites, hidden, locked, order keys + values, resume keys + "pl" inside extra, lastLive). True when anything changed. */
    private fun migrateProfile(p: JSONObject, res: (String) -> String?): Boolean {
        var ch = false
        for (name in listOf("favorites", "hidden", "locked")) { val a = p.optJSONArray(name) ?: continue; val l = (0 until a.length()).map { a.optString(it) }; val m = l.map { migKey(it, res) }; if (m != l) { p.put(name, JSONArray(m)); ch = true } }
        p.optJSONObject("order")?.let { o -> val no = JSONObject(); var c = false
            for (k in o.keys().asSequence().toList()) { val a = o.optJSONArray(k) ?: continue; val l = (0 until a.length()).map { a.optString(it) }; val m = l.map { migKey(it, res) }; val nk = migKey(k, res); if (nk != k || m != l) c = true; no.put(nk, JSONArray(m)) }
            if (c) { p.put("order", no); ch = true } }
        p.optJSONObject("resume")?.let { o -> val nr = JSONObject(); var c = false
            for (k in o.keys().asSequence().toList()) { val e = o.optJSONObject(k) ?: continue; val nk = migKey(k, res); if (nk != k) { c = true; e.put("key", nk) }
                val ex = e.optString("extra"); if (ex.isNotEmpty()) try { val x = JSONObject(ex); val np = res(x.optString("pl")); if (np != null) { x.put("pl", np); e.put("extra", x.toString()); c = true } } catch (t: Exception) {}
                nr.put(nk, e) }
            if (c) { p.put("resume", nr); ch = true } }
        val ll = p.optString("lastLive"); if (ll.isNotEmpty()) { val n = migKey(ll, res); if (n != ll) { p.put("lastLive", n); ch = true } }
        return ch
    }
    /** Moves everything stored under old playlist ids to the new ones: profile, active / Crash Fix ids, disk cache file names (nothing is re-downloaded), memory caches. */
    private fun migrateIds(res: (String) -> String?): Boolean {
        var changed = false
        try { val p = prefs.profile; if (migrateProfile(p, res)) { prefs.profile = p; prefs.profileDirty = true; invalidateProf(); scheduleSync(); changed = true } } catch (e: Exception) {}
        res(prefs.activePlaylistId)?.let { prefs.activePlaylistId = it; changed = true }
        res(prefs.crashMainPl)?.let { prefs.crashMainPl = it }
        res(prefs.crashPlId)?.let { prefs.crashPlId = it }
        val ids = (cache.keys.map { it.substringBefore(":") } + hostFor.keys + epgCache.keys + m3uCache.keys + prefs.panelPlaylists.map { it.id }).toSet()
        for (old in ids) {
            val nw = res(old) ?: continue; changed = true
            try { for (k in Kind.values()) { val f = File(ctx.cacheDir, "c2_${old.hashCode()}_${k.name}.json"); if (f.exists()) f.renameTo(File(ctx.cacheDir, "c2_${nw.hashCode()}_${k.name}.json")) }
                val e = File(ctx.cacheDir, "epg_${old.hashCode()}.json"); if (e.exists()) e.renameTo(File(ctx.cacheDir, "epg_${nw.hashCode()}.json")) } catch (e: Exception) {}
            hostFor.remove(old)?.let { hostFor[nw] = it }
            // memory copies carry the old id inside every item key: drop them, they come back from the renamed disk files in a moment
            cache.keys.filter { it.startsWith(old + ":") }.forEach { cache.remove(it) }
            seriesCache.keys.filter { it.startsWith(old + ":") }.forEach { seriesCache.remove(it) }
            shortCache.keys.filter { it.startsWith(old + ":") }.forEach { shortCache.remove(it) }
            m3uCache.remove(old); epgCache.remove(old); epgFailedAt.remove(old)
        }
        return changed
    }

    suspend fun login(user: String, pass: String): String? = withContext(Dispatchers.IO) {
        val r = panel.login(user, pass)
        if (!r.ok) return@withContext r.error.ifEmpty { "wrong_login" }
        if (prefs.token.isNotEmpty() && playlists.value.isNotEmpty()) { addExtraLogin(r.json); return@withContext null }
        prefs.loggedOut = false; prefs.token = r.json.optString("token"); applyPayload(r.json); null
    }

    suspend fun codeLogin(code: String): String? = withContext(Dispatchers.IO) {
        val r = panel.codeLogin(code)
        if (!r.ok) return@withContext r.error.ifEmpty { "wrong_login" }
        if (prefs.token.isNotEmpty() && playlists.value.isNotEmpty()) { addExtraLogin(r.json); return@withContext null }
        prefs.loggedOut = false; prefs.token = r.json.optString("token"); applyPayload(r.json); null
    }

    /** "Add playlist" while playlists already exist: keep the current session and ADD the new account's lines (kept as own playlists, deletable). */
    private fun addExtraLogin(j: JSONObject) {
        val lines = j.optJSONArray("lines") ?: return
        val have = playlists.value.map { it.user }.toSet()
        val add = (0 until lines.length()).map { lines.getJSONObject(it) }.filter { (it.optString("kind") == "m3u" || it.optString("user").isNotEmpty()) && it.optString("user") !in have }.map { l ->
            asXtream(Playlist(id = "x" + l.optString("user") + "_" + (System.currentTimeMillis() % 100000), name = l.optString("name").ifEmpty { l.optString("user") }, kind = if (l.optString("kind") == "m3u") "m3u" else "xtream",
                hosts = l.optJSONArray("hosts")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(), user = l.optString("user"), pass = l.optString("pass"), panel = l.optString("panel"),
                trial = l.optBoolean("trial"), exp = l.optString("exp"), fromPanel = false, protect = l.optBoolean("protect"))) }
        val tk = j.optString("token"); if (tk.isNotEmpty()) scope.launch { try { panel.post("logout", tokenOverride = tk) } catch (e: Exception) {} }   // close the extra device session
        if (add.isEmpty()) return
        prefs.manualPlaylists = prefs.manualPlaylists + add
        playlists.value = prefs.panelPlaylists + prefs.manualPlaylists
        prefs.activePlaylistId = add.first().id; cache.clear(); profileVersion.value++
    }

    suspend fun refreshSession(): String? = withContext(Dispatchers.IO) {
        if (prefs.token.isEmpty()) { panel.brand()?.let { b -> brand.value = b }; return@withContext null }
        val r = panel.refresh()
        if (r.ok) { applyPayload(r.json); null }
        else { if (r.error == "token") { dropSession(); try { deviceHello() } catch (e: Exception) {} }; r.error }   // expired 90-day token: forget it and let a MAC-assigned device register again
    }

    /** The panel no longer accepts this device's token (logged out from the panel, blocked, or the token expired): forget the session, keep own playlists. */
    private fun dropSession() {
        prefs.clearSession(); invalidateProf(); account.value = null; playlists.value = prefs.manualPlaylists
        if (prefs.activePlaylistId.isNotEmpty() && playlists.value.none { it.id == prefs.activePlaylistId }) prefs.activePlaylistId = playlists.value.firstOrNull()?.id ?: ""
        contentVersion.value++
    }

    /**
     * Sign out — completely. Closes the panel session, forgets the account, EVERY playlist (panel ones and ones typed in later), favorites/resume,
     * the cached lists on disk, and sets `loggedOut` so the app does not log this device in again by itself through its MAC (that is what made the
     * TV "come back" after signing out). Signing in, or pressing "Refresh — check for my playlist", switches that back on. Settings stay.
     */
    fun logout() {
        val tk = prefs.token
        if (tk.isNotEmpty()) scope.launch { try { panel.post("logout", tokenOverride = tk) } catch (e: Exception) {} }
        preloadJob?.cancel(); preloadJob = null; syncJob?.cancel(); syncJob = null
        val ids = playlists.value.map { it.id }
        prefs.clearAll(); prefs.loggedOut = true; invalidateProf()
        account.value = null; playlists.value = emptyList(); messages.value = emptyList(); crashTrialAvailable.value = false
        ids.forEach { dropCaches(it) }
        cache.clear(); hostFor.clear(); seriesCache.clear(); m3uCache.clear(); epgCache.clear(); epgFailedAt.clear(); shortCache.clear(); matchCache.clear(); inflight.clear()
        try { ctx.cacheDir.listFiles()?.filter { it.name.startsWith("c2_") || it.name.startsWith("epg_") }?.forEach { it.delete() } } catch (e: Exception) {}
        liveContext = emptyList(); resetUi(); uiPlId = ""; nowPlaying = null; lastHv = ""; unlockedUntil = 0L
        contentVersion.value++; profileVersion.value++
    }

    fun addManualPlaylist(pl0: Playlist) { val pl = asXtream(pl0); prefs.manualPlaylists = prefs.manualPlaylists + pl; playlists.value = prefs.panelPlaylists + prefs.manualPlaylists; if (prefs.activePlaylistId.isEmpty()) prefs.activePlaylistId = pl.id }
    fun removeManualPlaylist(id: String) { prefs.manualPlaylists = prefs.manualPlaylists.filter { it.id != id }; playlists.value = prefs.panelPlaylists + prefs.manualPlaylists; if (prefs.activePlaylistId == id) prefs.activePlaylistId = playlists.value.firstOrNull()?.id ?: "" }

    /** The playlist in use. When its id changed since the last look (Playlists screen, panel, Crash Fix) the remembered tab positions are forgotten. */
    val activePlaylist: Playlist? get() { val id = prefs.activePlaylistId; if (id != uiPlId) { uiPlId = id; resetUi() }; return playlists.value.firstOrNull { it.id == id } ?: playlists.value.firstOrNull() }
    @Volatile private var uiPlId = ""

    // ---------------- Crash Fix: temporary line from another panel for 24 h, then back to the main account ----------------
    suspend fun crashFix(): String? = withContext(Dispatchers.IO) {
        val r = panel.crashTrial()
        if (!r.ok) return@withContext when (r.error) { "cooldown" -> "cooldown:" + r.json.optInt("wait", 5); "daily_limit" -> "daily_limit:" + r.json.optInt("limit", 3); else -> r.error.ifEmpty { "failed" } }
        val user = r.json.optJSONObject("line")?.optString("user") ?: ""
        refreshSession()
        val trial = playlists.value.firstOrNull { it.trial && it.user == user } ?: playlists.value.filter { it.trial }.lastOrNull() ?: return@withContext "no_line"
        val cur = activePlaylist
        if (cur != null && !cur.trial) prefs.crashMainPl = cur.id
        prefs.crashPlId = trial.id; prefs.crashUntil = System.currentTimeMillis() + 24 * 3600_000L
        prefs.activePlaylistId = trial.id; cache.clear(); hostFor.remove(trial.id)
        // the backup server needs a few seconds before the new line answers: wait for the channel list (max ~60 s)
        for (i in 0 until 12) { crashFixProgress.value = i + 1; try { val c = content(trial, Kind.LIVE, force = true); if (c.items.isNotEmpty()) break } catch (e: Throwable) { hostFor.remove(trial.id) }; delay(5000) }
        crashFixProgress.value = 0
        null
    }
    /** After login / picking a playlist: load the channel list first (retrying while the server warms up), then start Movies/Series in the background. */
    suspend fun prepareActive(): Boolean {
        val pl = activePlaylist ?: return false
        var ok = false
        for (i in 0 until 12) { crashFixProgress.value = i + 1; try { val c = content(pl, Kind.LIVE); if (c.items.isNotEmpty()) { ok = true; break } } catch (e: Throwable) { hostFor.remove(pl.id) }; delay(5000) }
        crashFixProgress.value = 0; preloadAll(); return ok
    }
    /** 0 = idle, n = waiting attempt n while the new trial line becomes active. */
    val crashFixProgress = MutableStateFlow(0)
    fun crashFixActive(): Boolean = prefs.crashUntil > System.currentTimeMillis() && playlists.value.any { it.id == prefs.crashPlId }
    fun crashFixHoursLeft(): Int = ((prefs.crashUntil - System.currentTimeMillis()) / 3600_000L).toInt().coerceAtLeast(0)
    /** Back to the main account (pressed "Back now", or the 24 h passed). */
    fun endCrashFix() {
        val main = playlists.value.firstOrNull { it.id == prefs.crashMainPl && !it.trial } ?: playlists.value.firstOrNull { !it.trial }
        prefs.crashUntil = 0L; prefs.crashPlId = ""; prefs.crashMainPl = ""
        if (main != null) prefs.activePlaylistId = main.id
        cache.clear()
        scope.launch { try { panel.crashEnd(); refreshSession() } catch (e: Exception) {} }
    }
    /** Called at start and every minute: switch back automatically when the 24 h are over. */
    fun checkCrashFix() { if (prefs.crashUntil > 0 && System.currentTimeMillis() > prefs.crashUntil) endCrashFix() }

    /**
     * Load Live, Movies and Series (and the guide) in the background so every tab opens instantly. Started when the app process starts, after
     * login / playlist change and on Refresh. Live first (Home needs it), then Movies and Series side by side (one after the other on weak boxes).
     */
    private var preloadJob: Job? = null
    fun preloadAll() {
        val pl = activePlaylist ?: return
        if (preloadJob?.isActive == true) return
        preloadJob = scope.launch {
            try { content(pl, Kind.LIVE) } catch (e: Throwable) {}
            if (DeviceInfo.lite) { for (k in listOf(Kind.MOVIE, Kind.SERIES)) try { content(pl, k) } catch (e: Throwable) {} }
            else listOf(Kind.MOVIE, Kind.SERIES).map { k -> launch { try { content(pl, k) } catch (e: Throwable) {} } }.forEach { it.join() }
            try { tv.ninekpro.app.TvHomeRow.update(ctx, this@Repo) } catch (e: Throwable) {}; try { tv.ninekpro.app.SmileWidget.refresh(ctx) } catch (e: Throwable) {} }
    }
    /** Live items already in memory (for the widget / TV row; no network). */
    fun cachedLive(pl: Playlist): List<Item> = cache[pl.id + ":" + Kind.LIVE.name]?.items ?: emptyList()

    fun markMessagesSeen(ids: List<Int>) { messages.value = messages.value.filter { it.id !in ids }; scope.launch { try { panel.messagesSeen(ids) } catch (e: Exception) {} } }

    // ---------------- content ----------------
    // read on the UI thread, written from several IO coroutines at once (preload) → concurrent maps
    private val cache = ConcurrentHashMap<String, XtreamApi.Content>()          // key playlistId:kind
    private val hostFor = ConcurrentHashMap<String, String>()
    private val hostInflight = ConcurrentHashMap<String, Deferred<String>>()    // one host race per playlist, however many screens ask at once
    private val seriesCache = ConcurrentHashMap<String, SeriesInfo>()
    private val m3uCache = ConcurrentHashMap<String, Map<Kind, XtreamApi.Content>>()
    private val inflight = ConcurrentHashMap<String, Deferred<XtreamApi.Content>>()   // one download per list, however many screens ask for it

    private fun cacheFile(pl: Playlist, kind: Kind) = File(ctx.cacheDir, "c2_${pl.id.hashCode()}_${kind.name}.json")
    /** Forget everything cached for one playlist id: memory lists, host, guide, and the files on disk. */
    private fun dropCaches(id: String) {
        cache.keys.filter { it.startsWith(id + ":") }.forEach { cache.remove(it) }
        seriesCache.keys.filter { it.startsWith(id + ":") }.forEach { seriesCache.remove(it) }
        shortCache.keys.filter { it.startsWith(id + ":") }.forEach { shortCache.remove(it) }
        m3uCache.remove(id); hostFor.remove(id); epgCache.remove(id); epgFailedAt.remove(id)
        try { for (k in Kind.values()) File(ctx.cacheDir, "c2_${id.hashCode()}_${k.name}.json").delete(); File(ctx.cacheDir, "epg_${id.hashCode()}.json").delete() } catch (e: Exception) {}
    }

    /** The working host of a playlist (remembered). Several callers at once share one probe race instead of each starting their own. */
    suspend fun host(pl: Playlist): String {
        hostFor[pl.id]?.let { return it }
        val d = synchronized(hostInflight) { hostInflight[pl.id] ?: scope.async { try { xtream.pickHost(pl).also { hostFor[pl.id] = it } } finally { hostInflight.remove(pl.id) } }.also { hostInflight[pl.id] = it } }
        return d.await()
    }
    fun forgetHost(pl: Playlist) { hostFor.remove(pl.id) }
    /** Change server: pin one of the playlist's hosts and reload from it. */
    fun setHost(pl: Playlist, h: String) { hostFor[pl.id] = xtream.baseUrl(h); cache.keys.filter { it.startsWith(pl.id + ":") }.forEach { cache.remove(it) } }
    fun currentHost(pl: Playlist): String = hostFor[pl.id] ?: ""

    /** Content for a playlist+kind: memory → disk cache (instant start) → network. `force` re-downloads. M3U lists are cached on disk the same way. */
    suspend fun content(pl: Playlist, kind: Kind, force: Boolean = false): XtreamApi.Content = withContext(Dispatchers.IO) {
        val k = pl.id + ":" + kind.name
        if (!force) cache[k]?.let { if (kind == Kind.LIVE) ensureEpg(pl, it.items); return@withContext it }
        if (!force) {
            val f = cacheFile(pl, kind)
            if (f.exists()) {
                try { val c = readContent(f, pl, kind); cache[k] = c
                    val old = System.currentTimeMillis() - f.lastModified() > 24 * 3600_000L
                    if (old && prefs.autoRefresh) scope.launch { try { refreshContent(pl, kind) } catch (e: Throwable) {} }
                    if (kind == Kind.LIVE) ensureEpg(pl, c.items)
                    return@withContext c } catch (e: Throwable) { }
            }
        }
        val c = refreshContent(pl, kind)
        if (kind == Kind.LIVE) ensureEpg(pl, c.items)
        c
    }

    /** Downloads one list. Preload and an open screen may ask for the same list at the same moment: they share one download (one M3U download fills all three kinds). */
    private suspend fun refreshContent(pl: Playlist, kind: Kind): XtreamApi.Content {
        val k = if (pl.kind == "m3u") pl.id + ":m3u" else pl.id + ":" + kind.name
        val d = synchronized(inflight) { inflight[k] ?: scope.async { try { downloadContent(pl, kind) } finally { inflight.remove(k) } }.also { inflight[k] = it } }
        val c = d.await()
        return if (pl.kind == "m3u") (m3uCache[pl.id]?.get(kind) ?: c) else c
    }
    private suspend fun downloadContent(pl: Playlist, kind: Kind): XtreamApi.Content {
        if (pl.kind == "m3u") {
            val all = xtream.loadM3u(pl); m3uCache[pl.id] = all
            val empty = XtreamApi.Content(emptyList(), emptyList())
            if (all.values.sumOf { it.items.size } == 0) return all[kind] ?: empty      // nothing in the link right now: never cache that
            for ((kk, c) in all) { cache[pl.id + ":" + kk.name] = c; try { writeContent(cacheFile(pl, kk), c) } catch (e: Exception) { } }
            return all[kind] ?: empty
        }
        val h = host(pl)
        val c = try { xtream.load(h, pl, kind) } catch (e: Exception) { forgetHost(pl); xtream.load(host(pl), pl, kind) }
        if (c.items.isEmpty()) return c            // a just-created line answers empty for a while: never cache that
        cache[pl.id + ":" + kind.name] = c
        try { writeContent(cacheFile(pl, kind), c) } catch (e: Exception) { }
        return c
    }

    private fun writeContent(f: File, c: XtreamApi.Content) {
        val tmp = File(f.path + ".tmp")
        android.util.JsonWriter(java.io.OutputStreamWriter(java.io.FileOutputStream(tmp), Charsets.UTF_8)).use { w ->
            w.beginObject(); w.name("cats"); w.beginArray()
            for (x in c.categories) { w.beginObject(); w.name("i").value(x.id); w.name("n").value(x.name); w.endObject() }
            w.endArray(); w.name("items"); w.beginArray()
            for (it in c.items) { w.beginObject(); w.name("i").value(it.id); w.name("n").value(it.name); w.name("c").value(it.categoryId); w.name("ic").value(it.icon); w.name("e").value(it.ext); w.name("r").value(it.rating); w.name("a").value(it.added); w.name("y").value(it.year); w.name("g").value(it.epgId); w.name("num").value(it.num.toLong()); w.name("ar").value(it.archive); w.name("ad").value(it.archiveDays.toLong())
                if (it.directUrl.isNotEmpty()) w.name("u").value(it.directUrl)   // M3U stream link
                w.endObject() }
            w.endArray(); w.endObject()
        }
        tmp.renameTo(f)
    }

    private fun readContent(f: File, pl: Playlist, kind: Kind): XtreamApi.Content {
        val cats = ArrayList<Category>(); val items = ArrayList<Item>(4096)
        android.util.JsonReader(java.io.InputStreamReader(java.io.FileInputStream(f), Charsets.UTF_8)).use { r ->
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "cats" -> { r.beginArray(); while (r.hasNext()) { r.beginObject(); var i = ""; var n = ""; while (r.hasNext()) { when (r.nextName()) { "i" -> i = r.nextString(); "n" -> n = r.nextString(); else -> r.skipValue() } }; r.endObject(); cats.add(Category(i, n, kind)) }; r.endArray() }
                    "items" -> { r.beginArray(); while (r.hasNext()) { r.beginObject(); var i = ""; var n = ""; var c = ""; var ic = ""; var e = ""; var rt = ""; var a = 0L; var y = ""; var g = ""; var num = 0; var ar = false; var ad = 0; var u = ""
                        while (r.hasNext()) { when (r.nextName()) { "i" -> i = r.nextString(); "n" -> n = r.nextString(); "c" -> c = r.nextString(); "ic" -> ic = r.nextString(); "e" -> e = r.nextString(); "r" -> rt = r.nextString(); "a" -> a = r.nextLong(); "y" -> y = r.nextString(); "g" -> g = r.nextString(); "num" -> num = r.nextInt(); "ar" -> ar = r.nextBoolean(); "ad" -> ad = r.nextInt(); "u" -> u = r.nextString(); else -> r.skipValue() } }
                        r.endObject(); items.add(Item(i, n, kind, c, ic, e, rt, a, y, "", g, num, pl.id, directUrl = u, archive = ar, archiveDays = ad)) }; r.endArray() }
                    else -> r.skipValue()
                }
            }
            r.endObject()
        }
        return XtreamApi.Content(cats.distinctBy { it.id }, items.distinctBy { it.key })   // category ids are LazyList keys: duplicates crash the list
    }

    suspend fun tmdbFor(name: String, year: String, isSeries: Boolean): TmdbInfo? = withContext(Dispatchers.IO) { try { tmdb.lookup(name, year, isSeries) } catch (e: Throwable) { null } }
    suspend fun vodInfo(pl: Playlist, id: String): JSONObject = withContext(Dispatchers.IO) { if (pl.kind != "xtream") JSONObject() else xtream.vodInfo(host(pl), pl, id) }
    suspend fun seriesInfo(pl: Playlist, id: String): SeriesInfo = withContext(Dispatchers.IO) {
        seriesCache[pl.id + ":" + id] ?: xtream.seriesInfo(host(pl), pl, id).also { seriesCache[pl.id + ":" + id] = it }
    }

    suspend fun streamUrl(pl: Playlist, item: Item, ts: Boolean = false): String = withContext(Dispatchers.IO) {
        if (item.directUrl.isNotEmpty()) return@withContext item.directUrl
        val h = host(pl)
        when (item.kind) { Kind.LIVE -> if (ts) xtream.liveUrlTs(h, pl, item) else xtream.liveUrl(h, pl, item); Kind.MOVIE -> xtream.movieUrl(h, pl, item); Kind.SERIES -> "" }
    }
    suspend fun episodeUrl(pl: Playlist, ep: Episode): String = withContext(Dispatchers.IO) { xtream.episodeUrl(host(pl), pl, ep) }

    /** Settings → Update Now / top-bar Refresh: drop the active playlist's caches (memory + disk) and re-download. The open screen reloads at once (contentVersion). */
    fun forceReload() { val pl = activePlaylist ?: return; dropCaches(pl.id); seriesCache.clear(); contentVersion.value++; preloadJob?.cancel(); preloadJob = null; preloadAll() }
    /** Refresh = first ask the panel which playlists this customer has now (a playlist changed in the panel shows up right away), then reload the lists. */
    suspend fun refreshAll() { try { if (prefs.token.isNotEmpty()) refreshSession() } catch (e: Throwable) {}; forceReload() }
    fun cachedContent(pl: Playlist, kind: Kind): XtreamApi.Content? = cache[pl.id + ":" + kind.name]
    /** Search across live/movies/series of the active playlist (cached content only). */
    fun searchCached(pl: Playlist, q: String): List<Item> {
        val s = q.trim().lowercase(); if (s.length < 2) return emptyList()
        return Kind.values().flatMap { k -> cache[pl.id + ":" + k.name]?.items?.filter { it.name.lowercase().contains(s) } ?: emptyList() }.distinctBy { it.key }.take(200)
    }

    // ---------------- EPG (XMLTV, loaded once per playlist in the background; keys are epg ids trimmed + lowercased) ----------------
    private val epgCache = ConcurrentHashMap<String, Map<String, List<EpgEntry>>>()
    private val epgLoading: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val epgFailedAt = ConcurrentHashMap<String, Long>()   // a failed guide download is tried again after 2 minutes, not cached as empty
    val epgVersion = MutableStateFlow(0)
    fun epgFor(pl: Playlist, item: Item): List<EpgEntry> { if (item.epgId.isEmpty()) return emptyList(); return epgCache[pl.id]?.get(item.epgId.trim().lowercase()) ?: emptyList() }
    fun nowNext(pl: Playlist, item: Item): Pair<EpgEntry?, EpgEntry?> { val now = System.currentTimeMillis() / 1000; val l = epgFor(pl, item); val i = l.indexOfFirst { it.stop > now }; if (i < 0) return null to null; return l[i] to l.getOrNull(i + 1) }
    private fun epgFile(pl: Playlist) = File(ctx.cacheDir, "epg_${pl.id.hashCode()}.json")
    /** Loads the guide once per playlist. `live` = the channel list, so only channels in it are kept in memory (defaults to the cached live list). */
    fun ensureEpg(pl: Playlist, live: List<Item>? = null) {
        if (pl.kind != "xtream" || epgCache.containsKey(pl.id)) return
        val failed = epgFailedAt[pl.id]; if (failed != null && System.currentTimeMillis() - failed < 2 * 60_000L) return
        if (!epgLoading.add(pl.id)) return
        val wanted = (live ?: cache[pl.id + ":" + Kind.LIVE.name]?.items)?.mapNotNull { it.epgId.trim().lowercase().ifEmpty { null } }?.toSet()
        scope.launch {
            try {
                val f = epgFile(pl)
                if (f.exists() && System.currentTimeMillis() - f.lastModified() < 12 * 3600_000L) { try { putEpg(pl.id, readEpg(f)); return@launch } catch (e: Throwable) { } }
                val m = xtream.loadEpg(host(pl), pl, wanted); putEpg(pl.id, m)
                try { writeEpg(f, m) } catch (e: Throwable) { }
            } catch (e: Throwable) { epgFailedAt[pl.id] = System.currentTimeMillis() } finally { epgLoading.remove(pl.id) }
        }
    }
    /** Keeps one guide in memory: the guides of playlists that are not active go when a new one arrives. */
    private fun putEpg(id: String, m: Map<String, List<EpgEntry>>) { epgCache.keys.filter { it != id && it != prefs.activePlaylistId }.forEach { epgCache.remove(it) }; epgCache[id] = m; epgVersion.value++ }
    private fun writeEpg(f: File, m: Map<String, List<EpgEntry>>) {
        android.util.JsonWriter(java.io.OutputStreamWriter(java.io.FileOutputStream(f), Charsets.UTF_8)).use { w -> w.beginObject(); for ((ch, l) in m) { w.name(ch); w.beginArray(); for (e in l) { w.beginArray(); w.value(e.start); w.value(e.stop); w.value(e.title); w.endArray() }; w.endArray() }; w.endObject() }
    }
    private fun readEpg(f: File): Map<String, List<EpgEntry>> {
        val out = HashMap<String, List<EpgEntry>>()
        android.util.JsonReader(java.io.InputStreamReader(java.io.FileInputStream(f), Charsets.UTF_8)).use { r -> r.beginObject(); while (r.hasNext()) { val ch = r.nextName().trim().lowercase(); val l = ArrayList<EpgEntry>(); r.beginArray(); while (r.hasNext()) { r.beginArray(); val a = r.nextLong(); val b = r.nextLong(); val t = r.nextString(); r.endArray(); l.add(EpgEntry(a, b, t)) }; r.endArray(); out[ch] = l }; r.endObject() }
        return out
    }
    suspend fun catchupUrl(pl: Playlist, item: Item, e: EpgEntry): String = withContext(Dispatchers.IO) { xtream.catchupUrl(host(pl), pl, item, e.start, ((e.stop - e.start) / 60).toInt().coerceAtLeast(1)) }

    private val shortCache = ConcurrentHashMap<String, Pair<Long, List<EpgEntry>>>()
    /** Now/next for one channel: full guide if loaded, else a quick per-channel request (cached 10 min). */
    suspend fun nowNextLive(pl: Playlist, item: Item): Pair<EpgEntry?, EpgEntry?> = withContext(Dispatchers.IO) {
        val full = nowNext(pl, item); if (full.first != null) return@withContext full
        if (pl.kind != "xtream") return@withContext null to null
        val k = pl.id + ":" + item.id; val now = System.currentTimeMillis() / 1000
        val cached = shortCache[k]; val list = if (cached != null && now - cached.first < 600) cached.second else try { xtream.shortEpg(host(pl), pl, item).also { shortCache[k] = now to it } } catch (e: Throwable) { emptyList() }
        val i = list.indexOfFirst { it.stop > now }; if (i < 0) null to null else list[i] to list.getOrNull(i + 1)
    }
    fun isAdultName(n: String) = Regex("xxx|adult|18\\+|porn|sex|\\berotic|\\bxx\\b", RegexOption.IGNORE_CASE).containsMatchIn(n)
    fun clearResume(kind: String) { val p = editProf(); val o = p.optJSONObject("resume") ?: return; val ks = o.keys().asSequence().toList(); ks.forEach { k -> val e = o.optJSONObject(k); if (e != null && (kind == "all" || e.optString("kind") == kind || (kind == "SERIES" && e.optString("kind") == "SERIES"))) o.remove(k) }; p.put("resume", o); saveProf(p) }

    /** Anonymous device session: registers this MAC so the panel can push playlists to it (My App → Devices). */
    /** Login screen "Refresh": asks the panel again whether a playlist was assigned to this device (My App → Add user by MAC) — no logout/login needed.
     *  manual = the customer pressed the button (allowed after a sign out); the automatic 15 s check stays quiet after a sign out. */
    suspend fun checkDevice(manual: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        if (manual) prefs.loggedOut = false
        if (prefs.loggedOut) return@withContext false
        try { if (prefs.token.isEmpty()) deviceHello() else refreshSession() } catch (e: Throwable) {}
        playlists.value.any { it.fromPanel }
    }
    /** Anonymous device session (MAC) so the panel can push playlists to this device. Never after a sign out — that is what logged the TV back in. */
    suspend fun deviceHello(): Boolean = withContext(Dispatchers.IO) {
        if (prefs.token.isNotEmpty()) return@withContext true
        if (prefs.loggedOut) return@withContext false
        val r = panel.post("device_hello", JSONObject().put("key", prefs.deviceKey()), auth = false)
        if (r.ok) { prefs.token = r.json.optString("token"); applyPayload(r.json); true } else false
    }

    // ---------------- app update (sideload build only: the panel hosts 9KProTV.version "code name sha" and 9KProTV.apk) ----------------
    /** A newer APK on the panel, asked at most once a day (force = Settings → App update asks right now). Always null in the Play build. */
    suspend fun checkUpdate(force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        if (BuildConfig.PLAY_BUILD) return@withContext null
        if (!force && System.currentTimeMillis() - prefs.updateCheckedAt < 24 * 3600_000L) return@withContext null
        try {
            val c = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()
            val t = c.newCall(Request.Builder().url(BuildConfig.PANEL_URL + "/9KProTV.version").build()).execute().use { r -> if (r.isSuccessful) (r.body?.string() ?: "") else "" }
            val p = t.trim().split(Regex("\\s+")); val code = p.getOrNull(0)?.toIntOrNull() ?: return@withContext null
            prefs.updateCheckedAt = System.currentTimeMillis()
            if (code > BuildConfig.VERSION_CODE) UpdateInfo(code, p.getOrElse(1) { "" }, BuildConfig.PANEL_URL + "/9KProTV.apk") else null
        } catch (e: Exception) { null }
    }

    // ---------------- Match day (fixtures via the panel, cached 10 min in memory) ----------------
    private val matchCache = ConcurrentHashMap<String, Pair<Long, List<Match>>>()
    suspend fun matches(date: String = today()): List<Match> = withContext(Dispatchers.IO) {
        val c = matchCache[date]; if (c != null && System.currentTimeMillis() - c.first < 10 * 60_000L) return@withContext c.second
        val r = panel.matches(date); val a = r.json.optJSONArray("rows") ?: return@withContext c?.second ?: emptyList()
        val l = (0 until a.length()).map { Match.fromJson(a.getJSONObject(it)) }; matchCache[date] = System.currentTimeMillis() to l; l
    }
    fun today(offsetDays: Int = 0): String { val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")); cal.add(java.util.Calendar.DATE, offsetDays); return String.format("%04d-%02d-%02d", cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DATE)) }
    /** One channel that (probably) shows a match. fromGuide = the guide's programme around kick-off names the team(s); title = that programme. */
    data class MatchHit(val item: Item, val title: String, val fromGuide: Boolean)
    private val sportsName = Regex("sport|bein|\\bssc\\b|espn|dazn|premier|arena|\\bmatch|football|kora|koora|on time|alkass|al kass|abu dhabi|\\bad s|mbc action|charlton|\\bone\\b|\\btnt\\b|eleven|canal\\+|sky s|fox s|idman|varzish|laliga|la liga|bundesliga|serie a|ligue 1|shoot|رياض|ספורט", RegexOption.IGNORE_CASE)
    /**
     * Channels showing this match. Looks at the guide programme that is on at kick-off (live match: on right now) and asks whether it names the
     * teams — the full names or known spellings ("Man City", "PSG", Arabic/Hebrew), never a lone word like "Manchester" that fits the wrong game.
     * Both teams named come first, then one team (a team plays one game at a time, so inside the kick-off window that is still a hit).
     * When the guide knows nothing, the sports channels are returned as a hint with fromGuide = false, and the dialog says so.
     */
    fun channelsForMatch(m: Match): List<MatchHit> {
        val pl = activePlaylist ?: return emptyList(); val live = cachedContent(pl, Kind.LIVE)?.items ?: return emptyList()
        val now = System.currentTimeMillis() / 1000
        val from = if (m.live || m.ts <= 0) now else m.ts - 15 * 60; val to = if (m.live || m.ts <= 0) now + 1 else m.ts + 110 * 60
        val home = TeamNames.matcher(m.home); val away = TeamNames.matcher(m.away)
        class S(val item: Item, val title: String, val score: Int)
        val scored = live.mapNotNull { ch ->
            var best = 0; var bestTitle = ""
            for (p in epgFor(pl, ch)) { if (p.stop <= from || p.start >= to) continue
                val t = TeamNames.norm(p.title); val s = (if (home(t)) 2 else 0) + (if (away(t)) 2 else 0)
                if (s > best) { best = s; bestTitle = p.title } }
            val sp = sportsName.containsMatchIn(ch.name)
            if (best == 0 && !sp) null else S(ch, bestTitle.ifEmpty { nowNext(pl, ch).first?.title ?: "" }, best + (if (sp) 1 else 0))
        }
        val guide = scored.filter { it.score >= 2 }.sortedWith(compareByDescending<S> { it.score }.thenBy { it.item.num }.thenBy { it.item.name })
        if (guide.isNotEmpty()) return guide.take(15).map { MatchHit(it.item, it.title, true) }
        return scored.sortedWith(compareBy<S> { it.item.num }.thenBy { it.item.name }).take(15).map { MatchHit(it.item, it.title, false) }
    }

    // ---------------- weather (clock corner) ----------------
    @Volatile private var wx: Triple<Long, String, Int>? = null; @Volatile private var wxCode = 0
    suspend fun weather(): Pair<String, Int>? = withContext(Dispatchers.IO) {
        val c = wx; if (c != null && System.currentTimeMillis() - c.first < 30 * 60_000L) return@withContext c.second to c.third
        val r = panel.weather(); if (!r.ok) return@withContext c?.let { it.second to it.third }
        val t = r.json.optInt("temp"); val code = r.json.optInt("code"); wx = Triple(System.currentTimeMillis(), r.json.optString("city"), t); wxCode = code; r.json.optString("city") to t
    }
    fun weatherIcon(): String = when (wxCode) { 0 -> "☀"; 1, 2 -> "🌤"; 3 -> "☁"; 45, 48 -> "🌫"; in 51..67 -> "🌧"; in 71..77 -> "❄"; in 80..82 -> "🌦"; in 95..99 -> "⛈"; else -> "🌡" }

    // ---------------- server health (red line on Home when the main server does not answer) ----------------
    val serverOk = MutableStateFlow(true)
    private suspend fun pingServer() { val pl = activePlaylist ?: return; if (pl.kind != "xtream") return
        serverOk.value = try { val h = hostFor[pl.id] ?: return; xtream.ping(h) } catch (e: Throwable) { false } }

    // ---------------- match / programme reminders and scheduled recordings (stored locally; the receivers fire them, BootReceiver re-arms after a reboot) ----------------
    /** True after the last reminder / recording had to use an inexact alarm (Android 12+ without "Alarms & reminders" — it may fire minutes late). The UI can send the customer to tv.ninekpro.app.Alarms.settingsIntent(ctx). */
    @Volatile var alarmInexact = false
    fun exactAlarmsAllowed(): Boolean = tv.ninekpro.app.Alarms.exactAllowed(ctx)
    fun reminders(): Set<Long> = prefs.reminders
    /** EPG reminder for a future programme (5 min before, opens the channel). Returns the new state. */
    fun hasProgReminder(chKey: String, start: Long): Boolean { val id = tv.ninekpro.app.RemindReceiver.progId(chKey, start).toString(); return prefs.epgReminders.any { it.startsWith(id + "|") } }
    fun hasRecSchedule(chKey: String, start: Long): Boolean { val k = chKey + "|" + start; return prefs.recSched.any { it == k || it.startsWith(k + "|") } }
    /** Schedule / unschedule a recording. Returns the new state; false also when nothing could be armed (programme over, alarm refused). */
    fun toggleRecSchedule(chKey: String, start: Long, stop: Long, url: String, title: String): Boolean {
        val k = chKey + "|" + start; val now = System.currentTimeMillis()
        val cur = prefs.recSched.filter { (it.split("|").getOrNull(1)?.toLongOrNull() ?: 0L) * 1000 > now - 6 * 3600_000 }.filter { it != k && !it.startsWith(k + "|") }.toMutableSet()
        val on = !hasRecSchedule(chKey, start)
        if (on) {
            val exact = tv.ninekpro.app.RecordReceiver.schedule(ctx, chKey, start, stop, url, title)
            if (!exact && (exactAlarmsAllowed() || stop * 1000 < now)) return false   // nothing armed (a false with exact alarms blocked = armed inexact)
            alarmInexact = !exact
            cur.add(tv.ninekpro.app.RecSched(chKey, start, stop, url, title).encode())
        } else tv.ninekpro.app.RecordReceiver.cancel(ctx, chKey, start)
        prefs.recSched = cur; profileVersion.value++; return on
    }
    fun toggleProgReminder(chKey: String, start: Long, title: String, channel: String): Boolean {
        val id = tv.ninekpro.app.RemindReceiver.progId(chKey, start).toString(); val now = System.currentTimeMillis()
        val cur = prefs.epgReminders.filter { !it.startsWith(id + "|") && (it.split("|").getOrNull(2)?.toLongOrNull() ?: 0L) * 1000 > now - 3600_000 }.toMutableSet()
        val on = !prefs.epgReminders.any { it.startsWith(id + "|") }
        if (on) {
            val exact = tv.ninekpro.app.RemindReceiver.scheduleProgramme(ctx, chKey, start, title, channel)
            if (!exact && (exactAlarmsAllowed() || start * 1000 - 5 * 60_000L < now)) return false
            alarmInexact = !exact
            cur.add(tv.ninekpro.app.RemindReceiver.progLine(chKey, start, title, channel))
        } else tv.ninekpro.app.RemindReceiver.cancelProgramme(ctx, chKey, start)
        prefs.epgReminders = cur; profileVersion.value++; return on
    }
    fun toggleReminder(m: Match): Boolean {
        val s = prefs.reminders.toMutableSet(); val on = !s.remove(m.id); if (on) s.add(m.id); prefs.reminders = s
        val ml = prefs.reminderMatches.filter { !it.startsWith(m.id.toString() + "|") && (it.split("|").getOrNull(1)?.toLongOrNull() ?: 0L) * 1000 > System.currentTimeMillis() - 3600_000 }.toMutableSet()
        if (on) { val exact = tv.ninekpro.app.RemindReceiver.schedule(ctx, m); alarmInexact = !exact && !exactAlarmsAllowed(); ml.add(tv.ninekpro.app.RemindReceiver.matchLine(m.id, m.ts, tv.ninekpro.app.RemindReceiver.matchTitle(m))) }
        else tv.ninekpro.app.RemindReceiver.cancel(ctx, m.id)
        prefs.reminderMatches = ml; return on
    }

    /** The live list the player was opened from — for channel up/down and number zapping. */
    @Volatile var liveContext: List<Item> = emptyList()
    /** Live tab UI state kept across navigation (Back from the player returns to the same category, channel and scroll). */
    /** Search screen state kept across navigation (open a title → Back returns to the same results). */
    @Volatile var searchQ: String = ""; @Volatile var searchScroll: Int = 0
    @Volatile var liveUiCat: String = "__all"; @Volatile var liveUiSel: Item? = null; @Volatile var liveUiList: Boolean = false; @Volatile var liveUiScroll: Int = 0
    /** Movies / Series tab state (category, pane, scroll) per kind — see VodUi. */
    val vodUi: Map<Kind, VodUi> = mapOf(Kind.MOVIE to VodUi(), Kind.SERIES to VodUi())
    /** Forget the remembered tab positions — on sign out and when the active playlist changes. */
    fun resetUi() {
        liveUiCat = "__all"; liveUiSel = null; liveUiList = false; liveUiScroll = 0; searchQ = ""; searchScroll = 0
        for (v in vodUi.values) { v.cat = "__all"; v.showList = false; v.scroll = 0 }
    }

    // ---------------- parental lock ----------------
    fun locked(): Set<String> = lockedCache ?: (prof().optJSONArray("locked")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()).also { lockedCache = it }
    fun setLocked(key: String, lock: Boolean) { val p = editProf(); val s = locked().toMutableSet(); if (lock) s.add(key) else s.remove(key); p.put("locked", JSONArray(s.toList())); saveProf(p) }
    var pin: String get() = prefs.pin; set(v) { prefs.pin = v }
    @Volatile var unlockedUntil: Long = 0L   // after a correct PIN, locked content stays open for 30 minutes

    // ---------------- profile: favorites / resume / order / hidden ----------------
    // The profile JSON is parsed ONCE and kept here (grid cells ask isFavorite/resumeOf on every frame); derived sets are built lazily and dropped on every change.
    @Volatile private var profCache: JSONObject? = null
    @Volatile private var favCache: Set<String>? = null
    @Volatile private var resumeCache: Map<String, ResumeEntry>? = null
    @Volatile private var lockedCache: Set<String>? = null
    @Volatile private var hiddenCache: Set<String>? = null
    private fun invalidateProf() { profCache = null; favCache = null; resumeCache = null; lockedCache = null; hiddenCache = null }
    /** The parsed profile for reading — never modify it (other threads read it too). */
    private fun prof(): JSONObject = profCache ?: prefs.profile.also { profCache = it }
    /** A private copy for changing; hand it to saveProf. */
    private fun editProf(): JSONObject = try { JSONObject(prof().toString()) } catch (e: Exception) { JSONObject() }
    private fun saveProf(p: JSONObject) { prefs.profile = p; invalidateProf(); profCache = p; prefs.profileDirty = true; profileVersion.value++; scheduleSync() }

    fun favorites(): List<String> { val a = prof().optJSONArray("favorites") ?: return emptyList(); return (0 until a.length()).map { a.getString(it) } }
    fun favoritesSet(): Set<String> = favCache ?: favorites().toSet().also { favCache = it }
    fun isFavorite(key: String) = favoritesSet().contains(key)
    fun toggleFavorite(key: String) { val p = editProf(); val l = favorites().toMutableList(); if (!l.remove(key)) l.add(0, key); p.put("favorites", JSONArray(l)); saveProf(p) }

    fun hidden(): Set<String> = hiddenCache ?: (prof().optJSONArray("hidden")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()).also { hiddenCache = it }
    fun setHidden(key: String, hide: Boolean) { val p = editProf(); val s = hidden().toMutableSet(); if (hide) s.add(key) else s.remove(key); p.put("hidden", JSONArray(s.toList())); saveProf(p) }

    /** Custom order of a list (category of items, or the category list itself): listId → ordered keys that were moved. Unlisted keys keep their natural order after the moved ones. */
    fun order(listId: String): List<String> { val o = prof().optJSONObject("order") ?: return emptyList(); val a = o.optJSONArray(listId) ?: return emptyList(); return (0 until a.length()).map { a.getString(it) } }
    fun setOrder(listId: String, keys: List<String>) { val p = editProf(); val o = p.optJSONObject("order") ?: JSONObject(); o.put(listId, JSONArray(keys)); p.put("order", o); saveProf(p) }
    /** Moved keys first (in their saved order), then everything else in natural order — one pass, no sorting. */
    fun <T> applyOrder(listId: String, items: List<T>, keyOf: (T) -> String): List<T> {
        val ord = order(listId); if (ord.isEmpty()) return items
        val byKey = HashMap<String, T>(items.size * 2); for (it in items) if (!byKey.containsKey(keyOf(it))) byKey[keyOf(it)] = it
        val moved = HashSet<String>(); val out = ArrayList<T>(items.size)
        for (k in ord) { val it = byKey[k] ?: continue; if (moved.add(k)) out.add(it) }
        for (it in items) if (!moved.contains(keyOf(it))) out.add(it)
        return out
    }
    fun move(listId: String, orderedKeys: List<String>, key: String, delta: Int) {
        val l = orderedKeys.toMutableList(); val i = l.indexOf(key); if (i < 0) return
        val j = if (delta == Int.MIN_VALUE) 0 else (i + delta).coerceIn(0, l.size - 1); if (i == j) return
        l.removeAt(i); l.add(j, key); setOrder(listId, l)
    }

    /** Items you're mid-way through, for the Movies/Series "Continue Watching" category (newest first). */
    fun continueItems(kind: Kind, content: XtreamApi.Content): List<Item> {
        val res = resumeList()
        return if (kind == Kind.MOVIE) { val byKey = content.items.associateBy { it.key }; res.filter { it.kind == "MOVIE" }.mapNotNull { byKey[it.key] } }
        else { val byId = content.items.associateBy { it.id }; val seen = LinkedHashSet<String>()
            res.filter { it.kind == "SERIES" }.mapNotNull { r -> Regex("\"series\"\\s*:\\s*\"([^\"]+)\"").find(r.extra)?.groupValues?.get(1) }.filter { seen.add(it) }.mapNotNull { byId[it] } }
    }
    /** key → resume entry, parsed once per profile change. */
    fun resumeMap(): Map<String, ResumeEntry> = resumeCache ?: run {
        val o = prof().optJSONObject("resume"); val m = HashMap<String, ResumeEntry>()
        if (o != null) for (k in o.keys().asSequence().toList()) { val e = o.optJSONObject(k) ?: continue; m[k] = ResumeEntry.fromJson(e) }
        m.also { resumeCache = it }
    }
    fun resumeList(): List<ResumeEntry> = resumeMap().values.sortedByDescending { it.updated }
    fun resumeOf(key: String): ResumeEntry? = resumeMap()[key]
    fun saveResume(e: ResumeEntry) {
        val p = editProf(); val o = p.optJSONObject("resume") ?: JSONObject()
        if (e.durationMs > 0 && (e.positionMs < 20_000 || e.positionMs > e.durationMs - 45_000)) o.remove(e.key) else o.put(e.key, e.toJson())
        // keep the newest 60
        if (o.length() > 60) { val ks = o.keys().asSequence().toList().sortedBy { o.getJSONObject(it).optLong("t") }; ks.take(o.length() - 60).forEach { o.remove(it) } }
        p.put("resume", o); saveProf(p)
    }
    fun removeResume(key: String) { val p = editProf(); p.optJSONObject("resume")?.remove(key); saveProf(p) }

    fun lastChannelKey(): String = prof().optString("lastLive")
    fun setLastChannel(key: String) { val p = editProf(); p.put("lastLive", key); prefs.profile = p; profCache = p }   // no sync, no dirty flag: the panel copy does not need the last channel

    private var syncJob: Job? = null
    private fun scheduleSync() {
        if (prefs.token.isEmpty()) return
        syncJob?.cancel()
        syncJob = scope.launch { delay(4000); try { val r = panel.syncSet(prefs.profile); if (r.ok) { prefs.profileDirty = false; prefs.lastSync = System.currentTimeMillis() } } catch (e: Exception) {} }
    }
    fun syncNow() { if (prefs.profileDirty) scheduleSync() }

    // ---------------- heartbeat (online status + watch stats) ----------------
    @Volatile var nowPlaying: JSONObject? = null
    private var hbJob: Job? = null
    /** Every 60 s while the app is on screen or playing, otherwise every 20 min; the IPTV host is pinged only while on screen. */
    fun startHeartbeat() {
        if (hbJob != null) return
        hbJob = scope.launch {
            var lastId = ""; var secs = 0; var lastBeat = 0L; var lastPing = 0L
            while (true) {
                try { checkCrashFix() } catch (e: Exception) { }
                val now = System.currentTimeMillis(); val active = foreground || nowPlaying != null
                if (foreground && now - lastPing >= 55_000L) { lastPing = now; try { pingServer() } catch (e: Exception) { } }
                if (prefs.token.isNotEmpty() && now - lastBeat >= (if (active) 55_000L else 20 * 60_000L)) {
                    try {
                        val np = nowPlaying; val id = np?.optString("id") ?: ""
                        val log = id.isNotEmpty() && id != lastId
                        secs = if (id == lastId && lastBeat > 0) secs + ((now - lastBeat) / 1000).toInt() else 0
                        lastBeat = now
                        val r = panel.heartbeat(np, secs, log)
                        if (r.ok) { lastId = id
                            // the panel changed the host order / this account's playlists (dead DNS replaced, new host added) → reload silently
                            val hv = r.json.optString("hv"); if (hv.isNotEmpty() && hv != lastHv) { if (lastHv.isNotEmpty()) try { refreshSession() } catch (e: Exception) {}; lastHv = hv } }
                        else if (r.error == "token") { dropSession(); try { deviceHello() } catch (e: Exception) {} }   // expired token: a MAC-assigned device registers again by itself
                        else if (r.error == "logged_out" || r.error == "device_blocked" || r.error == "blocked") dropSession()
                    } catch (e: Exception) { }
                }
                delay(20_000)
            }
        }
    }
}
