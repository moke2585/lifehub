# 生活聚合（LifeHub）

一个“一切皆插件”的生活聚合 Android 应用。首个版本由宿主以插件方式承载记账、任务清单与旅迹三个第一方插件。

设计见 [design.md](design.md)，实施计划见 [plan.md](plan.md)。

## 当前进度

- **P0 已完成**：技术形态、分发渠道、平台基线、数据语义、签名与故障策略已记录在 [docs/decisions](docs/decisions/README.md)。
- **P1 已完成**：Gradle/Kotlin/Compose 工程、`:app` 宿主壳、`:plugin-api` 契约模块、CI 与构建验收。
- **P2 已完成**：本地插件注册表、manifest 校验、启用/禁用状态持久化、生命周期事件，以及插件列表、详情与页面路由。内置示例插件位于 `app/src/main/assets/plugins/com.lifehub.plugin.hello`。
- **P3 已完成**：页面 schema v1、资源限额与结构化校验、Compose 声明式渲染器、设计 token、宿主 action 分发与页面导航参数。规范见 [插件页面 schema v1](docs/plugin-schema-v1.md)。
- **P4 已完成**：能力代理、启用前的显式授权确认、action 后台执行，以及按 `pluginId` 隔离的 SQLite 私有存储（独立数据库、集合/记录限额、无 SQL 透传）。
- **P5 已完成**：数据 schema 版本与事务迁移框架、卸载/重装/永久删除数据流程、口令加密备份导出/导入与完整性校验。格式见 [备份档案格式 v1](docs/backup-format.md)。
- **P6 已完成**：记账与任务清单（原备忘录）两个第一方插件，支持记账增删改查/金额格式化；任务清单支持截止日期、1–5 级优先级、Emoji 图标、紧急指数、紧急/最近两种排序、左右滑动完成与删除、30 天回收站、日常任务倍率，以及搜索/置顶/完成态置底；任务以独立大卡片展示完整标题、截止日期、剩余时间与紧急程度。列表页的“新建/回收站/统计”等高频入口改为顶栏图标，页面主体留给内容。已启用插件可在详情中开启底部导航独立标签，插件拥有独立图标；记账支持日/月/年周期切换、左右切换周期、回到今天，以及随周期变化的结余摘要、按日期分组的账本列表和独立统计页（支出柱状图与分类环图）。
- **交互优化迭代**：主页顶栏新增全局搜索，可搜索插件名称、任务标题/正文、账本分类/备注，并直接打开对应插件或编辑页。完成、删除等可逆操作改为立即执行并在 Snackbar 提供“撤销”，仅不可逆操作保留确认；记账记录支持点击编辑、左滑删除并可撤销，编辑页提供“保存并再记一笔”，列表支持按分类/备注搜索与支出/收入筛选，并在切换周期、编辑保存后保留当前视图。统计页新增“较上月、日均支出、最大单笔、Top 分类”洞察，预算页可设置月度预算，首页新增预算进度小组件。任务清单筛选默认“全部”，切换“今天”时按今天/日常/已完成分组，顶部支持“明天 18:00 交报告 p5”式的快速添加（可解析今天/明天/周X/日期与 p1–p5，并支持撤销）；搜索框使用键盘搜索键并保留筛选与排序；编辑页标题/金额自动聚焦，列表空态提供新建按钮；主页已启用插件卡片新增“+”快捷新建，直接进入对应编辑页。主页支持插件通过 manifest 的 `homeWidgets` 贡献紧凑小组件（今日任务、本月支出、预算进度），宿主统一渲染并支持隐藏金额；规范见 [主页小组件 schema v1](docs/home-widget-schema-v1.md)。第一批交互优化（2026-11-14）：全局搜索支持范围筛选、结果计数与返回保留关键词；可撤销操作优先展示；平板主页小组件一行最多三张；预算新增“今日还可花”；任务卡片点击进入编辑并提供可见“完成”按钮；插件页面返回恢复列表滚动位置。第二批：平板列表—详情改为 42/58 双栏，搜索支持最近关键词、清空与匹配高亮，账本筛选时显示“筛选结果 N 笔 · 支出/收入/净额”，滑动操作补充 TalkBack 动作。第三批：预算支持结转与分类预算，柱状图可点选查看金额，深色模式使用独立语义色，最近搜索支持单条删除。第四批：图表可下钻到当日/当月/分类明细，主页“今日概览”支持隐藏、排序与恢复默认，新增跟随系统/浅色/深色外观切换。第五批：预算按月独立并支持月份导航，结转按上月自己的预算计算，清除预算不再影响分类与结转；主页插件支持置顶与取消置顶。第六批：搜索历史保存插件范围，空搜索展示最近访问插件，结果按标题/名称相关度排序。第七批：柱状图触控目标提升到 48dp 并补充 TalkBack 删除动作，预算结转支持关闭/仅结余/双向，预算可批量应用到连续月份。第八批：平板分栏可拖拽并记忆宽度（双击复位），主页“我的工具”支持默认/最近使用排序。日常任务倍率改为点击 emoji 图标独立触发，连续操作只保留最新一条提示。第九批（动效）：页面进入/返回改为方向感知的 Material 共享轴 X 转场，前进时新页面从右侧轻推入、返回时反向滑出，并配合淡入淡出与轻微缩放；首页、插件详情、插件页面、搜索与备份之间按层级切换；底部标签改为 fade-through；平板分栏打开时右栏从右侧轻推入，返回时右栏淡出、左栏平滑展开；跟随系统“减少动效”时全部即时切换。
- **P7 已完成（首版范围）**：内置插件信任策略与统一路径校验、连续异常自动隔离、数据迁移失败回滚与隔离、宿主升级提示、按需初始化；远程插件包验签在开放外部包时再启用。安全用例与覆盖见 [ADR-0005](docs/decisions/0005-signing-upgrade-and-failure.md)。
- **设计系统刷新（2026-11-14）**：完成五批设计刷新：雾林 Calm Forest 颜色角色与中文 Typography、间距/形状/动效 token；顶栏改为淡绿表面并统一外壳与插件页面；主页 960dp 居中与双列插件卡片；渲染器统一卡片/输入框/列表/图表；页面与图表动效、应用内减少动效与 Android 12+ 动态取色。详见 [design-refresh-plan.md](docs/design-refresh-plan.md)。
- **P8 已完成（首版范围）**：新增 `:dev-harness` 插件包校验与生命周期模拟工具、最小插件模板与 `samples` 示例，以及 [插件开发指南](docs/plugin-development.md)；CI 可运行 `:dev-harness:validatePlugins`。
- **旅迹插件 v3.0（2026-11-15）**：第一方旅行足迹插件 `com.lifehub.plugin.travel` 采用“中国 → 省 → 市 → 区县”逐级下钻：全国地图同时绘制省界与市界（颜色、粗细不同），点击省份进入省地图，点击城市进入区县地图。点亮、地区评分、评论三者完全独立：地区评分使用五星控件、只保留一个并可随时修改；评论可多次发布、每条只含文字。市评分等于下属已点亮且有评分的区县均分，省评分等于下属已点亮且有评分的市均分，并在省/市地图标签与顶部指标中展示。旧版备注与 v2 随评评分通过 `dataSchemaVersion 3` 迁移：备注转为评论，随评评分聚合为地区评分，点亮记录保持不变。顶栏“我的评论”入口按时间收纳全部评论并标注省/市/区县与地区评分。全国一级页只显示省名与省评分，已点亮城市仅以绿色填充呈现；顶栏“评分榜”分省榜、市榜、县区榜，只收录已评分地区并按均分降序排列，同时提示已点亮但未评分的地区数量。一级页地图支持双指缩放、拖动与地图下方的倍率滑块，省名字号随省份面积与缩放自适应；顶栏新增地区搜索，可按名称直达省/市/区县，直接点亮或进入评分、评论；一级页可导出 3288×2747 的高清地图，左下角带导出日期并保存到系统相册。地图使用宿主 `geoMap` 节点与 `assetReader` 只读资产能力；内置地图以阿里云 DataV GeoAtlas 边界数据为主，东莞、中山、儋州、嘉峪关、天门、石河子、五家渠、北屯、铁门关、昆玉等没有区县的直筒子市/省直辖县级单位改用 OpenStreetMap 的乡镇街道边界（© OpenStreetMap contributors，ODbL）离线补齐。来源与处理方式见 [插件页面 schema v1](docs/plugin-schema-v1.md#地图数据)。
- **外部 ZIP 插件（阶段 C，2026-11-23）**：支持 `.lhplugin` 离线导入。主页顶栏“导入插件”通过系统文件选择器读取包，校验 ZIP 结构、路径、资源限额、页面 schema 与 ECDSA P-256 签名后展示名称、版本、发布者、能力和签名者指纹；确认后原子安装到 App 私有目录，失败恢复旧版本。内置与外部插件通过组合包源并存，外部插件标记为“外部导入 · 声明式”，备份仍走宿主 `PluginStorage`。规范见 [`.lhplugin` v1](docs/lhplugin-v1.md)。
- **P9 主体完成**：版本号 1.0.0、[隐私与数据说明](docs/privacy.md)、[发布检查表](docs/release-checklist.md)、release 签名校验任务与 [CI 发布工作流](.github/workflows/release.yml)。已在 vivo V2338A / Android 16 与三星 SM-X610 平板横屏上完成启用授权、记账、任务清单、统计、暗色模式、200% 字体、横屏、备份导出导入与应用升级回归；平板 ≥840dp 时任务与账本采用 38%/62% 双栏。页面栈、底部标签、日期条和 vivo 文件选择器兼容问题已修复。真实内存压力与故障注入项由单元测试覆盖或列入后续补测。
- **任务清单与旅迹细节优化（2026-11-20）**：任务清单筛选默认“全部”，搜索改为顶栏放大镜并新增独立搜索页；筛选、排序改为漏斗/三条线图标选择器并同行排列；任务卡片把编辑、完成、置顶收进行尾三点菜单，主体留给任务信息。旅迹一级页与省、市区县地图均支持双指缩放、拖动与地图下方倍率滑块；高清导出左上角标题改为“旅迹·中国”。
- **记账借还与自定义分类（2026-11-20）**：记账搜索改为顶栏放大镜并新增独立搜索页；分类支持自由输入与 40 个 Emoji 图标，新增“从分类库选择”页，自定义分类保存后自动记住图标；新增“借钱/还钱”分类与右上角借还台账：区分我借出（应收）与我借入（应付），还款可关联原借款并自动冲减剩余金额，未关联的还款可在台账中匹配；任务编辑的图标从 10 个扩展到 40 个。
- **分类选择与滑动体验（2026-11-22）**：记账分类库按使用频率排序，默认展示前 6 个，其余通过“展开其余”查看；展开后末尾提供“新建类型”，进入子页面填写名称并从折叠的 40 个图标中选择。任务与记账的图标选择默认只展示 10 个，其余通过“更多”展开。任务卡片的左滑删除、右滑完成提高了触发阈值，并增加图标、进度透明度与高对比底色，降低误触。
- **Material 3 Expressive 视觉重构（2026-11-22）**：功能与“一切皆插件”的架构保持不变，只调整主题与呈现。采用 Google 官方 `material3-adaptive-navigation-suite`：手机底部导航栏、平板自动切换左侧 `NavigationRail`；形状 token 放大为非对称大圆角，动效改为弹簧与错峰入场；首页新增生成式壁纸背景、渐变 Hero、工具插画与空态插画；插件页面卡片、记录行徽章与空态同步统一。支持动态取色（Android 12+）与应用内“减少动效”。官方开源依赖与素材授权见 [设计参考](#设计参考)。
- **主页与插件页背景（2026-11-22）**：主页换用两张新生成的 Material You 抽象壁纸（浅色鼠尾草绿/奶油、深色森林绿）；记账、任务清单、旅迹各配浅/深两版插件页背景（金色圆环、纸张层次、等高线等低对比主题），顶部可见、向下渐隐，列表与图表保持可读。插件页不显示品牌横幅。指标卡改为大号主指标 + 等宽数字小卡，窄栏自动折行避免金额截断；周期导航改为 tonal 圆形箭头并居中“回到今天”；柱状图改为渐变圆角柱并按月份自动收窄列宽，环图中心金额放大；地图圆角与图例改为胶囊样式，空态与卡片描边统一。所有改动只涉及渲染器与静态素材，插件 schema、action 与数据行为未变。

首版插件形态为 APK 内置声明式插件包 + 第一方 Kotlin action；后续版本按 [ADR-0006](docs/decisions/0006-external-plugin-packaging.md)/[ADR-0007](docs/decisions/0007-third-party-distribution.md) 增加外部 `.lhplugin` 与 APK 插件，不引入宿主内动态 dex/so 加载。

## 仓库布局

宿主与每个插件都是同级、独立的本地 Git 仓库：

```text
__name__/
├── lifehub/                 宿主仓库（本目录）
├── lifehub-plugin-ledger/   记账插件仓库
├── lifehub-plugin-memo/     任务清单插件仓库
└── lifehub-plugin-travel/   旅迹插件仓库
```

宿主通过 `settings.gradle.kts` 把三个插件项目纳入同一次 Gradle 构建，但 Git 历史互不包含。单独签出宿主时需要同时准备三个同级插件目录。

## 插件开发

- 开发指南：[docs/plugin-development.md](docs/plugin-development.md)
- `.lhplugin` v1 规范：[docs/lhplugin-v1.md](docs/lhplugin-v1.md)
- 最小模板：[samples/com.lifehub.plugin.template](samples/com.lifehub.plugin.template)
- 内置插件：Hello 位于 `app/src/main/assets/plugins/`；记账、任务清单、旅迹分别在 `../lifehub-plugin-ledger|memo|travel/src/main/assets/plugins/`
- 校验全部内置插件与示例：

```powershell
.\gradlew.bat :dev-harness:validatePlugins
```

- 校验外部 `.lhplugin`（含结构与签名）：

```powershell
.\gradlew.bat :dev-harness:run --args="path\to\plugin.lhplugin"
```

## 发布

- 隐私、数据与能力说明：[docs/privacy.md](docs/privacy.md)
- 发布检查表与真机回归矩阵：[docs/release-checklist.md](docs/release-checklist.md)
- 发布签名与 CI：[.github/workflows/release.yml](.github/workflows/release.yml)
- 发布前置检查：`.\gradlew.bat :app:checkReleasePrerequisites`
- 未配置签名时只会生成 `app-release-unsigned.apk`，不可直接发布。

## 设计参考

记账插件的交互借鉴了以下开源项目，仅参考交互模式，未复制代码或资源：

- [Ivy Wallet](https://github.com/Ivy-Apps/ivy-wallet)：顶部结余摘要、按日期分组的交易列表、Material 3 卡片。
- [Cashew](https://github.com/jameskokoska/Cashew)：分类视觉、快速记账、图表化收支。
- [My Expenses](https://github.com/mtotschnig/MyExpenses)：交易录入与日期组织方式。
- 周期导航采用“当前范围标签 + 左右切换 + 回到今天”，参考 [MoneyStats 周期切换](https://ttdeveloped.com/en/apps-2/moneystats/user-guide/structure-and-usage/)；Cashew 的日历视图、灵活周期和图表也是柱状图与日/月/年粒度的参考。
- 旅迹的省/市/区县边界来自 [阿里云 DataV GeoAtlas](https://geo.datav.aliyun.com/areas_v3/bound/)，没有区县的直筒子市与省直辖县级单位的乡镇街道边界来自 [OpenStreetMap](https://www.openstreetmap.org/copyright)（© OpenStreetMap contributors，ODbL）；两者均只使用边界坐标并离线简化为归一化多边形，不包含任何在线请求；地图交互参考高德/腾讯地图的“点亮城市”玩法。

Material 3 重构使用 Google 官方开源实现与设计资源（均可免费使用）：

- [Material 3 设计规范](https://m3.material.io/) 与 [Material 3 Expressive](https://m3.material.io/blog)：形状、色彩角色、字阶、运动物理与自适应布局。
- [Jetpack Compose Material3](https://developer.android.com/jetpack/compose/designsystems/material3)：Apache 2.0，包含本轮新增的 `NavigationSuiteScaffold`（`material3-adaptive-navigation-suite`）。源码在 [androidx/androidx](https://github.com/androidx/androidx)。
- [Material Symbols](https://fonts.google.com/icons)：Apache 2.0 图标库；本轮沿用项目已有矢量图标，未替换图标资源。
- [Material Theme Builder](https://m3.material.io/theme-builder) 与 [Material Color Utilities](https://github.com/material-foundation/material-color-utilities)：Apache 2.0，用于动态取色与配色校验。
- 主页壁纸、三个插件页背景、首页工具插画与空态插画均由 SiliconFlow 文生图生成，裁剪水印并压缩后作为应用内置静态素材，不引入运行时生图能力。

## 环境要求

- JDK 17
- Android SDK：`platforms;android-36`、`build-tools;35.0.0` 或更高
- 本机 SDK 路径写入 `local.properties`（不要提交）：

```properties
sdk.dir=E\:\\app_data\\Android
```

## 构建与验收

以下命令均在 `lifehub/` 目录执行：

```powershell
$env:JAVA_HOME="C:\path\to\jdk-17"
.\gradlew.bat testDebugUnitTest :plugin-runtime:test :plugin-api:test lintDebug assembleDebug
```

调试 APK 输出在 `app/build/outputs/apk/debug/`。发布签名通过环境变量或 Gradle 属性注入，私钥不入库：

```powershell
$env:LIFEHUB_RELEASE_STORE_FILE="D:\keys\lifehub-release.jks"
$env:LIFEHUB_RELEASE_STORE_PASSWORD="..."
$env:LIFEHUB_RELEASE_KEY_ALIAS="lifehub"
$env:LIFEHUB_RELEASE_KEY_PASSWORD="..."
```

工程位于含中文的路径下，`gradle.properties` 已设置 `android.overridePathCheck=true`；如后续工具链仍拒绝非 ASCII 路径，可用 ASCII 目录联接（junction）作为临时构建入口。

## 依赖规则

- 版本统一由 `gradle/libs.versions.toml` 管理，禁止 `+`、`latest.release` 等动态版本。
- 宿主内禁止引入动态 dex/native 加载、未审计的脚本引擎或远程代码下载执行；外部 ZIP/APK 插件形态按 ADR-0006/0007 执行。
- 新增依赖需说明用途、许可证与体积影响，并通过 P1 验收命令。

## 模块

| 模块 | 作用 |
|---|---|
| `:app` | 宿主：Compose 主题、插件注册表、插件列表/详情/页面路由、错误页 |
| `:plugin-api` | 插件 SDK 契约：manifest、描述符、状态、生命周期、兼容性校验 |
| `:plugin-runtime` | 插件模块契约 `PluginModule`、宿主侧通用存储工具、`.lhplugin` 读取与验签 |
| `:plugins:ledger` | 记账插件：代码 + manifest/pages/assets |
| `:plugins:memo` | 任务清单插件：代码 + manifest/pages/assets |
| `:plugins:travel` | 旅迹插件：Android library，含地图导出 |
| `:plugin-testing` | 插件模块单元测试使用的内存存储 |
| `:dev-harness` | 插件包校验与生命周期模拟工具 |

新增第一方插件时，在与宿主同级的 `lifehub-plugin-<name>/` 建独立仓库，并在 `settings.gradle.kts` 中以 `projectDir` 纳入构建、加入 `FirstPartyPluginHandlers.modules`，资产放到模块的 `src/main/assets/plugins/<pluginId>/`。第三方声明式插件可打包为带 `manifest.sig` 的 `.lhplugin` 离线导入。插件市场与外部 APK 形态见 [实施计划](docs/plugin-market-plan.md)。

## 后续步骤

1. P7/P9：安全加固、故障护栏、真机回归与发布准备。
2. 公开插件市场与云同步不进入首版。
