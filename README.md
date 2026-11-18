# Limette TV

> 面向 Android TV / 飞牛 OS（FnOS）的纯本地 NAS 播放器
> A purely local NAS video player for Android TV / FnOS

[中文](#中文) · [English](#english)

---

## 中文

### 这是什么

Limette TV 是一款运行在 Android TV / 飞牛 OS 设备上的本地视频播放器。它直接读取局域网内 NAS 的 **SMB / WebDAV** 共享，或用**飞牛影视账号**登录读取已刮削的影视库，扫描出的视频在首页平铺展示并直接播放，同时记住观看进度。

- 没有在线片源、云端刮削或推荐服务；飞牛账号仅用于局域网内读取本机 NAS 的影视库；
- 所有网络请求都发生在局域网 / 本机（飞牛影视登录、媒体列表、海报、HLS 播放均直连你的 NAS）；
- 视频字节不经过任何外部服务器。

本项目基于开源项目 [lomenTV-VDS](https://github.com/jia070310/lomenTV-VDS) 改造：保留其播放器与观看记录能力，移除直播、热门、TMDB 刮削、网盘与在线流等功能，并新增多源 SMB、WebDAV 与飞牛影视账号支持。详见文末致谢。

| 项目 | 值 |
| --- | --- |
| 应用名 / 包名 | `Limette TV` / `com.limette.tv` |
| 版本 | `1.0.0`（`versionCode = 10023`） |
| 系统要求 | Android 5.0（API 21）起，目标 API 34 |

### 主要功能

**多源媒体库**

- 支持三类媒体源：**SMB2/3**（服务器地址、共享名、根目录、域、账号密码、多个子目录）、**WebDAV**（协议、主机、端口、路径、账号密码）、**飞牛影视**（服务地址、端口默认 5666、账号密码）。
- 任意多个媒体源可**同时启用**，首页聚合展示所有已启用源的视频；每个源可单独启用 / 停用 / 编辑 / 删除。
- 删除媒体源会同步清理其索引，不留孤儿记录；视频路径以 `<sourceId>::<相对路径>` 存储，避免不同源的同名文件互相覆盖。

**首页**

- 左上角为圆形图标 + `Limette TV`；无开屏页，启动首帧直接是首页。
- 「全部视频」网格强制直接列出所有媒体源的视频文件，无需先选库、无需先刮削。
- 顶部保留「继续观看」，底部为居中悬浮的半透明胶囊 DOCK：首页 / 媒体库 / 设置。
- **封面不会出现黑图**，按优先级取图：飞牛影视缩略图 → 视频同目录下的本地封面（`poster.jpg`、`cover.jpg`、`folder.jpg`、`fanart.jpg` 等）→ 带片名与圆形 Logo 的品牌渐变卡片。加载中与加载失败同样显示渐变卡片，不会闪黑。

**播放（原生能力完整保留）**

- 记忆续播、断点续播提示、快进 / 快退时长、自动跳过片头片尾、自动下一集。
- 音轨 / 字幕选择，外挂字幕自动匹配（`.srt` / `.ass` / `.ssa` / `.vtt`）。
- 观看历史、最近观看、收藏、详情页、本地搜索。

**SMB 播放代理**

ExoPlayer 无法直接读取 `smb://`，因此内置一个只监听 `127.0.0.1:18999` 的本地 HTTP 代理：

- 路径格式 `http://127.0.0.1:18999/smb/<sourceId>/<相对路径>`；
- 支持 HTTP `Range`，返回 `206 Partial Content` + `Content-Range`；
- Range 起点直接映射为 SMB2 READ 的 `fileOffset`，拖动进度 / 记忆续播不会顺序读取并丢弃前面的数据；
- SMB 文件句柄在流关闭时释放，不随播放次数累积；
- 视频字节不经过任何外部服务器。

### 飞牛影视媒体源（原生账号登录）

在「媒体库 → 添加媒体源 → 飞牛影视」中填写 NAS 上「影视」服务的地址、端口（默认 `5666`）与账号密码即可。应用会登录并读取飞牛已刮削的影视库：电影直接入库，电视剧自动展开为分集，海报沿用 NAS 上刮削好的图片。飞牛源可与 SMB / WebDAV 同时启用，首页统一聚合展示。

直连你的 NAS，使用飞牛影视（fnOS Trimedia）的只读 HTTP 接口：

| 用途 | 接口 |
| --- | --- |
| 登录 | `POST /v/api/v1/login`，body `{"username":"...","password":"...","app_name":"trimemedia-web"}`，取 token |
| 媒体列表 | `POST /v/api/v1/item/list`，Header `Authorization: <token>`，取 `data.list[]`；电视剧再取 `GET /v/api/v1/episode/list/<guid>` 展开分集 |
| 图片 | `GET /v/api/v1/sys/img/<poster>?w=400`，同样带 `Authorization` |
| 播放信息 | `POST /v/api/v1/play/info` → `GET /v/api/v1/stream/list/<guid>` → `POST /v/api/v1/play/quality` → `POST /v/api/v1/play/play`，得到有时效的 `play_link`（HLS m3u8） |

由于 `play_link` 有时效，应用在**每次点击播放时**动态换取真实地址并附上 token，交给 Media3 ExoPlayer 的 HLS 模块播放。飞牛源生成的所有视频同时会参与「文件名 / 路径 → 海报 URL」索引，让本地 SMB / WebDAV 视频也能复用飞牛已刮削的海报；未添加飞牛源时该步自动跳过。飞牛图片 Token 与 WebDAV 本地封面 Basic 认证，均由全局 Coil ImageLoader 按 host 自动注入。

### 构建与运行

需要 JDK 17 与 Android SDK Platform 34（AGP 8.2.0 / Gradle 8.4 / Kotlin 1.9.20）。

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

release 签名配置未包含在内（上游仓库的 keystore 未随源码提供）；如需正式签名，请在 `app/build.gradle.kts` 中自行添加 `signingConfigs`。

首次使用：**媒体库 → 添加媒体源 → 选择 SMB / WebDAV / 飞牛影视 → 填写信息 → 保存 → 重新扫描 → 返回首页**，即可看到全部视频。

### 技术栈

Kotlin、Jetpack Compose（TV Material3 与 Material3 嵌套主题）、Hilt、Room、DataStore + Gson、OkHttp、Coil、Media3 / ExoPlayer、Jellyfin FFmpeg decoder、**smbj 0.11.5**、**NanoHTTPD 2.3.1**。

### 目录结构

```
app/src/main/java/com/limette/tv/
├── data/       # Room 数据库、媒体源配置、WebDAV / SMB 客户端、飞牛影视索引
├── domain/     # 媒体源模型、播放器、观看历史、封面与播放地址解析
├── ui/         # 首页 / 媒体库 / 搜索 / 收藏 / 详情 / 设置 / 播放器
└── utils/      # 纯本地文件名解析
```

### 开源协议

MIT License，详见 [LICENSE](./LICENSE)。

---

## English

### What it is

Limette TV is a local video player for Android TV / FnOS devices. It reads **SMB / WebDAV** shares on a LAN NAS, or signs in with a **FnOS Trimemedia account** to read the already-scraped library, indexes the videos, lists all of them directly on the home screen, plays them, and remembers watch progress.

- No online sources, cloud scraping, or recommendation services; the FnOS account only reads your own NAS over the LAN.
- Every request stays on the LAN / on-device (FnOS login, media list, posters and HLS playback all connect directly to your NAS).
- No video byte ever passes through an external server.

It is a derivative of the open-source project [lomenTV-VDS](https://github.com/jia070310/lomenTV-VDS): the player and watch-history stack are kept, while live TV, trending, TMDB scraping, cloud-drive and online-stream features were removed, and multi-source SMB, WebDAV and FnOS Trimemedia support was added. See the closing acknowledgement.

| Item | Value |
| --- | --- |
| App name / package | `Limette TV` / `com.limette.tv` |
| Version | `1.0.0` (`versionCode = 10023`) |
| Android | min SDK 21 (Android 5.0), target SDK 34 |

### Features

**Multi-source library** — three source types: **SMB2/3** (host, share, root path, domain, credentials, multiple sub-paths), **WebDAV** (protocol, host, port, path, credentials), and **FnOS Trimemedia** (server address, port 5666 by default, credentials). Any number of sources can be enabled at once and the home screen aggregates all of them. Sources can be toggled, edited or deleted individually; deleting one prunes its index. Paths are stored as `<sourceId>::<relativePath>` so identically named files from different sources never collide.

**Home screen** — a circular logo plus `Limette TV` at the top left, no splash screen, and an "All videos" grid that lists every video from every enabled source up front. "Continue watching", search, favorites and settings remain available. **Posters are never black**: FnOS thumbnail → local cover in the same NAS folder (`poster.jpg`, `cover.jpg`, `folder.jpg`, `fanart.jpg`, …) → a branded gradient card with the title and logo, which is also shown while loading and on failure.

**Playback** — resume / continue watching, configurable seek steps, auto-skip intro and outro, auto next episode, audio and subtitle track selection, external subtitle auto-match (`.srt` / `.ass` / `.ssa` / `.vtt`), watch history, favorites, detail view and local search.

**SMB streaming proxy** — ExoPlayer cannot read `smb://`, so a localhost-only HTTP proxy listens on `127.0.0.1:18999` at `/smb/<sourceId>/<relativePath>`. It supports HTTP `Range` with `206 Partial Content` + `Content-Range`, maps the range start directly to the SMB2 READ `fileOffset` (so seeking never downloads and discards leading gigabytes), closes SMB file handles when the stream closes, and never routes video bytes through an external server.

### FnOS Trimemedia source (native account sign-in)

Under **Library → Add source → FnOS Trimemedia**, enter the address and port (5666 by default) of the "Trimemedia" service on your NAS plus your username and password. The app signs in and reads the already-scraped library: movies are indexed directly, TV shows are expanded into individual episodes, and posters come from the artwork already scraped on the NAS. FnOS sources can be enabled alongside SMB / WebDAV and are aggregated on the same home screen.

It connects directly to your NAS using the read-only fnOS Trimemedia (5666) HTTP APIs:

| Purpose | API |
| --- | --- |
| Login | `POST /v/api/v1/login`, body `{"username":"...","password":"...","app_name":"trimemedia-web"}`, take token |
| Media list | `POST /v/api/v1/item/list`, Header `Authorization: <token>`, take `data.list[]`; TV shows then `GET /v/api/v1/episode/list/<guid>` to expand episodes |
| Image | `GET /v/api/v1/sys/img/<poster>?w=400` (same `Authorization` header) |
| Playback | `POST /v/api/v1/play/info` → `GET /v/api/v1/stream/list/<guid>` → `POST /v/api/v1/play/quality` → `POST /v/api/v1/play/play`, yielding a short-lived `play_link` (HLS m3u8) |

Because `play_link` expires, the app resolves a fresh URL with the token **on every play tap** and hands it to Media3 ExoPlayer's HLS module. Videos from a FnOS source also feed the "file name / path → poster URL" index so local SMB / WebDAV videos can reuse FnOS-scraped posters; with no FnOS source added this step is skipped. FnOS tokens and WebDAV Basic auth for local covers are injected per-host by the global Coil ImageLoader.

### Build & run

Requires JDK 17 and Android SDK Platform 34 (AGP 8.2.0 / Gradle 8.4 / Kotlin 1.9.20).

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Release signing is intentionally not configured (the upstream keystore was not distributed); add your own `signingConfigs` in `app/build.gradle.kts` if you need a signed build.

First run: **Library → Add source → pick SMB / WebDAV / FnOS Trimemedia → enter details → Save → Rescan → back to Home**, and all videos appear.

### Tech stack

Kotlin, Jetpack Compose (TV Material3 + Material3 nested themes), Hilt, Room, DataStore + Gson, OkHttp, Coil, Media3 / ExoPlayer, Jellyfin FFmpeg decoder, **smbj 0.11.5** and **NanoHTTPD 2.3.1**.

### Project layout

```
app/src/main/java/com/limette/tv/
├── data/       # Room, source preferences, WebDAV / SMB clients, FnOS index
├── domain/     # source model, player, watch history, poster & URL resolution
├── ui/         # home / library / search / favorites / detail / settings / player
└── utils/      # local-only file name parsing
```

### License

MIT License — see [LICENSE](./LICENSE).

---

## 鸣谢原作者 / Acknowledgements

**中文**：本项目是在 [jia070310](https://github.com/jia070310) 的开源项目 [**lomenTV-VDS**](https://github.com/jia070310/lomenTV-VDS) 基础上做的精简与改造。播放器、观看记录、收藏以及大量 TV 遥控器交互实现均来自原作者的工作，在此向原作者致以诚挚感谢 —— 没有这个扎实的基座，就不会有 Limette TV。原项目以 MIT License 发布，其版权声明完整保留在 [LICENSE](./LICENSE) 中。

同时感谢 [smbj](https://github.com/hierynomus/smbj)、[NanoHTTPD](https://github.com/NanoHttpd/nanohttpd)、[Media3](https://github.com/androidx/media) 等开源项目，以及 fnOSTV、fntv-electron 等飞牛影视第三方客户端提供的接口参考。

**English**: This project is a slimmed-down derivative of [**lomenTV-VDS**](https://github.com/jia070310/lomenTV-VDS) by [jia070310](https://github.com/jia070310). The player, watch history, favorites and much of the TV remote-control interaction come from the original author's work — sincere thanks for that solid foundation, without which Limette TV would not exist. The upstream project is released under the MIT License and its copyright notice is preserved verbatim in [LICENSE](./LICENSE).

Thanks also to [smbj](https://github.com/hierynomus/smbj), [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd), [Media3](https://github.com/androidx/media), and to the third-party FnOS Trimemedia clients (fnOSTV, fntv-electron and others) whose implementations informed the thumbnail API integration.
