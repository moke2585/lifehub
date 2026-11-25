# 生活聚合（LifeHub）

一个“一切皆插件”的 Android 生活聚合应用。宿主负责主题、插件注册、页面渲染、加密备份与安全隔离；功能以插件形式接入，目前包含记账、任务清单与旅迹。

- 最低 Android 8.0（API 26），目标 API 36，Debug 额外支持 x86_64，Release 仅 `arm64-v8a`
- 本地优先：数据保存在应用私有目录；1.1.0 插件市场仅申请网络权限用于索引刷新与插件下载
- 页面声明式：插件返回页面 JSON，宿主用 Jetpack Compose 渲染
- 三种插件形态：内置声明式插件、`.lhplugin` 离线包、独立 APK 插件（headless provider）

[![Release](https://github.com/moke2585/lifehub/actions/workflows/release.yml/badge.svg)](https://github.com/moke2585/lifehub/actions/workflows/release.yml)

## 功能

| 插件 | 版本 | 说明 |
|---|---|---|
| 记账 `com.lifehub.plugin.ledger` | 1.7.0 | 收支记录、日/月/年统计、图表下钻、月度与分类预算、结转、借还台账、自定义分类、首页小组件；支持独立 APK 插件替代内置 |
| 任务清单 `com.lifehub.plugin.memo` | 1.1.0 | 截止日期、1–5 级优先级、紧急指数、Emoji 图标、日常任务倍率、回收站、搜索与置顶；以独立 APK 插件分发 |
| 旅迹 `com.lifehub.plugin.travel` | 3.0.0 | 中国省/市/区县逐级点亮、五星地区评分、旅行评论、评分榜、地区搜索、高清地图导出 |
| 插件市场 | 宿主 1.1.0 | 从签名索引发现插件，应用内下载并安装 `.lhplugin` / APK；支持缓存、离线标识、签名者与能力展示 |

宿主通用能力：插件发现与启用授权、能力代理、按插件隔离的 SQLite 存储、加密备份导入导出、全局搜索、主页小组件、异常自动隔离、浅色/深色/动态取色与减少动效。

## 仓库布局

宿主与每个插件都是同级、独立的 Git 仓库：

| 仓库 | 说明 |
|---|---|
| [lifehub](https://github.com/moke2585/lifehub) | 宿主、插件 SDK、构建与文档（本仓库） |
| [lifehub-plugin-ledger](https://github.com/moke2585/lifehub-plugin-ledger) | 记账插件 |
| [lifehub-plugin-memo](https://github.com/moke2585/lifehub-plugin-memo) | 任务清单插件与 headless APK |
| [lifehub-plugin-travel](https://github.com/moke2585/lifehub-plugin-travel) | 旅迹插件与地图资产 |
| [lifehub-plugin-index](https://github.com/moke2585/lifehub-plugin-index) | 插件索引 `registry.json` 与签名 |

`settings.gradle.kts` 通过 `projectDir` 把三个同级插件目录纳入同一次 Gradle 构建，Git 历史互不包含；单独签出宿主无法直接构建。

## 快速开始

```powershell
git clone https://github.com/moke2585/lifehub.git
git clone https://github.com/moke2585/lifehub-plugin-ledger.git
git clone https://github.com/moke2585/lifehub-plugin-memo.git
git clone https://github.com/moke2585/lifehub-plugin-travel.git

cd lifehub
# local.properties（不要提交）：sdk.dir=C\:\\Android\\Sdk
$env:JAVA_HOME="C:\path\to\jdk-17"
.\gradlew.bat :app:assembleDebug
```

环境要求：

- JDK 17
- Android SDK：`platforms;android-36`、`build-tools;35.0.0` 或更高
- 本机 SDK 路径写入 `local.properties`（已忽略）

调试 APK 输出在 `app/build/outputs/apk/debug/`。工程位于含中文的路径时，`gradle.properties` 已设置 `android.overridePathCheck=true` 并在 Gradle 进程内编译 Kotlin。

## 构建与测试

```powershell
# 单元测试 + 插件校验 + 模块边界
.\gradlew.bat :app:testDebugUnitTest :plugins:ledger:testDebugUnitTest :plugins:memo:testDebugUnitTest :plugins:travel:testDebugUnitTest :plugin-api:test :plugin-runtime:test :dev-harness:test :dev-harness:validatePlugins verifyPluginModuleBoundaries

# lint
.\gradlew.bat :app:lintDebug :plugins:ledger:lintDebug :plugins:memo:lintDebug :plugins:travel:lintDebug :plugins:memo-apk:lintDebug

# 调试 APK（含任务清单插件 APK）
.\gradlew.bat assembleDebug :plugins:memo-apk:assembleDebug
```

当前基线（2026-11-24）：282 项单元测试全部通过（app 119、ledger 35、memo 27、travel 12、plugin-api 49、plugin-runtime 30、dev-harness 10）；`validatePlugins` 输出 `packages=5 ok=5`；lint 无错误。

## 发布

签名参数只从 Gradle 属性或环境变量读取，私钥与口令不入库：

```properties
# ~/.gradle/gradle.properties
lifehub.release.storeFile=D:/keys/lifehub-release.jks
lifehub.release.storePassword=...
lifehub.release.keyAlias=...
lifehub.release.keyPassword=...
```

```powershell
.\gradlew.bat :app:checkReleasePrerequisites -PlifehubRequireReleaseSigning=true :app:assembleRelease
```

产物：

- 宿主：`app/build/outputs/apk/release/app-release.apk`
- 任务清单插件：在 `lifehub-plugin-memo` 推送 `v*` 标签，由该仓库的 Release 工作流构建、签名并上传 APK、`.sha256` 与 `plugin-release.json`

CI：宿主 [release.yml](.github/workflows/release.yml) 负责宿主 APK；memo 仓库的 release 工作流负责插件 APK；索引仓库 `scan.yml` 扫描 Release 开 PR，合并后 `sign.yml` 在 `index-signing` 环境审批后签名 `registry.json`。宿主与 memo 仓库各需配置 `LIFEHUB_KEYSTORE_BASE64`、`LIFEHUB_RELEASE_STORE_PASSWORD`、`LIFEHUB_RELEASE_KEY_ALIAS`、`LIFEHUB_RELEASE_KEY_PASSWORD`。

## 架构

### 模块

| 模块 | 作用 |
|---|---|
| `:app` | 宿主：Compose 主题、插件注册表、插件列表/详情/页面路由、备份、错误页 |
| `:plugin-api` | 插件 SDK 契约：manifest、描述符、状态、action、页面数据、APK 协议、备份快照 |
| `:plugin-runtime` | `PluginModule` 契约、宿主侧存储工具、`.lhplugin` 读取与验签 |
| `:plugins:ledger` | 记账插件：Kotlin handler + manifest/pages/assets |
| `:plugins:ledger-apk` | 记账 headless APK：provider + 自有 SQLite（阶段 1 与内置并存） |
| `:plugins:memo` | 任务清单 handler 与声明式页面（APK 插件的共享库） |
| `:plugins:memo-apk` | 任务清单 headless APK：provider + 自有 SQLite |
| `:plugins:travel` | 旅迹插件：Kotlin handler、地图资产与高清导出 |
| `:plugin-testing` | 插件单元测试使用的内存存储 |
| `:dev-harness` | 插件包校验与生命周期模拟工具 |

### 插件形态

- **内置声明式**：插件代码编译进宿主，页面与 manifest 放在 `src/main/assets/plugins/<pluginId>/`；宿主启动时发现，启用前显式授权能力。
- **`.lhplugin` 离线包**：ZIP 内含 manifest、页面、资源与 `manifest.sig`（ECDSA P-256）；宿主校验结构、路径、限额与签名后原子安装。规范见 [`.lhplugin` v1](docs/lhplugin-v1.md)。
- **APK 插件**：独立应用通过自定义 intent action 暴露 `ContentProvider`，宿主经 `ContentResolver.call` 获取 manifest/page 并分发 action/data；插件使用自己的 SQLite。宿主不加载动态 dex/so。契约见 [APK 插件契约 v1](docs/apk-plugin-v1.md)。

### 数据与安全

- 内置与 `.lhplugin` 插件共用宿主 `PluginStorage`（每插件独立数据库、集合/记录限额、无 SQL 透传）。
- 备份格式 v1 使用口令加密；APK 插件快照写入 `externalPayload`，旧备份的集合数据可自动迁移，见 [备份档案格式 v1](docs/backup-format.md)。
- 内置插件与外部包分别采用内置信任、签名者指纹与 APK 安装来源策略；连续异常会停用插件并撤销能力，不删除用户数据。见 [ADR-0005](docs/decisions/0005-signing-upgrade-and-failure.md)。

## 插件开发

- 开发指南：[docs/plugin-development.md](docs/plugin-development.md)
- 页面 schema：[docs/plugin-schema-v1.md](docs/plugin-schema-v1.md)
- 主页小组件：[docs/home-widget-schema-v1.md](docs/home-widget-schema-v1.md)
- 最小模板：[samples/com.lifehub.plugin.template](samples/com.lifehub.plugin.template)
- 校验全部内置插件与示例：

```powershell
.\gradlew.bat :dev-harness:validatePlugins
```

- 校验外部 `.lhplugin`（结构与签名）：

```powershell
.\gradlew.bat :dev-harness:run --args="path\to\plugin.lhplugin"
```

新增第一方插件：在与宿主同级的 `lifehub-plugin-<name>/` 建独立仓库，在 `settings.gradle.kts` 中以 `projectDir` 纳入构建，加入 `FirstPartyPluginHandlers.modules`，资产放到模块的 `src/main/assets/plugins/<pluginId>/`。

## 文档

| 文档 | 内容 |
|---|---|
| [design.md](design.md) | 产品与技术设计 |
| [plan.md](plan.md) | 实施计划 |
| [docs/plugin-development.md](docs/plugin-development.md) | 插件开发指南 |
| [docs/plugin-schema-v1.md](docs/plugin-schema-v1.md) | 页面 schema v1 |
| [docs/home-widget-schema-v1.md](docs/home-widget-schema-v1.md) | 主页小组件 schema v1 |
| [docs/lhplugin-v1.md](docs/lhplugin-v1.md) | `.lhplugin` v1 规范 |
| [docs/apk-plugin-v1.md](docs/apk-plugin-v1.md) | APK 插件契约 v1 |
| [docs/backup-format.md](docs/backup-format.md) | 备份档案格式 v1 |
| [docs/plugin-index-v1.md](docs/plugin-index-v1.md) | 插件索引、签名与缓存规则 |
| [docs/privacy.md](docs/privacy.md) | 隐私、数据与能力说明 |
| [docs/release-checklist.md](docs/release-checklist.md) | 发布检查表与真机回归矩阵 |
| [docs/plugin-market-plan.md](docs/plugin-market-plan.md) | 插件市场实施计划与阶段状态 |
| [docs/decisions](docs/decisions/README.md) | 架构决策记录（ADR） |

## 环境与已知限制

- 仅支持 API 26 及以上；Release 仅打包 `arm64-v8a`。
- 1.0.0 无网络权限；1.1.0 插件市场仅访问索引与 GitHub Release 下载地址，断网时回退缓存/内置快照。
- 宿主 Release 仅打包 `arm64-v8a`。
- APK 插件的 Binder 单次载荷上限为 512 KiB；旅迹地图资产较大，暂保持内置声明式形态。记账内置数据迁移超过上限时自动回退内置实现，旧数据库保留。
- 安装记账 APK 后，宿主首次启动会把内置四个集合迁移到插件 SQLite；迁移成功前继续使用内置数据，避免空账本。卸载 APK 会删除插件数据库并回退到迁移前的内置数据。
- 卸载 APK 插件会删除其数据库，需要时通过备份恢复；APK 插件只能通过系统安装器或 adb 安装。
- APK provider 当前按调用方包名校验，尚未启用签名级权限与证书固定。
- Debug 包名为 `com.lifehub.app.debug`，Release 为 `com.lifehub.app`，可并存安装。

## 开发进展

- P0–P9 已完成：技术形态与 ADR、插件契约与注册表、页面 schema 与渲染器、能力代理与私有存储、迁移与加密备份、第一方插件、安全隔离、dev-harness、发布流程。
- 阶段 A/B/C 已完成：ADR 与备份 fixture、`plugin-runtime` 与三插件仓库拆分、`.lhplugin` 离线导入。
- 阶段 D 已完成：任务清单迁移为独立 headless APK，provider 发现、Binder action/data、自有 SQLite、备份迁移与未安装占位均已在真机验证。
- 阶段 E 已完成：`lifehub-plugin-index` 签名索引、市场列表/详情、ZIP 与 APK 应用内安装、索引 ETag 缓存与离线回退、memo 独立 Release 与索引扫描/签名 CI。

逐版本变更与真机回归记录见 [发布检查表](docs/release-checklist.md)。

## 设计参考与素材授权

- 记账交互参考 [Ivy Wallet](https://github.com/Ivy-Apps/ivy-wallet)、[Cashew](https://github.com/jameskokoska/Cashew)、[My Expenses](https://github.com/mtotschnig/MyExpenses)，仅参考交互，未复制代码或资源。
- 旅迹边界来自 [阿里云 DataV GeoAtlas](https://geo.datav.aliyun.com/areas_v3/bound/) 与 [OpenStreetMap](https://www.openstreetmap.org/copyright)（© OpenStreetMap contributors，ODbL），仅离线使用边界坐标。
- Material 3 使用 [Jetpack Compose Material3](https://developer.android.com/jetpack/compose/designsystems/material3)、[Material Symbols](https://fonts.google.com/icons) 与 [Material Color Utilities](https://github.com/material-foundation/material-color-utilities)（Apache 2.0）。
- 主页壁纸、插件页背景与空态插画由 SiliconFlow 文生图生成，作为静态素材内置，运行时无生成能力。
