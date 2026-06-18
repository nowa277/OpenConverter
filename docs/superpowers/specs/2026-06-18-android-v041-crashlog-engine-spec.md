# OpenConverter Android v0.4.1 — 崩溃日志捕获 + Engine 抽取 + 本地测试 Spec

> **Status:** Design approved (5 sections, user-approved decisions inline)
> **Author:** nowa277 + Claude (session 2026-06-18)
> **Branch:** `ui-redesign-v040` (后续 v0.4.1 工作在该分支或新开分支, 由 writing-plans 决定)
> **Motivation:** v0.4.0 在 vivo Y78 上一点击「开始转换」就闪退, 进程死, 重启后状态全无, 无法取得 stack trace。三次 FGS 修复失败 (v0.3.2/v0.3.3/v0.4.0) → 按 systematic-debugging 「3+ Fixes Failed: Question Architecture」, 不再盲改, 先拿到真机日志 + 补本地测试。

---

## 1. 范围

### 1.1 做 (4 件事)

1. **CrashReporter** — `Thread.setDefaultUncaughtExceptionHandler` + `CoroutineExceptionHandler` 写 stack trace 到 app 私有目录
2. **Settings「崩溃日志」UI** — 列表 + 预览 + 分享 (ACTION_SEND)
3. **抽 `ConversionEngine`** — 把 `ConversionService.onStartCommand` 里 156 行逐文件循环搬成纯 Kotlin 类, Service 变薄壳
4. **测试** — Engine 纯 JVM 测试 (全路径到 ffmpeg 边界) + Service Robolectric 测试 + CrashReporter 测试

### 1.2 不做 (YAGNI / 显式排除)

- ❌ 修 vivo FGS 闪退 (等 CrashReporter 拿到日志再开 v0.4.2 plan)
- ❌ 发 GitHub release (用户要求「不要每次都直接发布」, release 等用户点头)
- ❌ 装 KVM emulator / android-skills-mcp / mcp-android-emulator (chat_results.dm 工具链评估结论: 对 OEM 专属 bug 无效, 延后)
- ❌ 重建项目 (团队核实结论: 会丢弃 11 解码器/ffmpeg-kit/SAF 修复/OCLog/v0.4.0 UI 等已有资产, 不解决 vivo crash)
- ❌ Compose UI 测试 (v0.5)
- ❌ 真机 instrumented test (等 vivo 日志后再决定)
- ❌ ffmpeg-kit 真转码集成测 (native 依赖, 留 instrumented)
- ❌ 崩溃日志自动上报服务器 (无后端 + 隐私)
- ❌ 按崩溃类型聚合 badge (用户量小, 直接列表)

---

## 2. 架构 — Engine 抽取

### 2.1 现状

```
ConversionService.onStartCommand (294 行)
  ├─ 解 Intent (uris / targetFormat / folderUri)
  ├─ OCLog.i("svc.onStart")
  ├─ startForegroundCompat (FGS, API34→0x200000 / API29+→dataSync / else→none)
  └─ scope.launch {
       for uri in uris:  ← 156 行逐文件循环
         read input | orch.convertOneInMemory | createDocument | write
         | history.record | failureLog.record | _progress.emit | OCLog
     }
     最终通知 + stopForeground + stopSelf
```

痛点: 156 行循环把「转换业务逻辑」与「Android Service 生命周期 / FGS / ContentResolver / 通知」耦合在一个方法里, 无法 JVM 测, 三次盲改都改在这里。

### 2.2 v0.4.1 目标结构

```
ConversionService (薄壳, ~100 行)
  ├─ 解 Intent
  ├─ OCLog.i("svc.onStart")
  ├─ startForegroundCompat (FGS, 不变)
  ├─ scope.launch {
  │     val engine = ConversionEngine(deps...)
  │     val result = engine.convertAll(uris, format, folder, _progress)
  │     更新最终通知 (依据 result.ok / result.fail)
  │     stopForeground + stopSelf
  │  }

ConversionEngine (~250 行, 纯 Kotlin, 无 Service/Context 依赖)
  构造注入:
    - ContentResolverFacade  (read / write / createDocument / queryDisplayName)
    - EkeyProvider            (getEkey: () -> String?)
    - Orchestrator            (convertOneInMemory: (...) -> Result)
    - FailureSink             (record(uri, filename, error))
    - HistorySink             (record(HistoryEntry))
    - Clock                   (nowMs: () -> Long)
  方法:
    - convertAll(uris, targetFormat, folderUri, progressSink): EngineResult
  内部:
    - 原 156 行循环, 所有 Android API 走 facade 接口
```

### 2.3 接口定义

