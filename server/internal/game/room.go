package game

import (
	"errors"
	"sync"
	"time"

	"gomoku-server/internal/proto"
)

// 房间状态
const (
	RoomWaiting  = "waiting"
	RoomPlaying  = "playing"
	RoomFinished = "finished"
)

// Room 一局对局
type Room struct {
	ID         string
	TimeLimit  int           // 单步秒数，0不限时
	Status     string
	BlackName  string
	WhiteName  string
	BlackTime  int
	WhiteTime  int
	Turn       int           // 当前轮到的座位
	Winner     int
	WinReason  string
	Board      *Board
	Clients    [2]*Client    // 座位1=黑 座位2=白
	UndoReq    *UndoRequest  // 等待响应的悔棋请求
	DrawOffer  *DrawOffer    // 等待响应的和棋提议
	CreatedAt  time.Time
	StartedAt  time.Time
	EndedAt    time.Time
	mu         sync.Mutex
}

type UndoRequest struct {
	FromSeat  int
	CreatedAt time.Time
}

type DrawOffer struct {
	FromSeat  int
	CreatedAt time.Time
}

func NewRoom(id string, timeLimit int) *Room {
	return &Room{
		ID:        id,
		TimeLimit: timeLimit,
		Status:    RoomWaiting,
		Board:     NewBoard(),
		CreatedAt: time.Now(),
	}
}

// SeatEmpty 座位是否空
func (r *Room) SeatEmpty(seat int) bool {
	if seat < 1 || seat > 2 {
		return true
	}
	return r.Clients[seat-1] == nil
}

// BothSeated 是否两人都加入
func (r *Room) BothSeated() bool {
	return r.Clients[0] != nil && r.Clients[1] != nil
}

// AddPlayer 将客户端加入指定座位
func (r *Room) AddPlayer(c *Client, seat int) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if seat < 1 || seat > 2 {
		return
	}
	if r.Clients[seat-1] != nil {
		return
	}
	r.Clients[seat-1] = c
	if seat == 1 {
		r.BlackName = c.Nickname
	} else {
		r.WhiteName = c.Nickname
	}
}

// RemovePlayer 移除客户端
func (r *Room) RemovePlayer(seat int) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if seat < 1 || seat > 2 {
		return
	}
	if r.Clients[seat-1] == nil {
		return
	}
	r.Clients[seat-1] = nil
	if r.Status == RoomWaiting {
		if seat == 1 {
			r.BlackName = ""
		} else {
			r.WhiteName = ""
		}
	}
}

// TryStart 若两人都到齐则开始
func (r *Room) TryStart() bool {
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.Status != RoomWaiting {
		return false
	}
	if !r.BothSeated() {
		return false
	}
	r.Status = RoomPlaying
	r.Turn = 1 // 黑先
	if r.TimeLimit > 0 {
		r.BlackTime = r.TimeLimit
		r.WhiteTime = r.TimeLimit
	}
	r.StartedAt = time.Now()
	return true
}

// PlaceMove 在当前轮次落子
func (r *Room) PlaceMove(x, y, seat int) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.Status != RoomPlaying {
		return errors.New("对局未开始或已结束")
	}
	if seat != r.Turn {
		return errors.New("不是你的回合")
	}
	if r.TimeLimit > 0 {
		var remaining int
		if seat == 1 {
			remaining = r.BlackTime
		} else {
			remaining = r.WhiteTime
		}
		if remaining <= 0 {
			return errors.New("超时")
		}
	}
	if err := r.Board.Place(x, y, seat); err != nil {
		return err
	}
	// 切换回合
	if r.Turn == 1 {
		r.Turn = 2
	} else {
		r.Turn = 1
	}
	// 胜负判定
	if winner := r.Board.CheckWin(); winner != 0 {
		r.Status = RoomFinished
		r.Winner = winner
		r.WinReason = proto.ResultBlackWin
		if winner == 2 {
			r.WinReason = proto.ResultWhiteWin
		}
		r.EndedAt = time.Now()
	} else if r.Board.IsFull() {
		r.Status = RoomFinished
		r.Winner = 0
		r.WinReason = proto.ResultDraw
		r.EndedAt = time.Now()
	}
	// 重置本步计时
	if r.TimeLimit > 0 {
		if seat == 1 {
			r.WhiteTime = r.TimeLimit
		} else {
			r.BlackTime = r.TimeLimit
		}
	}
	return nil
}

