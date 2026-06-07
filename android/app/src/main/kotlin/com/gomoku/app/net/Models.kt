package com.gomoku.app.net

/** 与服务端协议对应的消息类型与数据类 */

object MsgType {
    // C2S
    const val CREATE_ROOM = "create_room"
    const val JOIN_ROOM = "join_room"
    const val LEAVE_ROOM = "leave_room"
    const val MOVE = "move"
    const val UNDO_REQUEST = "undo_request"
    const val UNDO_RESPONSE = "undo_response"
    const val RESIGN = "resign"
    const val DRAW_OFFER = "draw_offer"
    const val DRAW_RESPONSE = "draw_response"
    const val CHAT = "chat"
    const val EMOJI = "emoji"

    // S2C
    const val ERROR = "error"
    const val ROOM_CREATED = "room_created"
    const val ROOM_JOINED = "room_joined"
    const val ROOM_STATE = "room_state"
    const val GAME_START = "game_start"
    const val S2C_MOVE = "move"
    const val S2C_UNDO_REQUEST = "undo_request"
    const val S2C_UNDO_RESPONSE = "undo_response"
    const val GAME_OVER = "game_over"
    const val S2C_DRAW_OFFER = "draw_offer"
    const val S2C_DRAW_RESPONSE = "draw_response"
    const val S2C_CHAT = "chat"
    const val S2C_EMOJI = "emoji"
    const val OPPONENT_LEFT = "opponent_left"
    const val CLOCK = "clock"
}

object Stone {
    const val NONE = 0
    const val BLACK = 1
    const val WHITE = 2
}

object Result {
    const val BLACK_WIN = "black_win"
    const val WHITE_WIN = "white_win"
    const val DRAW = "draw"
    const val RESIGN = "resign"
    const val TIMEOUT = "timeout"
}

object Status {
    const val WAITING = "waiting"
    const val PLAYING = "playing"
    const val FINISHED = "finished"
}

data class Envelope(
    val type: String,
    val payload: Any? = null
)

data class Move(val x: Int, val y: Int, val seat: Int)

data class MovePayload(val x: Int, val y: Int, val seat: Int = 0)

data class ChatItem(val fromSeat: Int, val name: String, val text: String, val isEmoji: Boolean)
