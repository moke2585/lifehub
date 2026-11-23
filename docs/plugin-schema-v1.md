# 插件页面 schema v1

- 状态：已冻结（2026-11-11）
- 对应实现：`plugin-api` 的 `PluginPageSchema.kt`、`PluginPageValidator.kt`，以及 `app` 的 `PluginPageRenderer`
- 兼容规则：`schemaVersion` 只支持 1；新增节点或字段必须向后兼容，破坏性变更需提升版本并保留旧版本渲染路径
- 主页小组件使用独立 schema，见 [主页小组件 schema v1](home-widget-schema-v1.md)；页面 schema 不包含主页组件节点

## 页面文档

```json
{
  "schemaVersion": 1,
  "pageId": "home",
  "title": "示例首页",
  "topBarActions": [
    {
      "icon": "add",
      "contentDescription": "新建",
      "action": { "type": "navigate", "pageId": "edit" }
    }
  ],
  "nodes": []
}
```

- `pageId` 必须与 manifest 中的页面 id 一致。
- `title` 显示在宿主顶部栏，最长 120 字符。
- `topBarActions` 可选，最多 4 个，显示在顶部栏右侧；`icon` 支持 `add`、`trash`、`chart`、`notes`、`search`、`loans`，`action` 支持普通动作与 `confirm`。声明顺序从左到右，最后一个最靠右。
- `nodes` 至少一个，最多 400 个；嵌套深度最多 6 层。

## 节点

| type | 字段 | 说明 |
|---|---|---|
| `title` | `text`、`level`(1–3) | 页面标题文本 |
| `text` | `text` | 正文，最长 2000 字符 |
| `button` | `label`、`action`、`emphasis`(`primary`/`secondary`)、`enabled` | 触发宿主 action 或页面导航 |
| `section` | `title?`、`children` | 分组容器，参与嵌套深度校验 |
| `list` | `items[]`：`title`、`subtitle?`、`trailing?` | 静态列表，最多 200 项 |
| `recordList` | `sourceId`、`emptyTitle?`、`emptyMessage?`、`emptyActionLabel?`、`emptyAction?`、`carryInputs?`、`collapsedCount?` | 由宿主数据源动态填充的记录列表，见下文；空态可附带一个快捷动作按钮；`carryInputs` 为 `true` 时行动作会合并当前页面已填写的输入值；`collapsedCount` 大于 0 时默认只展示前 N 条，末尾提供“展开其余 / 收起” |
| `metrics` | `sourceId` | 展示同一数据源返回的指标与比例条 |
| `periodNav` | `sourceId`、`anchorId?`、`shiftAction`、`resetLabel?` | 周期导航：上一段/下一段与“回到今天”；`anchorId` 保存锚点日期 |
| `barChart` | `sourceId`、`title?`、`action?` | 数据源返回的柱状图；点选柱子后可通过“查看明细”触发 action，宿主附带 `barLabel`/`barValue`/`barIndex` 参数 |
| `donutChart` | `sourceId`、`title?` | 数据源返回的分类环图，中心显示总额，右侧显示分类金额与占比 |
| `geoMap` | `sourceId`、`height?`(240–720)、`zoomable?`、`action?` | 数据源返回的行政区划地图；区域可点亮、可点选，点选时宿主把区域参数附加给 `action`；`zoomable` 为 `true` 时支持双指缩放、拖动与地图下方的倍率滑块 |
| `dateStrip` | `id`、`days`(1–60)、`label?`、`action?`、`defaultToday?` | 横向滑动日期选择；选中值写入 `id` 并触发 action |
| `dateWheel` | `id`、`label?`、`allowEmpty?`、`emptyLabel?` | 年/月/日滚轮日期选择；选中值以 ISO 日期写入 `id`，空值表示未设置 |
| `emojiPicker` | `id`、`label?`、`options[]`(1–40)、`defaultValue?`、`collapsedCount?` | Emoji 图标选择；选项换行排列，选中值写入 `id`；`collapsedCount` 大于 0 时默认只展示前 N 个并提供“更多 / 收起” |
| `chips` | `id`、`label`、`options[]`、`defaultValue?`、`action?` | 单行快捷选择，例如收支方向与分类 |
| `selector` | `id`、`icon`(`filter`/`sort`)、`label?`、`options[]`、`defaultValue?`、`action?` | 图标 + 下拉菜单的紧凑选择器；适合筛选、排序，选择后写入 `id` 并触发 action |
| `row` | `children`(1–4) | 水平等宽排列子节点，例如把筛选与排序放在同一行 |
| `rating` | `id`、`label?`、`max?`(3–5)、`defaultValue?`(0..max) | 五星评分控件；点选星星写入 `id`，0 表示未评分，由后续按钮动作提交 |
| `input` | `id`、`label`、`placeholder?`、`kind`(`text`/`money`/`date`)、`required?`、`multiline?`、`action?`、`autofocus?` | 输入框；id 页面内唯一；`autofocus` 打开页面后自动聚焦，`action` 在键盘搜索键触发 |
| `empty` | `title`、`message?` | 统一空态卡片 |
| `loading` | `text?` | 加载态 |

