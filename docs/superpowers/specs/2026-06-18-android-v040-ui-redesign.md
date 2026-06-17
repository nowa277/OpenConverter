# OpenConverter Android v0.4.0 — UI Redesign + 调试日志增强 Spec

> **Status:** Design approved (12 user decisions captured in mockups), pending implementation
> **Author:** nowa277 + Claude (noa-session 2026-06-17)
> **Branch:** `ui-redesign-v040` (forked from `android-port`)
> **Visual Reference:** `docs/mockups/v0.4/` (6 HTML pages, Spotify design system)

---

## 1. 背景

### 1.1 现状(v0.3.2)
- 单一 `FileListScreen.kt` 一次性塞满: 格式 chip + 文件列表 + 4 个按钮 + 设置入口
- 视觉: 黑底 + 绿强调 + pill 按钮,已部分贴近 Spotify,但**缺层次感**
- 设置页 (`SettingsScreen.kt`) 是隐藏入口,需要从主页点击"设置"按钮才能进入
- v0.3.1 hotfix 已加 SAF 文件夹选择 + baseName 编辑,但 UI 没跟上
- **关键痛点**: 真机调试时缺结构化日志,定位 crash 只能靠 adb logcat 关键词 grep(0.3.x 没有任何 app-level log 标签)

### 1.2 为什么 v0.4.0
- v0.3.x 是 "fix crash + 加基础功能" 的救火期,UI 设计被挤压
- 真机测试暴露 4 个问题(选文件闪退/转换闪退/命名 bug/无调试日志),需要更多调试可见性
- 用户在 Bug 1 调研时反馈: "建议增加日志,方便我后续调试过程中更准确地反馈内容给你"

### 1.3 目标
- **UI 主线**: Spotify 完整设计语言(暗色层次 + pill 几何 + 卡片 lift + bottom nav)
- **调试副线**: 关键路径加结构化 logcat,真机调试可直接 grep `OpenConverter` tag 拿到完整上下文
- **不动**: 现有转码逻辑 + 11 个解码器 + ffmpeg-kit 集成 + SAF 写入逻辑

---

## 2. UI Redesign 决策 (12 个用户拍板 + 1 个补充)

### 2.1 视觉系统 (Spotify DESIGN.md 提炼)
| 决策 | 落地 |
|---|---|
| 暗色层次 | `#121212` 底 / `#181818` 卡片 / `#1f1f1f` 按钮 / `#252527` 高亮 |
| 单一品牌色 | Spotify Green `#1ed760` / 渐变到 `#1db954`(纯功能性,不做装饰) |
| 字体 | Roboto 默认(不下载额外包,包体积最小) |
| 几何 | Pill `9999px` / 大按钮 `500px` / 圆形 `50%` |
| 阴影 | Heavy `rgba(0,0,0,0.5) 0 8px 24px` (对话框/菜单) / Medium `0.3` (卡片 hover lift) |
| 大写按钮 | `uppercase` + `letter-spacing: 1.4px`,字号 12-14px |

### 2.2 Logo (用户提供 SVG)
- 形状: 黑色圆底 + Green 渐变圆环 + 5 根音频波形条
- 用途: TopAppBar 28×28 brand-mark + About 页 120×120 hero
- 渲染: 内联 SVG,不下载外部资源
- 实现: `res/drawable/ic_logo.xml` (Vector Drawable),代码引用 `R.drawable.ic_logo`

### 2.3 12 个 UI 决策表

