# APK 插件契约 v1（PoC）

- 状态：v1 已实现（任务清单 PoC，2026-11-23 真机验证）
- 关联：[ADR-0006](decisions/0006-external-plugin-packaging.md)、[ADR-0007](decisions/0007-third-party-distribution.md)、[实施计划](plugin-market-plan.md)

APK 插件是独立安装的 headless App，通过 `ContentProvider` 向宿主提供 manifest、页面 JSON、action、动态数据与备份。页面仍由宿主渲染，插件不依赖 Compose。

## 1. 进程与发现

```text
LifeHub 宿主进程                    插件 APK 进程
─────────────────                  ─────────────────
PluginRegistry                      MemoApkProvider
  └─ ApkPluginPackageSource  ──Binder──> provider.call(...)
       manifest / page                    ├─ manifest.json（assets）
       action / data                      ├─ pages/*.json（assets）
       export / import backup             └─ SQLite（插件私有）
```

- 插件在 `<provider>` 上声明 intent-filter：
  ```xml
  <action android:name="com.lifehub.plugin.apk.PROVIDER" />
  ```
- 宿主在 `<queries>` 中声明同一 action，Android 11+ 才可见。
- 宿主用 `PackageManager.queryIntentContentProviders` 发现 provider，再调用 `version`、`manifest` 完成注册。
- provider 的 authority 不写死；宿主使用 `ProviderInfo.authority`。

## 2. Provider 方法

| 方法 | `arg` | 返回 Bundle | 说明 |
|---|---|---|---|
| `version` | `null` | `version: Int` | 协议版本，必须为 1 |
| `manifest` | `null` | `json: String` | `manifest.json` 原文 |
| `page` | 页面相对路径 | `json: String` | 页面 JSON 原文 |
| `action` | `PluginActionRequest` JSON | `json: PluginActionResult` | 执行 action |
| `data` | `PluginPageDataRequest` JSON | `json: PluginDataResult` | 动态数据源 |
| `exportBackup` | `null` | `json: PluginDataSnapshot` | 导出全部数据 |
| `importBackup` | `PluginDataSnapshot` JSON | `ok: Boolean` | 覆盖导入数据 |

错误响应：`ok=false`、`error=<消息>`。宿主的 `ContentResolver.call` 超时、进程崩溃或返回非法 JSON 时，按 `PluginActionResult.Failure` / `PluginDataResult(isError=true)` 处理，不阻断宿主。

JSON 序列化使用 `plugin-api` 中的 `PluginActionRequest`、`PluginActionResult`、`PluginPageDataRequest`、`PluginDataResult` 与 `PluginDataSnapshot`；`PluginApkProtocol` 定义方法名、Bundle key、版本与 512 KiB 负载上限。

## 3. 信任与安全

- APK 由 Android 包管理器验证签名；升级必须使用同一签名，降级由系统拒绝。
- 宿主只接受 `exported=true` 且声明发现 action 的 provider。
- provider 应校验 `callingPackage`：任务清单 PoC 只允许 `com.lifehub.app` 与 `com.lifehub.app.debug`。
- 宿主信任等级为 `APK_PLUGIN_SIGNED`，来源显示“外部安装 · 代码插件”。
- 未安装时宿主显示“未安装”占位（`PluginStatus.NOT_INSTALLED`），不影响其他插件与宿主启动。
- 生产版仍需要：签名证书固定、可选签名级权限、安装来源提示与恶意 provider 限流。

## 4. 数据与存储

- 插件拥有独立 SQLite 数据库，宿主不能直接读写。
- PoC 表结构：`records(collection, id, payload, created_at, updated_at)`。
- 集合名、记录 ID、payload 大小与每集合记录数沿用宿主 `PluginStorageLimits`。
- 宿主通过 provider 的 `data` / `action` 访问数据，不注入宿主 `PluginStorage`。
- `dataSchemaVersion` 变更由插件自己负责；v1 不提供跨进程迁移表。

## 5. 备份

- 插件导出 `PluginDataSnapshot(pluginId, dataSchemaVersion, collections)`。
- 宿主备份 v1 的 `BackupPluginData` 新增可选 `externalPayload`，保存该快照；`collections` 为空。
- 导入时宿主直接调用 provider 的 `importBackup`，不经过宿主数据库。
- 旧备份（`collections` 非空、无 `externalPayload`）在导入到 APK 插件时，由宿主转换为快照 JSON 后交给 provider，实现旧数据迁移。
- 备份记录数预览使用快照中的记录总数；插件 APK 未安装时导入该插件会失败并提示，不影响其他插件。

## 6. Binder 限制与实测

- 单次请求/响应上限按 512 KiB 保护；超过时 provider 返回明确错误。
- 任务清单实测（Samsung SM-X610 / Android 16，Debug）：
  | 调用 | 负载 | 耗时 |
  |---|---|---|
  | version（冷启动） | 0 | 238 ms |
  | manifest | 0.4 KiB | 13 ms |
  | page | 0.4 KiB | 2 ms |
  | data | 0.1 KiB → 1.9–5.1 KiB | 1–62 ms |
  | action | ~0.2 KiB | 2–11 ms |
  | exportBackup | 2.5 KiB | 7–25 ms |
  | importBackup | 2.5 KiB | 15 ms |
- 冷启动主要耗时在拉起插件进程；后续调用在同一进程内通常为个位数毫秒。
- 大批量数据（数千条记录）或大于 512 KiB 的响应需要文件/管道通道，当前 PoC 不支持。

## 7. 任务清单 PoC 结论

- 页面、action、data、启用/停用、备份与回收站均通过 provider 工作。
- 旧宿主内备份的 1 条进行中任务 + 6 条回收站任务成功迁移到 APK SQLite。
- 卸载 APK 后宿主正常启动并显示“任务清单 · 未安装”；重装后重新发现。
- 卸载会由 Android 删除插件数据库，数据需通过备份恢复。

## 8. 旅迹是否适合 APK

暂不迁移。旅迹页面包含全国/省/市/区县地图与高分辨率 PNG 导出：

- 地图边界 JSON 与图片资产体积大，打包进 APK 会显著增加插件体积。
- `geoMap` 页面数据与高清导出 PNG 可能超过 512 KiB Binder 上限。
- 当前宿主渲染依赖 `assetReader` 读取内置地图；APK 化需要 provider 分块传输或文件通道。

结论：旅迹继续内置；APK 插件优先承载数据型、小页面插件。

## 9. 后续

- provider 契约版本协商与能力声明。
- 签名证书固定、调用方白名单与安装来源提示。
- 文件/管道通道，支持大页面与大备份。
- 统一的插件 APK 构建、签名与 Release 流程（阶段 E）。