```kotlin
// 全部在 service/engine/ 包下, 纯 Kotlin 接口

interface ContentResolverFacade {
    fun read(uri: Uri): ByteArray?
    fun write(uri: Uri, bytes: ByteArray): Boolean
    fun createDocument(folderUri: Uri, name: String, mime: String): Uri?
    fun queryDisplayName(uri: Uri): String?
}

interface EkeyProvider { fun getEkey(): String? }

interface Orchestrator {
    fun convertOneInMemory(
        input: ByteArray, fileName: String?, ekey: String?,
        targetFormat: String, bitrateKbps: Int,
    ): OrchestratorResult
    // OrchestratorResult = 现有 ConversionOrchestrator.Result (encoded, sourceFormat, targetFormat, durationSec)
}

interface FailureSink { fun record(uri: String, filename: String, error: String) }
interface HistorySink { fun record(entry: HistoryEntry) }
interface Clock { fun nowMs(): Long }

data class EngineResult(val ok: Int, val fail: Int, val total: Int)
```

### 2.4 生产实现 vs 测试实现

| 接口 | 生产实现 (在 Service 内构造) | 测试实现 |
|------|------------------------------|---------|
| ContentResolverFacade | 委托 `service.contentResolver` + `DocumentsContract` | fake: map<uri,bytes> |
| EkeyProvider | 委托 `ekeyStore.getEkey()` | fake: 返回固定/null |
| Orchestrator | 委托现有 `ConversionOrchestrator` 单例 | **fake: 返回固定字节或抛异常, 不加载 ffmpeg-kit native** |
| FailureSink | 委托 `FailureLog.record(...)` | fake: 收集到 list |
| HistorySink | 委托 `HistoryRepository.record(...)` | fake: 收集到 list |
| Clock | `System::currentTimeMillis` | fake: 固定值 |

**ffmpeg-kit 边界**: Engine 调 `Orchestrator.convertOneInMemory` 是边界。测试注入 fake Orchestrator 返回已知字节或抛已知异常, 断言后续 read/createDoc/write/history/progress 行为。**测试全程不加载 ffmpeg-kit native lib** — JVM 测无 .so, 这正是能本地跑的关键。

### 2.5 兼容性

- `ConversionService.start(context, uris, format, folder)` 签名不变 → `ConversionViewModel` / `HomeScreen` 不动
- Intent extras 不变 (`EXTRA_URIS` / `EXTRA_TARGET_FORMAT` / `EXTRA_FOLDER_URI`)
- `Progress` sealed class不变 → UI 订阅不变
- `HistoryRepository` / `HistoryEntry` / `FailureLog` 公开 API 不变, 只是 Service 调用点从直接调变成经 facade 委托

---

## 3. CrashReporter

### 3.1 职责

捕获未处理异常 → 写文件到私有目录 → chain 给原 handler (让系统弹「已停止运行」, 行为不变)。

### 3.2 安装点

`OpenConverterApp.onCreate()` 第一行 (越早越好, 任何后续初始化崩了也能抓)。

### 3.3 文件位置与命名

`getExternalFilesDir("crash")/crash-{yyyyMMdd-HHmmss}-{hash6}.txt`

- `yyyyMMdd-HHmmss`: 本地时区, 列表排序友好
- `hash6`: stack trace 文本的 SHA-256 前 6 hex → **同样崩溃同一文件名**, 避免列表 50 条重复
- 私有目录: 无运行时权限, 卸载自动清, 用户文件管理器可浏览

### 3.4 文件内容模板

```
=== OpenConverter Crash Report ===
请在分享前确认下方内容不含敏感信息(本机文件名/账号等),
不放心可以删掉对应行再发。

App: 0.4.1 (versionCode 5)
Git: 113882d (clean)
Built: 2026-06-18T02:30:00Z

Device: vivo Y78 (V2228A)
Manufacturer: vivo
Brand: vivo
Android: 14 (API 34)
ABI: arm64-v8a

Time: 2026-06-18T20:14:33+08:00
Thread: main (12)
Process: com.openconverter.app

=== Stack Trace ===
java.lang.RuntimeException: ...
    at android.app.ActivityThread...
    at ...

=== Caused by ===
...

=== Recent OCLog (last 200 lines) ===
[20:14:30] svc.onStart n=1 fmt=flac folder=primary:Music
[20:14:30] svc.file.start i=0 total=1 uri=content://...
...
```

### 3.5 关键决策

| 点 | 选择 | 理由 |
|----|------|------|
| 同一崩溃同名文件 | hash6 后缀 | 避免重复信息淹没列表 |
| OCLog 环大小 | 200 条 | 加 ringbuffer, 足够还原崩溃前上下文 |
| 时间戳格式 | 本地时区 + ISO 8601 | 用户看时间能对上, 我能解析 |
| 写文件失败 | catch 后仍 chain 给原 handler | 不能因写不进文件让 app 不崩 |
| 主线程 + 协程异常 | 都接 | `Thread.setDefaultUncaughtExceptionHandler` + `CoroutineExceptionHandler` 各装一个 |

