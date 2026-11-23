# LifeHub 插件市场与安装形态实施计划

- 状态：已批准，阶段 A/B/C 已完成，阶段 D 待开始
- 日期：2026-11-23
- 目标版本：后续版本（不进入当前首版范围）
- 关联文档：[ADR-0001](decisions/0001-plugin-form.md)、[ADR-0002](decisions/0002-distribution-and-compliance.md)、[ADR-0005](decisions/0005-signing-upgrade-and-failure.md)、[备份格式 v1](backup-format.md)、[插件开发指南](plugin-development.md)

## 1. 目标

1. 插件可以从 GitHub 独立发布、被 App 发现并安装，不再只能随宿主发版。
2. 支持两种插件形态：
   - `.lhplugin` ZIP：声明式插件，默认形态，离线导入最简单。
   - 插件 APK：带 Kotlin 代码的插件，适合复杂逻辑、神经网络或原生能力。
3. 支持完全离线安装：用户拿到安装包后，在 App 内导入即可完成安装与启用。
4. 保留“一切皆插件”的架构：页面仍由宿主渲染，插件通过 manifest、schema 和 action/data 契约接入。
5. 现有备份 v1 在后续版本中仍可导入；不因插件形态变化静默丢失用户数据。

## 2. 非目标

- 不在本阶段实现脚本运行时（JS/Lua）或 WASM 执行引擎。
- 不做宿主内动态加载 dex/so。
- 不做插件间数据总线、云同步、远程代码执行。
- 不改变现有三个插件的业务行为与数据 schema。
- 不一次性把三个第一方插件都改成 APK。

## 3. 关键决策（待评审确认）

| 编号 | 决策 | 建议 |
|---|---|---|
| D1 | 双轨形态 | ZIP 面向声明式/第三方轻量插件；APK 面向带代码的第一方/可信插件 |
| D2 | ZIP v1 是否带代码 | 不带。纯声明式；`logic.wasm` 留到 ZIP v2 |
| D3 | 第一方三插件 | 先拆成独立模块并继续内置，不立即 APK 化 |
| D4 | APK PoC 选型 | 任务清单（数据量小、CRUD 清晰） |
| D5 | APK 数据归属 | 插件自有数据库，提供 `exportBackup/importBackup` |
| D6 | 备份兼容 | 冻结 v1 读取器；插件 ID、集合名、`dataSchemaVersion` 保持稳定 |
| D7 | 注册表托管 | `lifehub-plugins` 仓库 + 静态 `registry.json`，App 缓存，不依赖 GitHub API |
| D8 | 包可见性 | 自定义 intent action + `<queries>`，避免 `QUERY_ALL_PACKAGES` |

## 4. 当前基线

### 4.1 拆分前代码耦合（低）

- `LedgerPlugin`、`MemoPlugin`、`TravelPlugin` 不引用 `com.lifehub.app.*` 内部类，只依赖 `plugin-api`。
- Travel 额外依赖 Android `Bitmap/Canvas` 做地图导出，需要 Android library；Ledger/Memo 可为纯 Kotlin。
- 页面完全由 manifest + schema v1 JSON 描述，宿主统一渲染。
- 主要耦合点：
  1. `FirstPartyPluginHandlers` 硬编码聚合三个插件的 handler。
  2. 插件资产位于 `app/src/main/assets/plugins`。
  3. `PluginSource` 只有 `BUILT_IN`。
  4. `PluginTrustPolicy` 只信任 `APK_SIGNED`。
  5. 存储、备份、能力代理假设 handler 与宿主同进程。

### 4.2 已有的可扩展点

- `PluginPackageSource` 已抽象包来源，注释明确预留 ZIP 实现。
- `dev-harness` 已能校验插件目录、manifest、页面 schema、路径安全与大小限制。
- manifest 已包含版本、宿主兼容范围、插件 SDK、能力、页面、主页小组件、搜索与数据 schema。
- 备份格式已有 `formatVersion`、`containerVersion`、per-plugin `dataSchemaVersion` 和严格校验。

### 4.3 治理约束

当前 ADR-0001、ADR-0002 与 `scope-freeze` 明确禁止插件市场、远程插件和第三方代码分发。本计划实施前必须：

