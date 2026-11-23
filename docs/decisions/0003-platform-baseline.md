# ADR-0003：平台与依赖版本基线

- 状态：已接受
- 日期：2026-11-11
- 责任人：项目维护者

## 版本基线

| 项 | 取值 | 说明 |
|---|---|---|
| `minSdk` | 26（Android 8.0） | 自适应图标、现代后台限制；避开 23–25 的额外兼容成本 |
| `compileSdk` / `targetSdk` | 36 / 36 | 当前安装的 platform 为 android-36 |
| JDK | 17 | AGP 8.x 支持区间内的长期版本 |
| Gradle | 8.11.1（Wrapper 固定） | 与 AGP 8.10 的最低要求一致 |
| AGP | 8.10.1 | 支持 `compileSdk = 36` |
| Kotlin | 2.1.21 | Compose 编译器插件与 Kotlin 同版本 |
| Compose BOM | 2025.05.01 | Material 3 与 UI 测试版本统管 |
| 发布 ABI | `arm64-v8a` | 目标平台为安卓 armv8 |

依赖统一由 `gradle/libs.versions.toml` 管理，禁止使用动态版本（`+`、`latest.release`）和未锁定的快照依赖。

## ABI 与调试

- 首版没有 native 依赖；`abiFilters` 只约束未来引入的 `.so`，纯 JVM 代码在 x86_64 模拟器上仍可安装调试。
- 一旦引入 native 运行时（例如 QuickJS），必须提供 `arm64-v8a`，并为本地调试额外提供 `x86_64`；release 只打包 arm64。
- 发布验收至少覆盖：一台 arm64 真机、一个模拟器、`minSdk` 与 `targetSdk` 各一次回归。

## 升级规则

1. 升级 AGP/Gradle/Kotlin/Compose 必须单独提交，附 `testDebugUnitTest`、`:plugin-api:test`、`lintDebug`、`assembleDebug` 结果与真机冒烟记录。
2. 升级不得改变插件 SDK 兼容判断；若必须改变，按 ADR-0005 的升级策略处理并更新兼容 fixture。
3. 新增依赖需说明用途、许可证与体积影响；禁止引入动态 native 加载、反射式插件框架或未审计的脚本引擎。

## 变更方式

新增 ADR 记录版本变更与回归证据，并同步更新本文件与 `libs.versions.toml`。
