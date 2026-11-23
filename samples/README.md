# 插件示例

- `com.lifehub.plugin.template`：最小声明式插件模板，适合从零创建页面。
- 记账与备忘录的完整示例在 `app/src/main/assets/plugins/`：
  - `com.lifehub.plugin.ledger`：动态数据源、周期导航、柱状图、环图、编辑页与权限声明。
  - `com.lifehub.plugin.memo`：记录列表、搜索、置顶与编辑页。
  - `com.lifehub.plugin.hello`：最小动态数据与 action 示例。

校验全部内置插件与示例：

```powershell
.\gradlew.bat :dev-harness:validatePlugins
```
