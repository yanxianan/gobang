package ws

import (
	"database/sql"
	"encoding/json"
	"time"

	_ "modernc.org/sqlite"
)

type GameRecord struct {
	ID        int64
	RoomID    string
	BlackName string
	WhiteName string
	Winner    int
	Reason    string
	StartedAt time.Time
	EndedAt   time.Time
	Moves     string // JSON
}

type Storage struct {
	db *sql.DB
}

func NewStorage(path string) (*Storage, error) {
	dsn := "file:" + path + "?_pragma=busy_timeout(5000)&_pragma=journal_mode(WAL)&_pragma=foreign_keys(on)"
	db, err := sql.Open("sqlite", dsn)
	if err != nil {
		return nil, err
	}
	if _, err := db.Exec(`CREATE TABLE IF NOT EXISTS games (
		id INTEGER PRIMARY KEY AUTOINCREMENT,
		room_id TEXT NOT NULL,
		black_name TEXT,
		white_name TEXT,
		winner INTEGER,
		reason TEXT,
		started_at DATETIME,
		ended_at DATETIME,
		moves TEXT
	)`); err != nil {
		return nil, err
	}
	if _, err := db.Exec(`CREATE INDEX IF NOT EXISTS idx_games_ended_at ON games(ended_at DESC)`); err != nil {
		return nil, err
	}
	return &Storage{db: db}, nil
}

func (s *Storage) Close() error {
	return s.db.Close()
}

func (s *Storage) InsertGame(g GameRecord) error {
	_, err := s.db.Exec(`INSERT INTO games(room_id, black_name, white_name, winner, reason, started_at, ended_at, moves)
		VALUES(?,?,?,?,?,?,?,?)`,
		g.RoomID, g.BlackName, g.WhiteName, g.Winner, g.Reason, g.StartedAt, g.EndedAt, g.Moves)
	return err
}

type GameSummary struct {
	ID        int64     `json:"id"`
	RoomID    string    `json:"room_id"`
	BlackName string    `json:"black_name"`
	WhiteName string    `json:"white_name"`
	Winner    int       `json:"winner"`
	Reason    string    `json:"reason"`
	StartedAt time.Time `json:"started_at"`
	EndedAt   time.Time `json:"ended_at"`
}

// ListGamesByName 列出某玩家参与的对局(分页)
func (s *Storage) ListGamesByName(name string, limit, offset int) ([]GameSummary, error) {
	rows, err := s.db.Query(`SELECT id, room_id, black_name, white_name, winner, reason, started_at, ended_at
		FROM games WHERE black_name=? OR white_name=? ORDER BY ended_at DESC LIMIT ? OFFSET ?`,
		name, name, limit, offset)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []GameSummary{}
	for rows.Next() {
		var g GameSummary
		if err := rows.Scan(&g.ID, &g.RoomID, &g.BlackName, &g.WhiteName, &g.Winner, &g.Reason, &g.StartedAt, &g.EndedAt); err != nil {
			return nil, err
		}
		out = append(out, g)
	}
	return out, nil
}

// GetGame 获取对局详情
func (s *Storage) GetGame(id int64) (*GameRecord, []byte, error) {
	row := s.db.QueryRow(`SELECT id, room_id, black_name, white_name, winner, reason, started_at, ended_at, moves
		FROM games WHERE id=?`, id)
	var g GameRecord
	if err := row.Scan(&g.ID, &g.RoomID, &g.BlackName, &g.WhiteName, &g.Winner, &g.Reason, &g.StartedAt, &g.EndedAt, &g.Moves); err != nil {
		return nil, nil, err
	}
	movesJSON, err := json.Marshal(parseMoves(g.Moves))
	if err != nil {
		return nil, nil, err
	}
	return &g, movesJSON, nil
}

func parseMoves(s string) []map[string]int {
	if s == "" {
		return []map[string]int{}
	}
	var moves []map[string]int
	_ = json.Unmarshal([]byte(s), &moves)
	if moves == nil {
		moves = []map[string]int{}
	}
	return moves
}
