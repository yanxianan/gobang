package ws

import (
	"encoding/json"
	"log"
	"net/http"
	"time"

	"gomoku-server/internal/game"
	"gomoku-server/internal/proto"

	"github.com/gorilla/websocket"
)

const (
	writeWait      = 10 * time.Second
	pongWait       = 60 * time.Second
	pingPeriod     = 30 * time.Second
	maxMessageSize = 4096
	clockTick      = 1 * time.Second
)

var upgrader = websocket.Upgrader{
	ReadBufferSize:  1024,
	WriteBufferSize: 4096,
	CheckOrigin:     func(r *http.Request) bool { return true },
}

// Hub 持有房间管理器
type Hub struct {
	Manager *game.Manager
	Storage *Storage
}

// HandleWS WebSocket入口
func (h *Hub) HandleWS(w http.ResponseWriter, r *http.Request) {
	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Println("upgrade:", err)
		return
	}
	send := make(chan []byte, 32)
	client := game.NewClient("", send)

	// writer
	go func() {
		ticker := time.NewTicker(pingPeriod)
		defer func() {
			ticker.Stop()
			_ = conn.Close()
		}()
		for {
			select {
			case msg, ok := <-send:
				_ = conn.SetWriteDeadline(time.Now().Add(writeWait))
				if !ok {
					_ = conn.WriteMessage(websocket.CloseMessage, []byte{})
					return
				}
				if err := conn.WriteMessage(websocket.TextMessage, msg); err != nil {
					return
				}
			case <-ticker.C:
				_ = conn.SetWriteDeadline(time.Now().Add(writeWait))
				if err := conn.WriteMessage(websocket.PingMessage, nil); err != nil {
					return
				}
			}
		}
	}()

	// reader
	conn.SetReadLimit(maxMessageSize)
	_ = conn.SetReadDeadline(time.Now().Add(pongWait))
	conn.SetPongHandler(func(string) error {
		_ = conn.SetReadDeadline(time.Now().Add(pongWait))
		return nil
	})
	h.readLoop(conn, client)
}

func (h *Hub) readLoop(conn *websocket.Conn, client *game.Client) {
	defer func() {
		h.onDisconnect(client)
		_ = conn.Close()
		client.Close()
	}()
	for {
		_, raw, err := conn.ReadMessage()
		if err != nil {
			return
		}
		var env proto.Envelope
		if err := json.Unmarshal(raw, &env); err != nil {
			sendError(client, "bad_json", "消息格式错误")
			continue
		}
		h.handle(client, env.Type, env.Payload, raw)
	}
}

func sendJSON(c *game.Client, msgType string, payload interface{}) {
	env := proto.Envelope{Type: msgType, Payload: payload}
	data, _ := json.Marshal(env)
	if c != nil {
		c.Send(data)
	}
}

func sendError(c *game.Client, code, message string) {
	sendJSON(c, proto.S2C_Error, proto.ErrorPayload{Code: code, Message: message})
}