1. 新增 ADR-0006：外部插件包与安装形态（部分替代 ADR-0001）。
2. 新增 ADR-0007：第三方分发与商店合规（替代 ADR-0002 的相关限制）。
3. 更新 `scope-freeze`，明确新增范围、信任模型与数据语义。
4. 发行负责人按当期 Google Play 政策复核远程插件、第三方代码和 APK 分发。

## 5. 目标架构

```text
GitHub 插件仓库 ──Release──► .lhplugin / APK
        │                         │
        └──CI 更新──► lifehub-plugins/registry.json
                                  │
                                  ▼
                         LifeHub 插件市场（缓存 + 离线）
                                  │
                    ┌─────────────┴─────────────┐
                    ▼                           ▼
             ZIP 导入/解压/验签             APK 安装/发现/校验
                    │                           │
                    ▼                           ▼
            PluginPackageSource         ExternalPluginPackageSource
                    │                           │
                    └─────────────┬─────────────┘
                                  ▼
                           PluginRegistry
                                  │
                 ┌────────────────┴────────────────┐
                 ▼                                 ▼
        宿主 PluginStorage                 插件自有存储 + provider
                 │                                 │
                 └────────────► 页面渲染 / action / data
```

### 5.1 模块拆分

```text
lifehub/                        宿主仓库
├── :plugin-api                 纯 Kotlin 契约
├── :plugin-runtime             包源、注册表、信任策略、分发器、外部插件适配
├── :app                        宿主 UI + 模块聚合
└── :dev-harness                ZIP/目录校验与签名校验

lifehub-plugin-ledger/          独立仓库：纯 Kotlin + manifest/pages/assets
lifehub-plugin-memo/            独立仓库：纯 Kotlin
lifehub-plugin-travel/          独立仓库：Android library
```

宿主与插件仓库同级，Git 历史互不包含；宿主通过 `settings.gradle.kts` 的 `projectDir` 把插件纳入同一次 Gradle 构建。统一插件模块入口：

```kotlin
interface PluginModule {
    val pluginId: String
    val actionHandlers: Map<String, PluginActionHandler>
    val dataHandlers: Map<String, PluginPageDataHandler>
}
```

`FirstPartyPluginHandlers` 改为模块列表聚合。独立 GitHub 仓库时，将 `plugin-api` 发布到 GitHub Packages/JitPack，插件仓库依赖固定版本。

### 5.2 `.lhplugin` ZIP v1

```text
com.example.water/
├── manifest.json
├── pages/
│   └── home.json
├── data/                 # 可选只读资源
└── manifest.sig          # ECDSA P-256，覆盖文件清单
```

- 不包含 dex、jar、so、JS、WASM。
- 使用宿主已注册的 action 与 data source。
- 数据写入宿主 `PluginStorage`，沿用现有备份/恢复。
- 新增 `PluginSource.EXTERNAL_ZIP`。
- 新增 `ZipPluginPackageSource`，实现现有 `PluginPackageSource`。
- 信任等级新增 `ZIP_SIGNED`；沿用 ADR-0005 的 ECDSA P-256 与同 ID 签名者连续性。

### 5.3 插件 APK

- 无 launcher 的 headless App。
- 暴露 `ContentProvider` 或绑定 Service，提供：
  - `manifest()`：返回 manifest JSON
  - `page(pageId)`：返回页面 JSON
  - `action(requestJson)`：返回 `PluginActionResult`
  - `data(requestJson)`：返回 `PluginPageDataResult`
  - `exportBackup()` / `importBackup(dataJson)`
- 页面仍由宿主 `PluginPageRenderer` 渲染，插件不写 Compose。
- 插件自有 SQLite；宿主备份通过 provider 路由。
- 发现方式：插件声明自定义 action，宿主在 `<queries>` 中声明同名 intent；避免 `QUERY_ALL_PACKAGES`。
- 信任：APK v2+ 签名、同 `pluginId` 签名连续性、versionCode 不降级。
- 大 payload（如旅迹地图）走 `openFile`/临时文件/管道，避免 Binder 约 1MB 限制；旅迹在解决前继续内置。

### 5.4 注册表

`lifehub-plugins` 仓库维护静态索引：

