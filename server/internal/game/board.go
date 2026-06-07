package game

import "errors"

const BoardSize = 15

// Board 15x15 棋盘
type Board struct {
	Cells [BoardSize][BoardSize]int
	Moves []Move
}

type Move struct {
	X    int
	Y    int
	Seat int // 1=黑 2=白
}

func NewBoard() *Board {
	return &Board{}
}

// Place 在 (x,y) 落子，seat 为 1/2。返回是否成功。
func (b *Board) Place(x, y, seat int) error {
	if x < 0 || x >= BoardSize || y < 0 || y >= BoardSize {
		return errors.New("坐标越界")
	}
	if b.Cells[y][x] != 0 {
		return errors.New("该位置已有棋子")
	}
	if seat != 1 && seat != 2 {
		return errors.New("seat 非法")
	}
	b.Cells[y][x] = seat
	b.Moves = append(b.Moves, Move{X: x, Y: y, Seat: seat})
	return nil
}

// UndoLast 撤销最后一步
func (b *Board) UndoLast() (Move, bool) {
	if len(b.Moves) == 0 {
		return Move{}, false
	}
	last := b.Moves[len(b.Moves)-1]
	b.Cells[last.Y][last.X] = 0
	b.Moves = b.Moves[:len(b.Moves)-1]
	return last, true
}

// CheckWin 检查最后一步是否形成五连，返回获胜方(0=无)
func (b *Board) CheckWin() int {
	if len(b.Moves) == 0 {
		return 0
	}
	last := b.Moves[len(b.Moves)-1]
	x, y, seat := last.X, last.Y, last.Seat
	// 4个方向
	directions := [][2]int{
		{1, 0},  // 横
		{0, 1},  // 竖
		{1, 1},  // 主对角
		{1, -1}, // 副对角
	}
	for _, d := range directions {
		count := 1
		// 正向
		for i := 1; i < 5; i++ {
			nx, ny := x+d[0]*i, y+d[1]*i
			if nx < 0 || nx >= BoardSize || ny < 0 || ny >= BoardSize {
				break
			}
			if b.Cells[ny][nx] != seat {
				break
			}
			count++
		}
		// 反向
		for i := 1; i < 5; i++ {
			nx, ny := x-d[0]*i, y-d[1]*i
			if nx < 0 || nx >= BoardSize || ny < 0 || ny >= BoardSize {
				break
			}
			if b.Cells[ny][nx] != seat {
				break
			}
			count++
		}
		if count >= 5 {
			return seat
		}
	}
	return 0
}

// IsFull 是否平局
func (b *Board) IsFull() bool {
	return len(b.Moves) >= BoardSize*BoardSize
}

// Snapshot 序列化棋盘到二维数组
func (b *Board) Snapshot() [][]int {
	out := make([][]int, BoardSize)
	for i := 0; i < BoardSize; i++ {
		out[i] = make([]int, BoardSize)
		copy(out[i], b.Cells[i][:])
	}
	return out
}
