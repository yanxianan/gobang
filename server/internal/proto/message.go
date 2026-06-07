package proto

// 消息类型常量
const (
	// C2S 客户端 -> 服务端
	C2S_CreateRoom    = "create_room"     // 创建房间
	C2S_JoinRoom      = "join_room"        // 加入房间
	C2S_LeaveRoom     = "leave_room"       // 离开房间
	C2S_Ready         = "ready"            // 准备
	C2S_Move          = "move"             // 落子
	C2S_UndoRequest   = "undo_request"    // 悔棋请求
	C2S_UndoResponse  = "undo_response"    // 悔棋响应
	C2S_Resign        = "resign"           // 认输
	C2S_DrawOffer     = "draw_offer"       // 和棋提议
	C2S_DrawResponse  = "draw_response"    // 和棋响应
	C2S_Chat          = "chat"             // 聊天
	C2S_Emoji         = "emoji"            // 表情

	// S2C 服务端 -> 客户端
	S2C_Error         = "error"            // 错误
	S2C_RoomCreated   = "room_created"     // 房间创建成功
	S2C_RoomJoined    = "room_joined"      // 加入房间成功
	S2C_RoomState     = "room_state"       // 房间状态(广播)
	S2C_GameStart     = "game_start"       // 游戏开始
	S2C_Move          = "move"             // 落子广播
	S2C_UndoRequest   = "undo_request"     // 悔棋请求广播
	S2C_UndoResponse  = "undo_response"    // 悔棋结果
	S2C_GameOver      = "game_over"        // 游戏结束
	S2C_DrawOffer     = "draw_offer"       // 和棋提议
	S2C_DrawResponse  = "draw_response"    // 和棋结果
	S2C_Chat          = "chat"             // 聊天消息
	S2C_Emoji         = "emoji"            // 表情
	S2C_OpponentLeft  = "opponent_left"    // 对手离开
	S2C_Clock         = "clock"            // 计时
)

// 棋子颜色
const (
	StoneNone = 0
	StoneBlack = 1 // 黑子(先手)
	StoneWhite = 2 // 白子
)

// 游戏结果
const (
	ResultBlackWin = "black_win"
	ResultWhiteWin = "white_win"
	ResultDraw     = "draw"
	ResultResign   = "resign"
	ResultTimeout  = "timeout"
)

// 消息信封
type Envelope struct {
	Type    string      `json:"type"`
	Payload interface{} `json:"payload,omitempty"`
}

// === Payload 定义 ===

type CreateRoomPayload struct {
	Nickname  string `json:"nickname"`
	TimeLimit int    `json:"time_limit"` // 单步秒数, 0表示不限时
}

type JoinRoomPayload struct {
	RoomID   string `json:"room_id"`
	Nickname string `json:"nickname"`
}

type RoomCreatedPayload struct {
	RoomID string `json:"room_id"`
	Seat   int    `json:"seat"` // 1=黑 2=白
}

type RoomJoinedPayload struct {
	RoomID string `json:"room_id"`
	Seat   int    `json:"seat"`
}

type RoomStatePayload struct {
	RoomID    string   `json:"room_id"`
	BlackName string   `json:"black_name"`
	WhiteName string   `json:"white_name"`
	YourSeat  int      `json:"your_seat"`
	Status    string   `json:"status"` // waiting/playing/finished
	Turn      int      `json:"turn"`   // 当前轮到的座位 1/2
	Board     [][]int  `json:"board"`  // 15x15
	Moves     []Move   `json:"moves"`  // 历史步
	Winner    int      `json:"winner"` // 0/1/2
	TimeLimit int      `json:"time_limit"`
	BlackTime int      `json:"black_time"` // 剩余秒
	WhiteTime int      `json:"white_time"`
}

type Move struct {
	X    int `json:"x"`
	Y    int `json:"y"`
	Seat int `json:"seat"`
}

type MovePayload struct {
	X    int `json:"x"`
	Y    int `json:"y"`
	Seat int `json:"seat,omitempty"`
}

type GameStartPayload struct {
	FirstSeat  int   `json:"first_seat"`
	TimeLimit  int   `json:"time_limit"`
	BlackTime  int   `json:"black_time"`
	WhiteTime  int   `json:"white_time"`
}

type UndoRequestPayload struct {
	FromSeat int `json:"from_seat"`
}

type UndoResponsePayload struct {
	Accept bool `json:"accept"`
}

type GameOverPayload struct {
	Winner  int    `json:"winner"`  // 0=平 1=黑 2=白
	Reason  string `json:"reason"`  // 胜负原因
	Moves   []Move `json:"moves"`
}

type DrawOfferPayload struct {
	FromSeat int `json:"from_seat"`
}

type DrawResponsePayload struct {
	Accept bool `json:"accept"`
}

type ChatPayload struct {
	FromSeat int    `json:"from_seat"`
	Name     string `json:"name"`
	Text     string `json:"text"`
}

type EmojiPayload struct {
	FromSeat int    `json:"from_seat"`
	Name     string `json:"name"`
	Emoji    string `json:"emoji"`
}

type ErrorPayload struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

type ClockPayload struct {
	BlackTime int `json:"black_time"`
	WhiteTime int `json:"white_time"`
	Turn      int `json:"turn"`
}