```json
{
  "schema": 1,
  "plugins": [{
    "pluginId": "com.lifehub.plugin.memo",
    "name": "任务清单",
    "version": "1.2.0",
    "artifactType": "apk",
    "url": "https://github.com/.../releases/download/v1.2.0/memo.apk",
    "sha256": "...",
    "signerSha256": "...",
    "minHostVersion": 1,
    "pluginSdkVersion": 1,
    "capabilities": ["ui.action", "storage.private"],
    "repo": "https://github.com/..."
  }]
}
```

- App 内置一份索引快照，联网时按 ETag 更新。
- 已安装插件始终本地发现，不依赖网络。
- 每个插件仓库 CI 构建产物、计算 SHA-256、上传 Release，并更新索引仓库。

## 6. 分阶段实施

### 阶段 A：治理与兼容基线

任务：

- [x] 新增 ADR-0006/0007，更新 ADR-0001/0002 状态与 `scope-freeze`。
- [x] 冻结备份 v1：新增“当前 App 导出的 golden fixture”测试资源（仅测试数据）。
- [x] 明确插件 ID、集合名、`dataSchemaVersion` 在重构期间不得变化。
- [x] 明确缺失插件、旧 schema、APK 自有存储的备份策略。

验收：

- ADR 与范围文档通过评审。
- v1 备份 fixture 在当前代码可导入。
- 现有 164 项 app 单测、49 项 plugin-api、8 项 dev-harness 全通过。

### 阶段 B：插件模块拆分（不改变行为）

任务：

- [x] 新建 `:plugin-runtime`、`:plugins:ledger`、`:plugins:memo`、`:plugins:travel`。
- [x] 引入 `PluginModule`，替换 `FirstPartyPluginHandlers` 硬编码聚合。
- [x] 移动插件源码与 assets；Travel 使用 Android library。
- [x] 更新 `settings.gradle.kts`、依赖与 dev-harness 路径。
- [x] 增加“插件模块不得引用 `com.lifehub.app.*`”的检查（`verifyPluginModuleBoundaries`）。

验收：

- App 功能、页面、数据、备份行为与当前完全一致。
- 三个插件模块可独立编译。
- 备份 v1 fixture 仍可导入。
- 全量测试与 lint 通过。

### 阶段 C：ZIP 离线安装 PoC

任务：

- [x] 定义 `.lhplugin` v1 规范（manifest、pages、data、签名、大小限制）。
- [x] 实现 `ZipPluginPackageSource` 与 `PluginSource.EXTERNAL_ZIP`。
- [x] 扩展 `PluginTrustPolicy`：ZIP 签名、同 ID 签名连续性、未知来源拒绝。
- [x] 实现 SAF 导入：大小限制、路径校验、原子解压、失败回滚。
- [x] 扩展 `dev-harness` 校验 ZIP 与 `manifest.sig`。
- [x] 用 `template`/`hello` 做端到端 PoC。

验收：

- 飞行模式下可导入 `.lhplugin` 并启用。
- 篡改签名、路径穿越、超大文件、重复条目被拒绝。
- 启用/停用/卸载/重装、备份导出/导入正常。
- 不依赖网络即可列出已安装插件。

### 阶段 D：APK 插件 PoC（任务清单）

任务：

- [ ] 定义 APK 插件 provider 契约与 JSON 协议。
- [ ] 宿主侧实现发现、签名校验、版本兼容、action/data 适配。
- [ ] 将任务清单改为 headless APK，页面仍由宿主渲染。
- [ ] 实现 `exportBackup/importBackup` 与宿主备份路由。
- [ ] 验证包可见性、安装未知来源、卸载/重装、数据保留。
- [ ] 记录 Binder 大小与调用延迟，评估旅迹是否适合 APK。

验收：

- 离线安装 APK 后，宿主能发现并打开插件。
- 任务增删改查、搜索、排序、完成、回收站全部通过。
- 备份导出/导入包含 APK 插件数据。
- 签名不一致、版本降级、未安装 provider 被拒绝。
- 与内置版本相比，页面与交互无回归。

### 阶段 E：市场与索引

任务：

- [ ] 建立 `lifehub-plugins` 索引仓库与 `registry.json` schema。
- [ ] 每个插件仓库 CI：构建、签名、SHA-256、Release、更新索引。
- [ ] App 插件市场：列表、详情、版本、能力、安装/更新、离线标识。
- [ ] 索引缓存、ETag、内置快照、失败降级。
- [ ] 安装前展示签名者、能力与来源。

