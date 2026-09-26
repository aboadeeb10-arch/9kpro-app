package tv.ninekpro.app.data

import org.json.JSONArray
import org.json.JSONObject

/** One source of channels: an Xtream Codes line (host/user/pass) or an M3U link. Hosts are tried in order (failover). */
data class Playlist(
    val id: String,
    val name: String,
    val kind: String,              // "xtream" | "m3u"
    val hosts: List<String>,       // xtream: base URLs; m3u: the single link
    val user: String = "",
    val pass: String = "",
    val panel: String = "",
    val trial: Boolean = false,
    val exp: String = "",
    val fromPanel: Boolean = true,
    val protect: Boolean = false,   // panel "Protect this": the app never shows the username / password of this playlist
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("kind", kind).put("hosts", JSONArray(hosts))
        .put("user", user).put("pass", pass).put("panel", panel).put("trial", trial).put("exp", exp).put("fromPanel", fromPanel).put("protect", protect)

    companion object {
        fun fromJson(o: JSONObject) = Playlist(
            id = o.optString("id"), name = o.optString("name"), kind = o.optString("kind", "xtream"),
            hosts = o.optJSONArray("hosts")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
            user = o.optString("user"), pass = o.optString("pass"), panel = o.optString("panel"), trial = o.optBoolean("trial"),
            exp = o.optString("exp"), fromPanel = o.optBoolean("fromPanel", true), protect = o.optBoolean("protect", false)
        )
    }
}

data class Category(val id: String, val name: String, val kind: Kind)

enum class Kind { LIVE, MOVIE, SERIES }

/** Live channel, movie or series entry. `key` is stable across playlists (playlistId:kind:id) for favorites / resume / order. */
data class Item(
    val id: String,
    val name: String,
    val kind: Kind,
    val categoryId: String,
    val icon: String = "",
    val ext: String = "",          // container extension for VOD (mp4/mkv)
    val rating: String = "",
    val added: Long = 0L,          // unix seconds
    val year: String = "",
    val plot: String = "",
    val epgId: String = "",
    val num: Int = 0,
    val playlistId: String = "",
    val directUrl: String = "",    // m3u playlists carry the stream link directly
    val archive: Boolean = false,  // live channel supports catch-up (tv_archive)
    val archiveDays: Int = 0,
) {
    val key: String get() = "$playlistId:${kind.name}:$id"
}

data class EpgEntry(val start: Long, val stop: Long, val title: String)   // unix seconds

data class Episode(
    val id: String, val title: String, val season: Int, val num: Int, val ext: String, val icon: String, val plot: String, val durationSecs: Int,
    val seriesId: String, val playlistId: String,
) { val key: String get() = "$playlistId:EP:$id" }

data class SeriesInfo(val plot: String, val cast: String, val genre: String, val year: String, val rating: String, val backdrop: String, val seasons: Map<Int, List<Episode>>)

data class ResumeEntry(val key: String, val title: String, val kind: String, val image: String, val positionMs: Long, val durationMs: Long, val updated: Long, val extra: String = "") {
    fun toJson(): JSONObject = JSONObject().put("key", key).put("title", title).put("kind", kind).put("image", image).put("pos", positionMs).put("dur", durationMs).put("t", updated).put("extra", extra)
    companion object { fun fromJson(o: JSONObject) = ResumeEntry(o.optString("key"), o.optString("title"), o.optString("kind"), o.optString("image"), o.optLong("pos"), o.optLong("dur"), o.optLong("t"), o.optString("extra")) }
}

data class Brand(
    val name: String = "9K Pro TV", val color: String = "#ffd21f", val bg1: String = "#1a1408", val bg2: String = "#0d0a04", val bg3: String = "#000000",
    val theme: String = "gold", val whatsapp: String = "", val telegram: String = "", val website: String = "", val logo: String = "", val splash: String = "",
    val apk: String = "", val renewDays: Int = 7, val renewText: String = "", val tmdb: String = "", val opensubsOn: Boolean = false, val footballOn: Boolean = false,
) {
    companion object {
        fun fromJson(o: JSONObject?): Brand {
            if (o == null) return Brand()
            return Brand(o.optString("name", "9K Pro TV"), o.optString("color", "#ffd21f"), o.optString("bg1", "#1a1408"), o.optString("bg2", "#0d0a04"), o.optString("bg3", "#000000"),
                o.optString("theme", "gold"), o.optString("whatsapp"), o.optString("telegram"), o.optString("website"), o.optString("logo"), o.optString("splash"), o.optString("apk"),
                o.optInt("renew_days", 7), o.optString("renew_text"), o.optString("tmdb"), o.optBoolean("opensubs_on"), o.optBoolean("football_on"))
        }
    }
}

data class Account(val id: Int, val username: String, val name: String, val exp: String, val days: Int?, val status: String, val expired: Boolean) {
    companion object {
        fun fromJson(o: JSONObject?): Account? {
            if (o == null) return null
            return Account(o.optInt("id"), o.optString("username"), o.optString("name"), o.optString("exp"), if (o.isNull("days")) null else o.optInt("days"), o.optString("status"), o.optBoolean("expired"))
        }
    }
}

data class Message(val id: Int, val title: String, val text: String, val kind: String, val image: String = "", val link: String = "")

/** One football fixture from the panel (API-Football). status: NS · 1H · HT · 2H · ET · P · FT · AET · PEN · PST · CANC … */
data class Match(val id: Long, val ts: Long, val status: String, val min: Int, val league: String, val leagueLogo: String, val country: String,
                 val home: String, val homeLogo: String, val away: String, val awayLogo: String, val hg: Int, val ag: Int) {
    val live: Boolean get() = status in setOf("1H", "2H", "HT", "ET", "BT", "P", "LIVE", "INT")
    val finished: Boolean get() = status in setOf("FT", "AET", "PEN", "CANC", "ABD", "AWD", "WO")
    val upcoming: Boolean get() = !live && !finished
    companion object { fun fromJson(o: JSONObject) = Match(o.optLong("id"), o.optLong("ts"), o.optString("status"), o.optInt("min"), o.optString("league"), o.optString("league_logo"), o.optString("country"),
        o.optString("home"), o.optString("home_logo"), o.optString("away"), o.optString("away_logo"), if (o.isNull("hg")) -1 else o.optInt("hg"), if (o.isNull("ag")) -1 else o.optInt("ag")) }
}
