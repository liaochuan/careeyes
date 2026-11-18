# CareEyes 项目约定

## 项目概况

- 包名：`org.liaocl.careeyes`
- 类型：原生 Android 护眼应用（Kotlin + Jetpack Compose）
- 构建产物：`app/build/outputs/`，打包产物：`dist/`

## 技术栈

- Kotlin 2.4.20（AGP 内置 Kotlin，**不要**再应用 `org.jetbrains.kotlin.android`）
- Jetpack Compose 1.12.1 / Material3 1.4.0
- Gradle 9.7.1 / AGP 9.4.1
- minSdk 26 / targetSdk 36 / compileSdk 37
- DataStore Preferences + kotlinx.serialization（不使用 Room）
- 单 Activity + 简单页面状态，不使用 Navigation-Compose

## 环境

- JDK 17（通过 `JAVA_HOME` 或 `local.properties` 的 `build.javaHome` 配置）
- Android SDK（通过 `ANDROID_HOME` 或 `local.properties` 的 `sdk.dir` 配置）
- build-tools：`36.1.0`

## 构建与测试

在项目根目录执行：

```powershell
# Debug
.\gradlew.bat :app:assembleDebug --console=plain

# 单元测试
.\gradlew.bat :app:testDebugUnitTest --console=plain

# 静态检查
.\gradlew.bat :app:lintDebug --console=plain

# 打包：默认 arm64-v8a，输出到 dist/
.\build.ps1
.\build.bat

# 清理后打包
.\build.ps1 -Clean

# 临时构建其他 ABI
.\build.ps1 -Abi x86_64
.\build.ps1 -Abi "arm64-v8a,x86_64"

# 直接调用 Gradle 打包任务
.\gradlew.bat :app:distRelease -Pabi=arm64-v8a
```

APK：

- Debug：`app/build/outputs/apk/debug/app-debug.apk`
- Release：`app/build/outputs/apk/release/app-release.apk`
- 打包：`dist/careeyes-1.0.0-1-arm64-v8a.apk`

## 签名

Release 签名从 `local.properties` 读取，密码不得写入源码、README、日志或提交记录：

```properties
signing.storeFile=...
signing.storePassword=...
signing.keyAlias=...
signing.keyPassword=...
signing.storeType=PKCS12
```

- `local.properties` 必须按 UTF-8 读取（`build.gradle.kts` 已使用 UTF-8 Reader，路径可能包含非 ASCII 字符）。
- `local.properties` 已在 `.gitignore` 中，不得提交或分享。
- 缺少配置或密钥文件不存在时，release 会退化为未签名 APK；`distRelease` 会明确失败。

## 目录约定

- `app/src/main/java/org/liaocl/careeyes/data/`：模型、DataStore 仓库、AppContainer
- `.../engine/`：纯计时逻辑，必须可在 JVM 单测
- `.../service/`：前台 Service、通知、开机接收器、媒体暂停、休息页
- `.../ui/`：Compose 页面、组件、主题
- `.../util/`：权限、时长格式化
- `app/src/main/res/values/`：英文默认资源
- `app/src/main/res/values-zh/`：简体中文资源

## 硬约束

- 不申请 `INTERNET` 权限；`allowBackup=false`，配置只存本机。
- Service 不得按秒循环；只在一个到期点 `delay`，UI 仅在可见时按秒刷新。
- 息屏冻结计时；真实息屏时长 ≥ 当前休息时长时，亮屏重置为完整专注，否则恢复剩余。锁屏亮屏时间不计入休息。
- 读取已保存设置必须用 `SettingsRepository.load()`；不要用 `settings.first()` 或 `.value` 作为冷启动初始值，`StateFlow` 会先返回默认 `AppSettings()`。
- 用户可见文本必须同时存在 `values/strings.xml` 与 `values-zh/strings.xml`，键集合保持一致。
- Release 默认只打 `arm64-v8a`；Debug 保留全部 ABI，便于模拟器。
- 不引入 Room、Navigation-Compose、Coil、Ktor、WorkManager、精确闹钟。
- 不把密码、keystore、`local.properties`、`dist/`、`docs/` 提交到版本库。
- 不执行 `commit` / `push` / `merge`，除非用户明确要求。

## 测试要求

- 纯逻辑改动必须补/跑 `:app:testDebugUnitTest`：
  - 计时与息屏决策：`TimerLogicTest`
  - 统计聚合/连续天数：`StatsTest`
  - 预设校验：`PresetValidationTest`
- 涉及 UI、Service、通知、权限、签名、ABI 的改动，至少完成：
  - `:app:assembleDebug`
  - `:app:lintDebug`
  - `:app:assembleRelease`
  - 有模拟器/设备时做一次安装回归
- 收尾必须自审或独立审查，结论带 `文件:行` 依据。

## 已知限制

- Android 14+ 可能不授予 `USE_FULL_SCREEN_INTENT`；主路径依赖悬浮窗权限，未授权时降级 heads-up 通知。
- 视频暂停/恢复依赖第三方应用发布并响应 MediaSession 控制；不发布会话或忽略后台 play() 的应用无法完整暂停/恢复。
- 部分 OEM 会限制前台服务或开机自启，需要用户手动允许。
- 跨午夜专注段整段计入落盘当天；进程被杀重启后从完整专注时长开始。
