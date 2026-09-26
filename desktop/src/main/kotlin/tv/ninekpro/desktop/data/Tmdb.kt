package tv.ninekpro.desktop.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class Person(val name: String, val role: String, val photo: String)
data class TmdbInfo(val title: String, val overview: String, val poster: String, val backdrop: String, val year: String, val rating: String, val genres: String, val cast: String, val trailer: String, val runtime: Int,
                    val people: List<Person> = emptyList(), val director: String = "", val tagline: String = "", val votes: Int = 0, val imdbId: String = "", val seasons: Int = 0, val episodes: Int = 0, val status: String = "")

/** The Movie Database — fills posters, plot, cast and trailers when the panel's own info is poor. Key comes from the panel brand (My App → Brand). */
class Tmdb(private val keyProvider: () -> String) {
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    private val cache = HashMap<String, TmdbInfo?>()
    private val IMG = "https://image.tmdb.org/t/p/"

    private fun get(url: String): JSONObject? = try { client.newCall(Request.Builder().url(url).build()).execute().use { r -> if (r.isSuccessful) JSONObject(r.body?.string() ?: "{}") else null } } catch (e: Exception) { null }

    /** Cleans panel names like "AR| The Batman (2022) 4K" → "The Batman", year 2022. */
    fun cleanName(raw: String): Pair<String, String> {
        var n = raw.replace(Regex("^\\s*[A-Z]{2,3}\\s*[|:\\-]\\s*"), "").replace(Regex("\\[[^\\]]*\\]|\\([^)]*\\)"), " ")
        val y = Regex("(19|20)\\d{2}").find(raw)?.value ?: ""
        n = n.replace(Regex("(?i)\\b(4k|fhd|hd|uhd|1080p|720p|multi|dubbed|arabic|ar|en|s\\d+e\\d+)\\b"), " ").replace(Regex("\\s+"), " ").trim(' ', '-', '|', ':')
        return n to y
    }

    fun lookup(name: String, year: String, isSeries: Boolean, lang: String = "en-US"): TmdbInfo? {
        val key = keyProvider(); if (key.isEmpty()) return null
        val (clean, y) = cleanName(name); val yy = year.take(4).ifEmpty { y }
        val ck = (if (isSeries) "tv:" else "m:") + clean + ":" + yy
        if (cache.containsKey(ck)) return cache[ck]
        val kind = if (isSeries) "tv" else "movie"
        val q = java.net.URLEncoder.encode(clean, "UTF-8")
        val s = get("https://api.themoviedb.org/3/search/$kind?api_key=$key&query=$q&language=$lang" + (if (yy.isNotEmpty()) (if (isSeries) "&first_air_date_year=" else "&year=") + yy else ""))
            ?: (if (yy.isNotEmpty()) get("https://api.themoviedb.org/3/search/$kind?api_key=$key&query=$q&language=$lang") else null)
        val first = s?.optJSONArray("results")?.optJSONObject(0)
        if (first == null) { cache[ck] = null; return null }
        val id = first.optInt("id")
        val d = get("https://api.themoviedb.org/3/$kind/$id?api_key=$key&language=$lang&append_to_response=credits,videos,external_ids") ?: first
        val genres = d.optJSONArray("genres")?.let { a -> (0 until a.length()).joinToString(", ") { a.getJSONObject(it).optString("name") } } ?: ""
        val castArr = d.optJSONObject("credits")?.optJSONArray("cast")
        val people = castArr?.let { a -> (0 until minOf(12, a.length())).map { val x = a.getJSONObject(it); Person(x.optString("name"), x.optString("character"), x.optString("profile_path").let { pp -> if (pp.isNotEmpty() && pp != "null") IMG + "w185" + pp else "" }) } } ?: emptyList()
        val cast = people.take(6).joinToString(", ") { it.name }
        val crew = d.optJSONObject("credits")?.optJSONArray("crew")
        val director = crew?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.optString("job") == "Director" }?.optString("name") } ?: (d.optJSONArray("created_by")?.optJSONObject(0)?.optString("name") ?: "")
        var trailer = ""
        d.optJSONObject("videos")?.optJSONArray("results")?.let { a -> for (i in 0 until a.length()) { val v = a.getJSONObject(i); if (v.optString("site") == "YouTube" && (v.optString("type") == "Trailer" || trailer.isEmpty())) { trailer = "https://www.youtube.com/watch?v=" + v.optString("key"); if (v.optString("type") == "Trailer") break } } }
        val info = TmdbInfo(
            title = d.optString(if (isSeries) "name" else "title", clean), overview = d.optString("overview"),
            poster = d.optString("poster_path").let { if (it.isNotEmpty() && it != "null") IMG + "w342" + it else "" },
            backdrop = d.optString("backdrop_path").let { if (it.isNotEmpty() && it != "null") IMG + "w780" + it else "" },
            year = d.optString(if (isSeries) "first_air_date" else "release_date").take(4), rating = String.format("%.1f", d.optDouble("vote_average", 0.0)),
            genres = genres, cast = cast, trailer = trailer, runtime = if (isSeries) (d.optJSONArray("episode_run_time")?.optInt(0) ?: 0) else d.optInt("runtime"),
            people = people, director = director, tagline = d.optString("tagline"), votes = d.optInt("vote_count"), imdbId = d.optJSONObject("external_ids")?.optString("imdb_id") ?: "",
            seasons = d.optInt("number_of_seasons"), episodes = d.optInt("number_of_episodes"), status = d.optString("status"))
        cache[ck] = info; return info
    }
}
