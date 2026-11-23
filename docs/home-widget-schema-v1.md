# 主页小组件 schema v1

- 状态：v1 草案（2026-11-13）
- 对应实现：`plugin-api` 的 `PluginHomeWidget`、`PluginManifestValidator`；`app` 的 `HomeWidgetHost`
- 与页面 schema 的关系：页面 schema 负责完整交互页面；本文档只描述主页上的紧凑小组件，两者不混用

## 1. 归属原则

| 层 | 归属 |
|---|---|
| 主页布局、排序、置顶、编辑模式、加载/错误/空态 | 宿主 |
| 小组件内容、数据源、标题、尺寸、点击动作 | 插件 |
| 打开插件、快捷新建“+” | 插件 manifest 的 `entryPage` / `quickCreate` |
| 编辑表单、筛选、统计详情 | 插件页面，不做成小组件 |

插件不注入任意 Compose/HTML；宿主只渲染固定模板。这样既保持“一切皆插件”的扩展方式，又保证主题、安全与兼容性。

## 2. manifest 示例

```json
{
  "pluginId": "com.lifehub.plugin.memo",
  "entryPage": "list",
  "quickCreate": { "pageId": "edit", "label": "新建任务" },
  "homeWidgets": [
    {
      "id": "today_tasks",
      "type": "summary",
      "title": "今日任务",
      "size": "small",
      "priority": 100,
      "sourceId": "memo_summary",
      "params": { "filter": "today" },
      "action": {
        "type": "navigate",
        "pageId": "list",
        "params": { "filter": "today" }
      }
    }
  ]
}
```

## 3. 字段

| 字段 | 必填 | 说明 |
|---|---|---|
| `id` | 是 | 插件内唯一，最多 64 字符；用户偏好按 `pluginId + id` 保存 |
| `type` | 是 | v1 支持 `summary`、`progress`、`list`、`quick_actions`；未知类型运行时忽略 |
| `title` | 是 | 展示标题，最多 20 字符 |
| `size` | 否 | `small`（1 列）、`medium`（2 列）、`large`（通栏），默认 `small` |
| `priority` | 否 | 0–100，仅用于默认排序；用户排序优先 |
| `sourceId` | 数据类必填 | 对应宿主注册的 `PluginPageDataHandler` |
| `params` | 否 | 传给数据源的参数，例如 `filter=today`、`period=month` |
| `action` | 否 | 点击小组件的动作；v1 只允许 `navigate` |
| `sensitive` | 否 | 金额/隐私数据；宿主提供“隐藏金额”开关 |
| `maxItems` | 否 | `list` 类型最多展示条数，默认 3，上限 5 |

## 4. 模板

| type | 展示 | 数据映射 | 交互 |
|---|---|---|---|
| `summary` | 标题 + 主数字 + 辅助说明 | `PluginDataResult.metrics[0]`，`metrics[1]` 作为说明 | 点击打开 `action` |
| `progress` | 标题 + 数值 + 进度条 | `metrics[0].value` 与 `metrics[0].ratio` | 点击打开 `action` |
| `list` | 标题 + 最多 3 条记录 | `PluginDataResult.items` | 条目 `clickAction` 或首个 `navigate` 动作 |
| `quick_actions` | 标题 + 1–2 个按钮 | 不需要数据源；使用 `quickCreate` / `entryPage` | 打开编辑页或插件页 |

小组件不是迷你页面：不支持表单、嵌套滚动、长列表；超过 3 条的数据请放在插件页面里。

## 5. 数据与动作

- 数据源复用 `PluginPageDataHandler`，返回现有 `PluginDataResult`。
- 数据请求的 `pageId` 使用 `home`；宿主可以通过 `surface=HOME_WIDGET` 区分场景（后续扩展）。
- 数据来源必须经过能力代理；插件未启用或未授权时小组件不展示。
- `PluginDataResult.isError = true` 表示拒绝、数据源缺失或执行失败，宿主显示“无法加载 + 重试”。
- `action` 只允许 `PluginAction.Navigate`；不允许直接执行 host action，避免主页误触破坏性操作。
- `quick_actions` 不新增动作类型，只复用 `quickCreate` 和 `entryPage`。

## 6. 生命周期与刷新

| 场景 | 行为 |
|---|---|
| 插件启用 | 小组件进入候选列表；默认按 priority 选前 3 个 |
| 插件禁用/隔离 | 小组件立即隐藏；保留用户偏好 |
| 插件卸载 | 小组件隐藏；数据按现有卸载策略保留或删除 |
| 重新启用/重装 | 恢复用户之前的显示与排序 |
| 主页恢复 | 并行加载可见小组件；失败可重试 |
| 插件动作完成 | 刷新该插件的小组件 |
| v1 后台刷新 | 不做；桌面小组件阶段再引入 WorkManager |

## 7. 限额

| 项 | 上限 |
|---|---|
| 单插件小组件数量 | 6 |
| 主页默认展示 | 3 |
| 主页最多展示 | 6（后续编辑模式） |
| `title` | 20 字符 |
| `list` 条目 | 5（默认 3） |
| 小组件动作参数 | 20 |

## 8. 版本与降级

- `homeWidgets` 是可选字段，向后兼容。
- 未知 `type` 不拒绝插件；宿主忽略该组件，`dev-harness` 输出 warning。
- 未知字段忽略，便于后续扩展。
- 数据源缺失/失败只影响该卡片，不阻塞主页其他内容。
- 第三方插件暂不开放；当前 data handler 仍是宿主注册的第一方 Kotlin 代码。

## 9. 校验

`dev-harness` 会输出：

```text
home widgets: today_tasks(summary)
data sources: memo_summary, memo_entries, memo_trash
  ^ 需要在宿主注册 PluginPageDataHandler
```

并校验：

- id 非空、唯一、长度合法；
- title 非空、长度合法；
- priority 在 0..100；
- 数据类小组件必须有 sourceId；
- `list` 的 maxItems 在 1..5；
- action 只能是 `navigate`，且目标页面存在；
- 单插件数量不超过 6。
