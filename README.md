# 五子棋 · 联机对弈 (Gomoku Online)

一个支持好友联机对战的安卓五子棋游戏。中式传统风格界面，房间号邀请制。

## 项目结构

```
game/
├── server/                 # Go服务端 (WebSocket + SQLite)
│   ├── main.go            # 入口
│   ├── go.mod
│   ├── internal/
│   │   ├── proto/         # 消息协议定义
│   │   ├── game/          # 游戏核心逻辑 (棋盘/房间/胜负判定)
│   │   └── ws/            # WebSocket & HTTP API & SQLite
│   └── README.md          # 服务端部署文档
│
├── android/                # Android客户端 (Kotlin)
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   ├── gradle.properties
│   ├── gradlew
│   ├── gradlew.bat
│   ├── gradle/wrapper/
│   └── app/
│       ├── build.gradle.kts
│       ├── src/main/
│       │   ├── AndroidManifest.xml
│       │   ├── kotlin/com/gomoku/app/
│       │   │   ├── App.kt
│       │   │   ├── net/          # 网络层 (WebSocket + HTTP API)
│       │   │   ├── ui/           # 界面
│       │   │   │   ├── home/     # 主页
│       │   │   │   ├── game/     # 游戏界面 + 棋盘View
│       │   │   │   ├── records/  # 战绩列表 + 回放
│       │   │   │   └── settings/ # 设置
│       │   │   └── util/Prefs.kt
│       │   └── res/              # 中式风格资源
│       └── proguard-rules.pro
│
└── docs/                    # 其它文档
```

## 功能特性

- 🎮 15x15 标准五子棋规则
- 🏠 房间号邀请制，6位字符房间号
- ⏱️ 可选 30秒/60秒/不限时 三种模式
- 💬 实时聊天 + 表情
- ↩️ 悔棋请求(对方同意后生效)
- 🤝 和棋提议
- 🏳️ 认输
- 📜 战绩保存与回放
- 🎨 中式传统风格界面(宣纸/朱砂/水墨色调)

## 快速开始

### 1. 启动服务端

详见 [server/README.md](server/README.md)

```bash
cd server
go mod tidy
go build -o gomoku-server .
./gomoku-server --addr :8080 --db gomoku.db
```

### 2. 在Android Studio中打开客户端

```
用 Android Studio (Hedgehog 2023.1.1 或更新版本) 打开 android/ 目录。
Gradle同步完成后, 即可编译运行。
```

### 3. 配置服务器地址

在Android客户端点击"服务器设置", 填入:
```
1.2.3.4:8080           (你的云服务器IP)
或
your-domain.com:8080   (你的域名)
```

### 4. 联机对战

- 玩家A: 输入昵称 → 创建房间 → 把6位房间号发给玩家B
- 玩家B: 输入昵称 → 输入房间号 → 加入房间
- 开始对战！

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 服务端 | Go 1.21+, gorilla/websocket, mattn/go-sqlite3 |
| 客户端 | Kotlin 1.9, AndroidX, Material Components, OkHttp |
| 通信协议 | WebSocket (JSON消息) |
| 数据存储 | SQLite (嵌入式) |
| 最低支持 | Android 8.0 (API 26) |

## 部署到生产环境

参见 [server/README.md](server/README.md) 中的 systemd 配置部分。