### 3.6 git hash 打进 BuildConfig

`build.gradle.kts` defaultConfig 加 (写法以 Gradle 8.x + AGP 8.5+ 配置缓存兼容为准, plan 实施时按实际 API 调整):

```kotlin
// 配置阶段读 git, 失败 fallback 到 "unknown" — 不能让 build 因 git 缺失挂掉
fun runGit(vararg args: String): String = try {
    val proc = ProcessBuilder(listOf("git") + args.toList())
        .directory(rootDir).redirectErrorStream(true).start()
    proc.inputStream.bufferedReader().readText().trim().also { proc.waitFor() }
} catch (e: Exception) { "unknown" }

val gitHash = runGit("rev-parse", "--short=7", "HEAD").ifEmpty { "unknown" }
val gitClean = runGit("status", "--porcelain").let { if (it.isEmpty()) "clean" else "dirty" }
buildConfigField("String", "GIT_HASH", "\"$gitHash\"")
buildConfigField("String", "GIT_CLEAN", "\"$gitClean\"")
```
CrashReporter 读 `BuildConfig.GIT_HASH` / `BuildConfig.GIT_CLEAN` / `BuildConfig.VERSION_NAME` / `BuildConfig.VERSION_CODE`。

### 3.7 OCLog ringbuffer

现有 `OCLog` 只 println 不存。加进程级 ringbuffer:
- `ArrayDeque<String>`, 容量 200, 同步 (`@Synchronized` 或 ReentrantLock)
- 每次 `i/d/w/e` append 一行格式化文本
- 超容量从队首移除
- `snapshot(): List<String>` 给 CrashReporter dump

---

## 4. Settings「崩溃日志」UI

### 4.1 位置

设置页「诊断」组,「失败日志」下面加一项「崩溃日志 (N)」。

### 4.2 两屏

**列表屏** `CrashLogListScreen`:
- Scaffold + TopAppBar「崩溃日志」+ 右上「清空」
- LazyColumn 列出 crash 文件, 每项: 时间 + hash6 + stack 首行类型摘要
- 点条目 → 预览屏
- 「清空」→ 二次确认弹窗 → 删除目录所有文件

**预览屏** `CrashLogPreviewScreen`:
- TopAppBar「← 返回」+ 右上「分享」
- 全文 monospace 可滚动
- 「分享」→ `ACTION_SEND` 把文件内容发出 (用户发给我)

### 4.3 数据源 `CrashLogStore`

纯函数类:
- `list(): List<CrashLogEntry>` — 扫 crash 目录, 按修改时间倒序
- `read(fileName): String` — 读全文
- `clear()` — 删所有
- `CrashLogEntry(fileName, timeMs, summary)` — summary 从首行匹配 stack 类型

纯函数, 可测 (注入临时目录)。

### 4.4 导航

NavHost 加 `crash-log` (列表) + `crash-log/{fileName}` (预览) 两条路由, 从 Settings 跳入。

### 4.5 YAGNI

- 不做自动上报服务器
- 不做按类型聚合 badge
- 不做崩溃数推送通知

---

## 5. 测试设计

### 5.1 Engine 纯 JVM 测试 (`ConversionEngineTest`, JUnit, 秒级)

注入 fake deps, 验证循环逻辑:

| 测试 | 验证 |
|------|------|
| 单文件成功 | fake orchestrator 返回字节 → createDoc 返回 uri → write 成功 → history 记 DONE + progress 发 Done |
| 读取失败 | fake read 返回 null → history 记 FAILED「读取失败」+ failureLog 记 + 不调 orchestrator |
| orchestrator 抛异常 | history 记 FAILED + 错误信息透传 + 后续文件继续 |
| 创建文档失败 | folderUri 非空但 createDoc 返回 null → 记 FAILED「无法创建输出文件」 |
| 写入失败 | write 抛异常 → 记 FAILED「保存失败: ...」 |
| 多文件混合 | 3 文件: 成功/读失败/orch失败 → EngineResult.ok=1 fail=2 |
| outName 命名 (回归 spec §2.5) | displayName="周杰伦-晴天.qmcflac" + fmt=flac → outName="周杰伦-晴天.flac" |
| 无扩展名 | displayName="无后缀" → outName="无后缀.mp3" |
| displayName 为 null | → outName="output_0.mp3" |
| progress 时序 | 单文件: Idle → Processing(0,1) → Done(1,1) |
| clock 固定时间 | history timestampMs == 注入固定值 |

**关键**: fake orchestrator 不加载 ffmpeg-kit native, JVM 测能跑。

