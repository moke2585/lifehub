# ADR-0005：插件签名、升级与故障处理

- 状态：已接受
- 日期：2026-11-11
- 责任人：项目维护者

## 插件包签名

- 算法：ECDSA P-256 + SHA-256，对包内文件清单生成摘要后做 detached signature（`manifest.sig`）。
- 宿主内置受信任公钥集合；同一 `pluginId` 的更新必须由同一签名者签署。
- 密钥轮换采用双签名过渡：新版本同时带旧、新签名，宿主确认连续性后更新信任集合，再移除旧公钥。
- 首版插件随 APK 发布，APK 签名（v2+）是唯一信任锚；插件级 detached signature 在开放远程/ZIP 包时再启用，避免在没有外部来源时引入无实际收益的密钥流程。

## 密钥管理

- 发布私钥离线保存两份，口令与私钥分开保管；不得进入仓库、CI 日志、崩溃报告或普通备份。
- CI 只通过受保护的 Secret 注入，构建日志禁止打印口令与私钥路径。
- 私钥丢失视为该 `pluginId` 无法继续发布更新：只能启用新 ID，并提供数据迁移工具；不得尝试伪造签名者连续性。

## 版本与升级

- 安装/启用前校验 `pluginSdkVersion`、`minHostVersion`、可选 `maxHostVersion` 与 `dataSchemaVersion`。
- 宿主只支持 ADR-0003 声明的主版本；不兼容时禁用插件并展示宿主升级提示，**不降级运行**。
- 数据迁移前创建插件级快照，迁移在事务内执行；失败回滚并禁用新版本，旧数据保留。
- 升级新增 capability 时必须重新向用户展示并取得同意，不能沿用旧授权。

## 故障处理

| 场景 | 处理 |
|---|---|
| 插件连续崩溃 | 10 分钟内 3 次自动禁用，进入安全模式（只加载宿主与已验证插件） |
| 崩溃计数重置 | 一次成功运行或用户显式启用；不使用定时器静默重置 |
| 运行时超时/死循环 | 终止本次调用并返回受控错误；不阻塞宿主主线程 |
| 备份档案损坏 | 拒绝导入，保留现有数据，提示重新导出 |
| schema/资源超限 | 拒绝安装或渲染，给出结构化错误，不进入空白页 |

## P7 实现记录（2026-11-12）

- **信任锚**：`PluginTrustPolicy` 只接受 `PluginPackageTrust.APK_SIGNED` 的包源；未知或外部来源在注册表加载前直接拒绝。`AssetPluginPackageSource` 与 manifest 校验共用 `PluginPathRules`，拒绝绝对路径、反斜杠、空段、`.` 与 `..`。
- **插件级签名**：在开放远程包或 ZIP 包之前不启用。启用前置条件：外部包分发立项、公钥托管、密钥轮换演练和安全测试完成；届时按上文 ECDSA P-256 方案实现 `manifest.sig` 与同 ID 签名者连续性校验。
- **连续异常隔离**：`PluginHealthMonitor` 只统计处理器抛出的未预期异常；默认 10 分钟内 3 次即自动停用插件、撤销能力并标记隔离；一次成功运行或用户显式重新启用会清零。用户输入校验等受控 `Failure` 不计入。
- **数据迁移失败**：`SqlitePluginStorageFactory` 捕获迁移异常后隔离插件；迁移事务回滚，旧数据保留。
- **兼容性提示**：`minHostVersion`、`pluginSdkVersion` 不满足时插件保持不可启用，详情页显示“需要更新 LifeHub”提示。
- **按需初始化**：注册表启动时只解析 manifest 与页面 schema；action/data handler 仅在用户操作或打开页面时执行，未启用插件不会预加载资源。
- **密钥**：仓库只读取 `lifehub.release.*` Gradle 属性或环境变量，不包含私钥；发布前按上文执行离线双份备份与轮换演练。

## 安全用例与自动化覆盖

| 用例 | 覆盖 |
|---|---|
| 页面路径穿越、反斜杠、绝对路径 | `PluginPathRulesTest`、`PluginManifestValidatorTest.rejectsUnsafePagePath` |
| 伪造/未知来源插件包 | `PluginRegistryTest.untrustedPackageSourceIsRejected` |
| 权限混淆：未声明、未授权、已停用 | `PluginCapabilityBrokerTest`、`PluginActionDispatcherTest` |
| 恶意/超限 schema、超大页面 | `PluginPageSchemaTest`、`PluginRegistryTest.oversizedPageIsRejected` |
| 数据迁移失败回滚 | `PluginDataSchemaManagerTest` |
| 连续异常隔离与重置 | `PluginHealthMonitorTest`、`PluginActionDispatcherTest` |
| 按需初始化 | `PluginActionDispatcherTest.constructingDispatcherDoesNotInvokeHandlers` |
| 日志不记录数据正文 | `AppLogContractTest` + 代码审查；日志接口只接受事件名、插件 ID、错误码与异常堆栈 |

仍需在真机/集成环境执行的项：SQLite 跨插件隔离、备份导入失败、进程重启后的隔离状态恢复、发布签名与密钥备份演练。

## 变更方式

签名算法、信任集合或崩溃阈值的修改必须新增 ADR，并附带安全测试与迁移演练记录。
