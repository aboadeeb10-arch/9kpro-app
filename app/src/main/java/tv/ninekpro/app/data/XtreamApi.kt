package tv.ninekpro.app.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Xtream Codes player_api + M3U parsing. All calls are blocking — run them on Dispatchers.IO. */
class XtreamApi {
    private val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

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

    /** First host that answers is remembered by the repo (failover). */
    fun pickHost(pl: Playlist): String {
        for (h in pl.hosts) {
            try {
                val t = get(base(h) + "/player_api.php?username=" + enc(pl.user) + "&password=" + enc(pl.pass))
                val j = JSONObject(t)
                val ui = j.optJSONObject("user_info")
                if (ui != null && ui.optString("auth", "1") != "0") return base(h)
            } catch (e: Exception) { /* try next */ }
        }
        throw IllegalStateException("no host answered")
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
        return Content(cats, items)
    }
    private fun str(r: android.util.JsonReader): String = when (r.peek()) {
        android.util.JsonToken.STRING -> r.nextString()
        android.util.JsonToken.NUMBER -> r.nextString()
        android.util.JsonToken.BOOLEAN -> r.nextBoolean().toString()
        else -> { r.skipValue(); "" }
    }

    fun seriesInfo(host: String, pl: Playlist, seriesId: String): SeriesInfo {
        val j = JSONObject(api(host, pl, "get_series_info", "&series_id=" + enc(seriesId)))
        val info = j.optJSONObject("info") ?: JSONObject()
        val seasons = LinkedHashMap<Int, MutableList<Episode>>()
        val eps = j.optJSONObject("episodes")
        if (eps != null) {
            val keys = eps.keys().asSequence().toList().sortedBy { it.toIntOrNull() ?: 0 }
            for (k in keys) {
                val arr = eps.optJSONArray(k) ?: continue
                val sn = k.toIntOrNull() ?: 0
                val list = seasons.getOrPut(sn) { ArrayList() }
                for (i in 0 until arr.length()) {
                    val e = arr.getJSONObject(i); val ei = e.optJSONObject("info") ?: JSONObject()
                    list.add(Episode(e.optString("id"), e.optString("title"), sn, e.optInt("episode_num"), e.optString("container_extension", "mp4"),
                        ei.optString("movie_image"), ei.optString("plot"), ei.optString("duration_secs").toIntOrNull() ?: 0, seriesId, pl.id))
                }
                list.sortBy { it.num }
            }
        }
        var backdrop = ""
        val bd = info.opt("backdrop_path"); if (bd is JSONArray && bd.length() > 0) backdrop = bd.optString(0) else if (bd is String) backdrop = bd
        return SeriesInfo(info.optString("plot"), info.optString("cast"), info.optString("genre"), info.optString("releaseDate").take(4), info.optString("rating"), backdrop, seasons)
    }

    fun vodInfo(host: String, pl: Playlist, vodId: String): JSONObject = try { JSONObject(api(host, pl, "get_vod_info", "&vod_id=" + enc(vodId))) } catch (e: Exception) { JSONObject() }

    /** Catch-up: Xtream timeshift endpoint. start = "YYYY-MM-DD:HH-MM" (server local time), duration in minutes. */
    fun catchupUrl(host: String, pl: Playlist, item: Item, startSecs: Long, durationMin: Int): String {
        val f = java.text.SimpleDateFormat("yyyy-MM-dd:HH-mm", java.util.Locale.US); val start = f.format(java.util.Date(startSecs * 1000))
        return "$host/streaming/timeshift.php?username=${enc(pl.user)}&password=${enc(pl.pass)}&stream=${item.id}&start=$start&duration=$durationMin"
    }

    /** XMLTV guide: only programmes from 6h ago to +36h are kept (memory). Returns epgId → sorted entries. */
    fun loadEpg(host: String, pl: Playlist): Map<String, List<EpgEntry>> {
        val out = HashMap<String, ArrayList<EpgEntry>>()
        val now = System.currentTimeMillis() / 1000; val lo = now - 6 * 3600; val hi = now + 36 * 3600
        val req = Request.Builder().url("$host/xmltv.php?username=${enc(pl.user)}&password=${enc(pl.pass)}").header("User-Agent", "9KProTV/1.0").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("HTTP " + r.code)
            val parser = android.util.Xml.newPullParser(); parser.setInput(r.body!!.byteStream(), null)
            var ev = parser.eventType; var ch = ""; var st = 0L; var sp = 0L; var title = ""; var inTitle = false; var inProg = false
            val fmt = java.text.SimpleDateFormat("yyyyMMddHHmmss Z", java.util.Locale.US)
            fun ts(v: String?): Long = try { if (v == null) 0L else fmt.parse(v.trim().let { if (it.contains(' ')) it else "$it +0000" })!!.time / 1000 } catch (e: Exception) { 0L }
            while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> when (parser.name) {
                        "programme" -> { inProg = true; ch = parser.getAttributeValue(null, "channel") ?: ""; st = ts(parser.getAttributeValue(null, "start")); sp = ts(parser.getAttributeValue(null, "stop")); title = "" }
                        "title" -> if (inProg) inTitle = true
                    }
                    org.xmlpull.v1.XmlPullParser.TEXT -> if (inTitle) title += parser.text
                    org.xmlpull.v1.XmlPullParser.END_TAG -> when (parser.name) {
                        "title" -> inTitle = false
                        "programme" -> { inProg = false; if (ch.isNotEmpty() && sp > lo && st < hi) out.getOrPut(ch) { ArrayList() }.add(EpgEntry(st, sp, title.trim())) }
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

    /** M3U: groups become categories; everything that is not a movie/series URL is treated as live. */
    fun loadM3u(pl: Playlist): Map<Kind, Content> {
        val text = get(pl.hosts.first())
        val cats = HashMap<Kind, LinkedHashMap<String, Category>>()
        val items = HashMap<Kind, ArrayList<Item>>()
        var name = ""; var group = ""; var logo = ""; var n = 0
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("#EXTINF")) {
                name = line.substringAfterLast(",").trim()
                group = Regex("group-title=\"([^\"]*)\"").find(line)?.groupValues?.get(1) ?: ""
                logo = Regex("tvg-logo=\"([^\"]*)\"").find(line)?.groupValues?.get(1) ?: ""
            } else if (line.isNotEmpty() && !line.startsWith("#")) {
                val kind = when {
                    Regex("/movie/").containsMatchIn(line) -> Kind.MOVIE
                    Regex("/series/").containsMatchIn(line) -> Kind.SERIES
                    Regex("\\.(mp4|mkv|avi|mov)(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(line) -> Kind.MOVIE
                    else -> Kind.LIVE
                }
                val cm = cats.getOrPut(kind) { LinkedHashMap() }
                val cid = group.ifEmpty { "all" }
                if (!cm.containsKey(cid)) cm[cid] = Category(cid, group.ifEmpty { "All" }, kind)
                n++
                items.getOrPut(kind) { ArrayList() }.add(Item(id = "m$n", name = name, kind = kind, categoryId = cid, icon = logo, num = n, playlistId = pl.id, directUrl = line))
                name = ""; group = ""; logo = ""
            }
        }
        return Kind.values().associateWith { k -> Content(cats[k]?.values?.toList() ?: emptyList(), items[k] ?: emptyList()) }
    }
}