func (h *Hub) handle(client *game.Client, msgType string, payloadRaw interface{}, raw []byte) {
	switch msgType {
	case proto.C2S_CreateRoom:
		var p proto.CreateRoomPayload
		b, _ := json.Marshal(payloadRaw)
		_ = json.Unmarshal(b, &p)
		client.Nickname = p.Nickname
		room := h.Manager.CreateRoom(p.TimeLimit)
		client.Room = room
		client.Seat = 1
		room.AddPlayer(client, 1)
		h.startRoomClock(room)
		sendJSON(client, proto.S2C_RoomCreated, proto.RoomCreatedPayload{RoomID: room.ID, Seat: 1})
		sendJSON(client, proto.S2C_RoomState, h.stateFor(client, room))

	case proto.C2S_JoinRoom:
		var p proto.JoinRoomPayload
		b, _ := json.Marshal(payloadRaw)
		_ = json.Unmarshal(b, &p)
		client.Nickname = p.Nickname
		room := h.Manager.GetRoom(p.RoomID)
		if room == nil {
			sendError(client, "room_not_found", "房间不存在")
			return
		}
		if room.Status != game.RoomWaiting {
			sendError(client, "room_started", "对局已开始")
			return
		}
		var seat int
		if room.SeatEmpty(1) {
			seat = 1
		} else if room.SeatEmpty(2) {
			seat = 2
		} else {
			sendError(client, "room_full", "房间已满")
			return
		}
		client.Room = room
		client.Seat = seat
		room.AddPlayer(client, seat)
		h.startRoomClock(room)
		sendJSON(client, proto.S2C_RoomJoined, proto.RoomJoinedPayload{RoomID: room.ID, Seat: seat})
		// 广播房间状态
		sendJSON(client, proto.S2C_RoomState, h.stateFor(client, room))
		broadcast(room, proto.S2C_RoomState, nil, h)
		// 双方都到齐则开始
		if room.TryStart() {
			gs := proto.GameStartPayload{
				FirstSeat:  room.Turn,
				TimeLimit:  room.TimeLimit,
				BlackTime:  room.BlackTime,
				WhiteTime:  room.WhiteTime,
			}
			broadcast(room, proto.S2C_GameStart, gs, h)
			broadcast(room, proto.S2C_RoomState, nil, h)
		}

	case proto.C2S_LeaveRoom:
		h.onDisconnect(client)

	case proto.C2S_Move:
		var p proto.MovePayload
		b, _ := json.Marshal(payloadRaw)
		_ = json.Unmarshal(b, &p)
		room := client.Room
		if room == nil {
			return
		}
		if err := room.PlaceMove(p.X, p.Y, client.Seat); err != nil {
			sendError(client, "move_invalid", err.Error())
			return
		}
		broadcast(room, proto.S2C_Move, proto.MovePayload{X: p.X, Y: p.Y, Seat: client.Seat}, h)
		broadcast(room, proto.S2C_RoomState, nil, h)
		if room.Status == game.RoomFinished {
			// 保存战绩
			h.saveGame(room)
			moves := make([]proto.Move, 0, len(room.Board.Moves))
			for _, m := range room.Board.Moves {
				moves = append(moves, proto.Move{X: m.X, Y: m.Y, Seat: m.Seat})
			}
			broadcast(room, proto.S2C_GameOver, proto.GameOverPayload{
				Winner: room.Winner,
				Reason: room.WinReason,
				Moves:  moves,
			}, h)
		}

	case proto.C2S_UndoRequest:
		room := client.Room
		if room == nil {
			return
		}
		ok, msg := room.RequestUndo(client.Seat)
		if !ok {
			sendError(client, "undo_fail", msg)
			return
		}
		broadcast(room, proto.S2C_UndoRequest, proto.UndoRequestPayload{FromSeat: client.Seat}, h)

	case proto.C2S_UndoResponse:
		var p proto.UndoResponsePayload
		b, _ := json.Marshal(payloadRaw)
		_ = json.Unmarshal(b, &p)
		room := client.Room
		if room == nil {
			return
		}
		if err := room.RespondUndo(p.Accept, client.Seat); err != nil {
			sendError(client, "undo_resp_fail", err.Error())
			return
		}
		broadcast(room, proto.S2C_UndoResponse, p, h)
		broadcast(room, proto.S2C_RoomState, nil, h)

	case proto.C2S_Resign:
		room := client.Room
		if room == nil {
			return
		}
		room.Resign(client.Seat)
		h.saveGame(room)
		moves := make([]proto.Move, 0, len(room.Board.Moves))
		for _, m := range room.Board.Moves {
			moves = append(moves, proto.Move{X: m.X, Y: m.Y, Seat: m.Seat})
		}
		broadcast(room, proto.S2C_GameOver, proto.GameOverPayload{
			Winner: room.Winner,
			Reason: room.WinReason,
			Moves:  moves,
		}, h)
		broadcast(room, proto.S2C_RoomState, nil, h)

	case proto.C2S_DrawOffer:
		room := client.Room
		if room == nil {
			return
		}
		ok, msg := room.OfferDraw(client.Seat)
		if !ok {
			sendError(client, "draw_fail", msg)
			return
		}
		broadcast(room, proto.S2C_DrawOffer, proto.DrawOfferPayload{FromSeat: client.Seat}, h)

	case proto.C2S_DrawResponse:
		var p proto.DrawResponsePayload
		b, _ := json.Marshal(payloadRaw)
		_ = json.Unmarshal(b, &p)
		room := client.Room
		if room == nil {
			return
		}
		if err := room.RespondDraw(p.Accept, client.Seat); err != nil {
			sendError(client, "draw_resp_fail", err.Error())
			return
		}
		if room.Status == game.RoomFinished {
			h.saveGame(room)
			moves := make([]proto.Move, 0, len(room.Board.Moves))
			for _, m := range room.Board.Moves {
				moves = append(moves, proto.Move{X: m.X, Y: m.Y, Seat: m.Seat})
			}
			broadcast(room, proto.S2C_GameOver, proto.GameOverPayload{
				Winner: room.Winner,
				Reason: room.WinReason,
				Moves:  moves,
			}, h)
		}
		broadcast(room, proto.S2C_DrawResponse, p, h)
		broadcast(room, proto.S2C_RoomState, nil, h)

	case proto.C2S_Chat:
		var p proto.ChatPayload
		b, _ := json.Marshal(payloadRaw)
		_ = json.Unmarshal(b, &p)
		p.FromSeat = client.Seat
		p.Name = client.Nickname
		broadcast(client.Room, proto.S2C_Chat, p, h)

	case proto.C2S_Emoji:
		var p proto.EmojiPayload
		b, _ := json.Marshal(payloadRaw)
		_ = json.Unmarshal(b, &p)
		p.FromSeat = client.Seat
		p.Name = client.Nickname
		broadcast(client.Room, proto.S2C_Emoji, p, h)

	case proto.C2S_RematchRequest:
		room := client.Room
		if room == nil {
			return
		}
		// 若之前被拒绝过, 清除本座位旧的rematch标记, 允许"再求一次"
		room.ClearRematchRequest(client.Seat)
		ok, msg := room.RequestRematch(client.Seat)
		if !ok {
			sendError(client, "rematch_fail", msg)
			return
		}
		// 只发给"对方", 不发给发送方 (发送方自己知道自己点了"不服再战")
		var opp *game.Client
		if client.Seat == 1 {
			opp = room.Clients[1]
		} else {
			opp = room.Clients[0]
		}
		if opp != nil {
			sendJSON(opp, proto.S2C_RematchRequest, proto.RematchRequestPayload{FromSeat: client.Seat})
		}

	case proto.C2S_RematchResponse:
		var p proto.RematchResponsePayload
		b, _ := json.Marshal(payloadRaw)
		_ = json.Unmarshal(b, &p)
		room := client.Room
		if room == nil {
			return
		}
		bothAccept, rejectCount := room.RespondRematch(client.Seat, p.Accept)
		// 广播给双方
		broadcast(room, proto.S2C_RematchResponse, proto.RematchResponsePayload{
			FromSeat: client.Seat,
			Accept:   p.Accept,
		}, h)
		if !p.Accept {
			// 拒绝: 通知双方, 弹"求对方再战"提示
			broadcast(room, proto.S2C_RematchCancel, proto.RematchCancelPayload{
				Reason:      "rejected",
				RejectCount: rejectCount,
			}, h)
			if rejectCount >= 2 {
				// 连续两次拒绝, 房间销毁
				h.Manager.RemoveRoom(room.ID)
			}
		} else if bothAccept {
			// 双方都接受, 重置游戏
			room.ResetForNewGame()
			broadcast(room, proto.S2C_RematchStart, proto.RematchStartPayload{
				FirstSeat: room.Turn,
				TimeLimit: room.TimeLimit,
				BlackTime: room.BlackTime,
				WhiteTime: room.WhiteTime,
				NewGameNo: room.GameNo,
			}, h)
			broadcast(room, proto.S2C_RoomState, nil, h)
		}
	}
}

