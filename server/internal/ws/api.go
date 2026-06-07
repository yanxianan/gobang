package ws

import (
	"encoding/json"
	"net/http"
	"strconv"

	"gomoku-server/internal/game"
)

// HandleListRooms GET /api/rooms - 列出等待中的房间
func (h *Hub) HandleListRooms(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Access-Control-Allow-Origin", "*")
	w.Header().Set("Content-Type", "application/json")
	rooms := h.Manager.ListWaiting()
	type item struct {
		RoomID    string `json:"room_id"`
		BlackName string `json:"black_name"`
		TimeLimit int    `json:"time_limit"`
	}
	out := make([]item, 0, len(rooms))
	for _, rr := range rooms {
		out = append(out, item{
			RoomID:    rr.ID,
			BlackName: rr.BlackName,
			TimeLimit: rr.TimeLimit,
		})
	}
	_ = json.NewEncoder(w).Encode(map[string]interface{}{"rooms": out})
}

// HandleListGames GET /api/games?name=xxx&limit=20&offset=0
func (h *Hub) HandleListGames(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Access-Control-Allow-Origin", "*")
	w.Header().Set("Content-Type", "application/json")
	if h.Storage == nil {
		_ = json.NewEncoder(w).Encode(map[string]interface{}{"games": []interface{}{}})
		return
	}
	name := r.URL.Query().Get("name")
	limit, _ := strconv.Atoi(r.URL.Query().Get("limit"))
	if limit <= 0 || limit > 100 {
		limit = 20
	}
	offset, _ := strconv.Atoi(r.URL.Query().Get("offset"))
	if offset < 0 {
		offset = 0
	}
	if name == "" {
		_ = json.NewEncoder(w).Encode(map[string]interface{}{"games": []interface{}{}})
		return
	}
	games, err := h.Storage.ListGamesByName(name, limit, offset)
	if err != nil {
		http.Error(w, err.Error(), 500)
		return
	}
	_ = json.NewEncoder(w).Encode(map[string]interface{}{"games": games})
}

// HandleGetGame GET /api/games/{id}
func (h *Hub) HandleGetGame(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Access-Control-Allow-Origin", "*")
	w.Header().Set("Content-Type", "application/json")
	if h.Storage == nil {
		http.Error(w, "no storage", 500)
		return
	}
	idStr := r.URL.Query().Get("id")
	id, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		http.Error(w, "bad id", 400)
		return
	}
	g, moves, err := h.Storage.GetGame(id)
	if err != nil {
		http.Error(w, err.Error(), 404)
		return
	}
	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"id":         g.ID,
		"room_id":    g.RoomID,
		"black_name": g.BlackName,
		"white_name": g.WhiteName,
		"winner":     g.Winner,
		"reason":     g.Reason,
		"started_at": g.StartedAt,
		"ended_at":   g.EndedAt,
		"moves":      json.RawMessage(moves),
	})
}

// HandleHealth GET /api/health
func (h *Hub) HandleHealth(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Access-Control-Allow-Origin", "*")
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]interface{}{"ok": true})
}

// 避免未使用
var _ = game.RoomWaiting