### 5.2 Service Robolectric 测试 (`ConversionServiceRobolectricTest`, 中速)

| 测试 | 验证 |
|------|------|
| start Intent 字段正确 | `ConversionService.start()` 构造的 Intent 三个 extra 齐全 |
| onStartCommand API34 调 startForeground 带 0x200000 | shadow 验证 |
| onStartCommand API34 之前调 dataSync 类型 | 切 SDK_INT 验证分支 |
| onStartCommand 委托给 Engine | 用 fake engine 注入, 验证 convertAll 被调 + 参数透传 |
| EXTRA 缺失不崩 | targetFormat null → 默认 mp3 |
| stopSelf 在完成后调用 | Engine 返回后 Service 自停 |

### 5.3 CrashReporter 测试

| 测试 | 验证 |
|------|------|
| 写文件到 crash 目录 | 注入临时目录, 触发 handler, 文件存在 |
| 内容含 app 版本 + git hash + device | 读回断言 |
| 同样 stack 同名文件 | 两次同样异常, 只产一个文件 |
| dump OCLog ring | 先写 200+ 条 log, 触发, 文件含最后 200 条 |
| 写失败不吞原异常 | 目录不可写, 原 handler 仍被调用 |

### 5.4 不测

- ❌ Compose UI 测试 (v0.5)
- ❌ 真机 instrumented (等 vivo 日志)
- ❌ ffmpeg-kit 真转码集成测 (native)

---

## 6. 风险 & Trade-off

### 6.1 风险

- **R1**: Engine 抽取改动大 (~200 行重构), 可能引入回归
  - 缓解: 先抽 Engine + 写满测试 (RED→GREEN), Service 切换前 Engine 测试全绿; Service 切换后 Robolectric 测试覆盖委托
- **R2**: CrashReporter 在 vivo 上可能因 OEM 限制无法写文件
  - 缓解: 私有目录 `getExternalFilesDir` 是无权限标准 API, OEM 一般不限制; 写失败 catch 后 chain 原 handler, 不影响崩溃行为本身
- **R3**: Robolectric 的 FGS shadow 可能与真机行为不一致
  - 缓解: Robolectric 只覆盖「AOSP 类」FGS bug (类型/通知/Intent), OEM 专属 bug 本来就不指望它; 明确写在测试注释里

### 6.2 Trade-off

- **抽 Engine 增加一层间接**: 牺牲一点直接性换可测性 + 职责清晰 (3 次盲改已证明现状不可持续)
- **崩溃日志走文件不走服务器**: 牺牲自动化换隐私 + 无后端依赖
- **OCLog 加 ringbuffer 占内存**: 200 条文本 ~20KB, 可忽略

---

## 7. 成功标准

v0.4.1 完成 = 全部满足:

1. ✅ 所有新代码 + 重构有对应测试, `./gradlew test` 全绿
2. ✅ `ConversionEngine` 是纯 Kotlin 类, 无 `Service`/`Context`/`ContentResolver` 直接依赖
3. ✅ `ConversionService` onStartCommand 不再含逐文件循环, 只解 Intent + FGS + 委托 Engine
4. ✅ vivo Y78 装上 v0.4.1 后, 点「开始转换」闪退 → crash 目录下产生 .txt 文件, 用户能从文件管理器 + app 内列表拿到, 分享给我
5. ✅ Settings「诊断」组有「崩溃日志」入口, 列表/预览/分享/清空可用
6. ✅ git hash + 版本号打进 BuildConfig, 崩溃日志含这些字段
7. ❌ 不发 GitHub release (等用户点头)
8. ❌ 不动 Intent extras / Progress / HistoryRepository 公开 API (兼容)

---

## 8. 不在 v0.4.1 范围 (deferred)

- **v0.4.2**: 拿到 vivo 崩溃日志后, 基于 stack trace 开 plan 修 FGS 闪退
- **v0.4.3 (可选)**: 装 KVM emulator + Anjos2/mcp-android-emulator, 跑 connectedAndroidTest 覆盖 AOSP 类回归
- **v0.5**: Compose UI 测试 + 文件持久化日志 + 可能的 Room 持久化 history

---

## 9. 测试设备 (用户已提供)

- 手机型号: vivo Y78
- 处理器: 天玑 7020 (ARM64 / AArch64)
- Android: 64 位
- 测试 APK: `arm64-v8a` split

---

## 10. 下一步

1. ✅ Spec 写盘 + commit (本文件)
2. ⏭️ 用户 review spec
3. ⏭️ 调 superpowers:writing-plans 写实施 plan
4. ⏭️ subagent-driven-development 执行 plan (TDD, 每个 task RED→GREEN→commit)
5. ⏭️ 用户跑 v0.4.1 on vivo Y78, 拿崩溃日志 → 开 v0.4.2
