# 部署教程（小白版）

本教程面向不熟悉后端部署的用户, 一步一步教你把五子棋服务部署到云服务器。

## 一、整体流程概览

```
云服务器(运行 gomoku-server) <--- WebSocket ---> 安卓手机
                                            <--- HTTP API  --->
```

你需要做两件事:
1. **服务端**: 在云服务器上运行 gomoku-server 程序
2. **客户端**: 在安卓手机上安装 app, 填入服务器地址

---

## 二、准备云服务器

如果你还没买云服务器, 推荐:
- **阿里云 ECS**: <https://www.aliyun.com/product/ecs>
- **腾讯云 CVM**: <https://cloud.tencent.com/product/cvm>
- **华为云 ECS**: <https://www.huaweicloud.com/product/ecs.html>

最低配置即可: 1核 2GB内存 1Mbps带宽, 一年几十块钱。

操作系统选 **Ubuntu 22.04 LTS** (本文档以Ubuntu为例)。

购买后, 在云服务商控制台:
1. **重置密码**: 给 root 用户设置密码
2. **安全组**: 添加入站规则, 放行 22(SSH) 和 8080(五子棋服务) 端口
3. **公网IP**: 记下服务器的公网 IP (例: `1.2.3.4`)

---

## 三、部署服务端 (Linux 服务器操作)

### 步骤 1: SSH 登录服务器

Windows 推荐用 [MobaXterm](https://mobaxterm.mobatek.net/) 或 Windows Terminal:
```bash
ssh root@1.2.3.4
```
密码就是你设置的root密码。

### 步骤 2: 安装 Go 运行环境

```bash
# 下载 Go 1.21
cd /tmp
wget https://go.dev/dl/go1.21.6.linux-amd64.tar.gz

# 解压到 /usr/local
tar -C /usr/local -xzf go1.21.6.linux-amd64.tar.gz

# 设置环境变量
echo 'export PATH=$PATH:/usr/local/go/bin' >> /etc/profile
echo 'export GOPATH=/root/go' >> /etc/profile
source /etc/profile

# 验证
go version
```

### 步骤 3: 上传代码并编译

在**本地电脑**打开终端:
```bash
cd D:\Study\Opencode\game\server

# 编译为 Linux 二进制
# (Windows上用 PowerShell 执行: $env:GOOS="linux"; $env:GOARCH="amd64"; go build -o gomoku-server .)
set GOOS=linux
set GOARCH=amd64
go build -o gomoku-server .
go mod tidy
go build -o gomoku-server-linux .
```

把 `gomoku-server-linux` 文件上传到服务器:
```bash
scp gomoku-server-linux root@1.2.3.4:/opt/gomoku/
```

### 步骤 4: 在服务器启动

```bash
ssh root@1.2.3.4
mkdir -p /opt/gomoku
cd /opt/gomoku
chmod +x gomoku-server-linux
./gomoku-server-linux --addr 0.0.0.0:8080 --db gomoku.db
```

看到 `五子棋服务器启动, 监听 :8080` 即成功。

### 步骤 5: 让服务保持后台运行(关键)

直接关闭SSH, 服务会停止。用 `nohup` 让它后台运行:
```bash
nohup ./gomoku-server-linux --addr 0.0.0.0:8080 --db gomoku.db > server.log 2>&1 &
```

按 `Ctrl+C` 退出也不影响。

### 步骤 6: 设置开机自启(可选但推荐)

```bash
cat > /etc/systemd/system/gomoku.service <<'EOF'
[Unit]
Description=Gomoku Server
After=network.target

[Service]
Type=simple
WorkingDirectory=/opt/gomoku
ExecStart=/opt/gomoku/gomoku-server-linux --addr 0.0.0.0:8080 --db /opt/gomoku/gomoku.db
Restart=always
RestartSec=5
User=root

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable gomoku
systemctl start gomoku
systemctl status gomoku
```

### 步骤 7: 验证服务

在浏览器访问:
```
http://1.2.3.4:8080/api/health
```
应该看到 `{"ok":true}`。

---

## 四、安装安卓客户端

### 方式 A: 用 Android Studio (开发用)

1. 打开 Android Studio, `File` → `Open` → 选择 `D:\Study\Opencode\game\android` 文件夹
2. 等待 Gradle 同步 (首次较慢, 需联网)
3. 连接手机(USB调试已开启) 或启动模拟器
4. 点击 ▶️ Run, 应用就安装到手机了

### 方式 B: 用编译好的 APK (给非开发用户用)

在 Android Studio 中:
- `Build` → `Generate Signed Bundle / APK` → 选 `APK`
- 创建一个临时 keystore (项目里)
- 选 `release` 模式, 点 Finish
- 把生成的 `app-release.apk` 发给好友, 微信/QQ 传都行
- 好友手机下载 APK 后, 系统会提示"允许安装未知来源", 同意即可

### 配置服务器地址

打开 app, 点击"服务器设置", 填入:
```
1.2.3.4:8080
```
(把 1.2.3.4 换成你服务器的公网 IP)

保存后回到主页, 就可以创建/加入房间了。

---

## 五、试玩

1. 玩家A: 输入昵称"小明" → 创建房间 → 得到6位房间号 `AB12CD`
2. 玩家B: 输入昵称"小红" → 输入房间号 `AB12CD` → 加入房间
3. 双方开始对局!
4. 完成后, 在主页点击"战绩回放"可查看历史对局

---

## 六、常见问题

**Q: 浏览器访问不到 `http://1.2.3.4:8080/api/health`?**
A: 检查云服务器安全组, 是否放行了 8080 端口。也要检查服务器防火墙。

**Q: 房间号是固定的还是每次创建都不同?**
A: 每次创建都不一样, 是随机6位字符。

**Q: 一个手机能同时加入多个房间吗?**
A: 不能, 当前只支持一局一房。

**Q: 服务端崩了/重启了, 对局会怎样?**
A: 内存中的房间状态会丢失, 战绩(SQLite)保留。

**Q: 如何备份战绩?**
A: 备份服务器上的 `gomoku.db` 文件即可, 包含所有对局和回放数据。

**Q: 想要 https/wss 加密?**
A: 建议用 Nginx 反向代理。参考网上"Nginx + Let's Encrypt"教程。
