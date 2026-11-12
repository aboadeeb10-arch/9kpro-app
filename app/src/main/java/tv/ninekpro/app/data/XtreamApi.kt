package tv.ninekpro.app.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Xtream Codes player_api + M3U parsing. All calls are blocking — run them on Dispatchers.IO. */
class XtreamApi {
    private val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    /** Short timeouts for the host race: a dead host must not hold the others up. */
    private val probeClient = client.newBuilder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()
    /** Server time zone per host (server_info.timezone from the login answer) — catch-up start times are in the server's local time. */
    val serverTz = ConcurrentHashMap<String, String>()

    class Content(val categories: List<Category>, val items: List<Item>)

    private fun get(url: String): String {
        client.newCall(Request.Builder().url(url).header("User-Agent", "9KProTV/1.0").build()).execute().use { r ->
            if (r.code == 403 || r.code == 406) { // some panels only serve M3U/API to "browsers" — retry once with a browser user agent
                client.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 11; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36").build()).execute().use { r2 ->
                    if (!r2.isSuccessful) throw IllegalStateException("HTTP " + r2.code)
                    return r2.body?.string() ?: ""
                }
            }
            if (!r.isSuccessful) throw IllegalStateException("HTTP " + r.code)
            return r.body?.string() ?: ""
        }
    }

    /** One login probe: the base URL when the line is accepted there, else null. Remembers the server time zone. */
    private fun probe(h: String, pl: Playlist): String? = try {
        probeClient.newCall(Request.Builder().url(h + "/player_api.php?username=" + enc(pl.user) + "&password=" + enc(pl.pass)).header("User-Agent", "9KProTV/1.0").build()).execute().use { r ->
            if (!r.isSuccessful) null else {
                val j = JSONObject(r.body?.string() ?: "")
                val ui = j.optJSONObject("user_info")
                if (ui != null && ui.optString("auth", "1") != "0") { val tz = j.optJSONObject("server_info")?.optString("timezone") ?: ""; if (tz.isNotEmpty()) serverTz[h] = tz; h } else null
            }
        }
    } catch (e: Exception) { null }

    /** All hosts are probed at the same time; the first one that accepts the line wins (the rest are dropped). Gives up after 15 s. Remembered by the repo (failover). */
    fun pickHost(pl: Playlist): String {
        val hosts = pl.hosts.map { base(it) }.filter { it.length > 7 }.distinct()
        if (hosts.isEmpty()) throw IllegalStateException("no host")
        if (hosts.size == 1) return probe(hosts[0], pl) ?: throw IllegalStateException("no host answered")
        val pool = Executors.newFixedThreadPool(hosts.size.coerceAtMost(8))
        try {
            val cs = ExecutorCompletionService<String?>(pool)
            for (h in hosts) cs.submit(Callable { probe(h, pl) })
            val deadline = System.currentTimeMillis() + 15_000L
            var left = hosts.size
            while (left > 0) {
                val f = cs.poll((deadline - System.currentTimeMillis()).coerceAtLeast(1L), TimeUnit.MILLISECONDS) ?: break
                left--
                val r = try { f.get() } catch (e: Exception) { null }
                if (r != null) return r
            }
            throw IllegalStateException("no host answered")
        } finally { pool.shutdownNow() }
    }

    /** Quick health check of a host (5 s): true when the panel answers at all. */
    fun ping(host: String): Boolean = try {
        val c = client.newBuilder().connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS).readTimeout(5, java.util.concurrent.TimeUnit.SECONDS).build()
        c.newCall(Request.Builder().url(base(host) + "/player_api.php").header("User-Agent", "9KProTV/1.0").build()).execute().use { it.code < 500 }
    } catch (e: Exception) { false }

    fun baseUrl(h: String) = base(h)
    private fun base(h: String): String {
        var s = h.trim().trimEnd('/')
        if (!s.startsWith("http")) s = "http://$s"
        return s
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    private fun api(host: String, pl: Playlist, action: String, extra: String = ""): String =
        get("$host/player_api.php?username=${enc(pl.user)}&password=${enc(pl.pass)}&action=$action$extra")

    /** Streams the (often 10–20 MB) list with JsonReader so only the fields we need stay in memory. */
    fun load(host: String, pl: Playlist, kind: Kind): Content {
        val (catAction, itemAction) = when (kind) {
            Kind.LIVE -> "get_live_categories" to "get_live_streams"
            Kind.MOVIE -> "get_vod_categories" to "get_vod_streams"
            Kind.SERIES -> "get_series_categories" to "get_series"
        }
        val cats = ArrayList<Category>()
        try {
            val a = JSONArray(api(host, pl, catAction))
            for (i in 0 until a.length()) { val o = a.getJSONObject(i); cats.add(Category(o.optString("category_id"), o.optString("category_name"), kind)) }
        } catch (e: Exception) { }
        val items = ArrayList<Item>(4096)
        val url = "$host/player_api.php?username=${enc(pl.user)}&password=${enc(pl.pass)}&action=$itemAction"
        client.newCall(Request.Builder().url(url).header("User-Agent", "9KProTV/1.0").build()).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("HTTP " + r.code)
            val reader = android.util.JsonReader(java.io.InputStreamReader(r.body!!.byteStream(), Charsets.UTF_8))
            reader.isLenient = true
            if (reader.peek() != android.util.JsonToken.BEGIN_ARRAY) throw IllegalStateException("bad list")
            reader.beginArray()
            while (reader.hasNext()) {
                if (reader.peek() != android.util.JsonToken.BEGIN_OBJECT) { reader.skipValue(); continue }
                reader.beginObject()
                var id = ""; var name = ""; var cat = ""; var icon = ""; var ext = ""; var rating = ""; var added = 0L; var year = ""; var epg = ""; var num = 0; var archive = false; var archiveDays = 0
                while (reader.hasNext()) {
                    val k = reader.nextName()
                    if (reader.peek() == android.util.JsonToken.NULL) { reader.skipValue(); continue }
                    when (k) {
                        // Some panels (Mobara) send BOTH stream_id and series_id in a series entry — take only the one that belongs to this kind, or the wrong show's episodes open.
                        "stream_id" -> if (kind != Kind.SERIES) id = str(reader) else reader.skipValue()
                        "series_id" -> if (kind == Kind.SERIES) id = str(reader) else reader.skipValue()
                        "name" -> name = str(reader)
                        "category_id" -> cat = str(reader)
                        "stream_icon", "cover" -> icon = str(reader)
                        "container_extension" -> ext = str(reader)
                        "rating" -> rating = str(reader)
                        "added", "last_modified" -> { val v = str(reader).toLongOrNull() ?: 0L; if (v > added) added = v }
                        "year" -> year = str(reader)
                        "releaseDate", "release_date" -> if (year.isEmpty()) year = str(reader).take(4) else reader.skipValue()
                        "epg_channel_id" -> epg = str(reader)
                        "num" -> num = str(reader).toIntOrNull() ?: 0
                        "tv_archive" -> archive = str(reader) == "1"
                        "tv_archive_duration" -> archiveDays = str(reader).toIntOrNull() ?: 0
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                if (id.isNotEmpty()) items.add(Item(id = id, name = name, kind = kind, categoryId = cat, icon = icon, ext = ext.ifEmpty { if (kind == Kind.MOVIE) "mp4" else "" }, rating = rating, added = added, year = year, epgId = epg, num = num, playlistId = pl.id, archive = archive, archiveDays = archiveDays))
            }
            reader.endArray()
        }
        return Content(cats.distinctBy { it.id }, items.distinctBy { it.key })   // category ids are LazyList keys: a panel sending one id twice crashed the list
    }
    private fun str(r: android.util.JsonReader): String = when (r.peek()) {
        android.util.JsonToken.STRING -> r.nextString()
        android.util.JsonToken.NUMBER -> r.nextString()
        android.util.JsonToken.BOOLEAN -> r.nextBoolean().toString()
        else -> { r.skipValue(); "" }
    }

    /** Series detail. `episodes` comes as an object (season → array) on most panels, but some send an array of season arrays or one flat array of episodes with a `season` field. */
    fun seriesInfo(host: String, pl: Playlist, seriesId: String): SeriesInfo {
        val j = JSONObject(api(host, pl, "get_series_info", "&series_id=" + enc(seriesId)))
        val info = j.optJSONObject("info") ?: JSONObject()
        val seasons = LinkedHashMap<Int, MutableList<Episode>>()
        fun add(e: JSONObject, sn: Int) {
            val ei = e.optJSONObject("info") ?: JSONObject()
            seasons.getOrPut(sn) { ArrayList() }.add(Episode(e.optString("id"), e.optString("title"), sn, e.optInt("episode_num"), e.optString("container_extension", "mp4"),
                ei.optString("movie_image"), ei.optString("plot"), ei.optString("duration_secs").toIntOrNull() ?: 0, seriesId, pl.id))
        }
        fun addAll(arr: JSONArray, defaultSeason: Int) { for (i in 0 until arr.length()) { val e = arr.optJSONObject(i) ?: continue; add(e, e.optInt("season", defaultSeason)) } }
        val eps = j.opt("episodes")
        if (eps is JSONObject) {
            val keys = eps.keys().asSequence().toList().sortedBy { it.toIntOrNull() ?: 0 }
            for (k in keys) { val arr = eps.optJSONArray(k) ?: continue; addAll(arr, k.toIntOrNull() ?: 0) }
        } else if (eps is JSONArray) {
            for (i in 0 until eps.length()) { val x = eps.opt(i); if (x is JSONArray) addAll(x, i + 1) else if (x is JSONObject) add(x, x.optInt("season", 1)) }
        }
        seasons.values.forEach { it.sortBy { e -> e.num } }
        var backdrop = ""
        val bd = info.opt("backdrop_path"); if (bd is JSONArray && bd.length() > 0) backdrop = bd.optString(0) else if (bd is String) backdrop = bd
        return SeriesInfo(info.optString("plot"), info.optString("cast"), info.optString("genre"), info.optString("releaseDate").take(4), info.optString("rating"), backdrop, seasons.toSortedMap())
    }

    fun vodInfo(host: String, pl: Playlist, vodId: String): JSONObject = try { JSONObject(api(host, pl, "get_vod_info", "&vod_id=" + enc(vodId))) } catch (e: Exception) { JSONObject() }

    /** Catch-up: Xtream timeshift endpoint. start = "YYYY-MM-DD:HH-MM" in the SERVER's local time (its time zone from the login answer when known, else this device's), duration in minutes. */
    fun catchupUrl(host: String, pl: Playlist, item: Item, startSecs: Long, durationMin: Int): String {
        val f = java.text.SimpleDateFormat("yyyy-MM-dd:HH-mm", java.util.Locale.US)
        serverTz[host]?.let { tz -> try { f.timeZone = java.util.TimeZone.getTimeZone(tz) } catch (e: Exception) {} }
        val start = f.format(java.util.Date(startSecs * 1000))
        return "$host/streaming/timeshift.php?username=${enc(pl.user)}&password=${enc(pl.pass)}&stream=${item.id}&start=$start&duration=$durationMin"
    }

    /** The other common catch-up form: …/streaming/timeshift.php?username=U&password=P&stream=ID&start=S&duration=D → …/timeshift/U/P/D/S/ID.ts (null when the URL is not the first form). */
    fun catchupAlt(url: String): String? {
        val i = url.indexOf("/streaming/timeshift.php?"); if (i < 0) return null
        val host = url.substring(0, i)
        val q = HashMap<String, String>()
        for (p in url.substring(i + "/streaming/timeshift.php?".length).split("&")) { val e = p.indexOf('='); if (e > 0) q[p.substring(0, e)] = p.substring(e + 1) }
        val u = q["username"] ?: return null; val pw = q["password"] ?: return null; val id = q["stream"] ?: return null; val s = q["start"] ?: return null; val d = q["duration"] ?: return null
        return "$host/timeshift/$u/$pw/$d/$s/$id.ts"
    }

    /** XMLTV guide: only programmes from 6h ago to +36h are kept (memory), and only for channels in `wanted` (epg ids, trimmed + lowercased) when given. Returns epgId (lowercased) → sorted entries. */
    fun loadEpg(host: String, pl: Playlist, wanted: Set<String>? = null): Map<String, List<EpgEntry>> {
        val out = HashMap<String, ArrayList<EpgEntry>>()
        val now = System.currentTimeMillis() / 1000; val lo = now - 6 * 3600; val hi = now + 36 * 3600
        val req = Request.Builder().url("$host/xmltv.php?username=${enc(pl.user)}&password=${enc(pl.pass)}").header("User-Agent", "9KProTV/1.0").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("HTTP " + r.code)
            val parser = android.util.Xml.newPullParser(); parser.setInput(r.body!!.byteStream(), null)
            var ev = parser.eventType; var ch = ""; var st = 0L; var sp = 0L; var title = ""; var inTitle = false; var inProg = false
            val fmt = java.text.SimpleDateFormat("yyyyMMddHHmmss Z", java.util.Locale.US)
            val tzTail = Regex("([+-]\\d{4})$")
            // "20261102120000 +0300", "20261102120000+0300" (no space) and "20261102120000" (UTC) all parse
            fun ts(v: String?): Long = try {
                if (v == null) 0L else {
                    var t = v.trim()
                    if (!t.contains(' ')) { val m = tzTail.find(t); t = if (m != null && t.length > 5) t.substring(0, m.range.first).padEnd(14, '0') + " " + m.value else t.padEnd(14, '0') + " +0000" }
                    fmt.parse(t)!!.time / 1000
                }
            } catch (e: Exception) { 0L }
            while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> when (parser.name) {
                        "programme" -> { val c = (parser.getAttributeValue(null, "channel") ?: "").trim().lowercase()
                            if (wanted == null || c in wanted) { inProg = true; ch = c; st = ts(parser.getAttributeValue(null, "start")); sp = ts(parser.getAttributeValue(null, "stop")); title = "" } else { inProg = false; ch = "" } }
                        "title" -> if (inProg) inTitle = true
                    }
                    org.xmlpull.v1.XmlPullParser.TEXT -> if (inTitle) title += parser.text
                    org.xmlpull.v1.XmlPullParser.END_TAG -> when (parser.name) {
                        "title" -> inTitle = false
                        "programme" -> { if (inProg && ch.isNotEmpty() && sp > lo && st < hi) out.getOrPut(ch) { ArrayList() }.add(EpgEntry(st, sp, title.trim())); inProg = false }
                    }
                }
                ev = parser.next()
            }
        }
        out.values.forEach { it.sortBy { e -> e.start } }
        return out
    }

    /** Now/next for one channel (base64 titles) — instant, used until the full guide is ready. */
    fun shortEpg(host: String, pl: Playlist, item: Item): List<EpgEntry> {
        val j = JSONObject(api(host, pl, "get_short_epg", "&stream_id=" + enc(item.id) + "&limit=6"))
        val a = j.optJSONArray("epg_listings") ?: return emptyList()
        val out = ArrayList<EpgEntry>()
        for (i in 0 until a.length()) { val o = a.getJSONObject(i)
            val t = try { String(android.util.Base64.decode(o.optString("title"), android.util.Base64.DEFAULT), Charsets.UTF_8) } catch (e: Exception) { o.optString("title") }
            out.add(EpgEntry(o.optString("start_timestamp").toLongOrNull() ?: 0L, o.optString("stop_timestamp").toLongOrNull() ?: 0L, t.trim())) }
        return out.sortedBy { it.start }
    }

    fun liveUrl(host: String, pl: Playlist, item: Item) = "$host/live/${enc(pl.user)}/${enc(pl.pass)}/${item.id}.m3u8"
    fun liveUrlTs(host: String, pl: Playlist, item: Item) = "$host/live/${enc(pl.user)}/${enc(pl.pass)}/${item.id}.ts"
    fun movieUrl(host: String, pl: Playlist, item: Item) = "$host/movie/${enc(pl.user)}/${enc(pl.pass)}/${item.id}.${item.ext.ifEmpty { "mp4" }}"
    fun episodeUrl(host: String, pl: Playlist, ep: Episode) = "$host/series/${enc(pl.user)}/${enc(pl.pass)}/${ep.id}.${ep.ext.ifEmpty { "mp4" }}"

    private val reGroup = Regex("group-title=\"([^\"]*)\""); private val reLogo = Regex("tvg-logo=\"([^\"]*)\""); private val reTvgId = Regex("tvg-id=\"([^\"]*)\"")
    private val reCatchup = Regex("catchup=\"([^\"]*)\""); private val reCatchupDays = Regex("catchup-days=\"([^\"]*)\""); private val reMovieExt = Regex("\\.(mp4|mkv|avi|mov)(\\?|$)", RegexOption.IGNORE_CASE)
    /** M3U, read line by line (a big list never sits in memory as one string): groups become categories; everything that is not a movie/series URL is treated as live. */
    fun loadM3u(pl: Playlist): Map<Kind, Content> {
        val cats = HashMap<Kind, LinkedHashMap<String, Category>>()
        val items = HashMap<Kind, ArrayList<Item>>()
        var name = ""; var group = ""; var logo = ""; var tvgId = ""; var catchup = false; var catchupDays = 0; var n = 0
        val req = Request.Builder().url(pl.hosts.first()).header("User-Agent", "9KProTV/1.0").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("HTTP " + r.code)
            r.body!!.byteStream().bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (raw in lines) {
                    val line = raw.trim()
                    if (line.startsWith("#EXTINF")) {
                        // the name is what follows the first comma AFTER the last quoted attribute — so "AR| MBC 1, HD" keeps its comma
                        val lastQ = line.lastIndexOf('"'); val ci = line.indexOf(',', if (lastQ >= 0) lastQ + 1 else 0)
                        name = (if (ci >= 0) line.substring(ci + 1) else line.substringAfterLast(",")).trim()
                        group = reGroup.find(line)?.groupValues?.get(1) ?: ""
                        logo = reLogo.find(line)?.groupValues?.get(1) ?: ""
                        tvgId = reTvgId.find(line)?.groupValues?.get(1)?.trim() ?: ""
                        val cu = reCatchup.find(line)?.groupValues?.get(1) ?: ""; catchup = cu.isNotEmpty() && cu != "0"
                        catchupDays = reCatchupDays.find(line)?.groupValues?.get(1)?.trim()?.toIntOrNull() ?: 0
                    } else if (line.isNotEmpty() && !line.startsWith("#")) {
                        val kind = when {
                            line.contains("/movie/") -> Kind.MOVIE
                            line.contains("/series/") -> Kind.SERIES
                            reMovieExt.containsMatchIn(line) -> Kind.MOVIE
                            else -> Kind.LIVE
                        }
                        val cm = cats.getOrPut(kind) { LinkedHashMap() }
                        val cid = group.ifEmpty { "all" }
                        if (!cm.containsKey(cid)) cm[cid] = Category(cid, group.ifEmpty { "All" }, kind)
                        n++
                        // id from the stream link, not the position: favorites / resume survive when the list is re-ordered
                        items.getOrPut(kind) { ArrayList() }.add(Item(id = line.hashCode().toUInt().toString(16), name = name.ifEmpty { "Channel $n" }, kind = kind, categoryId = cid, icon = logo, epgId = tvgId, num = n, playlistId = pl.id, directUrl = line,
                            archive = kind == Kind.LIVE && catchup, archiveDays = if (kind == Kind.LIVE) catchupDays else 0))
                        name = ""; group = ""; logo = ""; tvgId = ""; catchup = false; catchupDays = 0
                    }
                }
            }
        }
        return Kind.values().associateWith { k -> Content(cats[k]?.values?.toList() ?: emptyList(), (items[k] ?: emptyList()).distinctBy { it.key }) }
    }
}
