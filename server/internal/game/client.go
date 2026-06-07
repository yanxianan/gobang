package game

import (
	"sync"
	"time"
)

// Client 连接的客户端
type Client struct {
	Nickname string
	Room     *Room
	Seat     int // 1=黑 2=白
	send     chan []byte
	closeCh  chan struct{}
	once     sync.Once
	lastPing time.Time
}

func NewClient(nickname string, send chan []byte) *Client {
	return &Client{
		Nickname: nickname,
		send:     send,
		closeCh:  make(chan struct{}),
		lastPing: time.Now(),
	}
}

func (c *Client) Close() {
	c.once.Do(func() { close(c.closeCh) })
}

// Send 尝试发送，超时丢弃
func (c *Client) Send(data []byte) {
	if c == nil {
		return
	}
	select {
	case c.send <- data:
	case <-time.After(2 * time.Second):
	case <-c.closeCh:
	}
}

// IsClosed 是否已关闭
func (c *Client) IsClosed() bool {
	select {
	case <-c.closeCh:
		return true
	default:
		return false
	}
}
