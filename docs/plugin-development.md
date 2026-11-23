# 插件开发指南

本文面向在 LifeHub 中新增声明式插件的开发者。插件页面由 manifest 与页面 schema 描述，宿主使用 Compose 渲染；不执行任意 HTML、脚本或 dex。插件可以随宿主 APK 内置，也可以打包为 `.lhplugin` 离线导入。

当前插件 SDK 为 **v1.0.0**，页面 schema 为 **v1**。宿主只接受相同 SDK 主版本的插件，不兼容时禁止运行并提示更新 LifeHub，不做降级。

## 1. 包结构

```text
com.example.myplugin/
├── manifest.json
├── pages/
│   ├── home.json
│   └── edit.json
├── data/              # 可选，只读静态资源（例如地图边界）
├── manifest.sig       # 可选；打包为 .lhplugin 时必需
└── README.md          # 可选，不参与渲染
```

目录名必须与 `manifest.json` 中的 `pluginId` 完全一致。页面文件路径必须是包内相对路径，不能包含绝对路径、反斜杠、`.` 或 `..`。

最小模板见 [samples/com.lifehub.plugin.template](../samples/com.lifehub.plugin.template)。

## 2. 快速开始

1. 复制模板到插件资产目录：
   - 第一方模块插件：同级插件仓库 `../lifehub-plugin-<name>/src/main/assets/plugins/com.example.myplugin`。
   - 内置示例/单插件：`app/src/main/assets/plugins/com.example.myplugin`。
2. 修改目录名与 `pluginId`，并在 `FirstPartyPluginHandlers.modules` 中登记模块（第一方模块插件）。
3. 编辑 `pages/home.json`，可用节点见 [页面 schema v1](plugin-schema-v1.md)。
4. 运行校验：

```powershell
.\gradlew.bat :dev-harness:validatePlugins
```

5. 构建 APK，在主页启用插件并打开。

## 3. manifest 字段

```json
{
  "pluginId": "com.example.myplugin",
  "name": "我的插件",
  "version": "1.0.0",
  "description": "一句话说明这个插件解决什么问题。",
  "publisher": "LifeHub",
  "icon": "plugin",
  "minHostVersion": 1,
  "pluginSdkVersion": 1,
  "capabilities": [],
  "entryPage": "home",
  "quickCreate": { "pageId": "edit", "label": "新建记录" },
  "homeWidgets": [
    {
      "id": "today_summary",
      "type": "summary",
      "title": "今日",
      "size": "small",
      "priority": 100,
      "sourceId": "home_summary",
      "action": { "type": "navigate", "pageId": "home" }
    }
  ],
  "search": { "sourceId": "search_results" },
  "pages": [
    { "id": "home", "title": "首页", "file": "pages/home.json" }
  ],
  "dataSchemaVersion": 1
}
```

- `pluginId`：小写反向域名，发布后不可复用。
- `icon`：小写字母开头的标识；未识别时使用默认图标。
- `minHostVersion` / `maxHostVersion`：宿主 `versionCode` 范围；不满足时插件不可启用，详情页会提示更新 LifeHub。
- `pluginSdkVersion`：插件使用的 SDK 主版本；与宿主不兼容时禁止运行，不降级。
- `capabilities`：只声明实际使用的能力，每项必须有 `reason`。
- `quickCreate`：可选；在主页已启用卡片上显示“+”快捷新建按钮，点击直接打开 `pageId` 指定页面，`label` 最长 16 字符。
- `homeWidgets`：可选；向宿主主页贡献紧凑小组件，宿主用固定模板渲染，插件只声明内容类型、数据源和跳转动作。字段与限额见 [主页小组件 schema v1](home-widget-schema-v1.md)。
- `search`：可选；声明全局搜索数据源。宿主以 `pageId=search`、`query` 和 `limit` 参数调用该数据源，返回的条目应带 `navigate` 动作，点击后由宿主打开对应插件页面。
- `dataSchemaVersion`：插件数据版本，升级时必须提供迁移。

## 4. 页面与动作

页面节点包括标题、正文、按钮、分组、静态列表、输入框、五星评分、日期条、快捷选择、周期导航、记录列表、指标卡、柱状图、环图、行政区划地图和空/加载态。完整字段与限额见 [plugin-schema-v1.md](plugin-schema-v1.md)。

`geoMap` 用归一化的多边形环绘制省/市/区县地图，支持省界粗线、市县界细线、点亮填充、名称标签与点选；区域可携带 `params`，宿主点选时会自动合并区域 id、名称、层级、父级与省市信息。声明 `zoomable` 后地图额外提供双指缩放、拖动与右上角放大/缩小/复位按钮。

动作分两类：

- `navigate`：跳转到 manifest 已声明的页面，参数会作为目标页面输入框初值。
- `host`：交给宿主注册的 `PluginActionHandler`，例如保存记录、删除记录。

按钮触发时，页面内所有输入值会合并进参数；必填项为空会阻止动作并提示。`confirm` 存在时先展示宿主确认对话框。

## 5. 动态数据源

`recordList`、`metrics`、`barChart`、`donutChart`、`geoMap` 和 `periodNav` 通过 `sourceId` 引用宿主注册的 `PluginPageDataHandler`。宿主在页面打开、导航参数变化或收到 `Refresh` 时重新加载。

