# 智能静音（Phone Silencer）

个人自用 Android 应用：**工作时间自动静音，其他时间恢复正常响铃，自动识别法定节假日与调休补班**。
目标机型：华为 Mate 70（HarmonyOS 4.3，兼容安卓 APK）。

## 功能

- 总开关，一键启停
- 工作时间段（支持多段、支持跨天如 22:00—06:00）
- 勾选生效星期（周一~周日）
- 法定节假日自动识别：**节假日不静音，调休补班日按工作日静音**
- 节假日数据来自 timor.tech 免费接口，整年缓存、离线可用
- 静音方式二选一：勿扰模式（推荐） / 静音铃声
- 开机自启恢复、精确到分钟的闹钟调度 + 2 小时兜底校正（防系统拦截闹钟，单次开销约 10-50ms）
- 权限状态可视化引导（勿扰授权 / 电池优化 / 精确闹钟）

## 目录结构

```
phone-silencer/
├── app/src/main/java/com/silencer/app/
│   ├── MainActivity.kt          # 入口
│   ├── logic/                   # 纯判定逻辑（无 Android 依赖）
│   │   ├── Models.kt            # WorkWindow / Settings / DayType
│   │   └── ScheduleChecker.kt   # 是否静音 + 下次切换时刻
│   ├── data/
│   │   ├── HolidayRepository.kt # 节假日 API + 缓存
│   │   └── SettingsStore.kt     # DataStore 设置持久化
│   ├── control/
│   │   └── SilenceController.kt # 勿扰/铃声切换（保存与恢复原状态）
│   ├── alarm/                   # AlarmManager + WorkManager + 开机自启
│   └── ui/                      # Compose 界面 + ViewModel
├── demo/index.html              # 浏览器体验 Demo（同款逻辑）
└── build.sh                     # 命令行构建脚本
```

## 构建与安装

### 方式 A：命令行（推荐，无需 Android Studio）

```bash
# 1. 安装 JDK 17（如已装可跳过）
brew install --cask temurin@17
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home

# 2. 构建（会自动安装 gradle 并生成 wrapper）
./build.sh

# 3. 产物：app/build/outputs/apk/debug/app-debug.apk
#    传到手机（微信/数据线）点击安装即可
```

### 方式 B：Android Studio

用 Android Studio 打开 `phone-silencer/` 目录，同步后直接 Run（需手机开 USB 调试）或 Build APK。

## 首次使用必做（3 项权限）

App 内“系统权限”卡片提供跳转：

1. **通知使用权**（勿扰模式必需）：设置 → 通知 → 更多通知设置 → 通知使用权
2. **忽略电池优化**：防止系统杀后台导致不切换
3. **允许精确闹钟**：Android 12+ 需要，保证准点切换

**华为专属**：设置 → 应用 → 应用启动管理 → 本应用 → 设为“手动管理”并允许自启动/关联启动/后台活动。

## 逻辑说明

- 勾选的星期 × 时间段内 → 静音（按所选静音方式）
- 其余时间 → 恢复正常响铃（会还原你之前手动设置的模式）
- 法定节假日 → 不静音
- 调休补班日（如 2026-02-14 周六）→ 按工作日静音
- 节假日接口：`https://timor.tech/api/holiday/year/{年份}/?type=Y&week=Y`，type=2 节日 / type=3 补班

## 体验 Demo

浏览器直接打开 `demo/index.html`（或 `python3 -m http.server` 后访问），
可实时查看当前时间的静音判定、切换时间点，并试玩 2026 年任意日期的判定结果。

## 已知限制

- 勿扰模式能拦通知但来电可能仍响（华为策略）；需要连来电一起拦可选"静音铃声"模式
- 华为省电策略下极端情况可能推迟闹钟，2 小时兜底任务保证最终校正（兜底任务不常驻内存、正常路径无网络请求，对电量影响可忽略）
- 升级鸿蒙 NEXT（纯血鸿蒙）后 APK 不再兼容，届时需迁移到 ArkTS 重写
