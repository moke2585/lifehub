# ADR-0006：外部插件包与安装形态

- 状态：已接受
- 日期：2026-11-23
- 责任人：项目维护者
- 部分替代：[ADR-0001](0001-plugin-form.md)

## 背景

ADR-0001 的首版形态是“随 APK 内置的声明式插件包 + 第一方 Kotlin action”，并明确不实现远程插件、插件市场与插件自更新。现在需要：

- 插件在独立 GitHub 仓库发布，不依赖宿主发版；
- 用户从插件市场或本地文件安装插件；
- 保留离线安装能力；
- 继续坚持“一切皆插件”，页面由宿主渲染。

现有代码的插件页面已经是声明式 schema，插件实现与 `com.lifehub.app.*` 没有直接依赖，`PluginPackageSource` 也预留了 ZIP 包来源。因此可以在不推翻页面契约的前提下扩展安装形态。

## 决策

采用双轨插件形态：

### 1. `.lhplugin` ZIP（声明式插件）

- 包内容：`manifest.json`、`pages/*.json`、`data/` 静态资源、`manifest.sig`。
- 不包含 dex、jar、native `.so`、JavaScript、WASM。
- 只使用宿主已注册的 action 与 data source；数据写入宿主 `PluginStorage`。
- 新增 `PluginSource.EXTERNAL_ZIP` 与 `ZipPluginPackageSource`。
- 信任锚：ADR-0005 定义的 ECDSA P-256 detached signature，同一 `pluginId` 必须保持签名者连续。
- 适用于第三方轻量插件：记录、清单、提醒、静态内容、简单计算。

### 2. 插件 APK（代码插件）

- 独立的 headless Android App，不提供 launcher 图标。
- 页面仍由宿主 `PluginPageRenderer` 渲染；插件通过 provider 提供 manifest、页面 JSON、action 与 data。
- 插件拥有自己的数据库；宿主备份通过 `exportBackup/importBackup` 路由。
- 新增 `PluginSource.EXTERNAL_APK`。
- 信任锚：APK v2+ 签名、同 `pluginId` 签名者连续性、versionCode 单调。
- 适用于第一方复杂插件、神经网络、需要原生能力的插件。

### 3. 第一方插件迁移顺序

- 先将记账、任务清单、旅迹拆成独立 Gradle 模块，继续随宿主内置。
- 任务清单作为 APK PoC；记账与旅迹在备份桥、Binder 大 payload、地图数据方案验证前继续内置。
- 插件 ID、集合名、`dataSchemaVersion` 保持不变，保证备份兼容。

### 4. 注册表与信任

- `lifehub-plugins` 仓库只提供发现与元数据（版本、下载地址、SHA-256、签名者指纹、兼容范围、能力）。
- 信任根是包签名与校验和，不是索引仓库。
- 不实现宿主内 dex/so 动态加载。

## 理由

- ZIP 声明式延续现有 schema、存储、备份与能力模型，安全边界清晰，离线导入最简单。
- APK 是 Android 上运行现有 Kotlin 业务代码的标准形态，具备独立 UID/进程沙箱和成熟签名体系。
- 双轨允许轻量插件快速扩张，同时不必把账本、旅迹等复杂逻辑重写成宿主通用能力。
- 不引入动态代码加载，避免 Android 14 只读限制、类加载冲突和商店合规风险。

## 后果

- 宿主需要新增包源、信任策略、外部插件分发器、注册表缓存与市场 UI。
- APK 插件的数据在插件自有数据库，宿主备份必须增加导入/导出桥；未实现前不得迁移有数据的插件。
- APK 插件的能力由 Android 沙箱约束，manifest 能力声明降级为“告知与同意”，宿主的 capability broker 不能跨进程强制执行。
- Binder 事务约 1MB 限制；旅迹等大 payload 需要文件/管道，或继续内置。
- 包可见性需要自定义 intent + `<queries>`，避免申请 `QUERY_ALL_PACKAGES`。

## 备选方案

| 方案 | 结论 |
|---|---|
| 只做 ZIP | 无法承载现有 Kotlin 业务逻辑，除非重写为宿主通用能力 |
| 只做 APK | 轻量插件也要走系统安装，离线体验和体积成本高 |
| 动态 dex/so | 不采用：Android 14 限制、安全与合规风险 |
| ZIP + WASM | 延后：可作为 ZIP v2 的可选执行层，另行 ADR |

## 变更方式

新增 ADR 替代本记录，并同步更新 `plugin-market-plan.md`、`plugin-development.md`、`backup-format.md` 与发布检查表。
