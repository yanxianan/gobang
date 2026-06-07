package com.gomoku.app.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gomoku.app.net.ChatItem
import com.gomoku.app.net.Envelope
import com.gomoku.app.net.Move
import com.gomoku.app.net.MsgType
import com.gomoku.app.net.Status
import com.gomoku.app.net.Stone
import com.gomoku.app.net.WebSocketClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class GameUiState(
    val roomId: String = "",
    val mySeat: Int = 0,
    val blackName: String = "",
    val whiteName: String = "",
    val status: String = Status.WAITING,
    val turn: Int = Stone.BLACK,
    val board: List<List<Int>> = List(15) { List(15) { Stone.NONE } },
    val lastMove: Move? = null,
    val winner: Int = 0,
    val winReason: String = "",
    val timeLimit: Int = 0,
    val blackTime: Int = 0,
    val whiteTime: Int = 0,
    val messages: List<ChatItem> = emptyList(),
    val pendingUndo: Boolean = false,
    val undoRequestFrom: Int = 0,
    val pendingDraw: Boolean = false,
    val drawOfferFrom: Int = 0,
    val errorMessage: String? = null,
    val opponentLeft: Boolean = false,
    val gameOverAnnounced: Boolean = false,
    val gameOverTimestamp: Long = 0L,
    // rematch 流程
    val waitingMyRematch: Boolean = false,    // 我方已点"不服再战", 等待对方
    val pendingRematchOffer: Boolean = false, // 对方发来"不服再战", 等我响应
    val rematchOfferFrom: Int = 0,
    val rematchRejectCount: Int = 0,          // 对方拒绝后再次求的累计拒绝数
    val rematchCancelled: Boolean = false,    // 一方拒绝, 房间即将结束
    val rematchCancelReason: String = "",
    val lastGameResultShown: Long = 0L,       // 用于防重弹结算框
    val moveCount: Int = 0,                   // 当前棋盘步数
    val myPendingDrawOffer: Boolean = false,  // 我方发起了求饶, 等待对方响应
    val drawResponseResult: String? = null    // null=无, "accepted"=对方饶了我, "rejected"=对方甩我一巴掌
)