func (h *Hub) stateFor(c *game.Client, r *game.Room) proto.RoomStatePayload {
	st := r.Snapshot()
	st.YourSeat = c.Seat
	return st
}

// broadcast 通用广播
func broadcast(r *game.Room, msgType string, payload interface{}, h *Hub) {
	if r == nil {
		return
	}
	for _, c := range r.Clients {
		if c == nil {
			continue
		}
		if payload == nil && msgType == proto.S2C_RoomState {
			sendJSON(c, msgType, h.stateFor(c, r))
		} else {
			sendJSON(c, msgType, payload)
		}
	}
}

func (h *Hub) onDisconnect(c *game.Client) {
	if c.Room == nil {
		return
	}
	r := c.Room
	seat := c.Seat
	c.Room = nil
	if r.Status == game.RoomWaiting {
		r.RemovePlayer(seat)
	} else if r.Status == game.RoomPlaying {
		// 对局中掉线，对手获胜
		r.Resign(seat)
		h.saveGame(r)
		moves := make([]proto.Move, 0, len(r.Board.Moves))
		for _, m := range r.Board.Moves {
			moves = append(moves, proto.Move{X: m.X, Y: m.Y, Seat: m.Seat})
		}
		broadcast(r, proto.S2C_GameOver, proto.GameOverPayload{
			Winner: r.Winner,
			Reason: r.WinReason,
			Moves:  moves,
		}, h)
		// 标记该客户端座位为空，但保留回放数据
		r.Clients[seat-1] = nil
	}
	broadcast(r, proto.S2C_OpponentLeft, map[string]interface{}{"seat": seat}, h)
}

