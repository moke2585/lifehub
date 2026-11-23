# 备份档案格式 v1

- 状态：已实现
- 入口：宿主首页「备份」→ 导出/导入
- 实现：`BackupCrypto`、`PluginBackupManager`、`BackupViewModel`
- 特性：用户主动选择文件、口令加密、完整性校验、导入前预览、逐插件事务写入

## 外层容器

```
magic           8 字节  "LIFEHUB1"
containerVersion int   1
saltLength       int   16
salt             bytes 随机
ivLength         int   12
iv               bytes 随机
ciphertextLength int
ciphertext       bytes AES-256-GCM 密文（含 128 位 tag）
```

- 密钥：PBKDF2-HMAC-SHA256，迭代 600000 次，256 位。
- 口令至少 8 位，只存在于内存，不写入 Keystore、文件或日志。
- 不使用设备 Keystore 作为唯一密钥来源，换机后可用同一口令恢复。

## 内层 ZIP

| 文件 | 内容 |
|---|---|
| `archive.json` | `formatVersion`、`createdAt`、`appVersion`、`hostVersionCode`、插件清单 |
| `data/<pluginId>.json` | `pluginId`、`dataSchemaVersion`、集合与记录 |
| `checksums.sha256` | 每个文件一行：`<sha256>  <path>` |

`archive.json` 中的每个插件包含：`pluginId`、`name`、`pluginVersion`、`dataSchemaVersion`、`recordCount`。

## 导出规则

- 只导出清单可读且状态不是 `ERROR` / `INCOMPATIBLE` 的插件；禁用、卸载但保留数据的插件仍会导出。
- 单档案上限 50MB；每插件页面/数据记录沿用存储层限额（单记录 64KB、每集合 5000 条）。
- 文件名建议：`lifehub-backup-yyyyMMdd-HHmm.lifehub.zip`。使用 `.zip` 后缀是为了让部分手机的文件选择器把备份识别为文件并允许导入；外层仍是 LifeHub 加密容器，不是可直接解压的 ZIP。

## 导入校验

按顺序执行，任一步失败都不会写入现有数据：

1. 解密并检查容器版本、长度和多余数据。
2. 解压并限制总大小，拒绝绝对路径、`..`、反斜杠、重复条目和未知文件。
3. 校验 `checksums.sha256` 与文件清单完全一致。
4. 检查 `formatVersion`、插件数量、插件 ID 唯一性。
5. 要求备份中的插件当前已安装、清单可用且 `dataSchemaVersion` 与宿主一致。
6. 校验集合名、记录 ID、载荷大小、重复记录与 `recordCount` 一致性。
7. 展示预览：备份时间、每个插件的记录数与现有记录数，明确提示覆盖。

## 写入语义

- 用户确认后，按插件清空并写入，使用插件数据库事务。
- 单插件写入失败会回滚该插件到导入前状态。
- 跨插件写入不是全局事务；所有结构校验在写入前完成，正常 I/O 失败之外的错误不会进入写入阶段。
- 导入会覆盖备份中包含的插件的现有数据；未出现在备份中的插件不受影响。

## 数据版本与迁移

- 每个插件数据库通过 `plugin_meta.data_schema_version` 记录数据版本。
- manifest 的 `dataSchemaVersion` 升高时，宿主在插件数据库事务中执行注册的 `PluginDataMigration`。
- 迁移抛异常会回滚数据与版本号，旧数据保留；缺少迁移或版本降级会被拒绝。
- 备份导入要求归档版本与当前 manifest 一致，不尝试跨版本导入。

## v1 兼容性契约（2026-11-23）

- 仓库内保存一份当前 v1 导出的 golden fixture：`app/src/test/resources/backup-v1-fixture.lifehub.zip`，固定测试口令，仅含测试数据。
- 回归测试：`PluginBackupManagerTest.v1GoldenFixtureStillImports`。任何改动备份读取器、容器版本、插件数据 schema 后都必须通过。
- 导入仍要求：容器版本 1、`formatVersion = 1`、插件已安装且 manifest 可读、`dataSchemaVersion` 与当前 manifest 一致、数据可由宿主 `PluginStorage` 写入。
- 插件模块拆分、ZIP 声明式插件只要保持 `pluginId`、集合名与 `dataSchemaVersion` 不变，v1 备份继续兼容。
- APK 插件使用自有数据库时，必须提供 `exportBackup/importBackup`，并在宿主导入器中增加桥接；否则旧备份无法写入该插件。
- 提升 `formatVersion` 或加密/KDF 时必须保留 v1 读取/解密路径，不得直接拒绝旧档案。

## 未纳入首版

- 自动云同步、后台备份、设备间迁移。
- 跨插件全局原子导入。
- 备份内容的选择性导出（当前导出全部可备份插件）。