验收：

- 从 GitHub Release 安装 ZIP/APK 均可完成。
- 更新与降级策略正确。
- 断网时仍可查看已安装插件与缓存索引。
- 索引损坏时不影响已安装插件运行。

### 阶段 F：加固与发布

任务：

- [ ] 安全测试：伪造包、签名替换、路径穿越、zip bomb、降级、超大 schema。
- [ ] 备份回归：v1 fixture、APK 迁移、缺失插件、旧 schema。
- [ ] 性能：ZIP 导入、启动发现、provider 调用、Binder 大 payload。
- [ ] 文档：`.lhplugin` 规范、APK 契约、注册表 schema、插件开发指南、签名与发布流程。
- [ ] 更新 `release-checklist.md` 与隐私/合规章节。

验收：

- 全量测试、lint、真机回归通过。
- 旧备份可导入，新备份可导出。
- 文档足以让第三方作者独立完成一个 ZIP 插件。

## 7. 备份兼容策略

### 7.1 当前规则（必须保留）

- 外层容器版本 1、`LIFEHUB1`、PBKDF2 600k、AES-256-GCM。
- 内层 `formatVersion = 1`。
- 备份中的插件必须当前已安装、manifest 可读，且不是 `ERROR`/`INCOMPATIBLE`。
- `dataSchemaVersion` 必须与当前 manifest 完全一致。
- 数据通过宿主 `PluginStorage` 写入，集合名与记录 ID 保持不变。

### 7.2 后续改造的兼容要求

| 改造 | 要求 |
|---|---|
| 模块拆分 | 插件 ID、集合名、dataSchemaVersion 不变 |
| ZIP 插件 | 继续使用宿主存储；沿用同一 pluginId |
| APK 插件 | 必须提供 `exportBackup/importBackup`；旧备份走桥接导入 |
| 提升 dataSchemaVersion | 导入器需接受旧版本并在事务中迁移 |
| 提升 formatVersion | 必须保留 v1 读取器 |
| 更改加密/KDF | 必须保留 v1 解密路径 |
| 删除/重命名插件 | 提供跳过缺失插件模式或 ID 映射 |

### 7.3 备份回归测试

- [ ] 保存一份当前 App 导出的 v1 golden fixture（测试数据、固定口令）。
- [ ] 每次重构后跑“v1 fixture 导入”测试。
- [ ] APK 插件加入后，跑“内置数据 → APK 数据”迁移测试。
- [ ] 发布检查表加入“旧备份可导入”。

## 8. 安全与合规

- ZIP：ECDSA P-256 detached signature；同 `pluginId` 签名者连续性；路径穿越、zip bomb、大小与数量限制。
- APK：APK v2+ 签名；签名者连续性；versionCode 单调；安装前校验 SHA-256。
- 注册表：SHA-256 + signerSha256；索引只作为发现与元数据，不作为信任根。
- 能力：ZIP 由宿主 broker 强制执行；APK 由 Android 沙箱隔离，manifest 能力作为用户告知。
- 合规：远程插件、第三方 APK、网络能力、安装未知来源均需 ADR-0007 与发行负责人复核。

## 9. 风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| ADR/范围未更新 | 实现与既有决策冲突 | 阶段 A 先完成 ADR 与 scope-freeze |
| APK 数据无法进入宿主备份 | 用户换机丢数据 | 强制 `exportBackup/importBackup`；未实现前不迁移 |
| Binder 1MB 限制 | 地图等大 payload 失败 | 旅迹继续内置；大 payload 走文件/管道 |
| 包可见性限制 | 无法发现已安装插件 | 自定义 intent action + `<queries>` |
| ZIP 供应链攻击 | 恶意包执行/数据泄露 | 签名、校验和、来源提示、拒绝未知签名 |
| 插件 SDK 版本漂移 | 插件无法运行 | `pluginSdkVersion`、`minHostVersion`、兼容测试 |
| 两种格式认知成本 | 用户/作者困惑 | 市场统一入口，详情页标明“声明式/代码插件” |

## 10. 待评审拍板