未知节点类型会解析失败，插件被标记为不兼容；未知字段会被忽略，便于后续扩展。

## 动作

```json
{
  "type": "host",
  "actionId": "com.example.plugin.submit",
  "params": { "mode": "quick" },
  "confirm": {
    "title": "确认提交",
    "message": "提交后不能修改。",
    "confirmLabel": "提交",
    "cancelLabel": "取消"
  }
}
```

- `host`：交给宿主注册的 `PluginActionHandler`，请求包含 `pluginId`、`pageId`、`actionId` 和参数。
- `navigate`：只允许跳转到 manifest 已声明的页面；`params` 会作为目标页面输入框初值。
- 触发按钮时，页面内所有输入框的值会合并进参数，优先于 action 的静态 `params`；必填项为空会阻止动作并提示。
- `rating` 只把选中的星数写入 `id`，不会自动提交；通常再放一个按钮，用 `host` 动作把 `id` 的值交给插件。
- 记录列表的行点击、滑动、`leadingAction` 与行内动作携带完整参数，宿主**不会**叠加当前页面的初始参数与输入值；`recordList` 声明 `carryInputs: true` 时会叠加当前输入值（动作自身的参数优先），用于编辑页点选分类等场景；顶栏动作与普通按钮仍会合并当前页面参数。
- `confirm` 存在时先展示宿主确认对话框，确认后才执行。
- `actionId` 最长 128 字符；参数最多 20 个，键名匹配 `[a-zA-Z][a-zA-Z0-9_]*`。
- action 处理结果可以是 `Success`、`ShowMessage`、`Failure`、`Navigate`、`Refresh` 或 `ExportImage`；`Refresh` 会重新加载当前页面的数据源。`ExportImage` 携带文件名与 PNG 字节，宿主保存到系统相册并提示保存位置，插件不需要直接访问存储权限。
- `Refresh` 可携带 `undoLabel` 与 `undoAction`：宿主显示带“撤销”按钮的 Snackbar，点击后执行 `undoAction`，因此可逆操作（完成、移入回收站）无需二次确认。
- `Refresh.clearInputIds` 可指定刷新后清空的输入框，例如快速添加成功后清空 `quick` 输入框。

## 动态数据源

`recordList` 的 `sourceId` 由第一方插件在宿主注册（例如记账的 `ledger_entries`）。宿主在页面打开、导航参数变化或收到 `Refresh` 时重新请求数据。

- 每次请求经过能力代理：需要 `ui.action` 与 `storage.private` 授权。
- 数据源只能访问该插件的私有存储。
- 返回条目上限 200，每项最多 3 个动作，文本上限 500 字符；指标最多 5 个，`ratio` 必须在 0..1。
- 条目动作同样是 `host` 或 `navigate`，可以携带记录 ID 等参数；编辑、删除、置顶都由返回的动作驱动。
- 条目可选 `leadingText`（分类图标/表情）、`groupTitle`/`groupTotal`（日期分组）与 `tone`（`positive`/`negative`/`neutral`，宿主用主题色渲染金额）。同一日期的条目在宿主中合并为一张卡片，首个带 `navigate` 的动作作为整行点击行为。
- 条目可选 `completed`（宿主置灰并排在列表底部）、`clickAction`（覆盖整行点击行为）以及 `swipeRightAction`/`swipeLeftAction`（右滑/左滑动作）。动作声明 `confirm` 时，宿主在执行前弹出确认框；未声明时直接执行，可逆操作应通过 `Refresh` 的撤销能力回退。
- 条目可选 `titleMaxLines`、`subtitleMaxLines`（副标题可含换行）、`footer`（副标题下方的弱化小字，例如紧急程度）与 `roomy`（独立宽松卡片，展示完整标题和多行信息）。`leadingText` 可放 Emoji 图标；`leadingAction` 可让该图标独立可点击（例如日常任务提升倍率），不占用整行点击，也不计入每条的 3 个动作上限。
- 条目可选 `actionsInMenu`：为 `true` 时把全部动作收进行尾三点菜单，行内不再平铺按钮，为标题和副标题留出更多空间；整行点击与左右滑动不受影响。
- 指标可选 `tone`，宿主据此给进度条着色（收入绿、支出红）。
- 数据源还可返回 `periodLabel`（周期导航显示当前范围）、`bars`（柱状图：标签、展示值、0..1 比例）、`segments`（环图分类：标签、展示值、0..1 比例，最多 8 个）与 `chartTotal`（环图中心总额）。
- `barChart` 可选 `action`：用户点选柱子后可点击“查看明细”，宿主以 `barLabel`、`barValue`、`barIndex` 作为附加参数触发动作；未提供时只展示金额。
- `metrics`、`barChart`、`donutChart`、`recordList`、`geoMap` 与 `periodNav` 可以使用同一个 `sourceId`，宿主只加载一次；第一个指标作为摘要卡的大号结余，其余显示为支出/收入两列并带比例条。

