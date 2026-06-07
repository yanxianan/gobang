# Android 客户端使用说明

## 在 Android Studio 中打开项目

1. 启动 **Android Studio** (推荐 Hedgehog 2023.1.1 或更新版本)
2. `File` → `Open` → 选择 `android/` 目录
3. 等待 Gradle 同步完成 (首次会下载 Gradle 8.4 + 依赖, 需联网)
4. 连接安卓手机(需开启 USB调试) 或启动模拟器
5. 点击 ▶️ 运行按钮, 即可将应用安装到设备

> 如果提示 `gradle-wrapper.jar` 缺失, 在项目根目录执行:
> ```
> gradle wrapper
> ```
> 然后重新同步项目。

## 项目目录

```
android/
├── settings.gradle.kts            # 项目设置
├── build.gradle.kts               # 项目级构建配置
├── gradle.properties              # Gradle JVM 参数
├── gradle/wrapper/                # Gradle Wrapper
├── gradlew, gradlew.bat           # 启动脚本
└── app/                           # 应用模块
    ├── build.gradle.kts           # 模块构建配置
    ├── proguard-rules.pro         # 混淆规则
    └── src/main/
        ├── AndroidManifest.xml    # 应用清单
        ├── kotlin/                # Kotlin 源码
        └── res/                   # 资源 (layout/drawable/values)
```

## 配置服务器地址

应用首次启动时, 主页会显示"未配置服务器"。点击 **服务器设置** 按钮, 填入你的服务器地址:

```
1.2.3.4:8080
```
或
```
gomoku.your-domain.com:8080
```

应用会把 `http://` 加在前面, 把 `ws://` 加在 WebSocket 前缀前面。
- HTTP 接口: `http://你的地址/api/...`
- WebSocket:  `ws://你的地址/ws`

## 编译 Release APK

```bash
cd android
./gradlew assembleRelease
```

生成的 APK 在:
```
android/app/build/outputs/apk/release/app-release-unsigned.apk
```

未签名的APK不能直接安装。需要签名:
```bash
# 生成签名密钥(仅首次)
keytool -genkey -v -keystore gomoku.keystore -alias gomoku -keyalg RSA -keysize 2048 -validity 10000

# 签名
jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 -keystore gomoku.keystore app-release-unsigned.apk gomoku

# 对齐(可选, 但建议)
zipalign -v 4 app-release-unsigned.apk app-release.apk
```

## 最低运行要求

- Android 8.0 (API 26) 及以上
- 网络连接
- 屏幕分辨率 720p 及以上

## 常见问题

**Q: 应用打开后立即闪退?**
A: 检查手机网络是否能访问服务器。打开日志(`adb logcat`)查看错误。

**Q: 创建房间后对方搜不到?**
A: 双方都需在主页输入了相同的服务器地址。房间号是6位字符(如 `AB12CD`)。

**Q: 是否需要账号登录?**
A: 不需要, 只需输入昵称即可对战。战绩通过昵称关联。
