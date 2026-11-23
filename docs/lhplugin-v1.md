# `.lhplugin` 插件包格式 v1

- 状态：v1 已实现（阶段 C，2026-11-23 真机验证）
- 日期：2026-11-23
- 关联：[ADR-0006](decisions/0006-external-plugin-packaging.md)、[ADR-0007](decisions/0007-third-party-distribution.md)、[插件页面 schema v1](plugin-schema-v1.md)

`.lhplugin` 是 ZIP 容器，承载**纯声明式**插件：只有 manifest、页面 JSON 与只读资源，不含任何可执行代码。宿主负责渲染页面、执行 action 与数据读写。

## 1. 容器结构

ZIP 内必须只有一个顶层目录，目录名等于 `pluginId`：

```text
com.example.water/
├── manifest.json          # 必需
├── pages/
│   └── home.json          # 必需，至少一个页面
├── data/                  # 可选，只读资源
│   └── ...
└── manifest.sig           # 必需，JSON 格式的分离签名
```

- 允许 ZIP store 或 deflate 压缩；ZIP64、加密、分卷不支持。
- 条目名必须是 UTF-8 相对路径，使用 `/` 分隔。
- 目录条目（以 `/` 结尾）会被忽略。
- 禁止绝对路径、反斜杠、空段、`.`、`..`、重复条目与跨顶层目录的条目。
- 禁止包含可执行代码：`dex`、`jar`、`class`、`so`、`js`、`mjs`、`cjs`、`wasm`、`apk`、`aar`。
- `manifest.sig` 不参与自身的签名文件清单。

## 2. 资源限额

| 项目 | 上限 |
|---|---|
| ZIP 文件大小 | 16 MiB |
| 条目数量 | 256 |
| 单个条目解压后 | 8 MiB |
| 全部条目解压后 | 16 MiB |
| `manifest.json` | 64 KiB |
| `manifest.sig` | 64 KiB |
| 单个页面 JSON | 256 KiB（`PluginSchemaLimits.MAX_PAGE_BYTES`） |
| 页面数量 | 50（`PluginSchemaLimits.MAX_PAGES_PER_PLUGIN`） |

任一上限被突破时整包拒绝，不做部分导入。

## 3. `manifest.json`

沿用宿主现有插件清单 schema（见 [插件开发指南](plugin-development.md)）。外部 ZIP 的额外要求：

- `pluginSdkVersion` 必须是宿主支持的 SDK 主版本。
- `minHostVersion` / `maxHostVersion` 必须与当前宿主 `versionCode` 兼容。
- `pluginId` 必须与 ZIP 顶层目录名一致。
- manifest 与页面必须通过宿主同一套 `PluginManifestValidator`、`PluginPageValidator` 校验；任何错误都会拒绝导入。

## 4. `manifest.sig`

`manifest.sig` 是 UTF-8 JSON：

```json
{
  "schema": 1,
  "algorithm": "ECDSA_P256_SHA256",
  "pluginId": "com.example.water",
  "version": "1.0.0",
  "keyId": "author-key-2026",
  "publicKey": "<base64，X.509 SubjectPublicKeyInfo DER>",
  "signature": "<base64，ASN.1 DER 编码的 ECDSA 签名>",
  "files": [
    { "path": "manifest.json", "sha256": "<小写 hex>" },
    { "path": "pages/home.json", "sha256": "<小写 hex>" }
  ]
}
```

规则：

- `schema` 必须为 `1`，`algorithm` 必须为 `ECDSA_P256_SHA256`。
- `pluginId`、`version` 必须与 `manifest.json` 完全一致。
- `files` 必须**恰好**覆盖除 `manifest.sig` 以外的全部条目；缺一个或多一个都拒绝。
- `files[].path` 使用相对插件根目录的路径，排序不影响解析。
- `sha256` 为对应文件原始字节的 SHA-256 小写十六进制。

### 4.1 签名载荷

