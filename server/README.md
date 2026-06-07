# 五子棋服务端 (Go + WebSocket + SQLite)

## 本地编译

```bash
cd server
go mod tidy
go build -o gomoku-server .
```

## 本地运行

```bash
./gomoku-server --addr :8080 --db gomoku.db
```

参数说明:
- `--addr`  监听地址，默认 `:8080`
- `--db`    SQLite数据库文件路径，默认 `gomoku.db`

第一次运行会在当前目录创建 `gomoku.db` 文件, 记录所有对局。

## 接口

- `ws://host:port/ws`        WebSocket 长连接, 用于游戏对局
- `GET /api/health`          健康检查
- `GET /api/rooms`           等待中的房间列表
- `GET /api/games?name=xxx`  某玩家战绩列表
- `GET /api/games?id=xxx`    单场对局详情(含回放)

## 部署到云服务器(Linux)

### 1. 上传二进制
将本地编译好的 `gomoku-server` 文件(以及 `gomoku.db`, 如果有战绩) 上传到服务器, 例如:
```bash
scp gomoku-server user@your-server-ip:/opt/gomoku/
```

### 2. 在服务器上运行
```bash
cd /opt/gomoku
chmod +x gomoku-server
nohup ./gomoku-server --addr 0.0.0.0:8080 --db /opt/gomoku/gomoku.db > server.log 2>&1 &
```

### 3. 开放防火墙
```bash
# CentOS
sudo firewall-cmd --zone=public --add-port=8080/tcp --permanent
sudo firewall-cmd --reload

# Ubuntu (ufw)
sudo ufw allow 8080/tcp
```

### 4. 云服务商安全组
到云服务器控制台, 给实例的安全组添加入站规则, 放行 8080 端口。

### 5. Android客户端配置
在Android客户端设置中填入服务器地址:
```
ws://你的服务器IP:8080/ws
http://你的服务器IP:8080
```

## 后台进程管理(可选, 推荐)

使用 systemd 让服务器开机自启:

```bash
sudo tee /etc/systemd/system/gomoku.service <<EOF
[Unit]
Description=Gomoku Server
After=network.target

[Service]
Type=simple
WorkingDirectory=/opt/gomoku
ExecStart=/opt/gomoku/gomoku-server --addr 0.0.0.0:8080 --db /opt/gomoku/gomoku.db
Restart=always
RestartSec=5
User=root

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable gomoku
sudo systemctl start gomoku
sudo systemctl status gomoku
```

## 协议说明(客户端集成)

通过 `/ws` 建立 WebSocket 连接, 消息均为 JSON 格式:
```json
{"type": "create_room", "payload": {"nickname": "小明", "time_limit": 30}}
```

详见 `internal/proto/message.go`。
