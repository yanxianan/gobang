package com.gomoku.app.net

import android.util.Log
import com.gomoku.app.util.Prefs
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ApiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    data class RoomItem(val roomId: String, val blackName: String, val timeLimit: Int)

    fun listRooms(): List<RoomItem> {
        val base = Prefs.httpBase()
        if (base.isBlank()) return emptyList()
        val req = Request.Builder().url("$base/api/rooms").build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList<RoomItem>()
                val body = resp.body?.string() ?: return@use emptyList<RoomItem>()
                val arr = JSONObject(body).optJSONArray("rooms") ?: JSONArray()
                val out = ArrayList<RoomItem>(arr.length())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    out.add(RoomItem(
                        o.optString("room_id"),
                        o.optString("black_name"),
                        o.optInt("time_limit")
                    ))
                }
                out
            }
        }.onFailure { Log.w("ApiService", "listRooms", it) }.getOrDefault(emptyList())
    }

    data class GameSummary(
        val id: Long,
        val roomId: String,
        val blackName: String,
        val whiteName: String,
        val winner: Int,
        val reason: String,
        val startedAt: String,
        val endedAt: String
    )

    fun listGames(name: String, limit: Int = 20, offset: Int = 0): List<GameSummary> {
        val base = Prefs.httpBase()
        if (base.isBlank() || name.isBlank()) return emptyList()
        val url = "$base/api/games?name=${java.net.URLEncoder.encode(name, "UTF-8")}&limit=$limit&offset=$offset"
        val req = Request.Builder().url(url).build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList<GameSummary>()
                val body = resp.body?.string() ?: return@use emptyList<GameSummary>()
                val arr = JSONObject(body).optJSONArray("games") ?: JSONArray()
                val out = ArrayList<GameSummary>(arr.length())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    out.add(GameSummary(
                        id = o.optLong("id"),
                        roomId = o.optString("room_id"),
                        blackName = o.optString("black_name"),
                        whiteName = o.optString("white_name"),
                        winner = o.optInt("winner"),
                        reason = o.optString("reason"),
                        startedAt = o.optString("started_at"),
                        endedAt = o.optString("ended_at")
                    ))
                }
                out
            }
        }.onFailure { Log.w("ApiService", "listGames", it) }.getOrDefault(emptyList())
    }

    data class GameDetail(
        val id: Long,
        val roomId: String,
        val blackName: String,
        val whiteName: String,
        val winner: Int,
        val reason: String,
        val moves: List<Move>
    )

    fun getGame(id: Long): GameDetail? {
        val base = Prefs.httpBase()
        if (base.isBlank()) return null
        val req = Request.Builder().url("$base/api/games?id=$id").build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val o = JSONObject(body)
                val movesArr = o.optJSONArray("moves") ?: JSONArray()
                val moves = ArrayList<Move>(movesArr.length())
                for (i in 0 until movesArr.length()) {
                    val m = movesArr.getJSONObject(i)
                    moves.add(Move(m.optInt("x"), m.optInt("y"), m.optInt("seat")))
                }
                GameDetail(
                    id = o.optLong("id"),
                    roomId = o.optString("room_id"),
                    blackName = o.optString("black_name"),
                    whiteName = o.optString("white_name"),
                    winner = o.optInt("winner"),
                    reason = o.optString("reason"),
                    moves = moves
                )
            }
        }.onFailure { Log.w("ApiService", "getGame", it) }.getOrNull()
    }
}
