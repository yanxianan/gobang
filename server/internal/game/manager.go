package game

import (
	"crypto/rand"
	"fmt"
	"math/big"
	"sync"
	"time"
)

// Manager 全局房间管理器
type Manager struct {
	mu    sync.RWMutex
	rooms map[string]*Room
}

func NewManager() *Manager {
	m := &Manager{rooms: make(map[string]*Room)}
	go m.gcLoop()
	return m
}

// CreateRoom 创建房间
func (m *Manager) CreateRoom(timeLimit int) *Room {
	m.mu.Lock()
	defer m.mu.Unlock()
	id := genRoomID()
	for {
		if _, ok := m.rooms[id]; !ok {
			break
		}
		id = genRoomID()
	}
	r := NewRoom(id, timeLimit)
	m.rooms[id] = r
	return r
}

func (m *Manager) GetRoom(id string) *Room {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.rooms[id]
}

func (m *Manager) RemoveRoom(id string) {
	m.mu.Lock()
	defer m.mu.Unlock()
	delete(m.rooms, id)
}

// ListRooms 列出所有等待中的房间
func (m *Manager) ListWaiting() []*Room {
	m.mu.RLock()
	defer m.mu.RUnlock()
	out := make([]*Room, 0)
	for _, r := range m.rooms {
		if r.Status == RoomWaiting {
			out = append(out, r)
		}
	}
	return out
}

func (m *Manager) gcLoop() {
	t := time.NewTicker(30 * time.Second)
	defer t.Stop()
	for range t.C {
		m.mu.Lock()
		now := time.Now()
		for id, r := range m.rooms {
			// 等待中超时清理
			if r.Status == RoomWaiting && now.Sub(r.CreatedAt) > 30*time.Minute {
				delete(m.rooms, id)
				continue
			}
			// 结束后无客户端清理
			if r.Status == RoomFinished && r.Clients[0] == nil && r.Clients[1] == nil && now.Sub(r.EndedAt) > 5*time.Minute {
				delete(m.rooms, id)
			}
		}
		m.mu.Unlock()
	}
}

func genRoomID() string {
	// 4位数字 (0000-9999)
	n, _ := rand.Int(rand.Reader, big.NewInt(10000))
	return fmt.Sprintf("%04d", n.Int64())
}
