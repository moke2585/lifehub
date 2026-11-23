# 插件模板

这是最小声明式插件包，只包含 `manifest.json` 和页面 schema。

## 使用方式

1. 复制本目录到 `app/src/main/assets/plugins/`。
2. 同时修改目录名和 `manifest.json` 中的 `pluginId`，两者必须一致，例如 `com.example.myplugin`。
3. 修改 `name`、`description`、`version`、`icon` 与页面内容。
4. 需要宿主能力时，在 `capabilities` 中声明，并在宿主注册对应的 `PluginActionHandler`。
5. 运行校验：

```powershell
.\gradlew.bat :dev-harness:validatePlugins
```

6. 重新构建 APK，在插件主页启用并打开。

## 说明

- `com.lifehub.plugin.template.greet` 只是示例 action，宿主未注册时点击会提示“未注册的插件动作”。
- 动态列表、指标卡、柱状图和环图需要宿主注册 `PluginPageDataHandler`，并通过 `sourceId` 引用。
- 数据 schema 大于 1 时，宿主必须提供 `PluginDataMigration`；否则插件会被隔离并保留旧数据。