对以下 UTF-8 文本（`\n` 表示换行，最后一行也以 `\n` 结尾）做 SHA-256 后，用 ECDSA P-256 签名：

```text
lifehub-lhplugin-v1
<pluginId>
<version>
<按 path 字节序升序排列的每个文件>
```

每个文件占一行：

```text
<path>\t<sha256>
```

`manifest.sig` 自身不进入载荷。

### 4.2 签名者指纹

`fingerprint = SHA-256(publicKey 的 DER 字节)`，小写十六进制。App 以 `pluginId` 为键记住首次确认的签名者指纹：

- 同一 `pluginId` 的后续导入必须使用相同指纹，否则拒绝。
- 首次出现的签名者会在导入确认页展示，用户确认后固定（TOFU）。
- 官方或注册表已验证的签名者可以直接显示为“已验证”。

签名只证明包内容与该公钥一致；公钥本身的可信度由上述固定关系、官方签名者列表或注册表元数据决定。

## 5. 安装流程

1. 用户通过系统文件选择器（SAF）选择 `.lhplugin`。
2. App 将文件读入内存并执行结构与限额校验。
3. App 校验 `manifest.sig`、文件摘要、manifest 与页面 schema。
4. App 展示名称、版本、发布者、签名者指纹、能力清单与风险提示。
5. 用户确认后，App 解压到私有暂存目录，再原子替换
   `files/plugins/<pluginId>/`；失败时恢复旧版本。
6. 注册表重新发现插件；插件保持未启用状态，需用户显式启用并授权能力。

导入不需要网络，不需要系统安装权限，也不会自动启用插件。

## 6. 更新与回滚

- 同一 `pluginId` 的新包版本不得低于已安装版本；降级被拒绝。
- 签名者指纹变化被拒绝。
- v1 不支持在升级时修改 `dataSchemaVersion`：迁移表在宿主内，第三方包无法提供。需要变更数据结构时保持版本号并在插件数据内自行兼容，或发布新的 `pluginId`。
- 替换采用“暂存目录 → 备份旧目录 → 切换 → 删除备份”；切换失败会恢复旧目录。
- 插件数据由宿主 `PluginStorage` 按 `pluginId` 保存，更新插件包不会删除数据。
- `dataSchemaVersion` 变化时沿用现有迁移与隔离机制。

## 7. 信任等级

| 来源 | 信任等级 | 说明 |
|---|---|---|
| 随宿主 APK 内置 | `APK_SIGNED` | APK v2+ 签名 |
| 外部 `.lhplugin` | `ZIP_SIGNED` | 导入时完成签名校验并固定签名者 |
| 无法验证 | `UNTRUSTED` | 注册表拒绝加载 |

`ZIP_SIGNED` 表示“导入时通过校验”，运行期不再重复验签；插件目录位于 App 私有存储，其他应用无法写入。

## 8. 作者参考流程

生成 P-256 密钥（私钥不得提交仓库）：

```bash
openssl ecparam -name prime256v1 -genkey -noout -out plugin-key.pem
openssl pkcs8 -topk8 -nocrypt -in plugin-key.pem -out plugin-key.pk8
openssl ec -in plugin-key.pem -pubout -outform DER | base64 -w0
```

打包后按第 4 节生成 `manifest.sig`，再用工具校验：

```powershell
.\gradlew.bat :dev-harness:validatePlugins --args="path\to\plugin.lhplugin"
```

或直接运行 dev-harness：

```powershell
.\gradlew.bat :dev-harness:run --args="path\to\plugin.lhplugin"
```

## 9. v1 明确不做

- 不执行任何代码（无 dex/jar/so/JS/WASM）。
- 不提供网络、文件系统或系统 API 能力。
- 不支持插件间通信。
- 不支持 ZIP 内嵌套插件或一次导入多个插件。
- 不支持签名算法协商；v2 若引入脚本/WASM，必须新增 ADR。