class GameViewModel : ViewModel() {

    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            WebSocketClient.events.collect { ev ->
                handleEvent(ev)
            }
        }
    }

    private fun handleEvent(ev: Envelope) {
        when (ev.type) {
            MsgType.ROOM_CREATED -> {
                val p = parseObj(ev.payload) ?: return
                _state.value = _state.value.copy(
                    roomId = p.optString("room_id"),
                    mySeat = p.optInt("seat"),
                    status = Status.WAITING
                )
            }
            MsgType.ROOM_JOINED -> {
                val p = parseObj(ev.payload) ?: return
                _state.value = _state.value.copy(
                    roomId = p.optString("room_id"),
                    mySeat = p.optInt("seat"),
                    status = Status.WAITING
                )
            }
            MsgType.ROOM_STATE -> {
                val p = parseObj(ev.payload) ?: return
                val moves = parseMoves(p.optJSONArray("moves"))
                _state.value = _state.value.copy(
                    roomId = p.optString("room_id"),
                    mySeat = p.optInt("your_seat"),
                    blackName = p.optString("black_name"),
                    whiteName = p.optString("white_name"),
                    status = p.optString("status"),
                    turn = p.optInt("turn"),
                    board = parseBoard(p.optJSONArray("board")),
                    winner = p.optInt("winner"),
                    timeLimit = p.optInt("time_limit"),
                    blackTime = p.optInt("black_time"),
                    whiteTime = p.optInt("white_time"),
                    lastMove = moves.lastOrNull(),
                    moveCount = moves.size
                )
            }
            MsgType.GAME_START -> {
                val p = parseObj(ev.payload) ?: return
                _state.value = _state.value.copy(
                    status = Status.PLAYING,
                    turn = p.optInt("first_seat"),
                    timeLimit = p.optInt("time_limit"),
                    blackTime = p.optInt("black_time"),
                    whiteTime = p.optInt("white_time")
                )
            }
            MsgType.S2C_MOVE -> {
                val p = parseObj(ev.payload) ?: return
                val x = p.optInt("x"); val y = p.optInt("y")
                val placedSeat = p.optInt("seat")
                val st = _state.value
                val newBoard = st.board.map { it.toMutableList() }.toMutableList()
                val seat = if (placedSeat == Stone.BLACK || placedSeat == Stone.WHITE) placedSeat else st.turn
                if (y in newBoard.indices && x in newBoard[y].indices && newBoard[y][x] == Stone.NONE) {
                    newBoard[y][x] = seat
                }
                val newTurn = if (seat == Stone.BLACK) Stone.WHITE else Stone.BLACK
                _state.value = st.copy(
                    board = newBoard,
                    turn = newTurn,
                    lastMove = Move(x, y, seat)
                )
            }
            MsgType.S2C_UNDO_REQUEST -> {
                val p = parseObj(ev.payload) ?: return
                val from = p.optInt("from_seat")
                val st = _state.value
                if (from != st.mySeat) {
                    _state.value = st.copy(pendingUndo = true, undoRequestFrom = from)
                }
            }
            MsgType.S2C_UNDO_RESPONSE -> {
                val p = parseObj(ev.payload) ?: return
                val st = _state.value
                if (!p.optBoolean("accept")) {
                    appendMsg(st.mySeat, "系统", "对方拒绝了你的悔棋请求")
                }
                _state.value = st.copy(pendingUndo = false, undoRequestFrom = 0)
            }
            MsgType.GAME_OVER -> {
                val p = parseObj(ev.payload) ?: return
                _state.value = _state.value.copy(
                    status = Status.FINISHED,
                    winner = p.optInt("winner"),
                    winReason = p.optString("reason"),
                    gameOverAnnounced = true,
                    gameOverTimestamp = System.currentTimeMillis(),
                    // 清空rematch相关
                    waitingMyRematch = false,
                    pendingRematchOffer = false
                )
            }
            MsgType.S2C_DRAW_OFFER -> {
                val p = parseObj(ev.payload) ?: return
                val from = p.optInt("from_seat")
                val st = _state.value
                if (from != st.mySeat) {
                    _state.value = st.copy(pendingDraw = true, drawOfferFrom = from)
                }
            }
            MsgType.S2C_DRAW_RESPONSE -> {
                val p = parseObj(ev.payload) ?: return
                val st = _state.value
                if (st.myPendingDrawOffer) {
                    // 我方发起的求饶, 收到对方响应, 弹反馈弹框
                    val result = if (p.optBoolean("accept")) "accepted" else "rejected"
                    _state.value = st.copy(
                        myPendingDrawOffer = false,
                        drawResponseResult = result,
                        pendingDraw = false,
                        drawOfferFrom = 0
                    )
                } else {
                    // 防御性: 接收方收到 S2C_DRAW_RESPONSE (理论不会)
                    if (!p.optBoolean("accept")) {
                        appendMsg(st.mySeat, "系统", "对方拒绝了你的和棋提议")
                    }
                    _state.value = st.copy(pendingDraw = false, drawOfferFrom = 0)
                }
            }
            MsgType.S2C_CHAT -> {
                val p = parseObj(ev.payload) ?: return
                appendMsg(p.optInt("from_seat"), p.optString("name"), p.optString("text"), false)
            }
            MsgType.S2C_EMOJI -> {
                val p = parseObj(ev.payload) ?: return
                appendMsg(p.optInt("from_seat"), p.optString("name"), p.optString("emoji"), true)
            }
            MsgType.CLOCK -> {
                val p = parseObj(ev.payload) ?: return
                _state.value = _state.value.copy(
                    blackTime = p.optInt("black_time"),
                    whiteTime = p.optInt("white_time"),
                    turn = p.optInt("turn")
                )
            }
            MsgType.OPPONENT_LEFT -> {
                _state.value = _state.value.copy(opponentLeft = true)
            }
            MsgType.ERROR -> {
                val p = parseObj(ev.payload) ?: return
                _state.value = _state.value.copy(errorMessage = p.optString("message"))
            }
            MsgType.S2C_REMATCH_REQUEST -> {
                val p = parseObj(ev.payload) ?: return
                val from = p.optInt("from_seat")
                val st = _state.value
                if (from != st.mySeat && st.status == Status.FINISHED) {
                    _state.value = st.copy(pendingRematchOffer = true, rematchOfferFrom = from)
                }
            }
            MsgType.S2C_REMATCH_RESPONSE -> {
                val p = parseObj(ev.payload) ?: return
                val from = p.optInt("from_seat")
                val st = _state.value
                // 对方接受了我方"不服再战", 我方等待中
                if (p.optBoolean("accept") && from != st.mySeat && st.waitingMyRematch) {
                    appendMsg(st.mySeat, "系统", "对方已接下挑战!")
                }
            }
            MsgType.S2C_REMATCH_START -> {
                // 双方都接受, 新一局开始
                val p = parseObj(ev.payload) ?: return
                val st = _state.value
                _state.value = st.copy(
                    status = Status.PLAYING,
                    turn = p.optInt("first_seat"),
                    timeLimit = p.optInt("time_limit"),
                    blackTime = p.optInt("black_time"),
                    whiteTime = p.optInt("white_time"),
                    winner = 0,
                    winReason = "",
                    gameOverAnnounced = false,
                    gameOverTimestamp = 0L,
                    waitingMyRematch = false,
                    pendingRematchOffer = false,
                    rematchOfferFrom = 0,
                    board = List(15) { List(15) { Stone.NONE } },
                    lastMove = null,
                    pendingUndo = false,
                    undoRequestFrom = 0,
                    pendingDraw = false,
                    drawOfferFrom = 0
                )
                appendMsg(0, "系统", "新一局开始! 第${p.optInt("new_game_no")}局, 轮到${if (p.optInt("first_seat")==1) "黑方" else "白方"}先手", false)
            }
            MsgType.S2C_REMATCH_CANCEL -> {
                val p = parseObj(ev.payload) ?: return
                val st = _state.value
                _state.value = st.copy(
                    rematchCancelled = true,
                    rematchCancelReason = p.optString("reason"),
                    rematchRejectCount = p.optInt("reject_count"),
                    waitingMyRematch = false,
                    pendingRematchOffer = false
                )
            }
        }
    }

    private fun appendMsg(seat: Int, name: String, text: String, isEmoji: Boolean = false) {
        val st = _state.value
        _state.value = st.copy(messages = st.messages + ChatItem(seat, name, text, isEmoji))
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun consumeOpponentLeft() {
        _state.value = _state.value.copy(opponentLeft = false)
    }

    fun markRematchRequested() {
        _state.value = _state.value.copy(waitingMyRematch = true)
    }

    fun markMyDrawOffer() {
        _state.value = _state.value.copy(myPendingDrawOffer = true)
    }

    fun consumeDrawResponse() {
        _state.value = _state.value.copy(drawResponseResult = null)
    }

    fun consumeRematchOffer() {
        _state.value = _state.value.copy(pendingRematchOffer = false, rematchOfferFrom = 0)
    }

    fun consumeRematchCancel() {
        _state.value = _state.value.copy(rematchCancelled = false)
    }

    fun consumeGameOverAnnounced() {
        _state.value = _state.value.copy(gameOverAnnounced = false, gameOverTimestamp = 0L)
    }

    private fun parseObj(any: Any?): JSONObject? = when (any) {
        is JSONObject -> any
        is Map<*, *> -> {
            val o = JSONObject()
            any.forEach { (k, v) -> o.put(k.toString(), v) }
            o
        }
        else -> null
    }

    private fun parseBoard(arr: JSONArray?): List<List<Int>> {
        val empty = (0 until 15).map { (0 until 15).map { Stone.NONE } }
        if (arr == null) return empty
        return (0 until arr.length()).map { y ->
            val row = arr.optJSONArray(y) ?: JSONArray()
            (0 until 15).map { x -> row.optInt(x, Stone.NONE) }
        }
    }

    private fun parseMoves(arr: JSONArray?): List<Move> {
        if (arr == null) return emptyList()
        val out = ArrayList<Move>(arr.length())
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            out.add(Move(m.optInt("x"), m.optInt("y"), m.optInt("seat")))
        }
        return out
    }
}