// RequestUndo 处理悔棋请求
func (r *Room) RequestUndo(fromSeat int) (ok bool, err string) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.Status != RoomPlaying {
		return false, "对局未在进行"
	}
	if fromSeat != r.Turn {
		// 只有刚下完子的一方可申请悔棋（上一手）
		// 简化：允许任意一方申请，对方响应
	}
	if r.UndoReq != nil {
		return false, "已有待响应的悔棋请求"
	}
	if len(r.Board.Moves) == 0 {
		return false, "无子可悔"
	}
	r.UndoReq = &UndoRequest{FromSeat: fromSeat, CreatedAt: time.Now()}
	return true, ""
}

// RespondUndo 处理悔棋响应
func (r *Room) RespondUndo(accept bool, fromSeat int) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.UndoReq == nil {
		return errors.New("无待响应的悔棋请求")
	}
	if fromSeat == r.UndoReq.FromSeat {
		return errors.New("不能响应自己的请求")
	}
	if accept {
		// 撤销上一步
		last, ok := r.Board.UndoLast()
		if !ok {
			return errors.New("无可悔棋步")
		}
		// 恢复回合到被悔棋的一方
		r.Turn = last.Seat
	}
	r.UndoReq = nil
	return nil
}

// OfferDraw 提议和棋
func (r *Room) OfferDraw(fromSeat int) (bool, string) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.Status != RoomPlaying {
		return false, "对局未在进行"
	}
	if r.DrawOffer != nil {
		return false, "已有待响应的和棋提议"
	}
	r.DrawOffer = &DrawOffer{FromSeat: fromSeat, CreatedAt: time.Now()}
	return true, ""
}

// RespondDraw 响应和棋
func (r *Room) RespondDraw(accept bool, fromSeat int) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.DrawOffer == nil {
		return errors.New("无待响应的和棋提议")
	}
	if fromSeat == r.DrawOffer.FromSeat {
		return errors.New("不能响应自己的提议")
	}
	if accept {
		r.Status = RoomFinished
		r.Winner = 0
		r.WinReason = proto.ResultDraw
		r.EndedAt = time.Now()
	}
	r.DrawOffer = nil
	return nil
}

// Resign 认输
func (r *Room) Resign(seat int) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.Status != RoomPlaying {
		return
	}
	r.Status = RoomFinished
	r.Winner = 3 - seat // 对方赢
	r.WinReason = proto.ResultResign
	r.EndedAt = time.Now()
}

// TickClock 减少当前回合时间
func (r *Room) TickClock() (timeout bool) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.Status != RoomPlaying || r.TimeLimit <= 0 {
		return false
	}
	if r.Turn == 1 {
		r.BlackTime--
		if r.BlackTime <= 0 {
			r.BlackTime = 0
			r.Status = RoomFinished
			r.Winner = 2
			r.WinReason = proto.ResultTimeout
			r.EndedAt = time.Now()
			return true
		}
	} else {
		r.WhiteTime--
		if r.WhiteTime <= 0 {
			r.WhiteTime = 0
			r.Status = RoomFinished
			r.Winner = 1
			r.WinReason = proto.ResultTimeout
			r.EndedAt = time.Now()
			return true
		}
	}
	return false
}

// Snapshot 获取房间快照(无锁)
func (r *Room) Snapshot() proto.RoomStatePayload {
	board := r.Board.Snapshot()
	moves := make([]proto.Move, 0, len(r.Board.Moves))
	for _, m := range r.Board.Moves {
		moves = append(moves, proto.Move{X: m.X, Y: m.Y, Seat: m.Seat})
	}
	return proto.RoomStatePayload{
		RoomID:    r.ID,
		BlackName: r.BlackName,
		WhiteName: r.WhiteName,
		Status:    r.Status,
		Turn:      r.Turn,
		Board:     board,
		Moves:     moves,
		Winner:    r.Winner,
		TimeLimit: r.TimeLimit,
		BlackTime: r.BlackTime,
		WhiteTime: r.WhiteTime,
	}
}