// startRoomClock 启动该房间的计时器
func (h *Hub) startRoomClock(r *game.Room) {
	if r.TimeLimit <= 0 {
		return
	}
	go func() {
		t := time.NewTicker(clockTick)
		defer t.Stop()
		for range t.C {
			if r == nil || r.Status == game.RoomFinished {
				return
			}
			timeout := r.TickClock()
			broadcast(r, proto.S2C_Clock, proto.ClockPayload{
				BlackTime: r.BlackTime,
				WhiteTime: r.WhiteTime,
				Turn:      r.Turn,
			}, h)
			if timeout {
				h.saveGame(r)
				moves := make([]proto.Move, 0, len(r.Board.Moves))
				for _, m := range r.Board.Moves {
					moves = append(moves, proto.Move{X: m.X, Y: m.Y, Seat: m.Seat})
				}
				broadcast(r, proto.S2C_GameOver, proto.GameOverPayload{
					Winner: r.Winner,
					Reason: r.WinReason,
					Moves:  moves,
				}, h)
				return
			}
		}
	}()
}

func (h *Hub) saveGame(r *game.Room) {
	if h.Storage == nil {
		return
	}
	moves := make([]proto.Move, 0, len(r.Board.Moves))
	for _, m := range r.Board.Moves {
		moves = append(moves, proto.Move{X: m.X, Y: m.Y, Seat: m.Seat})
	}
	movesJSON, _ := json.Marshal(moves)
	record := GameRecord{
		RoomID:    r.ID,
		BlackName: r.BlackName,
		WhiteName: r.WhiteName,
		Winner:    r.Winner,
		Reason:    r.WinReason,
		StartedAt: r.StartedAt,
		EndedAt:   r.EndedAt,
		Moves:     string(movesJSON),
	}
	if err := h.Storage.InsertGame(record); err != nil {
		log.Println("save game err:", err)
	}
}