- [x] D1：确认 ZIP + APK 双轨边界。
- [x] D2：确认 ZIP v1 纯声明式，WASM 留到 v2。
- [x] D3：确认三个第一方插件先模块化、暂不全部 APK 化。
- [x] D4：确认 APK PoC 选任务清单。
- [x] D5：确认 APK 插件自有存储 + 备份导入桥。
- [x] D6：确认备份 v1 冻结与 golden fixture 方案。
- [x] D7：确认 `lifehub-plugins` 静态索引仓库。
- [x] D8：确认自定义 intent + `<queries>` 的发现方式。

## 11. 实施记录（阶段 A/B，2026-11-23）

- 治理：新增 ADR-0006/0007，ADR-0001/0002 标注被部分替代，`scope-freeze` 增加后续版本扩展说明。
- 备份：新增 v1 golden fixture `app/src/test/resources/backup-v1-fixture.lifehub.zip`，回归测试 `PluginBackupManagerTest.v1GoldenFixtureStillImports` 通过。
- 模块：新增 `:plugin-runtime`、`:plugin-testing`、`:plugins:ledger/memo/travel`；插件源码与 assets 已迁入模块；`FirstPartyPluginHandlers` 改为 `PluginModule` 聚合。
- 测试：app 91 + ledger 35 + memo 27 + travel 12，共 165 项单元测试通过；dev-harness 校验 5 个包全部 OK。
- Lint：app 0 errors / 4 既有 warnings；ledger、memo、travel 模块均无问题。
- 构建：Debug APK 15,160,945 字节，SHA-256 `93DB5C4897F3EDCCC28E990B51D4C8BA0B462440ABF7EEB03525959F144BF2A7`。
- 边界检查：`verifyPluginModuleBoundaries` 通过，插件模块只允许引用 `plugin-runtime`。
- 遗留：真机在构建后断开连接，安装与冒烟验证待设备重连。

## 12. 实施记录（阶段 C，2026-11-23）

- 规范：新增 [`docs/lhplugin-v1.md`](lhplugin-v1.md)，定义 ZIP 布局、限额、`manifest.sig` JSON 与 ECDSA P-256 签名载荷。
- 运行时：`plugin-runtime` 新增 `PluginArchiveReader` 与 `PluginSignatureFormat`，校验路径、重复/可执行条目、解压限额、文件摘要与签名。
- 导入：新增 `PluginZipImporter`，SAF 选择后先校验再暂存；用户确认后原子替换 `files/plugins/<pluginId>/`，失败恢复旧版本；同 ID 签名者固定（TOFU），拒绝签名者变化与版本降级。
- 信任：`PluginPackageTrust` 新增 `ZIP_SIGNED`，`PluginSource` 新增 `EXTERNAL_ZIP`；`PluginTrustPolicy` 按插件校验，内置包与外部 ZIP 通过 `CompositePluginPackageSource` 组合。
- UI：主页顶栏新增“导入插件”，展示名称、版本、发布者、能力、签名者指纹与首次信任提示；插件详情显示来源。
- 工具：`dev-harness` 支持直接校验 `.lhplugin` 文件并输出签名者指纹。
- 测试：新增 `plugin-runtime` 11 项、`app` 15 项、`dev-harness` 2 项；全量单元测试 250 项通过；`validatePlugins` 5 包 OK；lint 与模块边界检查通过。
- 构建：Debug APK 15,218,233 字节，SHA-256 `FD45F14B34149C68D985E1E9A735C1A3EFFC1C179AA460F3A89DBD07597EB054`。
- 真机验证（Samsung SM-X610 / Android 16）：安装 APK；飞行模式下通过 SAF 导入模板 `.lhplugin`；预览展示签名者指纹，首次导入记录签名者；启用后页面由宿主正常渲染；卸载后重新导入恢复为“未启用”；备份导出包含 `com.lifehub.plugin.template`，覆盖导入成功。
- 真机发现并修复：重新导入已卸载插件后状态仍为“已卸载”（现导入后恢复为未启用）；导入预览“将更新已安装版本”改为“将替换已有版本”。

## 13. 完成定义

- 用户可在飞行模式下安装一个 ZIP 插件和一个 APK 插件。
- 两者的页面都由宿主渲染，风格与现有三个插件一致。
- 当前 App 导出的 v1 备份在改造后的 App 中仍可导入。
- 现有三个插件功能与数据无回归。
- 全量单测、lint、真机构建通过。
- ADR、范围文档、插件开发指南、备份文档与发布检查表已更新。
