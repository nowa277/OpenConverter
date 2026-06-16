# M1+M2+M3：原代码结构 + 分类清单

## 1. 解包流程
- DMG (170MB) → `dmg2img` → IMG (385MB, GPT + HFS+)
- 7z 抽 HFS+ → `金舟音频转换器/金舟音频转换器.app`
- `npx asar extract` → `build/original-app/`
- 目标目录：`build/original-app/` (gitignore)

## 2. 包信息
- name: `jzaudioconverter`, version: 2.1.7
- main: `dist-electron/main/index.js` (单文件 20K token, minified)
- preload: `dist-electron/preload/index.js` (79 行)
- 渲染端: `dist/index.html` + `index-b049d463.js` + `style-b988050e.css`
- UI 库: element-plus + pinia
- productName: 金舟音频转换器

## 3. IPC 架构
**只有 1 个 IPC 频道**: `process-message`（一切请求都走它）
- 内部 method: `ChangeUserInfo`, `destroyWindow`, `resize`
- preload 暴露 `window.api = { send, receive, resize, openExternal, logout, destroyCurrentWindow }`

## 4. **关键发现：解密算法不在 JS 里**
原版用 `@inigolabs/ffi-napi` 调原生 `.dylib` (macOS)：
- `__jaaCreateAnalysis`
- `__jaaVerifyFile`  ← 验证文件
- `__jaaSetFileName`
- `__jaaSetMaskData`
- `__jaaParse`       ← 实际解密
- `__jaaGetOrignalFormat` ← 取原格式
- `__jaaGetLastError`
- `__jaaInterrupt`
- `__jaaReleaseAnalysis`

**闭源 dylib，没有源码。Linux 没这库，必须纯 JS 重写 NCM/QMC0/KGM/KWM。**

## 5. ffmpeg 调用
- 打包 7za 7x.sh 在 dist-electron 里（用于解压 .ncm 内部的 zip 头）
- `ffmpeg` (spawn) — 转格式
- `ffprobe` (spawn) — 取元数据

## 6. macOS-only 调用
- `system_profiler -xml` — 系统信息
- `mdfind` — Spotlight 搜索
- `uname -m` — 架构
- `chmod`, `unzip`, `open` (macOS `open` 命令)
- 依赖 `macos-version`, `plist` — 全删

## 7. 商业元素清单（必删）
- **字段**: vip_type, use_num, trialData, LoadUrl, loadUrl, doLoadInfo, adInfo, package_validity, ol_token, is_online, appStoreProductConfig, permanentMemberId, subscriptionId, role, receiptData, webviewUrl
- **中文**: 永久, 试用
- **资源**: ic_vip_sure, ic_vip_symbol, pic_probg_left, red-bg-right
- **远程端点**:
  - https://app.jiangxiatech.com/ (主 API)
  - https://app.onlinedo.cn/ (dev)
  - https://buy.itunes.apple.com/verifyReceipt (iOS 校验)
  - https://sandbox.itunes.apple.com/verifyReceipt
  - https://wbman.jiangxiatech.com/ / https://wbman.onlinedo.cn/
  - http://wss.jiangxiatech.com/ / http://wss.onlinedo.cn/ (WSS)
  - http://logs.jiangxiatech.com/ / http://logs.onlinedo.cn/ (telemetry)
  - https://www.callmysoft.com/audioconvert/jiaocheng (支持页)
- **依赖**: @sentry/electron, electron-updater

## 8. electron-store 全部 key
商业相关 (删): appStoreProductConfig, doLoadInfo, m_id, ol_token, packageName, receiptData, role, session_id, token, trialData, u_id, updateInfo, userInfo, webviewUrl, versionInfo, sysInfo, appStartTime
保留: customDir, allowAnalytics, installed, version

## 9. 分类清单

### 必须做 (核心)
- 音频转换主流程 (ffmpeg)
- **NCM 解密** (纯 JS 重写, 必须用 ncmdump/ncm-py 字节对拍)
- **QMC0/KGM/KWM 解密** (纯 JS, 但**无样本**, 只能算法实现 + 标"未实测")
- IPC 通信 (用 process-message 单频道)
- 文件选择 + 输出目录
- electron-store 配置 (只存非商业字段)
- 美观的 spotify 风 UI

### 必须删 (商业元素, 无例外)
- 所有 VIP 字段、ad 字段、role、use_num、trialData
- 所有远程 API 调用 (除了可能要保留一个 update check, 改成 GitHub releases)
- iOS/Mac 收据验证
- Sentry, electron-updater, 商业化 telemetry
- 角标/ad 资源 (ic_vip_*.svg, pic_probg_*.png, red-bg-right-*.png)
- macOS-version, plist 依赖
- 试用次数限制逻辑 (即使 use_num=9999, 整个限流逻辑删除)

### macOS 专属 (改 Linux 等价)
- `system_profiler -xml` → `cat /etc/os-release` + `uname -a`
- `mdfind` → `find` (或者直接列目录)
- `open` → `xdg-open`
- @inigolabs/ffi-napi → **完全删除** (重写为纯 JS)
- `~/Library/...` 路径 → `~/.config/jzc/` 或 `~/Music/jzc/`
- 7za/7x.sh (用于 NCM 内部 zip 头) — 保留, Linux 也有 7z

### UI (重做, spotify 风)
- 不用原始丑 UI
- 不用 element-plus (太大, 用轻量 stack: pure CSS + Vue 3)
- 加品牌色、卡片化布局、转换进度条、文件拖放
- 不要播放控件 (本次只做转换)