| # | 决策点 | 选项 | 选定 | 落地 |
|---|---|---|---|---|
| 1 | Redesign 幅度 | A/B/C/D | **A. 视觉大改版** | 多页 + Bottom Nav + FAB + 全套 Spotify 风格 |
| 2 | 品牌色 | Green/紫/现有/不选 | **Spotify Green `#1ed760`** | Theme.kt 替换 primary |
| 3 | 导航结构 | Bottom Nav / Drawer / 单页 | **Bottom Navigation** | Material3 `NavigationBar` + 4 Tab |
| 4 | 字体 | Roboto / 思源黑体 / SpotifyMix | **Roboto 默认** | Theme typography 默认 |
| 5 | 文件列表视觉 | Spotify list / 网格 / Material 默认 | **Spotify 风格列表 (hover lift)** | LazyColumn + 自定义 Card |
| 6 | 进度反馈 | TopAppBar / mini player / 仅通知 | **TopAppBar LinearProgressIndicator** | `LinearProgressIndicator` 在 brand 下 |
| 7 | 主 CTA | FAB / inline / TopAppBar action | **Scaffold FAB (Spotify 风格)** | `FloatingActionButton` 在 Scaffold |
| 8 | 错误反馈 | Snackbar / Banner / Sheet | **Snackbar (Green on #1f1f1f)** | Material3 `Snackbar` host |
| 9 | 转换后行为 | Banner / 跳转 / 仅通知 | **顶部 Banner 提示 + 留在主页** | `TopAppBar` 上方 `Banner` Composable |
| 10 | Bottom Nav Tab | 4/3 / 拆分队列 | **主页 / 历史 / 设置 / 关于** | 4 `NavigationBarItem` |
| 11 | 主题模式 | Dark only / 跟随 / 手动 | **纯深色 (锁定)** | `darkColorScheme()` only,setting 锁死 |
| 12 | 空状态 | 插画 / 极简 / 示例 | **Spotify 风格插画 + 大按钮** | (mockup 占位,实施时用 SVG) |
| 13 | **贴图 vs 文字** | 保留 / 全删 | **删除所有 emoji/贴图,纯文字 + SVG logo** | 全部 mockup 已无 emoji,只用 SVG logo + 文字 |

### 2.4 Mockup 文件清单 (`docs/mockups/v0.4/`)

| 文件 | 页面 | 演示场景 |
|---|---|---|
| `index.html` | 总览 | 5 mockup 网格预览 |
| `home.html` | 主页 (转换) | 文件队列 + FAB + 格式 chip + 输出文件夹 |
| `home-progress.html` | 转换中 | TopAppBar 进度条 + 文件状态 badge + 取消 FAB |
| `history.html` | 历史 | 时间分组列表 + FAILED badge |
| `settings.html` | 设置 | QMC ekey 输入 + 分组设置项 |
| `about.html` | 关于 | 大 SVG logo + 项目信息 + 捐赠 |
| `_common.css` | 设计系统 | Spotify 色板/几何/阴影/字号 |
| `_logo.html` | Logo 片段 | 内联 SVG(参考) |

---

## 3. 调试日志增强

### 3.1 目标
- 真机调试时,直接 `adb logcat | grep OpenConverter` 拿到完整上下文
- v0.3.x 缺 app-level tag,定位 crash 必须靠运气 grep `AndroidRuntime` stack trace
- 关键路径必须 log: 事件名 + 关键参数 + 持续时间 + 结果

### 3.2 日志规范

**Tag**: 统一用 `OpenConverter`(单一 tag,方便 grep;不再用 `openconverter_ffmpeg` 子 tag)

**格式**:
```
Log.{LEVEL}("OpenConverter", "{Class}.{method} | {event} | key=value ...")
```

**示例** (预期 v0.4 输出):
```
D OpenConverter: SafAdapter.openDocumentsContract | received | uris=3
D OpenConverter: SafAdapter.openDocumentTreeContract | received | treeUri=content://...
D OpenConverter: FileListViewModel.addUris | entry | uri=... displayName="DJ小女孩-我知道.mp3" sizeBytes=7654321 sourceFormat=mp3
W OpenConverter: FileListViewModel.readEntry | detect_null | uri=... displayName=null
D OpenConverter: ConversionViewModel.startConversion | invoke | uris=1 targetFormat=flac hasFolder=true baseName="mySong"
D OpenConverter: ConversionService.onStartCommand | start | uris=1 targetFormat=flac api=34 fgType=MEDIA_PROCESSING
D OpenConverter: ConversionService.startForegroundCompat | invoked | api=34 type=MEDIA_PROCESSING (0x200000)
D OpenConverter: ConversionService | progress | 1/3 file="DJ小女孩-我知道.mp3"
D OpenConverter: ConversionService | success | 1/3 file="DJ小女孩-我知道.mp3" encoded=12103456
D OpenConverter: ConversionService | failed | 1/3 file="..." error="Unknown format"
D OpenConverter: ConversionService.onStartCommand | done | success=2 failed=1
```

### 3.3 必须加日志的位置

| 文件 | 方法 | 事件 | 参数 |
|---|---|---|---|
| `SafAdapter.kt` | `openDocumentsContract` 回调 | `received` / `empty` | uris=count |
| `SafAdapter.kt` | `openDocumentTreeContract` 回调 | `received` / `empty` | treeUri (safe) |
| `FileListViewModel.kt` | `addUris` | `entry` / `detect_null` | uri, displayName, sizeBytes, sourceFormat |
| `FileListViewModel.kt` | `setOutputFolder` | `set` | treeUri |
| `ConversionViewModel.kt` | `startConversion` | `invoke` / `no_folder` / `no_files` | uris, targetFormat, hasFolder, baseName |
| `ConversionService.kt` | `onStartCommand` | `start` / `done` | uris, targetFormat, api, fgType, success, failed |
| `ConversionService.kt` | `startForegroundCompat` | `invoked` | api, type (string + int) |
| `ConversionService.kt` | orchestrator.convertOneInMemory 失败 | `failed` | uri, filename, error message |
| `ConversionService.kt` | orchestrator.convertOneInMemory 成功 | `success` | uri, filename, encoded size |
| `ConversionService.kt` | `queryDisplayName` 失败 | `query_failed` | uri (safe) |
| `ConversionService.kt` | `createDocumentInFolder` 失败 | `create_failed` | folderUri (safe) |
| `SettingsViewModel.kt` | `saveEkey` | `saved` | ekey length |
| `MainActivity.kt` | `onCreate` | `start` | api, app version |

### 3.4 不加日志的位置(避免噪音)
- Compose recomposition 触发
- StateFlow emit (除非是关键状态变化)
- Lifecycle onPause/onResume (Android 系统已有)
- Animation 帧
- LazyColumn scroll

### 3.5 日志实现
- 用 `android.util.Log` (无依赖,无额外包)
- 抽 `Logger.kt` helper:
  ```kotlin
  object OCLog {
      private const val TAG = "OpenConverter"
      fun d(event: String, vararg kv: Pair<String, Any?>) = ...
      fun w(event: String, vararg kv: Pair<String, Any?>) = ...
      fun e(event: String, vararg kv: Pair<String, Any?>, t: Throwable? = null) = ...
  }
  ```
- Release build 自动 strip `Log.d`(用 `if (BuildConfig.DEBUG) Log.d(...)` 或 R8 minify)
- 不做文件持久化(用户通过 logcat 自己取;文件持久化 v0.5 再加)

### 3.6 测试策略
- `Logger.kt` 单元测试:验证 format 正确 + null-safe 渲染
- 其他位置 log 加 `// verify: logcat -s OpenConverter` 注释,manual verification
- 不为日志写"必须看到"测试(脆性 + 时序)

---

## 4. 架构

### 4.1 项目结构变化
```
android/app/src/main/kotlin/com/openconverter/app/
├── MainActivity.kt                  # 改为 setContent { OpenConverterApp() } + NavHost
├── ui/
│   ├── OpenConverterApp.kt          # 新: NavHost + Scaffold + Bottom Nav 容器
│   ├── theme/
│   │   ├── Color.kt                 # 新: Spotify 色板常量
│   │   ├── Type.kt                  # 新: Typography(默认 Roboto + Spotify 字号比例)
│   │   └── Theme.kt                 # 新: darkColorScheme() + OpenConverterTheme
│   ├── home/
│   │   ├── HomeScreen.kt            # 拆 FileListScreen → HomeScreen
│   │   ├── HomeViewModel.kt         # 拆 FileListViewModel → HomeViewModel
│   │   ├── FileCard.kt              # 新: 文件卡片 Composable(Spotify 风格)
│   │   ├── FormatChipRow.kt         # 新: FilterChip 包装
│   │   ├── OutputFolderRow.kt       # 新: 输出文件夹 Composable
│   │   └── ConversionFAB.kt         # 新: Scaffold FAB
│   ├── history/
│   │   ├── HistoryScreen.kt         # 新
│   │   ├── HistoryViewModel.kt      # 新: 读 FailureLog + 输出文件夹列表
│   │   └── HistoryCard.kt
│   ├── settings/
│   │   ├── SettingsScreen.kt        # 重构:分组列表
│   │   └── SettingsViewModel.kt     # 重构:加 EkeyStore 等
│   ├── about/
│   │   └── AboutScreen.kt           # 新
│   └── components/
│       ├── OpenConverterLogo.kt     # 新: Compose 包装 SVG Vector Drawable
│       ├── ProgressStrip.kt         # 新: TopAppBar LinearProgressIndicator
│       └── SnackbarHost.kt          # 新: 全局错误反馈
├── service/
│   └── ConversionService.kt         # 加日志(见 §3.3)
├── saf/SafAdapter.kt                # 加日志
└── util/
    └── OCLog.kt                     # 新: 日志 helper

android/app/src/main/res/
├── drawable/
│   └── ic_logo.xml                  # 新: SVG logo vector drawable
└── values/
    └── colors.xml                   # 新: Spotify 色板常量
```

### 4.2 导航图

```
NavHost("/")
├── "home"        → HomeScreen         (默认)
├── "history"     → HistoryScreen
├── "settings"    → SettingsScreen
└── "about"       → AboutScreen
```

Bottom Navigation 4 项,每项点击 = NavController.navigate(route)。Back press = 默认 pop stack。

### 4.3 数据模型(不变)
- `FileEntry` 已存在,沿用
- `Progress` sealed class 已存在,沿用
- `FailureLog` 已存在,HistoryScreen 复用

### 4.4 ViewModel 拆分
| 当前 | v0.4 |
|---|---|
| `FileListViewModel` (混合文件 + 转换) | `HomeViewModel` (文件 + 文件夹) + `ConversionViewModel` (转换启动) |
| `SettingsViewModel` | `SettingsViewModel` (加更多字段: 默认格式/默认比特率/通知开关/保留文件名) |
| - | `HistoryViewModel` (新) |

---

## 5. 错误处理 & 进度反馈

### 5.1 错误流

**输入层**:
- SAF URI 接收失败 → SAF picker 弹错误,自动 fallback 到文件选择
- `takePersistableUriPermission` 失败 → `OCLog.w` + Snackbar "文件选择失败,请重试"

**转换层**:
- ConversionService `runCatching` 失败 → `OCLog.e` + Snackbar 显示具体错误
- Foreground service start 失败 → `OCLog.e` + Snackbar "无法启动后台服务,请尝试重启 app"

**UI 层**:
- `SnackbarHostState` 全局挂在 Scaffold
- `ConversionViewModel` 暴露 `error: SharedFlow<String>`,UI 订阅显示
- ConversionService 通过 broadcast 或 ResultReceiver 回传错误(ViewModel 收集)

### 5.2 进度反馈

**TopAppBar progress**:
- `LinearProgressIndicator(progress = progressFraction)`
- 数据源: `ConversionViewModel.progressFraction: StateFlow<Float>`
- 更新时机: ConversionService 每个文件完成 → 通过 ResultReceiver 通知 ViewModel

**Banner 完成提示**:
- 转换全部完成后,显示 5 秒 Banner "X 个文件完成"
- 数据源: `ConversionViewModel.lastCompletedCount: StateFlow<Int?>` (null = 隐藏)

---

## 6. 测试策略

### 6.1 单元测试(继续)
- `FormatDetectorTest` (现有, 已加 v0.3.2 passthrough 测试)
- `ConversionOrchestratorTest` (现有)
- `OCLogTest` (新): 验证 log 格式 + null-safe
- `SettingsViewModelTest` (新): 验证 EkeyStore 集成

### 6.2 UI 测试(暂不投入)
- Compose Preview 即可
- 完整 UI 测试 (Compose Test) v0.5 再加

### 6.3 真机测试
- 用户真机测试矩阵: vivo y78 / 荣耀 / 三星(后续扩 OPPO/小米)
- redroid 容器本地 debug(Phase 1)
- Firebase Test Lab 真机回归(Phase 2)
- 每次 release 前,真机走一遍 NCM/QMC/明文 3 种输入 × MP3/FLAC/WAV/M4A/OGG 5 种输出

---

## 7. 实施里程碑

### v0.3.3 hotfix (优先,本周)
| 步骤 | 内容 | 时间 |
|---|---|---|
| 1 | 修 Bug A (SAF URI 权限) | 0.5 天 |
| 2 | 修 Bug B (vivo FGS 回滚 0x200000) | 0.5 天 |
| 3 | 修 Bug C (命名 5 个 bug) | 0.5 天 |
| 4 | 加日志(§3) | 0.5 天 |
| 5 | release 0.3.3 + 上传 GitHub | 0.5 天 |

### v0.4.0 redesign (2 周后)
| 步骤 | 内容 | 时间 |
|---|---|---|
| 1 | 引入 Compose Navigation + Bottom Nav | 1 天 |
| 2 | 拆 FileListScreen → Home/History/Settings/About | 2 天 |
| 3 | 重构 Theme.kt + Color.kt + Type.kt (Spotify 色板) | 1 天 |
| 4 | 加 SVG logo + OpenConverterLogo Composable | 0.5 天 |
| 5 | 改 HomeScreen 文件卡片 + FAB + Snackbar + Banner | 1.5 天 |
| 6 | HistoryScreen + SettingsScreen 重构 + AboutScreen 新建 | 1.5 天 |
| 7 | 联调 + 真机测试 + 修 bug | 1.5 天 |
| 8 | release 0.4.0 + 上传 GitHub | 0.5 天 |

---

## 8. 风险 & 已知 trade-off

### 8.1 风险
- **风险 R1**: Bottom Nav 引入意味着拆 FileListScreen,改动面大,容易引入新 bug
  - 缓解: 保留 FileListScreen.kt 一周,git revert 路径清晰
- **风险 R2**: Theme 改成纯深色,破坏现有用户视觉习惯
  - 缓解: v0.4 是 major bump,允许 breaking visual change
- **风险 R3**: 加日志可能影响性能(release build)
  - 缓解: Release build strip `Log.d`,只保留 `Log.w/e`
- **风险 R4**: SVG logo 缩放模糊
  - 缓解: 用 Vector Drawable,Android 自带矢量渲染,各 DPI 都清晰

### 8.2 Trade-off
- **不用 思源黑体**: 牺牲美观换包体积 (-2MB)
- **不用 浅色模式**: 牺牲灵活性换一致性 (Spotify 本身无浅色)
- **加 Bottom Nav**: 牺牲单页简洁换多页结构
- **删 emoji**: 牺牲趣味换简洁专业 (用户明确要求)

---

## 9. Spec 自检

✅ 无 placeholder/TODO
✅ 章节无内部矛盾
✅ 范围聚焦 (单 spec,无跨 plan)
✅ 模糊点已澄清 (12 决策 + 1 补充)
✅ Mockup 已存档 git 防止丢失
✅ 真机测试计划明确

---

## 10. 下一步

1. **等 user review** 本 spec
2. User approve → 调 superpowers:writing-plans 写实施 plan
3. 实施 → v0.3.3 hotfix (含日志) 先出 → 真机验证 → v0.4.0 重设计

---

## 11. 详细修复 plan + commit 策略 (user approved, 直接可执行)

### 11.1 v0.3.2 working tree 处理
**决策**: 不单独 commit v0.3.2 hotfix 5 处, 整合进 v0.3.3 commit 链 (理由: 同属 "v0.3.x 真机 bug 修复" 范畴, 分裂增加认知负担)。

### 11.2 v0.3.3 commit 链 (android-port 分支)

按 commit-by-commit 顺序, 每个 commit 单独独立可 revert:

| # | Commit 标题 | 内容 | 文件 |
|---|---|---|---|
| **C1** | `feat(format): passthrough MP3/FLAC/WAV/M4A/OGG/AAC` | FormatDetector.kt 加 passthrough 分支 + 测试 | FormatDetector.kt, FormatDetectorTest.kt |
| **C2** | `feat(orchestrator): route plaintext audio direct to ffmpeg` | ConversionOrchestrator.kt 加明文分支 → AudioData(input, sourceFormat), 跳过 decrypt | ConversionOrchestrator.kt, ConversionOrchestratorTest.kt |
| **C3** | `fix(android): revert FGS type to MEDIA_PROCESSING (0x200000)` | manifest dataSync + runtime 0x200000 (Agent 1 怀疑 specialUse 在 vivo 上不稳) | AndroidManifest.xml, ConversionService.kt |
| **C4** | `fix(android): SAF persistable URI permission + RECEIVER_NOT_EXPORTED` | Bug A 修: SafAdapter.kt takePersistableUriPermission + try/catch SecurityException | SafAdapter.kt |
| **C5** | `fix(android): output filename preserves source stem + sanitizes baseName` | Bug C 修: 5 个命名 bug 综合修 | ConversionService.kt, FileListViewModel.kt, FileListScreen.kt |
| **C6** | `feat(android): structured logcat (OpenConverter tag, 12 hot points)` | 日志增强: OCLog.kt 新增 + 12 个关键 log 点 | OCLog.kt (新), 6 个文件, LoggerTest.kt (新) |
| **C7** | `chore(android): use BuildConfig.VERSION_NAME in FileListScreen` | 版本号修: 硬编码 v0.3.0 → BuildConfig.VERSION_NAME + buildConfig = true | FileListScreen.kt, build.gradle.kts |

### 11.3 实施顺序

按 superpowers:test-driven-development: 每个 commit 先 RED (写测试) → 验证失败 → GREEN (最小代码) → 验证通过。

```
Phase A: 在 android-port 分支实施 C1-C7 (含日志)
  - 每个 commit: RED → GREEN → 单 commit
  - 全部完成后: gradle test + assembleRelease
  - release v0.3.3 (3 ABI APK + sha256)
  - 上传 GitHub Release

Phase B: 真机验证 (用户)
  - vivo y78 / 荣耀: 装 arm64-v8a APK
  - 抓 logcat: adb logcat -s OpenConverter
  - 反馈 → hotfix v0.3.4 如需要

Phase C: 启动 v0.4.0 redesign (ui-redesign-v040 分支)
  - 主题色板 → Logo → 拆 Home → 拆 History → 拆 Settings → 新建 About → 联调
  - release v0.4.0 + 真机验证
```

### 11.4 v0.4.0 实施拆分 (subagent-driven-development)

| Task | 内容 | 文件 | 并行 |
|---|---|---|---|
| T1 | 引入 Compose Navigation + OpenConverterApp (NavHost) | build.gradle.kts, OpenConverterApp.kt | ✅ |
| T2 | Theme 重构 (Spotify 色板 + Typography) | theme/Color.kt, theme/Type.kt, theme/Theme.kt | ✅ |
| T3 | SVG Logo Vector Drawable + Logo Composable | res/drawable/ic_logo.xml, components/OpenConverterLogo.kt | ✅ |
| T4 | 拆 FileListScreen → HomeScreen | ui/home/HomeScreen.kt | 依赖 T1+T2 |
| T5 | HistoryScreen + HistoryViewModel (新) | ui/history/ | 依赖 T1+T2 |
| T6 | SettingsScreen 重构 (分组列表) | ui/settings/ | 依赖 T1+T2 |
| T7 | AboutScreen 新建 | ui/about/ | 依赖 T1+T2+T3 |
| T8 | 联调 + Bottom Nav + 删旧 FileListScreen | OpenConverterApp.kt | 依赖 T1-T7 |
| T9 | assembleRelease + 真机测试 | - | 依赖 T8 |

### 11.5 时间预算 (opus subagent 并行)

- Phase A (v0.3.3 hotfix): 7 commit × 0.5 天 = 3.5 天
- Phase B (真机): ~1 天
- Phase C (v0.4.0 redesign): T1-T8 并行 = 3 天, T9 = 1 天

总: 3.5 + 1 + 4 = **8.5 天**

### 11.6 不做 (YAGNI)

- ❌ Compose UI 测试 (v0.5)
- ❌ Dark theme 切换 (锁定深色)
- ❌ 文件持久化日志 (v0.5)
- ❌ redroid docker 集成脚本 (用户手动)
- ❌ Firebase Test Lab 集成 (Phase 2)
- ❌ Material You dynamic color (锁定深色)

---

## 12. 已 approve + 立即执行

User 已 approve (选择 "决策当前 spec 是否够用" + "你自己安排, 选择最合理的方案")。

直接进入 Phase A: 实施 v0.3.3 commit C1-C7 (在 android-port 分支)。

按 superpowers:test-driven-development: 每个 commit RED → GREEN → 单 commit, 无 Co-Authored-By。

mockup server 在 http://localhost:8765/ 仍运行, 真机测试时随时刷新查看设计参考。
