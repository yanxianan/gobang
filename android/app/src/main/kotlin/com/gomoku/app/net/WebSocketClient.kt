package com.gomoku.app.net

import android.util.Log
import com.gomoku.app.util.Prefs
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

object WebSocketClient {
    private const val TAG = "GomokuWS"
    private var socket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val _events = MutableSharedFlow<Envelope>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<Envelope> = _events.asSharedFlow()

    @Volatile
    var connected: Boolean = false
        private set

    // 连接建立前的待发送消息
    private val pendingQueue = ConcurrentLinkedQueue<String>()

    fun connect() {
        if (socket != null) return
        val base = Prefs.wsBase()
        if (base.isBlank()) {
            Log.w(TAG, "服务器地址未配置")
            return
        }
        val url = "$base/ws"
        val req = Request.Builder().url(url).build()
        socket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected = true
                Log.d(TAG, "WS opened: $url")
                // flush 排队的消息
                while (true) {
                    val msg = pendingQueue.poll() ?: break
                    Log.d(TAG, "WS flush: $msg")
                    webSocket.send(msg)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "WS recv: $text")
                try {
                    val obj = JSONObject(text)
                    val type = obj.optString("type")
                    val payload = obj.opt("payload")
                    _events.tryEmit(Envelope(type, payload))
                } catch (e: Exception) {
                    Log.w(TAG, "解析失败: $text", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WS closing: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
                socket = null
                Log.d(TAG, "WS closed: $code $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected = false
                socket = null
                Log.w(TAG, "WS failure", t)
            }
        })
    }

    fun disconnect() {
        socket?.close(1000, "bye")
        socket = null
        connected = false
        pendingQueue.clear()
    }

    fun send(envelope: Envelope) {
        val obj = JSONObject()
        obj.put("type", envelope.type)
        if (envelope.payload != null) {
            obj.put("payload", JSONObject(envelope.payload as Map<*, *>))
        }
        val text = obj.toString()
        Log.d(TAG, "WS send: $text")
        val ws = socket
        if (ws != null && connected) {
            ws.send(text)
        } else {
            Log.d(TAG, "WS queue (not connected yet): $text")
            pendingQueue.offer(text)
            connect()
        }
    }

    fun sendCreateRoom(nickname: String, timeLimit: Int) {
        send(Envelope(MsgType.CREATE_ROOM, mapOf("nickname" to nickname, "time_limit" to timeLimit)))
    }

    fun sendJoinRoom(roomId: String, nickname: String) {
        send(Envelope(MsgType.JOIN_ROOM, mapOf("room_id" to roomId, "nickname" to nickname)))
    }

    fun sendLeaveRoom() {
        send(Envelope(MsgType.LEAVE_ROOM, null))
    }

    fun sendMove(x: Int, y: Int) {
        send(Envelope(MsgType.MOVE, mapOf("x" to x, "y" to y)))
    }

    fun sendUndoRequest() {
        send(Envelope(MsgType.UNDO_REQUEST, null))
    }

    fun sendUndoResponse(accept: Boolean) {
        send(Envelope(MsgType.UNDO_RESPONSE, mapOf("accept" to accept)))
    }

    fun sendResign() {
        send(Envelope(MsgType.RESIGN, null))
    }

    fun sendDrawOffer() {
        send(Envelope(MsgType.DRAW_OFFER, null))
    }

    fun sendDrawResponse(accept: Boolean) {
        send(Envelope(MsgType.DRAW_RESPONSE, mapOf("accept" to accept)))
    }

    fun sendChat(text: String) {
        send(Envelope(MsgType.CHAT, mapOf("text" to text)))
    }

    fun sendEmoji(emoji: String) {
        send(Envelope(MsgType.EMOJI, mapOf("emoji" to emoji)))
    }

    fun sendRematchRequest() {
        send(Envelope(MsgType.REMATCH_REQUEST, null))
    }

    fun sendRematchResponse(accept: Boolean) {
        send(Envelope(MsgType.REMATCH_RESPONSE, mapOf("accept" to accept)))
    }
}
