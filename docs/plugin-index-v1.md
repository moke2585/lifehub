# 插件索引 v1

- 状态：已实现（阶段 E）
- 日期：2026-11-24
- 仓库：[lifehub-plugin-index](https://github.com/moke2585/lifehub-plugin-index)
- 关联：[APK 插件契约 v1](apk-plugin-v1.md)、[`.lhplugin` v1](lhplugin-v1.md)、[ADR-0008](decisions/0008-market-index-signing.md)

## 1. 作用与信任边界

索引是插件市场的静态目录，只用于发现与元数据：列出可安装插件的最新版本、下载地址、SHA-256、签名者指纹、最低宿主版本与能力。

索引不是信任根。宿主下载后仍会校验包结构、SHA-256、签名者连续性、版本不降级与能力声明；索引被篡改或损坏时，已安装插件不受影响。

## 2. 仓库文件

```text
lifehub-plugin-index/
├── registry.json          # 索引本体
├── registry.sig           # 索引签名
├── keys/<keyId>.pem       # 索引公钥（App 内置同一份）
├── scripts/validate.py    # 结构与字段校验
├── scripts/sync_releases.py
├── scripts/IndexSignatureTool.java
├── .github/workflows/validate.yml
├── .github/workflows/scan.yml
└── .github/workflows/sign.yml
```

## 3. registry.json（schema 1）

```json
{
  "schema": 1,
  "generatedAt": "2026-11-24T00:00:00Z",
  "plugins": [
    {
      "pluginId": "com.lifehub.plugin.memo",
      "name": "任务清单",
      "version": "1.1.0",
      "artifactType": "apk",
      "applicationId": "com.lifehub.plugin.memo",
      "versionCode": 1,
      "url": "https://github.com/moke2585/lifehub-plugin-memo/releases/download/v1.1.0/memo-apk-release.apk",
      "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      "signerSha256": "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
      "minHostVersion": 1,
      "pluginSdkVersion": 1,
      "capabilities": ["ui.action", "storage.private"],
      "repo": "https://github.com/moke2585/lifehub-plugin-memo",
      "sizeBytes": 970273
    }
  ]
}
```

顶层字段：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `schema` | 整数 | 是 | 固定为 `1` |
| `generatedAt` | 字符串 | 否 | ISO-8601 UTC 时间 |
| `plugins` | 数组 | 是 | 每个 `pluginId` 只保留一条最新版本，最多 200 条 |

插件字段：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `pluginId` | 字符串 | 是 | 反向域名，必须与 manifest 一致且全表唯一 |
| `name` | 字符串 | 是 | 展示名称 |
| `version` | 字符串 | 是 | 语义化版本 |
| `artifactType` | 字符串 | 是 | `apk` 或 `lhplugin` |
| `applicationId` | 字符串 | APK 必填 | APK 的包名；`.lhplugin` 若填写必须等于 `pluginId` |
| `versionCode` | 整数 | APK 必填 | APK 的 versionCode，用于更新与降级判断 |
| `url` | 字符串 | 是 | HTTPS 下载地址，指向 GitHub Release 资产 |
| `sha256` | 字符串 | 是 | 产物 SHA-256，64 位小写十六进制 |
| `signerSha256` | 字符串 | 是 | APK 证书或 `.lhplugin` 签名公钥的 SHA-256 |
| `minHostVersion` | 整数 | 是 | 最低宿主 versionCode |
| `pluginSdkVersion` | 整数 | 是 | 插件 SDK 主版本 |
| `capabilities` | 字符串数组 | 是 | 与 manifest 一致，非空且不重复 |
| `repo` | 字符串 | 是 | 插件源码仓库 HTTPS 地址 |
| `sizeBytes` | 整数 | 否 | 产物大小，字节 |

v1 不接受未知字段；新增字段需要提升 `schema`。

## 4. registry.sig

```json
{
  "schema": 1,
  "algorithm": "ECDSA_P256_SHA256",
  "keyId": "index-2026-11",
  "registrySha256": "20a3354862d6466af98ce7b05f9b599569784f6c0fedc764ff8e015a05b76af8",
  "signature": "MEUCIQ...",
  "signedAt": "2026-11-24T00:00:00Z"
}
```

- 签名载荷：`lifehub-index-v1\n<registry.json 原始字节的 64 位小写 SHA-256>`，UTF-8。
- 算法：ECDSA P-256 + SHA-256（`SHA256withECDSA`）。
- `keyId` 只允许 `[A-Za-z0-9._-]`；App 按 `keyId` 读取 `assets/market/keys/<keyId>.pem`。
- 签名由 `IndexSignatureTool.java` 生成，工作流使用 GitHub Environment `index-signing` 的审批与 Secret。

## 5. 宿主行为

### 5.1 拉取与缓存

- 地址：`https://raw.githubusercontent.com/moke2585/lifehub-plugin-index/main/registry.json` 与 `registry.sig`。
- 只允许 HTTPS；连接超时 10s、读取 15s；索引上限 1 MiB；最多 3 次重定向。
- 发送 `If-None-Match` 与 `If-Modified-Since`，304 时复用缓存。
- 缓存：`filesDir/market_index/registry.json`、`registry.sig`、`meta.properties`；先写 `.tmp` 再原子替换。
- 回退顺序：网络 → 通过验签的缓存 → APK 内置 `assets/market/registry.json`。
- 内置快照由 APK 签名保护，不再单独验索引签名；网络与缓存索引必须通过验签。

### 5.2 安装校验

- ZIP：SHA-256 → `PluginZipImporter` 结构/页面 schema/`manifest.sig` 校验 → 签名者固定与版本不降级 → 用户确认 → 原子安装。
- APK：SHA-256 → `PackageManager.getPackageArchiveInfo` 校验 `applicationId`、`versionName`、`versionCode`、证书 SHA-256 → 与已安装签名者比较 → `PackageInstaller` 系统确认 → 返回应用后刷新发现。
- 内置插件 ID 与市场条目冲突时拒绝安装；同一插件 ID 的 ZIP/APK 来源冲突时显示“来源冲突”。

## 6. 发布与审核流程

1. memo 仓库打 `v*` 标签：CI 检出宿主与 ledger/travel 到同级目录，构建签名 APK，上传 APK、`.sha256` 与 `plugin-release.json` 到 memo 的 GitHub Release。
2. 索引仓库 `scan.yml` 定时或手动扫描最新 Release：下载产物、复核 SHA-256、用 `apksigner`/`aapt2` 复核包名、版本与证书指纹、检查签名者连续性，生成 `registry.json` 变更并开 PR。
3. 维护者逐项审核 PR（来源、哈希、签名者、能力、版本）后合并。
4. `sign.yml` 在 `index-signing` 环境审批后用 `IndexSignatureTool` 签名 `registry.json`，提交 `registry.sig`。
5. App 拉取索引并验签；未签名或签名不匹配时回退缓存/内置快照。

索引只接受经过 PR 审核的条目；自动扫描不会自动合并。签名密钥轮换必须先发布内置新公钥的 App，再切换签名工作流的 `keyId`。
