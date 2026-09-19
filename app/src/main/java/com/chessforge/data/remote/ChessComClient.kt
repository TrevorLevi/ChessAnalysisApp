package com.chessforge.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

class ChessComException(message: String, val statusCode: Int = -1) : Exception(message)

/** Une partie telle que renvoyee par l'API publique de chess.com. */
data class ChessComGame(
    val url: String,
    val pgn: String,
    val timeControl: String?,
    val timeClass: String?,
    val rules: String?,
    val endTime: Long,
    val rated: Boolean,
    val whiteUsername: String,
    val whiteRating: Int?,
    val whiteResult: String?,
    val blackUsername: String,
    val blackRating: Int?,
    val blackResult: String?,
    val eco: String?,
)

data class ChessComProfile(
    val username: String,
    val name: String?,
    val avatarUrl: String?,
    val followers: Int,
    val joined: Long,
)

/**
 * Client de l'API publique chess.com (`api.chess.com/pub`). Aucune authentification :
 * seules les donnees publiques d'un pseudo sont lues, en lecture seule.
 *
 * Un `User-Agent` explicite est obligatoire, chess.com renvoie 403 sans lui.
 */
class ChessComClient(
    private val userAgent: String = "ChessForge/1.0 (application Android locale d'analyse de parties)",
) {

    suspend fun profile(username: String): ChessComProfile = withContext(Dispatchers.IO) {
        val json = getJson("$BASE/player/${username.trim().lowercase()}")
        ChessComProfile(
            username = json.optString("username", username),
            name = json.optString("name").ifBlank { null },
            avatarUrl = json.optString("avatar").ifBlank { null },
            followers = json.optInt("followers"),
            joined = json.optLong("joined"),
        )
    }

    /** Liste des URL d'archives mensuelles, de la plus ancienne a la plus recente. */
    suspend fun archives(username: String): List<String> = withContext(Dispatchers.IO) {
        val json = getJson("$BASE/player/${username.trim().lowercase()}/games/archives")
        val array = json.optJSONArray("archives") ?: return@withContext emptyList()
        (0 until array.length()).map { array.getString(it) }
    }

    suspend fun games(archiveUrl: String): List<ChessComGame> = withContext(Dispatchers.IO) {
        val json = getJson(archiveUrl)
        val array = json.optJSONArray("games") ?: return@withContext emptyList()
        (0 until array.length()).mapNotNull { i ->
            val g = array.optJSONObject(i) ?: return@mapNotNull null
            val white = g.optJSONObject("white")
            val black = g.optJSONObject("black")
            val pgn = g.optString("pgn")
            if (pgn.isBlank()) return@mapNotNull null
            ChessComGame(
                url = g.optString("url"),
                pgn = pgn,
                timeControl = g.optString("time_control").ifBlank { null },
                timeClass = g.optString("time_class").ifBlank { null },
                rules = g.optString("rules").ifBlank { null },
                endTime = g.optLong("end_time"),
                rated = g.optBoolean("rated", true),
                whiteUsername = white?.optString("username") ?: "",
                whiteRating = white?.optInt("rating")?.takeIf { it > 0 },
                whiteResult = white?.optString("result"),
                blackUsername = black?.optString("username") ?: "",
                blackRating = black?.optInt("rating")?.takeIf { it > 0 },
                blackResult = black?.optString("result"),
                eco = g.optString("eco").ifBlank { null },
            )
        }
    }

    /** Classements actuels par cadence (blitz, rapide, bullet, quotidien). */
    suspend fun currentRatings(username: String): Map<String, Int> = withContext(Dispatchers.IO) {
        val json = getJson("$BASE/player/${username.trim().lowercase()}/stats")
        val out = LinkedHashMap<String, Int>()
        for ((key, label) in RATING_KEYS) {
            val rating = json.optJSONObject(key)?.optJSONObject("last")?.optInt("rating") ?: continue
            if (rating > 0) out[label] = rating
        }
        out
    }

    private fun getJson(url: String): JSONObject {
        val body = get(url)
        return try {
            JSONObject(body)
        } catch (e: Exception) {
            throw ChessComException("Reponse illisible de chess.com : ${e.message}")
        }
    }

    private fun get(url: String): String {
        var attempt = 0
        var lastError: Exception? = null
        while (attempt < MAX_RETRIES) {
            attempt++
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", userAgent)
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Accept-Encoding", "gzip")
                }
                val code = connection.responseCode
                if (code == 404) throw ChessComException("Pseudo ou ressource introuvable sur chess.com.", 404)
                if (code == 429) {
                    Thread.sleep(2_000L * attempt)
                    lastError = ChessComException("Trop de requetes, nouvelle tentative...", 429)
                    continue
                }
                if (code !in 200..299) {
                    throw ChessComException("chess.com a repondu $code.", code)
                }
                val raw = connection.inputStream
                val stream = if (connection.contentEncoding?.contains("gzip") == true) GZIPInputStream(raw) else raw
                return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            } catch (e: ChessComException) {
                if (e.statusCode == 404) throw e
                lastError = e
            } catch (e: Exception) {
                Log.w(TAG, "Echec GET $url (tentative $attempt)", e)
                lastError = e
                Thread.sleep(800L * attempt)
            } finally {
                connection?.disconnect()
            }
        }
        throw ChessComException("Impossible de joindre chess.com : ${lastError?.message ?: "erreur reseau"}")
    }

    companion object {
        private const val TAG = "ChessComClient"
        private const val BASE = "https://api.chess.com/pub"
        private const val MAX_RETRIES = 3

        private val RATING_KEYS = listOf(
            "chess_bullet" to "bullet",
            "chess_blitz" to "blitz",
            "chess_rapid" to "rapid",
            "chess_daily" to "daily",
        )
    }
}