## 地图数据

`geoMap` 的数据源在 `PluginDataResult.geo` 返回 `PluginGeoMapData`：

- `aspectRatio`：投影后地图的宽高比，宿主按可用宽度等比缩放并受 `height` 上限约束。
- `title` / `subtitle`：地图卡片标题与说明。
- `regions`：行政区列表，每项包含 `id`、`name`、`level`（`province` / `city` / `county`）、`parentId` / `parentName`、`provinceId` / `provinceName`、`lit`、`interactive`、`label` / `labelX` / `labelY`、`params` 与 `rings`。
- `rings` 是归一化到 0..1 的多边形环列表，每个环为 `PluginGeoPoint` 序列；宿主按地图尺寸缩放绘制，并负责边界描边、点亮填充、名称标签与点选命中。
- 省级区域只描边、不填充，用于绘制省界；市级/县级区域填充并描边，`interactive=false` 可用于九段线等装饰。
- 点击区域时，宿主自动附加 `regionId`、`regionName`、`level`、`lit`、`note`、`parentId`、`parentName`、`provinceId`、`provinceName`，并合并区域自带的 `params`。插件可用这些参数导航到详情页。
- 通过多个 `geoMap` 页面与区域 `params` 可以组织成多级下钻：例如全国页的省区域携带 `provinceId`，省页的市区域携带 `cityId`，城市页的区县区域携带父级信息；点击时宿主把当前区域参数合并给动作，插件无需额外维护选中状态。
- `zoomable=true` 时地图支持 1–4 倍双指缩放与拖动，地图下方显示倍率滑块（含复位）；缩放状态下点选仍按反变换命中基础坐标，未缩放时的单指拖动不拦截页面滚动；标签绘制在缩放变换之外，字号随缩放温和增大，避免放大后被文字遮挡。
- 旅迹等插件的地图资产在构建前离线生成：区县边界来自阿里云 DataV GeoAtlas；没有区县的直筒子市/省直辖县级单位可用 OpenStreetMap 的乡镇街道边界补齐（© OpenStreetMap contributors，ODbL）。地图资产随 APK 内置，运行时不发起网络请求。
- 地图数据源同样经过能力代理与限额校验；单次最多 500 个区域、40000 个坐标点，坐标必须位于 0..1。

## 资源与安全限额

| 项 | 上限 |
|---|---|
| 单页 JSON | 256 KB |
| 每插件页面数 | 50 |
| 节点数 | 400 |
| 嵌套深度 | 6 |
| 单段文本 | 2000 字符 |
| 列表项 | 200 |
| 动态数据源条目 | 200 |
| 动态条目动作 | 3 |
| 指标数量 | 5 |
| 柱状图条目 | 31 |
| 环图分类 | 8 |
| 地图区域 | 500 |
| 地图坐标点 | 40000 |
| 日期选择天数 | 60 |
| 输入框 | 50 |
| 动作参数 | 20 |

渲染器只使用宿主 Compose 组件与主题 token，不支持 HTML、WebView、脚本、任意原生视图、自定义系统栏或插件自定义颜色。

## 错误码

校验失败会以 `PluginIssue` 返回，常见 code：

- `UNSUPPORTED_SCHEMA_VERSION`：不支持的 schema 版本
- `PAGE_ID_MISMATCH`：页面 id 与 manifest 不一致
- `INVALID_SCHEMA`：结构或必填字段错误
- `TOO_MANY_NODES` / `NODE_TOO_DEEP` / `STRING_TOO_LONG` / `TOO_MANY_LIST_ITEMS`：资源超限
- `INVALID_INPUT` / `DUPLICATE_INPUT_ID`：输入框声明错误
- `INVALID_ACTION` / `UNKNOWN_NAVIGATION_TARGET`：动作或导航目标非法

任一页面校验失败时，整个插件进入 `INCOMPATIBLE`，不会渲染部分页面，也不会尝试降级运行。
