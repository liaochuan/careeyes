# CareEyes / 护眼时刻

一个原生 Android 护眼应用。遵循 20-20-20 规则，支持自定义专注/休息方案、全屏休息提醒、后台常驻、统计、主题与中英双语。

## 功能

- **20-20-20 与自定义方案**：内置 `20-20-20`、`45-5`、`60-5`；可新增、命名、编辑、删除多套方案并随时切换。
- **全屏休息提醒**：专注结束展示全屏倒计时、护眼文案和跳过按钮；跨应用展示依赖悬浮窗权限，未授权时降级通知。
- **后台运行**：前台 Service（`specialUse`）、开机自启、电池优化引导、最近任务隐藏（运行时通过 `AppTask.setExcludeFromRecents` 切换）。
- **通知**：常驻通知显示当前模式与倒计时（系统 chronometer，不逐秒刷新）；可选独立进度通知条。
- **统计**：按日记录专注时长、完成/跳过休息，展示 7 日趋势、累计数据与连续天数。
- **省电计时**：事件驱动，只在一个到期点唤醒；息屏冻结，息屏时长达到休息时长后亮屏重置为新的一轮专注。
- **视频暂停**：休息开始时通过通知使用权 + 使用情况访问，只暂停当前前台应用的视频，后台音乐不受影响。需要系统授予“通知使用权”，不使用无障碍或 root。
- **主题**：青玉、海蓝、暖橙、紫丁香、岩灰 5 套配色，支持自定义主色与浅色/深色/跟随系统。
- **双语**：简体中文与英语，跟随系统语言。

## 构建

环境：

- JDK 17
- Android SDK：platform 37、build-tools 36.1.0
- Gradle 9.7.1 + AGP 9.4.1（AGP 内置 Kotlin）

在项目根目录执行，并确保 `ANDROID_HOME` 指向本机 Android SDK：

```powershell
$env:JAVA_HOME = '<jdk-17>'
$env:ANDROID_HOME = '<android-sdk>'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

APK：

- Debug：`app/build/outputs/apk/debug/app-debug.apk`
- Release：`app/build/outputs/apk/release/app-release.apk`（已签名，可直接安装）
- 打包输出：`dist/careeyes-1.0.0-1-arm64-v8a.apk`

## 打包脚本

默认构建已签名的 arm64-v8a release APK，并复制到项目根目录的 `dist/`：

```powershell
.\build.ps1
```

如果 PowerShell 执行策略受限，也可以直接运行：

```powershell
.\build.bat
```

可选参数：

```powershell
# 清理后重新构建
.\build.ps1 -Clean

# 临时构建其他 ABI（例如模拟器 x86_64）
.\build.ps1 -Abi x86_64

# 同时打包多个 ABI
.\build.ps1 -Abi "arm64-v8a,x86_64"
```

对应的 Gradle 任务为 `:app:distRelease`，也可直接执行：

```powershell
.\gradlew.bat :app:distRelease -Pabi=arm64-v8a
```

Release ABI 默认只有 `arm64-v8a`；Debug 构建保持全部 ABI，便于模拟器调试。

## 签名配置

Release 签名从本机 `local.properties` 读取，密码不写入 `build.gradle.kts`：

```properties
signing.storeFile=<keystore-path>
signing.storePassword=<store-password>
signing.keyAlias=<key-alias>
signing.keyPassword=<key-password>
signing.storeType=PKCS12
```

`local.properties` 已在 `.gitignore` 中，请勿提交或分享。缺少任一 `signing.*` 配置或密钥文件不存在时，release 构建会退化为未签名 APK。

## 首次使用

1. 安装并打开应用。
2. 授予通知权限。
3. 在首页或设置页授予“显示在其他应用上层”，确保休息页能覆盖其他应用；MIUI/HyperOS 等系统还需在应用信息里允许「后台弹出界面」。
4. 可选：将应用加入电池优化白名单，提高后台计时可靠性。
5. 可选：需要休息时暂停视频时，在设置页授予“通知使用权”和“使用情况访问”，再打开开关。

## 权限说明

| 权限 | 用途 |
|---|---|
| `POST_NOTIFICATIONS` | 前台计时通知与休息提醒 |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | 后台计时 |
| `RECEIVE_BOOT_COMPLETED` | 开机恢复计时 |
| `SYSTEM_ALERT_WINDOW` | 在其他应用上方展示全屏休息页 |
| `USE_FULL_SCREEN_INTENT` | 无悬浮窗权限时的降级提醒 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 用户主动加入电池白名单 |
| `PACKAGE_USAGE_STATS` | 特殊访问权限，仅用于判断当前前台应用，以便只暂停前台视频 |
| 通知使用权 | 仅用于读取活动 MediaSession 并暂停播放 |

应用**不申请网络权限**，配置与统计只保存在本机私有 DataStore，`allowBackup=false`。

## 已知限制

- 未授予悬浮窗权限且应用在后台时，只能通过高优先级通知提醒，无法强制全屏覆盖。
- 部分 OEM 会限制前台服务或开机自启，需要手动允许自启动/后台运行；MIUI/HyperOS 等还需额外允许「后台弹出界面」，否则后台到点只发通知，不会自动弹出全屏休息页。
- 视频暂停依赖第三方应用发布 MediaSession，且只作用于当前前台应用；不发布或忽略暂停指令的应用无法被暂停，也不会自动恢复播放。
- 息屏时长达到休息时长时只重置为新一轮专注，不记为休息，也不判断用户是否真的看向远处。
- Android 14+ 可能不授予 `USE_FULL_SCREEN_INTENT`；此时无悬浮窗权限的降级提醒会表现为 heads-up 通知。主路径仍是悬浮窗全屏页。
- 跨午夜的专注段会整段计入落盘当天，不按午夜拆分。
- 服务被系统杀死后 `START_STICKY` 会重启，但从当前预设的完整专注时长开始，不恢复被中断的剩余秒数。

## 目录

```
app/src/main/java/org/liaocl/careeyes/
  data/      模型、DataStore 仓库
  engine/    纯计时状态机与息屏决策
  service/   前台 Service、通知、媒体暂停、休息页、开机接收器
  ui/        Compose 页面、组件、主题
  util/      权限与时间格式化
```
