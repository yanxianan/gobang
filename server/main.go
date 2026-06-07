package main

import (
	"flag"
	"log"
	"net/http"

	"gomoku-server/internal/game"
	"gomoku-server/internal/ws"
)

func main() {
	addr := flag.String("addr", ":8080", "监听地址")
	dbPath := flag.String("db", "gomoku.db", "SQLite数据库文件")
	flag.Parse()

	storage, err := ws.NewStorage(*dbPath)
	if err != nil {
		log.Fatalf("初始化数据库失败: %v", err)
	}
	defer storage.Close()

	mgr := game.NewManager()
	hub := &ws.Hub{Manager: mgr, Storage: storage}

	mux := http.NewServeMux()
	mux.HandleFunc("/ws", hub.HandleWS)
	mux.HandleFunc("/api/health", hub.HandleHealth)
	mux.HandleFunc("/api/rooms", hub.HandleListRooms)
	mux.HandleFunc("/api/games", func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Query().Get("id") != "" {
			hub.HandleGetGame(w, r)
		} else {
			hub.HandleListGames(w, r)
		}
	})
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/plain; charset=utf-8")
		_, _ = w.Write([]byte("五子棋对战服务器运行中。\nWebSocket: /ws\nAPI: /api/rooms, /api/games, /api/health\n"))
	})

	log.Printf("五子棋服务器启动, 监听 %s, 数据库 %s", *addr, *dbPath)
	if err := http.ListenAndServe(*addr, mux); err != nil {
		log.Fatal(err)
	}
}
