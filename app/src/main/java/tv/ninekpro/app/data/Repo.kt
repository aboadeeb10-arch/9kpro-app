package tv.ninekpro.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The one place the UI reads from. Holds the session (account/brand/playlists/messages), the content of each playlist,
 * and the customer's profile (favorites, resume, order, hidden) which is synced to the panel.
 */
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

    val loggedIn: Boolean get() = prefs.token.isNotEmpty() || prefs.manualPlaylists.isNotEmpty()

    // ---------------- session ----------------
    fun applyPayload(j: JSONObject) {
        j.optJSONObject("brand")?.let { prefs.brandJson = it.toString(); brand.value = Brand.fromJson(it) }
        j.optJSONObject("account")?.let { prefs.accountJson = it.toString(); account.value = Account.fromJson(it) }
        val lines = j.optJSONArray("lines")
        if (lines != null) {
            val pls = (0 until lines.length()).map { i ->
                val l = lines.getJSONObject(i)
                val hosts = l.optJSONArray("hosts")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
                Playlist(id = "p" + i + "_" + l.optString("user"), name = l.optString("name"), kind = "xtream", hosts = hosts, user = l.optString("user"),
                    pass = l.optString("pass"), panel = l.optString("panel"), trial = l.optBoolean("trial"), exp = l.optString("exp"), fromPanel = true, protect = l.optBoolean("protect"))
            }
            // the panel sends hosts healthiest-first (10-minute checks): follow it, so a customer on a dead host moves to a working one by himself
            for (pl in pls) { val best = pl.hosts.firstOrNull() ?: continue; val cur = hostFor[pl.id]; if (cur != null && cur != xtream.baseUrl(best)) { hostFor[pl.id] = xtream.baseUrl(best); cache.keys.filter { it.startsWith(pl.id + ":") }.forEach { cache.remove(it) } } }
            prefs.panelPlaylists = pls
            playlists.value = pls + prefs.manualPlaylists
        }
        j.optJSONArray("messages")?.let { a -> messages.value = (0 until a.length()).map { val m = a.getJSONObject(it); Message(m.optInt("id"), m.optString("title"), m.optString("text"), m.optString("kind"), m.optString("image"), m.optString("link")) } }
        crashTrialAvailable.value = j.optBoolean("crash_trial")
        // cloud profile: the panel copy wins only when the local one was never changed (fresh install)
        j.optJSONObject("sync")?.let { s -> if (!prefs.profileDirty && s.length() > 0) { prefs.profile = s; profileVersion.value++ } }
        if (prefs.activePlaylistId.isEmpty() && playlists.value.isNotEmpty()) prefs.activePlaylistId = playlists.value.first().id
    }

    suspend fun login(user: String, pass: String): String? = withContext(Dispatchers.IO) {
        val r = panel.login(user, pass)
        if (!r.ok) return@withContext r.error.ifEmpty { "wrong_login" }
        if (prefs.token.isNotEmpty() && playlists.value.isNotEmpty()) { addExtraLogin(r.json); return@withContext null }
        prefs.token = r.json.optString("token"); applyPayload(r.json); null
    }

    suspend fun codeLogin(code: String): String? = withContext(Dispatchers.IO) {
        val r = panel.codeLogin(code)
        if (!r.ok) return@withContext r.error.ifEmpty { "wrong_login" }
        if (prefs.token.isNotEmpty() && playlists.value.isNotEmpty()) { addExtraLogin(r.json); return@withContext null }
        prefs.token = r.json.optString("token"); applyPayload(r.json); null
    }

    /** "Add playlist" while playlists already exist: keep the current session and ADD the new account's lines (kept as own playlists, deletable). */
    private fun addExtraLogin(j: JSONObject) {
        val lines = j.optJSONArray("lines") ?: return
        val have = playlists.value.map { it.user }.toSet()
        val add = (0 until lines.length()).map { lines.getJSONObject(it) }.filter { it.optString("user").isNotEmpty() && it.optString("user") !in have }.map { l ->
            Playlist(id = "x" + l.optString("user") + "_" + (System.currentTimeMillis() % 100000), name = l.optString("name").ifEmpty { l.optString("user") }, kind = "xtream",
                hosts = l.optJSONArray("hosts")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(), user = l.optString("user"), pass = l.optString("pass"), panel = l.optString("panel"),
                trial = l.optBoolean("trial"), exp = l.optString("exp"), fromPanel = false, protect = l.optBoolean("protect")) }
        val tk = j.optString("token"); if (tk.isNotEmpty()) scope.launch { try { panel.post("logout", tokenOverride = tk) } catch (e: Exception) {} }   // close the extra device session
        if (add.isEmpty()) return
        prefs.manualPlaylists = prefs.manualPlaylists + add
        playlists.value = prefs.panelPlaylists + prefs.manualPlaylists
        prefs.activePlaylistId = add.first().id; cache.clear(); profileVersion.value++
    }

    suspend fun refreshSession(): String? = withContext(Dispatchers.IO) {
        if (prefs.token.isEmpty()) { panel.brand()?.let { b -> brand.value = b }; return@withContext null }
        val r = panel.refresh()
        if (r.ok) { applyPayload(r.json); null } else r.error
    }

    fun logout() {
        scope.launch { try { panel.logout() } catch (e: Exception) {} }
        prefs.clearSession(); account.value = null; playlists.value = prefs.manualPlaylists; messages.value = emptyList()
        cache.clear(); prefs.activePlaylistId = prefs.manualPlaylists.firstOrNull()?.id ?: ""
    }

    fun addManualPlaylist(pl: Playlist) { prefs.manualPlaylists = prefs.manualPlaylists + pl; playlists.value = prefs.panelPlaylists + prefs.manualPlaylists; if (prefs.activePlaylistId.isEmpty()) prefs.activePlaylistId = pl.id }
    fun removeManualPlaylist(id: String) { prefs.manualPlaylists = prefs.manualPlaylists.filter { it.id != id }; playlists.value = prefs.panelPlaylists + prefs.manualPlaylists; if (prefs.activePlaylistId == id) prefs.activePlaylistId = playlists.value.firstOrNull()?.id ?: "" }

    val activePlaylist: Playlist? get() = playlists.value.firstOrNull { it.id == prefs.activePlaylistId } ?: playlists.value.firstOrNull()

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

    /** Load Live, Movies and Series (and the guide) in the background so every tab opens instantly. */
    private var preloadJob: Job? = null
    fun preloadAll() {
        val pl = activePlaylist ?: return
        if (preloadJob?.isActive == true) return
        preloadJob = scope.launch { for (k in listOf(Kind.LIVE, Kind.MOVIE, Kind.SERIES)) try { content(pl, k) } catch (e: Throwable) {}
            try { tv.ninekpro.app.TvHomeRow.update(ctx, this@Repo) } catch (e: Throwable) {}; try { tv.ninekpro.app.SmileWidget.refresh(ctx) } catch (e: Throwable) {} }
    }
    /** Live items already in memory (for the widget / TV row; no network). */
    fun cachedLive(pl: Playlist): List<Item> = cache[pl.id + ":" + Kind.LIVE.name]?.items ?: emptyList()

    fun markMessagesSeen(ids: List<Int>) { messages.value = messages.value.filter { it.id !in ids }; scope.launch { try { panel.messagesSeen(ids) } catch (e: Exception) {} } }

    // ---------------- content ----------------
    private val cache = HashMap<String, XtreamApi.Content>()          // key playlistId:kind
    private val hostFor = HashMap<String, String>()
    private val seriesCache = HashMap<String, SeriesInfo>()
    private val m3uCache = HashMap<String, Map<Kind, XtreamApi.Content>>()

    private fun cacheFile(pl: Playlist, kind: Kind) = File(ctx.cacheDir, "c2_${pl.id.hashCode()}_${kind.name}.json")

    suspend fun host(pl: Playlist): String = withContext(Dispatchers.IO) { hostFor[pl.id] ?: xtream.pickHost(pl).also { hostFor[pl.id] = it } }
    fun forgetHost(pl: Playlist) { hostFor.remove(pl.id) }
    /** Change server: pin one of the playlist's hosts and reload from it. */
    fun setHost(pl: Playlist, h: String) { hostFor[pl.id] = xtream.baseUrl(h); cache.keys.filter { it.startsWith(pl.id + ":") }.forEach { cache.remove(it) } }
    fun currentHost(pl: Playlist): String = hostFor[pl.id] ?: ""

    /** Content for a playlist+kind: memory → disk cache (instant start) → network. `force` re-downloads. */
    suspend fun content(pl: Playlist, kind: Kind, force: Boolean = false): XtreamApi.Content = withContext(Dispatchers.IO) {
        val k = pl.id + ":" + kind.name
        if (kind == Kind.LIVE) ensureEpg(pl)
        if (!force) cache[k]?.let { return@withContext it }
        if (pl.kind == "m3u") {
            val all = m3uCache[pl.id] ?: xtream.loadM3u(pl).also { m3uCache[pl.id] = it }
            val c = all[kind] ?: XtreamApi.Content(emptyList(), emptyList()); cache[k] = c; return@withContext c
        }
        if (!force) {
            val f = cacheFile(pl, kind)
            if (f.exists()) {
                try { val c = readContent(f, pl, kind); cache[k] = c
                    val old = System.currentTimeMillis() - f.lastModified() > 24 * 3600_000L
                    if (old && prefs.autoRefresh) scope.launch { try { refreshContent(pl, kind) } catch (e: Throwable) {} }
                    return@withContext c } catch (e: Throwable) { }
            }
        }
        refreshContent(pl, kind)
    }

    private suspend fun refreshContent(pl: Playlist, kind: Kind): XtreamApi.Content {
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
            for (it in c.items) { w.beginObject(); w.name("i").value(it.id); w.name("n").value(it.name); w.name("c").value(it.categoryId); w.name("ic").value(it.icon); w.name("e").value(it.ext); w.name("r").value(it.rating); w.name("a").value(it.added); w.name("y").value(it.year); w.name("g").value(it.epgId); w.name("num").value(it.num.toLong()); w.name("ar").value(it.archive); w.name("ad").value(it.archiveDays.toLong()); w.endObject() }
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
                    "items" -> { r.beginArray(); while (r.hasNext()) { r.beginObject(); var i = ""; var n = ""; var c = ""; var ic = ""; var e = ""; var rt = ""; var a = 0L; var y = ""; var g = ""; var num = 0; var ar = false; var ad = 0
                        while (r.hasNext()) { when (r.nextName()) { "i" -> i = r.nextString(); "n" -> n = r.nextString(); "c" -> c = r.nextString(); "ic" -> ic = r.nextString(); "e" -> e = r.nextString(); "r" -> rt = r.nextString(); "a" -> a = r.nextLong(); "y" -> y = r.nextString(); "g" -> g = r.nextString(); "num" -> num = r.nextInt(); "ar" -> ar = r.nextBoolean(); "ad" -> ad = r.nextInt(); else -> r.skipValue() } }
                        r.endObject(); items.add(Item(i, n, kind, c, ic, e, rt, a, y, "", g, num, pl.id, archive = ar, archiveDays = ad)) }; r.endArray() }
                    else -> r.skipValue()
                }
            }
            r.endObject()
        }
        return XtreamApi.Content(cats, items)
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

    /** Settings → Update Now: drop caches and re-download everything for the active playlist in the background. */
    fun forceReload() { val pl = activePlaylist ?: return; cache.clear(); m3uCache.remove(pl.id); seriesCache.clear(); hostFor.remove(pl.id); epgCache.remove(pl.id); try { epgFile(pl).delete() } catch (e: Exception) {}
        scope.launch { for (k in Kind.values()) try { content(pl, k, force = true) } catch (e: Throwable) {} } }
    fun cachedContent(pl: Playlist, kind: Kind): XtreamApi.Content? = cache[pl.id + ":" + kind.name]
    /** Search across live/movies/series of the active playlist (cached content only). */
    fun searchCached(pl: Playlist, q: String): List<Item> {
        val s = q.trim().lowercase(); if (s.length < 2) return emptyList()
        return Kind.values().flatMap { k -> cache[pl.id + ":" + k.name]?.items?.filter { it.name.lowercase().contains(s) } ?: emptyList() }.take(200)
    }

    // ---------------- EPG (XMLTV, loaded once per playlist in the background) ----------------
    private val epgCache = HashMap<String, Map<String, List<EpgEntry>>>()
    private val epgLoading = HashSet<String>()
    val epgVersion = MutableStateFlow(0)
    fun epgFor(pl: Playlist, item: Item): List<EpgEntry> { if (item.epgId.isEmpty()) return emptyList(); return epgCache[pl.id]?.get(item.epgId) ?: emptyList() }
    fun nowNext(pl: Playlist, item: Item): Pair<EpgEntry?, EpgEntry?> { val now = System.currentTimeMillis() / 1000; val l = epgFor(pl, item); val i = l.indexOfFirst { it.stop > now }; if (i < 0) return null to null; return l[i] to l.getOrNull(i + 1) }
    private fun epgFile(pl: Playlist) = File(ctx.cacheDir, "epg_${pl.id.hashCode()}.json")
    fun ensureEpg(pl: Playlist) {
        if (pl.kind != "xtream" || epgCache.containsKey(pl.id) || !epgLoading.add(pl.id)) return
        scope.launch {
            try {
                val f = epgFile(pl)
                if (f.exists() && System.currentTimeMillis() - f.lastModified() < 12 * 3600_000L) { try { epgCache[pl.id] = readEpg(f); epgVersion.value++; return@launch } catch (e: Throwable) { } }
                val m = xtream.loadEpg(host(pl), pl); epgCache[pl.id] = m; epgVersion.value++
                try { writeEpg(f, m) } catch (e: Throwable) { }
            } catch (e: Throwable) { epgCache[pl.id] = emptyMap() } finally { epgLoading.remove(pl.id) }
        }
    }
    private fun writeEpg(f: File, m: Map<String, List<EpgEntry>>) {
        android.util.JsonWriter(java.io.OutputStreamWriter(java.io.FileOutputStream(f), Charsets.UTF_8)).use { w -> w.beginObject(); for ((ch, l) in m) { w.name(ch); w.beginArray(); for (e in l) { w.beginArray(); w.value(e.start); w.value(e.stop); w.value(e.title); w.endArray() }; w.endArray() }; w.endObject() }
    }
    private fun readEpg(f: File): Map<String, List<EpgEntry>> {
        val out = HashMap<String, List<EpgEntry>>()
        android.util.JsonReader(java.io.InputStreamReader(java.io.FileInputStream(f), Charsets.UTF_8)).use { r -> r.beginObject(); while (r.hasNext()) { val ch = r.nextName(); val l = ArrayList<EpgEntry>(); r.beginArray(); while (r.hasNext()) { r.beginArray(); val a = r.nextLong(); val b = r.nextLong(); val t = r.nextString(); r.endArray(); l.add(EpgEntry(a, b, t)) }; r.endArray(); out[ch] = l }; r.endObject() }
        return out
    }
    suspend fun catchupUrl(pl: Playlist, item: Item, e: EpgEntry): String = withContext(Dispatchers.IO) { xtream.catchupUrl(host(pl), pl, item, e.start, ((e.stop - e.start) / 60).toInt().coerceAtLeast(1)) }

    private val shortCache = HashMap<String, Pair<Long, List<EpgEntry>>>()
    /** Now/next for one channel: full guide if loaded, else a quick per-channel request (cached 10 min). */
    suspend fun nowNextLive(pl: Playlist, item: Item): Pair<EpgEntry?, EpgEntry?> = withContext(Dispatchers.IO) {
        val full = nowNext(pl, item); if (full.first != null) return@withContext full
        if (pl.kind != "xtream") return@withContext null to null
        val k = pl.id + ":" + item.id; val now = System.currentTimeMillis() / 1000
        val cached = shortCache[k]; val list = if (cached != null && now - cached.first < 600) cached.second else try { xtream.shortEpg(host(pl), pl, item).also { shortCache[k] = now to it } } catch (e: Throwable) { emptyList() }
        val i = list.indexOfFirst { it.stop > now }; if (i < 0) null to null else list[i] to list.getOrNull(i + 1)
    }
    fun isAdultName(n: String) = Regex("xxx|adult|18\\+|porn|sex|\\berotic|\\bxx\\b", RegexOption.IGNORE_CASE).containsMatchIn(n)
    fun clearResume(kind: String) { val p = prof(); val o = p.optJSONObject("resume") ?: return; val ks = o.keys().asSequence().toList(); ks.forEach { k -> val e = o.optJSONObject(k); if (e != null && (kind == "all" || e.optString("kind") == kind || (kind == "SERIES" && e.optString("kind") == "SERIES"))) o.remove(k) }; p.put("resume", o); saveProf(p) }

    /** Anonymous device session: registers this MAC so the panel can push playlists to it (My App → Devices). */
    suspend fun deviceHello(): Boolean = withContext(Dispatchers.IO) {
        if (prefs.token.isNotEmpty()) return@withContext true
        val r = panel.post("device_hello", JSONObject().put("key", prefs.deviceKey()), auth = false)
        if (r.ok) { prefs.token = r.json.optString("token"); applyPayload(r.json); true } else false
    }

    // ---------------- Match day (fixtures via the panel, cached 10 min in memory) ----------------
    private val matchCache = HashMap<String, Pair<Long, List<Match>>>()
    suspend fun matches(date: String = today()): List<Match> = withContext(Dispatchers.IO) {
        val c = matchCache[date]; if (c != null && System.currentTimeMillis() - c.first < 10 * 60_000L) return@withContext c.second
        val r = panel.matches(date); val a = r.json.optJSONArray("rows") ?: return@withContext c?.second ?: emptyList()
        val l = (0 until a.length()).map { Match.fromJson(a.getJSONObject(it)) }; matchCache[date] = System.currentTimeMillis() to l; l
    }
    fun today(offsetDays: Int = 0): String { val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")); cal.add(java.util.Calendar.DATE, offsetDays); return String.format("%04d-%02d-%02d", cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DATE)) }
    /** Channels showing this match: programme title mentions the teams (2 points each), sports channel names (1 point). */
    fun channelsForMatch(m: Match): List<Item> {
        val pl = activePlaylist ?: return emptyList(); val live = cachedContent(pl, Kind.LIVE)?.items ?: return emptyList()
        val toks = (TeamNames.tokens(m.home) + TeamNames.tokens(m.away)).filter { it.length >= 4 }
        val sports = Regex("sport|bein|ssc|espn|dazn|premier|arena|match|football|kora|koora|on time|alkass|abu dhabi sports|mbc action|charlton|one\b", RegexOption.IGNORE_CASE)
        val scored = live.mapNotNull { ch ->
            val now = nowNext(pl, ch).first?.title ?: ""
            var sc = toks.count { now.contains(it, true) } * 2
            if (sports.containsMatchIn(ch.name)) sc += 1
            if (sc == 0) null else ch to sc
        }.sortedByDescending { it.second }
        val top = scored.filter { it.second >= 2 }.map { it.first }
        return (if (top.isNotEmpty()) top else scored.map { it.first }).take(15)
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

    // ---------------- match reminders (stored locally; RemindReceiver fires the notification) ----------------
    fun reminders(): Set<Long> = prefs.reminders
    /** EPG reminder for a future programme (5 min before, opens the channel). Returns the new state. */
    fun hasProgReminder(chKey: String, start: Long): Boolean { val id = tv.ninekpro.app.RemindReceiver.progId(chKey, start).toString(); return prefs.epgReminders.any { it.startsWith(id + "|") } }
    fun hasRecSchedule(chKey: String, start: Long): Boolean = prefs.recSched.contains(chKey + "|" + start)
    fun toggleRecSchedule(chKey: String, start: Long, stop: Long, url: String, title: String): Boolean {
        val k = chKey + "|" + start; val cur = prefs.recSched.filter { (it.split("|").getOrNull(1)?.toLongOrNull() ?: 0L) * 1000 > System.currentTimeMillis() - 6 * 3600_000 }.toMutableSet()
        val on = !cur.contains(k)
        if (on) { if (!tv.ninekpro.app.RecordReceiver.schedule(ctx, chKey, start, stop, url, title)) return false; cur.add(k) } else { tv.ninekpro.app.RecordReceiver.cancel(ctx, chKey, start); cur.remove(k) }
        prefs.recSched = cur; profileVersion.value++; return on
    }
    fun toggleProgReminder(chKey: String, start: Long, title: String, channel: String): Boolean {
        val id = tv.ninekpro.app.RemindReceiver.progId(chKey, start).toString(); val cur = prefs.epgReminders.filter { !it.startsWith(id + "|") && (it.split("|").getOrNull(2)?.toLongOrNull() ?: 0L) * 1000 > System.currentTimeMillis() - 3600_000 }.toMutableSet()
        val on = !prefs.epgReminders.any { it.startsWith(id + "|") }
        if (on) { if (!tv.ninekpro.app.RemindReceiver.scheduleProgramme(ctx, chKey, start, title, channel)) return false; cur.add(id + "|" + chKey + "|" + start + "|" + title) } else tv.ninekpro.app.RemindReceiver.cancelProgramme(ctx, chKey, start)
        prefs.epgReminders = cur; profileVersion.value++; return on
    }
    fun toggleReminder(m: Match): Boolean { val s = prefs.reminders.toMutableSet(); val on = !s.remove(m.id); if (on) s.add(m.id); prefs.reminders = s
        if (on) tv.ninekpro.app.RemindReceiver.schedule(ctx, m) else tv.ninekpro.app.RemindReceiver.cancel(ctx, m.id); return on }

    /** The live list the player was opened from — for channel up/down and number zapping. */
    @Volatile var liveContext: List<Item> = emptyList()
    /** Live tab UI state kept across navigation (Back from the player returns to the same category, channel and scroll). */
    /** Search screen state kept across navigation (open a title → Back returns to the same results). */
    @Volatile var searchQ: String = ""; @Volatile var searchScroll: Int = 0
    @Volatile var liveUiCat: String = "__all"; @Volatile var liveUiSel: Item? = null; @Volatile var liveUiList: Boolean = false; @Volatile var liveUiScroll: Int = 0

    // ---------------- parental lock ----------------
    fun locked(): Set<String> { val a = prof().optJSONArray("locked") ?: return emptySet(); return (0 until a.length()).map { a.getString(it) }.toSet() }
    fun setLocked(key: String, lock: Boolean) { val p = prof(); val s = locked().toMutableSet(); if (lock) s.add(key) else s.remove(key); p.put("locked", JSONArray(s.toList())); saveProf(p) }
    var pin: String get() = prefs.pin; set(v) { prefs.pin = v }
    @Volatile var unlockedUntil: Long = 0L   // after a correct PIN, locked content stays open for 30 minutes

    // ---------------- profile: favorites / resume / order / hidden ----------------
    private fun prof(): JSONObject = prefs.profile
    private fun saveProf(p: JSONObject) { prefs.profile = p; prefs.profileDirty = true; profileVersion.value++; scheduleSync() }

    fun favorites(): List<String> { val a = prof().optJSONArray("favorites") ?: return emptyList(); return (0 until a.length()).map { a.getString(it) } }
    fun isFavorite(key: String) = favorites().contains(key)
    fun toggleFavorite(key: String) { val p = prof(); val l = favorites().toMutableList(); if (!l.remove(key)) l.add(0, key); p.put("favorites", JSONArray(l)); saveProf(p) }

    fun hidden(): Set<String> { val a = prof().optJSONArray("hidden") ?: return emptySet(); return (0 until a.length()).map { a.getString(it) }.toSet() }
    fun setHidden(key: String, hide: Boolean) { val p = prof(); val s = hidden().toMutableSet(); if (hide) s.add(key) else s.remove(key); p.put("hidden", JSONArray(s.toList())); saveProf(p) }

    /** Custom order of a list (category of items, or the category list itself): listId → ordered keys that were moved. Unlisted keys keep their natural order after the moved ones. */
    fun order(listId: String): List<String> { val o = prof().optJSONObject("order") ?: return emptyList(); val a = o.optJSONArray(listId) ?: return emptyList(); return (0 until a.length()).map { a.getString(it) } }
    fun setOrder(listId: String, keys: List<String>) { val p = prof(); val o = p.optJSONObject("order") ?: JSONObject(); o.put(listId, JSONArray(keys)); p.put("order", o); saveProf(p) }
    fun <T> applyOrder(listId: String, items: List<T>, keyOf: (T) -> String): List<T> {
        val ord = order(listId); if (ord.isEmpty()) return items
        val idx = ord.withIndex().associate { it.value to it.index }
        return items.sortedWith(compareBy { idx[keyOf(it)] ?: (ord.size + items.indexOf(it)) })
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
    fun resumeList(): List<ResumeEntry> { val o = prof().optJSONObject("resume") ?: return emptyList(); return o.keys().asSequence().map { ResumeEntry.fromJson(o.getJSONObject(it)) }.sortedByDescending { it.updated }.toList() }
    fun resumeOf(key: String): ResumeEntry? = prof().optJSONObject("resume")?.optJSONObject(key)?.let { ResumeEntry.fromJson(it) }
    fun saveResume(e: ResumeEntry) {
        val p = prof(); val o = p.optJSONObject("resume") ?: JSONObject()
        if (e.durationMs > 0 && (e.positionMs < 20_000 || e.positionMs > e.durationMs - 45_000)) o.remove(e.key) else o.put(e.key, e.toJson())
        // keep the newest 60
        if (o.length() > 60) { val ks = o.keys().asSequence().toList().sortedBy { o.getJSONObject(it).optLong("t") }; ks.take(o.length() - 60).forEach { o.remove(it) } }
        p.put("resume", o); saveProf(p)
    }
    fun removeResume(key: String) { val p = prof(); p.optJSONObject("resume")?.remove(key); saveProf(p) }

    fun lastChannelKey(): String = prof().optString("lastLive")
    fun setLastChannel(key: String) { val p = prof(); p.put("lastLive", key); prefs.profile = p }

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
    fun startHeartbeat() {
        if (hbJob != null) return
        hbJob = scope.launch {
            var lastId = ""; var secs = 0
            while (true) {
                try { checkCrashFix() } catch (e: Exception) { }
                try { pingServer() } catch (e: Exception) { }
                try {
                    if (prefs.token.isNotEmpty()) {
                        val np = nowPlaying; val id = np?.optString("id") ?: ""
                        val log = id.isNotEmpty() && id != lastId
                        secs = if (id == lastId) secs + 60 else 0
                        val r = panel.heartbeat(np, secs, log)
                        if (r.ok) { lastId = id }
                        else if (r.error == "logged_out" || r.error == "device_blocked" || r.error == "blocked") { prefs.clearSession(); account.value = null; playlists.value = prefs.manualPlaylists }
                    }
                } catch (e: Exception) { }
                delay(60_000)
            }
        }
    }
}