- 数据源需要 `ui.action` 与 `storage.private` 授权。
- 数据只能来自该插件的私有存储。
- 单个数据源最多返回 200 条记录、31 个柱状图条目、8 个环图分类、5 个指标、500 个地图区域与 40000 个地图坐标点。
- 记录可带 `groupTitle`、`groupTotal`、`leadingText` 与 `tone`；同日记录会合并为一张卡片。`leadingAction` 可让 `leadingText` 图标独立响应点击，不影响整行点击。
- 数据源可通过 `PluginPageDataContext.assetReader(path)` 读取本插件包内的只读资产，例如 `data/china.json`；宿主拒绝绝对路径与 `..`，不提供网络访问。
- 提升 `manifest.dataSchemaVersion` 时必须在宿主注册 `PluginDataMigration`；迁移在事务中执行，失败会隔离插件而不是清空用户数据。
- 动态数据依赖宿主 Kotlin 实现，纯声明式模板无法凭空产生业务数据。

## 6. 权限与用户同意

插件启用前，宿主会展示 manifest 声明的能力清单与用途说明，用户确认后才授予。禁用、卸载或自动隔离会撤销授权。新增能力必须重新征得同意，不能沿用旧授权。

能力代理在每次 action 和数据请求时同时检查：插件状态、manifest 声明、用户授权。未声明、未授权或已停用的调用都会失败。

## 7. 生命周期与数据

| 操作 | 行为 |
|---|---|
| 启用 | 校验能力并授权，插件可打开 |
| 停用 | 撤销能力，插件页面不可打开，数据保留 |
| 卸载 | 移除入口，数据保留 |
| 重新安装 | 恢复为“未启用”，数据仍可读 |
| 永久删除数据 | 独立操作，二次确认后不可恢复 |

宿主统计未预期异常：10 分钟内 3 次会自动停用插件并撤销能力，主页显示“连续异常，已自动停用”。一次成功运行或用户显式重新启用会清零。

## 8. 数据迁移

`dataSchemaVersion` 大于 1 时，宿主必须提供对应的 `PluginDataMigration`。迁移在事务中执行；失败会回滚版本与数据、保留旧数据并隔离插件，不会覆盖现有记录。

## 9. 签名与信任

内置插件随已签名的 APK 分发，APK v2+ 签名是信任锚；外部 `.lhplugin` 使用 `manifest.sig` 中的 ECDSA P-256 签名，导入时校验并固定签名者。不要把私钥、口令或签名材料提交到仓库。

`.lhplugin` 的 ZIP 布局、资源限额、签名 JSON 字段与签名载荷见 [`.lhplugin` v1 规范](lhplugin-v1.md)。第一方插件全部内置时不需要 `manifest.sig`；导出为外部包时必须生成。

签名者连续性规则：

- 首次导入时展示签名者指纹，用户确认后按 `pluginId` 固定。
- 同一 `pluginId` 的后续版本必须使用同一签名者；指纹变化直接拒绝。
- 版本不得降级。
- 外部插件导入后保持未启用，仍需用户显式启用并授权能力。

## 10. 校验命令

校验全部内置插件与 `samples`：

```powershell
.\gradlew.bat :dev-harness:validatePlugins
```

校验单个插件目录：

```powershell
.\gradlew.bat :dev-harness:run --args="samples/com.lifehub.plugin.template"
```

校验 `.lhplugin` 文件（含结构与签名）：

```powershell
.\gradlew.bat :dev-harness:run --args="path\to\plugin.lhplugin"
```

选项：

- `--host-version <n>`：模拟宿主 `versionCode`，用于检查 `minHostVersion`。
- `--deny-capabilities`：模拟用户拒绝能力授权，查看启用被拒绝的生命周期。
- `--strict`：出现 warning 时也返回非零退出码，适合 CI。

校验器会输出：

- manifest 与页面的结构化错误（文件 + 错误码）；
- 需要宿主注册的 action 与数据源；
- 生命周期模拟结果与能力确认提示；
- 插件包总大小、数据 schema、签名校验结果与签名者指纹。

CI 可直接运行 `.\gradlew.bat :dev-harness:validatePlugins`，失败时退出码为 1。

## 11. 常见错误

| 现象 | 原因 | 处理 |
|---|---|---|
| `目录名 ... 与 pluginId ... 不一致` | 文件夹与 manifest ID 不同 | 改成一致 |
| `页面文件不存在` | `pages[].file` 路径错误 | 检查相对路径与大小写 |
| `页面文件路径不安全` | 使用了 `..`、绝对路径或反斜杠 | 使用包内相对路径 |
| `UNSUPPORTED_SCHEMA_VERSION` | 页面 schema 版本不受支持 | 使用当前 `SUPPORTED_SCHEMA_VERSION` |
| `UNKNOWN_CAPABILITY` | 声明了宿主不支持的能力 | 删除或改用已支持能力 |
| `HOST_VERSION_TOO_LOW` | 插件要求更高的宿主 | 调低 `minHostVersion` 或升级宿主 |
| `未注册的插件动作` | 使用了 `host` action 但宿主未注册 | 在宿主注册 `PluginActionHandler`，或改用 `navigate` |
| `未注册的数据源` | 使用了 `sourceId` 但宿主未注册 | 在宿主注册 `PluginPageDataHandler` |

## 12. 发布前检查

- [ ] `:dev-harness:validatePlugins` 通过，且 `--strict` 无 warning。
- [ ] 页面覆盖加载、空、失败状态，支持字体放大与暗色模式。
- [ ] 新增能力有用途说明和负向测试。
- [ ] `dataSchemaVersion` 变更提供迁移与回滚测试。
- [ ] 备份/恢复、禁用/卸载/重装路径验证通过。
- [ ] 不记录数据正文、金额、任务内容或备份口令。
