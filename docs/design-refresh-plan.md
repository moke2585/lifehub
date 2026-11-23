# LifeHub 设计系统与视觉重构计划（v1）

- 状态：第 0–5 批已实施（设计刷新计划完成）
- 日期：2026-11-14
- 关联：[优化计划 v2](optimization-plan-v2.md)、[发布检查清单](release-checklist.md)
- 范围：宿主全部界面、插件页面渲染器、主题能力
- 不改变：插件 JSON schema、插件数据格式、微内核与能力边界、现有用户数据
- 核心目标：**降低视觉噪音、建立稳定层级、统一节奏，让界面“看得舒服、用得顺手”**

---

## 0. 结论先行

推荐方向 **A「雾林 Calm Forest」**：

1. 保留绿色作为品牌识别，但**不再让深绿占满每屏顶栏**。顶栏回归表面色，绿色只出现在主行动、选中态、关键数字和品牌标识上。
2. 用**五级表面色阶**建立层次，卡片不再依赖阴影。浅色主题的白卡、浅灰底、分隔线形成清晰但柔和的分层。
3. 重设**中文排版**：字号阶梯更少、行高更松（正文 1.55–1.65）、字重只用 400/500/600 三档，取消散落的 Bold 700。
4. 间距收敛到 **4/8/12/16/24/32/48**，页面边距按窗口宽度切换，杜绝 10/14/18/20 混用。
5. 动效统一为**短、稳、可关闭**：页面转场 220ms、列表 220ms、按压 120ms，支持系统“移除动画”。
6. 主题能力补齐：**动态取色（可选）、深浅色、字体缩放、减少动效**；启动窗口背景跟随主题，消除暗色闪白。

预期量化结果：

| 指标 | 现状 | 目标 |
|---|---|---|
| 每屏主色（深绿大面积）占比 | 顶栏整条 + 多处按钮，约 15–25% | ≤ 10% |
| 正文对比度 | 部分 `onSurfaceVariant` 接近下限 | 全部 ≥ 4.5:1 |
| 裸 dp 间距值 | 58 处（含 10/14/18/20） | 0 处非 token 值 |
| 分散字重覆盖 | 19 处 `FontWeight.*` | ≤ 4 处（仅数字强调） |
| 卡片阴影 | 24 处 elevation | 卡片 0 阴影，仅浮层保留 |
| 硬编码颜色 | 图表 16 个色值 + 窗口色 | 0 处（全部走 token） |

---

## 1. 调研结论：优秀 App 的共性

### 1.1 七个可验证的共性

1. **层级靠对比，不靠装饰。**
   Refactoring UI 的核心方法是“先用灰度设计，最后再加颜色”：用字号、字重、颜色深浅和留白建立主/次/三级信息，而不是靠边框、阴影、图标堆叠。约束化的间距、字号、颜色、阴影刻度，比“凭感觉调”更稳定。
2. **颜色是角色系统，不是色卡。**
   Material 3 把颜色定义为 26 个角色（primary、secondary、tertiary、error、surface、outline 及其 on/container 配对）。角色配对保证对比度；primary 只用于最高优先级行动，secondary 用于低优先级元素，tertiary 用于需要强调但不必立即行动的小元素。
3. **字体少、字阶稳、行高随语言调整。**
   Apple HIG 明确建议尽量减少字样数量，避免 Light/Thin 字重，优先 Regular/Medium/Semibold/Bold；正文默认 17pt，最小 11pt。Material 3 的字体比例以 14 为基准、Major Second（1.125）比例扩展；中文属于“medium language height”，行高应比拉丁文高约 7%。
4. **空间有节奏。**
   Android 使用 8dp 基准网格，图标、文字等小元素对齐 4dp 网格。间距不是“看起来差不多”，而是固定刻度。
5. **用表面分层，而不是到处投影。**
   高优先级内容靠近前景、低优先级内容退到背景；阴影只用于真正浮起的元素（对话框、菜单、Snackbar）。Material 3 用 surface container 五级色阶表达层级，深色主题尤其不能靠“越黑越深”。
6. **动效服务理解，不服务炫技。**
   M3 Expressive 引入基于物理的 motion physics，强调自然、可中断、有弹性；Calm UX 则强调“可预测、短、克制、可关闭”。对工具类 App，动效的目标是解释状态变化，而不是制造注意力。
7. **平静来自一致性。**
   Calm UX 的实践结论：熟悉的布局、一致的间距、稳定的导航、克制的颜色和少而明确的强调色，能显著降低认知负担。“清晰替代杂乱，宽容替代恐惧，可预测替代噪音”。

### 1.2 可直接落地的数字依据

| 依据 | 数值/规则 | 来源 |
|---|---|---|
| 正文对比度 | 小文本 ≥ 4.5:1，大文本 ≥ 3:1 | Material 3 Typography / Android 无障碍 |
| 正文字号下限 | 不小于 12sp | Android 无障碍指南 |
| 中文行高 | 在拉丁文基础上约 +7% | Material 3 language height |
| 字体缩放 | 全部使用 sp，支持系统字体缩放 | Android 无障碍指南 |
| 触控目标 | ≥ 48dp | Material / Android 无障碍 |
| 间距网格 | 8dp 基准，小元素 4dp | Android 设计规范 |
| 字体数量 | 尽量少，避免 Light/Thin | Apple HIG |
| 免费商用字体 | MiSans 可免费商用，嵌入需注明；Noto 为 OFL | MiSans FAQ / Noto docs |

### 1.3 来源

- [Material 3 Typography](https://m3.material.io/styles/typography/type-scale-tokens)
- [Material 3 Color roles](https://m3.material.io/styles/color/roles)
- [Material 3 Motion](https://m3.material.io/styles/motion/)
- [M3 Expressive](https://m3.material.io/)
- [Apple HIG Typography（中文）](https://developer.apple.com/cn/design/human-interface-guidelines/typography)
- [Android 无障碍](https://developer.android.google.cn/design/ui/mobile/guides/foundations/accessibility?hl=zh-cn)
- [Android 网格与单位](https://developer.android.google.cn/design/ui/mobile/guides/layout-and-content/grids-and-units?hl=zh-cn)
- [Refactoring UI 原则摘要](https://lobehub.com/skills/jeremylongshore-claude-code-plugins-plus-skills-wondelai-refactoring-ui)
- [Designing Calm: UX Principles](https://www.uxmatters.com/mt/archives/2025/05/designing-calm-ux-principles-for-reducing-users-anxiety.php)
- [MiSans 字体 FAQ](https://hyperos.mi.com/font/faq)
- [Noto 字体使用说明](https://notofonts.github.io/noto-docs/website/use/)

---

## 2. 现状审计

| 维度 | 现状 | 问题 | 影响 |
|---|---|---|---|
| 顶栏 | `HomeScreen.kt:122-127`、`GlobalSearchScreen.kt:195-197`、`PluginPageScreen.kt:177-181` 全部使用 `primary` 深绿 | 每屏顶部一整条重色，内容区被压暗，顶栏与内容争抢注意力 | 视觉沉重、页面之间缺乏呼吸感 |
| 字体 | `Theme.kt:109-112` 未传 `typography`，使用 Roboto 默认比例；19 处 `FontWeight` 覆盖；`labelSmall` 11sp 用于版本号等 | 中文行高偏紧；字重散落；小字承担了不应承担的信息 | 长列表阅读疲劳，层级不稳定 |
| 颜色 | `Theme.kt:15-77` 缺少 `inverseSurface`、`inverseOnSurface`、`inversePrimary`、`surfaceDim`、`surfaceBright`、`scrim`；图表色板硬编码 `PluginPageRenderer.kt:1256-1276`；`colors.xml` 窗口底色固定浅色 | 颜色角色不完整，图表与主题脱钩，暗色启动闪白 | 暗色体验割裂；换肤需改代码 |
| 间距 | `ui/` 下 58 处裸 dp；10/14/18/20 混用（如 `GlobalSearchScreen.kt:271/358/375/391`） | 未遵循 8dp 节奏 | 页面“看起来不齐”，但说不出哪里不对 |
| 形状 | `DesignTokens.kt:23-29` 为 4/8/12/16/24；图标容器 14dp（`HomeScreen.kt:687`）、胶囊 50、卡片默认 12 | 同一界面出现 4–5 种圆角，缺乏统一语言 | 精致感下降 |
| 层次 | 24 处 elevation；卡片统一 `surface` + 1dp 阴影 | 浅色下白卡与 `#F5F7F5` 背景对比弱，靠阴影硬撑 | 卡片边界模糊，列表密度高时显脏 |
| 暗色 | `MainActivity.kt:37-43` 状态栏图标明暗与主题相反；`themes.xml` 启动背景固定 `#F5F7F5` | 暗色启动闪白；状态栏逻辑依赖深绿顶栏 | 夜间使用刺眼 |
| 动效 | 页面切换、列表增删、图表更新均为默认或无动画 | 状态变化缺少空间连续性 | 操作“跳”，缺少品质感 |
| 主题能力 | `ThemePreferenceStore` 仅 SYSTEM/LIGHT/DARK | 无动态取色、无对比度档位、无减少动效 | 无法适配用户偏好 |

---

## 3. 方向选择

| 方向 | 关键词 | 适合 | 风险 |
|---|---|---|---|
| **A. 雾林 Calm Forest（推荐）** | 中性暖灰底、深绿品牌、tonal 分层、少阴影、短动效 | 日常高频工具、记账、任务、长时间阅读 | 需要克制，容易做得“素”；靠排版和色阶取胜 |
| B. 纸感编辑风 | 米白纸感、衬线大标题、细分隔线、杂志留白 | 内容阅读、笔记、周报 | 数据密集页面可读性下降；中文字体搭配难度高 |
| C. M3 Expressive | 高饱和、大圆角、弹性动效、对比形状 | 年轻化、社交、内容消费 | 对记账/任务类工具过吵；趋势性强，易过时 |

**推荐 A**：LifeHub 是“每天打开多次、停留时间中等、以数据与任务为主”的工具集合。它需要信任感、平静感和高信息可读性，而不是强表现力。A 也保留了此前用户指定的 Forest/Emerald 绿色家族，但把它从“背景色”降级为“强调色”，品牌识别仍在。

---

## 4. 设计规范 v1（Design Tokens）

### 4.1 色彩

**原则**

- 60/30/10：约 90% 面积为中性表面，≤8% 品牌绿，≤2% 语义色。
- `primary` 只用于：主按钮、选中态、当前导航项、关键数字、品牌标识。
- 不使用纯黑 `#000000` 作为背景或正文；不使用大面积纯白作为页面底色。
- 插件只能通过 `MaterialTheme.colorScheme` 与扩展语义色取色，不允许硬编码。

**浅色主题**

| 角色 | 色值 | 用途 |
|---|---|---|
| primary | `#1F5C43` | 主按钮、选中、关键数字 |
| onPrimary | `#FFFFFF` | 主色上的文字 |
| primaryContainer | `#D6EFDD` | 选中背景、品牌徽标底 |
| onPrimaryContainer | `#0B2A1B` | 容器上的文字 |
| secondary | `#4C6B57` | 次级行动、未选中图标 |
| onSecondary | `#FFFFFF` | — |
| secondaryContainer | `#DDE9DF` | 次级容器、标签底 |
| onSecondaryContainer | `#12251A` | — |
| tertiary | `#7A5A32` | 提醒、图表暖色锚点 |
| onTertiary | `#FFFFFF` | — |
| tertiaryContainer | `#F3E5CF` | 提醒容器 |
| onTertiaryContainer | `#2E1F0C` | — |
| error | `#B3261E` | 错误、删除 |
| onError | `#FFFFFF` | — |
| errorContainer | `#F9DEDC` | 错误容器 |
| onErrorContainer | `#410E0B` | — |
| background | `#F7F8F6` | 页面底色 |
| onBackground | `#17201A` | 页面文字 |
| surface | `#FCFDFB` | 卡片、面板 |
| onSurface | `#17201A` | 卡片文字 |
| surfaceVariant | `#E1E8E2` | 输入框底、标签底 |
| onSurfaceVariant | `#5A665E` | 次要文字（对白底约 6.0:1） |
| outline | `#7C8A80` | 输入框边、强分隔 |
| outlineVariant | `#DDE4DE` | 卡片描边、弱分隔 |
| inverseSurface | `#2B322D` | Snackbar |
| inverseOnSurface | `#EFF3EF` | Snackbar 文字 |
| inversePrimary | `#9ED8B5` | Snackbar 行动 |
| scrim | `#000000` | 遮罩 |
| surfaceDim | `#DDE3DD` | 低层表面 |
| surfaceBright | `#FCFDFB` | 高层表面 |
| surfaceContainerLowest | `#FFFFFF` | 最高层内容 |
| surfaceContainerLow | `#F8FAF8` | 列表底 |
| surfaceContainer | `#F2F4F1` | 分区底 |
| surfaceContainerHigh | `#ECEFEB` | 悬浮容器 |
| surfaceContainerHighest | `#E5E9E4` | 最强容器 |

**深色主题**

| 角色 | 色值 |
|---|---|
| primary | `#9ED8B5` |
| onPrimary | `#06301E` |
| primaryContainer | `#214836` |
| onPrimaryContainer | `#D6EFDD` |
| secondary | `#B3CBB9` |
| onSecondary | `#1E3326` |
| secondaryContainer | `#31493A` |
| onSecondaryContainer | `#DDE9DF` |
| tertiary | `#E5C08A` |
| onTertiary | `#3D2B10` |
| tertiaryContainer | `#553F1E` |
| onTertiaryContainer | `#F3E5CF` |
| error | `#FFB4AB` |
| onError | `#690005` |
| errorContainer | `#93000A` |
| onErrorContainer | `#FFDAD6` |
| background | `#121612` |
| onBackground | `#E3E9E4` |
| surface | `#171C18` |
| onSurface | `#E3E9E4` |
| surfaceVariant | `#3A453D` |
| onSurfaceVariant | `#A9B5AC` |
| outline | `#78857B` |
| outlineVariant | `#3A453D` |
| inverseSurface | `#E3E9E4` |
| inverseOnSurface | `#2B322D` |
| inversePrimary | `#1F5C43` |
| surfaceDim | `#121612` |
| surfaceBright | `#333C35` |
| surfaceContainerLowest | `#0D110E` |
| surfaceContainerLow | `#1B211C` |
| surfaceContainer | `#1F2620` |
| surfaceContainerHigh | `#242C26` |
| surfaceContainerHighest | `#2A332C` |

**扩展语义色（收支、提醒、信息）**

| 语义 | 浅色 | 深色 | 用途 |
|---|---|---|---|
| positive | `#2E6B4F` | `#8FD3AE` | 收入、完成、正向变化 |
| negative | `#B3261E` | `#FFB4AB` | 支出、超预算、错误 |
| warning | `#8A5A00` | `#F0C36A` | 临近截止、预算预警 |
| info | `#2C5C8A` | `#9CC7EF` | 提示、说明 |

**图表色板（8 色，色相分散）**

| # | 浅色 | 深色 |
|---|---|---|
| 1 | `#2F6B4F` | `#8FC7A8` |
| 2 | `#6E8B3D` | `#A9C46A` |
| 3 | `#B08948` | `#E0BE7A` |
| 4 | `#A65E4E` | `#D89A88` |
| 5 | `#5B7C99` | `#8FB0CC` |
| 6 | `#7A6B9A` | `#B0A3CC` |
| 7 | `#4E8C8A` | `#7FC3C0` |
| 8 | `#8A8F98` | `#AEB4BC` |

图表规则：
- 网格线用 `outlineVariant`，坐标标签用 `onSurfaceVariant`。
- 选中柱/扇区用 `primary`，其余用图表色板；同一图内不超过 6 色。
- 环图中心留白，中心显示总额（`titleLarge`，600）。
- 深色主题降低饱和度，不使用荧光色。

**使用规则**

| 元素 | 颜色角色 |
|---|---|
| 页面背景 | `background` |
| 页面顶栏 | `surface`（滚动后 `surfaceContainer`） |
| 卡片 | `surfaceContainerLow` + `outlineVariant` 1dp 描边 |
| 悬浮菜单/对话框 | `surfaceContainerHigh` + elevation 3 |
| 主按钮 | `primary` / `onPrimary` |
| 次按钮 | `secondaryContainer` / `onSecondaryContainer` |
| 文本按钮 | `primary` |
| 正文 | `onSurface` |
| 次要说明 | `onSurfaceVariant` |
| 分隔线 | `outlineVariant` |
| 禁用态 | `onSurface` 38% |

### 4.2 字体

**字体策略**

- 默认：**系统字体**（Android 的 Roboto + 系统中文字体，三星设备为 Noto Sans CJK）。零体积、自动跟随系统字体缩放、无需授权。
- 可选：**MiSans**（免费商用，嵌入需在关于页注明），可提供跨设备一致的品牌感；需评估 APK 体积（建议只打包 Regular/Medium/SemiBold 三个子集）。
- 不推荐同时混用多套字体；标题与正文使用同一字族。

**字阶（覆盖 M3 默认值，为中文重设行高）**

| 样式 | 字号/行高 | 字重 | 用途 |
|---|---|---|---|
| displaySmall | 32/44 | 600 | 首页 hero 数字、年度总额 |
| headlineLarge | 28/38 | 600 | 页面主标题 |
| headlineMedium | 24/34 | 600 | 分区大标题 |
| headlineSmall | 21/30 | 600 | 卡片组标题 |
| titleLarge | 19/28 | 600 | 对话框标题、列表主标题 |
| titleMedium | 16/24 | 500 | 卡片标题、表单标签 |
| titleSmall | 14/22 | 500 | 行标题、小节标题 |
| bodyLarge | 16/26 | 400 | 长文本、说明 |
| bodyMedium | 14/22 | 400 | 默认正文 |
| bodySmall | 13/20 | 400 | 辅助说明 |
| labelLarge | 14/20 | 500 | 按钮 |
| labelMedium | 12/16 | 500 | 标签、状态 |
| labelSmall | 11/16 | 500 | 仅 overline/版本号等非关键信息 |

**中文排版规则**

- 正文不小于 14sp；`labelSmall` 不得承载关键信息（金额、截止日期、错误原因）。
- 行高：正文 1.55–1.65；标题 1.35–1.45。
- 字重只用 400/500/600；取消 700 Bold，避免中文“糊成一块”。
- 中文不设负字距；数字与金额使用 tabular figures（等宽数字），便于纵向对齐。
- 长文本每行 28–38 个汉字；平板内容区最大宽度 720dp。
- 字体缩放 0.85–2.0 时：标题可降级（`headlineSmall` → `titleLarge`），按钮文字不截断，列表行高随内容增长。

### 4.3 间距与布局

| Token | 值 | 用途 |
|---|---|---|
| space2 | 2dp | 极细内衬 |
| space4 | 4dp | 图标与文字 |
| space8 | 8dp | 紧凑元素 |
| space12 | 12dp | 表单内间距 |
| space16 | 16dp | 卡片内边距、手机页面边距 |
| space24 | 24dp | 分区间距、平板页面边距 |
| space32 | 32dp | 大分区、空状态 |
| space48 | 48dp | 页面顶部留白 |

布局规则：
- 手机页面边距 16dp；≥840dp 窗口 24dp；≥1200dp 32dp。
- 卡片内边距 16dp；紧凑卡片 12dp。
- 分区之间 24dp；卡片之间 12dp；标题与内容 8–12dp。
- 列表单行最小高度 56dp，双行 72dp，三行 88dp。
- 平板主页小组件使用自适应网格：≥840dp 两列，≥1200dp 三列；卡片等高。
- 分栏模式两侧各自使用 24dp 页面边距，分隔条保持 24dp 热区。

### 4.4 形状与层次

| Token | 值 | 用途 |
|---|---|---|
| shapeXS | 8dp | 标签、徽标 |
| shapeS | 12dp | 输入框、按钮、chip |
| shapeM | 16dp | 卡片、列表容器 |
| shapeL | 20dp | 大卡片、面板 |
| shapeXL | 28dp | 对话框、底部面板顶部 |

- 图标容器 44dp，圆角 12dp；页面图标 24dp，内联图标 20dp。
- 按钮高度 48dp（主按钮）/ 40dp（紧凑）；胶囊按钮仅用于 chip。
- 卡片：`surfaceContainerLow` + `outlineVariant` 1dp 描边 + 0 阴影。
- 浮层：`surfaceContainerHigh` + elevation 3（对话框、菜单、Snackbar）。
- 分隔线：1dp `outlineVariant`，只用于列表行之间，不用于卡片外框。
- 层次顺序：`background` → `surfaceContainer` → `surfaceContainerLow` → `surfaceContainerLowest`；不使用纯白大面积。

### 4.5 动效

| Token | 值 | 用途 |
|---|---|---|
| motionShort | 120ms | 按压、图标切换 |
| motionMedium | 220ms | 页面转场、列表增删 |
| motionLong | 320ms | 图表增长、展开面板 |
| easingStandard | FastOutSlowIn | 通用 |
| easingEmphasized | CubicBezier(0.2, 0, 0, 1) | 页面进入 |
| springDefault | damping 0.85 / stiffness 380 | 可中断手势、按钮回弹 |

规则：
- 页面推入：淡入 120ms + 水平位移 24dp/220ms；返回反向。
- 底部标签切换：淡入 120ms，不做横向滑动。
- 列表增删：`animateItem()` 220ms；完成/删除先变色再移除。
- 图表：柱子从 0 增长 320ms；环图扇区同步。
- Snackbar：从底部滑入 220ms。
- 所有动效读取系统 `animatorDurationScale`；为 0 时直接切换。设置页提供“减少动效”开关。
- 不使用持续循环动画、粒子、重模糊；玻璃拟态不纳入本计划。

### 4.6 图标与插图

- 统一 24dp 网格、2dp 视觉线宽、圆角端点；填充与线性图标不混用。
- 48dp 触控热区；图标与文字间距 8dp。
- 空状态：96dp 单色线性插图 + 一句说明 + 一个主按钮。
- 状态点 6dp，仅用于“已启用/异常”，不承担文字信息。

### 4.7 暗色

- 背景 `#121612`，表面从 `#171C18` 到 `#2A332C` 递进，不使用纯黑。
- 品牌色提亮为 `#9ED8B5`，避免深绿在暗底上“消失”。
- 图表降低饱和度；错误色改为 `#FFB4AB`。
- 启动窗口背景按主题切换（`values-night`），消除闪白。
- 状态栏/导航栏图标明暗由当前 `surface` 亮度决定，不再依赖顶栏颜色。

---

## 5. 分批实施计划

| 批次 | 主题 | 主要文件 | 可独立回滚 |
|---|---|---|---|
| 0 | 设计 token 与主题骨架 | `ui/theme/Theme.kt`、`DesignTokens.kt`、`colors.xml`、`themes.xml` | 是 |
| 1 | 全局外壳 | `HomeScreen.kt`、`GlobalSearchScreen.kt`、`PluginPageScreen.kt`、`MainActivity.kt` | 是 |
| 2 | 主页重构 | `HomeScreen.kt`、`HomeWidgetHost.kt` | 是 |
| 3 | 渲染器与通用组件 | `PluginPageRenderer.kt`、`PluginDetailScreen.kt`、`BackupScreen.kt` | 是 |
| 4 | 动效与反馈 | `LifeHubApp.kt`、列表、图表 | 是 |
| 5 | 主题能力与适配 | `ThemePreference.kt`、`MainActivity.kt`、设置界面 | 是 |

### 第 0 批：设计 token 与主题骨架

改动：
- `Theme.kt`：补齐 26 个颜色角色与扩展语义色；新增 `LifeHubChartColors`；提供 `Typography`；保留 `LifeHubShapes` 并扩展到 v1 形状 token。
- `DesignTokens.kt`：间距改为 4/8/12/16/24/32/48；新增 `LifeHubMotion`、`LifeHubSizes`、`LifeHubElevation`、`LifeHubContentWidth`。
- `colors.xml` + 新增 `values-night/colors.xml`：启动窗口背景随主题。
- `themes.xml`：父主题改为 DayNight 兼容，状态栏透明。
- `PluginPageRenderer.kt:1256-1276`：删除硬编码图表色板，改读 `LocalLifeHubChartColors`。
- 新增单测：`ThemeContrastTest`（关键配对的 WCAG 对比度）、`DesignTokenTest`（间距/字号单调、无非法值）。

验收：
- 全量测试通过；lint 不新增 warning。
- 截图对比：同一界面在 token 替换后结构不变，但底色、边界、文字色更清晰。
- grep 检查：`ui/` 与 `render/` 下无 `Color(0x`（theme 目录除外）。

### 第 1 批：全局外壳

改动：
- 新增共享 `LifeHubTopBar`（`ui/LifeHubTopBar.kt`）：统一 `surface` 背景、`onSurface` 标题、滚动后 `surfaceContainer`、可选返回与动作。
- 替换三处 `TopAppBar`（`HomeScreen.kt:117-129`、`GlobalSearchScreen.kt:191-200`、`PluginPageScreen.kt:177-181`）。
- `NavigationBar`：`surfaceContainer` 背景、`primaryContainer` 选中指示、`onSurfaceVariant` 未选中、选中标签 500。
- `MainActivity.kt`：状态栏/导航栏图标明暗由 `surface` 亮度计算；Edge-to-edge 内容不被系统栏遮挡。
- 顶栏标题字号：主页 `headlineSmall`，其他页面 `titleLarge`，避免每页都是 22sp 粗体。

验收：
- 主页、搜索、插件页、备份页顶栏一致；浅色/深色均无深绿色横条。
- 暗色冷启动无白闪。
- 状态栏图标在两种主题下均清晰。

### 第 2 批：主页重构

改动：
- `HomeHeader`：改为“大标题 + 一行副标题”，标题 `headlineMedium`，副标题 `bodyMedium`，去掉多余的计数胶囊。
- 分区头：`titleSmall` + 数量弱化；排序控件改为 48dp 图标按钮（Tooltip/TalkBack 说明“按最近使用排序”）。
- `PluginHomeCard`：0 阴影、`surfaceContainerLow`、16dp 圆角、`outlineVariant` 描边；图标容器 44dp/12dp；标题 16/24/500；描述 13/20；状态改为 tonal 胶囊；主行动用 `FilledTonalButton`，只有“启用”用 `Button`。
- 卡片内动作从 5 个降为 3 个：主行动 + 快捷新建 + 更多；更多菜单保持。
- 小组件卡片：与插件卡片同一套 token；标题 14/22/600；金额 `titleLarge` + tabular；进度条 8dp 高、圆角全圆。
- 平板：小组件两列网格；插件卡片两列（≥1000dp），列表保持单列。
- 空状态：96dp 图标 + 标题 + 说明 + 主按钮；版本号移到页面底部 `labelSmall`。
- 编辑小组件对话框：统一 28dp 圆角、16dp 内边距、48dp 行动按钮。

验收：
- 首屏可在 10 秒内完成“看到最近使用 → 打开插件”。
- 浅色/深色下卡片边界清晰，无“白卡浮在灰底上看不清”。
- 字体缩放 1.3 时卡片不截断标题与状态。

### 第 3 批：渲染器与通用组件

改动：
- `PluginPageRenderer.kt`：
  - 页面内容最大宽度 720dp，水平居中；分栏时按可用宽度收缩。
  - 统一 `PluginSectionHeader`、`PluginCard`、`PluginListRow`、`PluginMetric` 四个通用组件，消除重复样式。
  - 间距全部换 token；18/20dp 改为 24dp；输入框/按钮/标签统一形状。
  - 指标数字用 tabular figures + `headlineMedium`/`displaySmall`；单位用 `labelMedium`。
  - 图表：主题色板、网格线、选中态、深色变体；柱宽 ≥ 12dp，柱间距 8dp；点击命中区 ≥ 48dp。
  - 记录列表：行高 72dp、左右 16dp、分隔线 inset 16dp；金额右对齐。
  - 输入框：`OutlinedTextField` 12dp 圆角、标签 12sp、错误文案 12sp；聚焦描边 `primary`。
  - 按钮层级：主行动 `Button`、次行动 `FilledTonalButton`、第三级 `TextButton`。
- `PluginDetailScreen.kt`、`BackupScreen.kt`：卡片、列表、按钮统一 token；状态标签改 tonal 胶囊。
- 插件 JSON 不改；如渲染器新增样式能力，只作为宿主侧默认值。

验收：
- 记账：编辑、列表、统计、预算、搜索五个页面在浅色/深色下风格一致。
- 任务清单：列表、编辑、回收站、筛选一致；大卡片不因字体缩放溢出。
- 所有页面无硬编码颜色/圆角/间距。
- 现有 184 个测试全部通过，新增渲染器样式测试。

### 第 4 批：动效与反馈

改动：
- `LifeHubApp.kt`：插件页面栈推入/返回使用 220ms 位移 + 淡入；底栏切换 120ms 淡入。
- 列表：`animateItem()`；完成/删除先变背景色，再移除；撤销 Snackbar 滑入。
- 按钮：按压缩放 0.98 / 120ms；图标切换交叉淡入。
- 图表：数据变化时柱状图从 0 增长；环图扇区插值。
- 加载：插件页首次加载用骨架屏（3 行占位），超过 400ms 才显示骨架，避免闪烁。
- 读取系统动画时长缩放；设置中增加“减少动效”。

验收：
- 连续打开/返回 10 次无卡顿、无跳变。
- 列表增删时相邻项平滑移动，不闪白。
- 开启“减少动效”后无位移动画，仅保留即时状态变化。

### 第 5 批：主题能力与适配

改动：
- `ThemePreference.kt`：新增 `dynamicColor`（Android 12+）、`reduceMotion` 偏好；保留 SYSTEM/LIGHT/DARK。
- 设置入口：主页外观对话框扩展为“主题 / 动态取色 / 减少动效”三项。
- 动态取色：`dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)`；扩展语义色与图表色板仍由宿主提供，保证收支语义不被系统色覆盖。
- 字体缩放：在 0.85/1.0/1.3/1.5/2.0 下逐页回归；必要时对标题做降级映射。
- 平板：分栏 28/42/65 三档宽度下检查内容不溢出；横竖屏切换保持状态。

验收：
- 动态取色开启/关闭均可读，主行动仍清晰。
- 字体缩放 2.0 时无文字截断、无按钮溢出。
- 深色冷启动无闪白；状态栏/导航栏图标正确。
- 用户既有偏好（分栏宽度、排序、置顶、主题）全部保留。

---

## 6. 验收与回归

### 6.1 自动化

- 单元测试：`ThemeContrastTest` 覆盖 12 组关键配对（正文、次要文字、主按钮、错误、图表标签），要求 ≥ 4.5:1；大文本 ≥ 3:1。
- 单元测试：`DesignTokenTest` 校验间距/字号/圆角为白名单值。
- 静态检查：`scripts/check-design-tokens.ps1`（或 Gradle task）扫描 `ui/`、`render/`，禁止 `Color(0x`、非 token 间距、非 token 圆角；白名单仅 theme 目录。
- 既有门禁：`:app:testDebugUnitTest`、`:plugin-api:test`、`:dev-harness:test`、`:app:lintDebug`、`:app:assembleDebug`、`:app:assembleRelease`。

### 6.2 真机回归（三星 SM-X610，2560×1600，横屏）

| 场景 | 检查点 |
|---|---|
| 主页浅色/深色 | 顶栏、卡片、小组件、空状态、排序控件 |
| 搜索页 | 输入框、历史、最近访问、结果高亮 |
| 记账 5 页 | 编辑、列表、统计、预算、搜索；图表色板与下钻 |
| 任务 4 页 | 列表、编辑、回收站、筛选；Emoji 与紧急度 |
| 插件详情/备份 | 卡片、列表、按钮层级 |
| 分栏 28%/42%/65% | 两侧内容不溢出、分隔条热区、双击复位 |
| 字体 0.85/1.0/1.3/1.5/2.0 | 无截断、无重叠、按钮可点 |
| 动态取色开/关 | 主行动与语义色清晰 |
| 减少动效开 | 无位移动画 |
| 冷启动 | 浅色/深色均无闪屏色差 |

### 6.3 量化验收

| 项 | 标准 |
|---|---|
| 正文对比度 | ≥ 4.5:1 |
| 大字/图标对比度 | ≥ 3:1 |
| 触控目标 | ≥ 48dp |
| 主色面积 | 单屏 ≤ 10% |
| 非 token 间距 | 0 |
| 卡片阴影 | 0（浮层除外） |
| 硬编码颜色 | 0（theme 目录除外） |
| 测试 | 全绿，新增设计测试 ≥ 15 条 |
| lint | 0 error，warning 不高于 6 |

---

## 7. 风险与取舍

1. **插件渲染器是共享层**：改动会影响记账与任务的全部页面，必须逐页回归；插件 JSON 不变，避免破坏兼容性。
2. **间距 token 从 20/24 调整为 24/32**：会让现有页面变“松”，属于预期效果；但字体缩放 2.0 时可能增加滚动，需逐页确认。
3. **动态取色与品牌**：默认关闭，避免系统壁纸色破坏收支语义；开启时语义色与图表色板仍由宿主固定。
4. **内置字体体积**：MiSans 子集预计增加 1.5–3MB；默认使用系统字体，内置字体作为可选实验项，不阻塞主线。
5. **不做的事**：不引入玻璃拟态、重模糊、循环动画；不改变信息架构与导航层级；不新增权限或网络能力；不改用户数据与备份格式。
6. **可回滚**：第 0 批先落 token，后续每批独立提交；任何一批可通过还原对应文件回退，不影响数据。

---

## 8. 已确认决策（2026-11-14）

| 决策 | 结论 |
|---|---|
| 视觉方向 | **A 雾林 Calm Forest**：中性暖灰表面 + 深绿强调，顶栏回归表面色 |
| 字体策略 | **系统字体**：零体积、跟随系统字体缩放，不内置 MiSans |
| 动态取色 | **默认关闭**，外观设置中提供开关（Android 12+） |
| 品牌色 | **保留绿色**，降级为强调色，不再大面积铺满顶栏 |

实施顺序：第 0 批（token 与主题骨架）→ 第 1 批（外壳）→ 第 2 批（主页）→ 第 3 批（渲染器）→ 第 4 批（动效）→ 第 5 批（主题能力）。每批完成后执行单测、lint、Debug/Release 构建与真机回归，确认无回退再进入下一批。

---

## 9. 实施记录

### 2026-11-14 第 0–1 批

已完成：

- `Theme.kt`：补齐 26 个颜色角色与 `inverseSurface`、`inversePrimary`、`surfaceDim`、`surfaceBright`、`scrim` 等角色；新增中文 Typography 字阶；扩展 positive/negative/warning/info 语义色；新增浅色/深色图表色板 `LocalLifeHubChartColors`。
- `DesignTokens.kt`：间距改为 2/4/8/12/16/24/32/48；新增 `LifeHubContentWidth`、`LifeHubSizes`、`LifeHubElevation`、`LifeHubMotion`、`LifeHubType`；形状改为 8/12/16/20/28。
- `colors.xml` + `values-night/colors.xml`：启动窗口背景跟随主题，消除暗色冷启动白闪。
- `PluginPageRenderer.kt`：删除硬编码的 16 个图表色值，环图改读主题图表色板。
- 新增 `LifeHubTopBar.kt`：主页、搜索、插件页统一表面色顶栏，滚动后使用 `surfaceContainer`。
- `LifeHubApp.kt`：底部导航改为 `surfaceContainer` 背景 + `primaryContainer` 选中指示。
- `MainActivity.kt`：系统栏图标明暗直接跟随主题，不再依赖深绿顶栏取反。
- 新增 `ThemeContrastTest`、`DesignTokenTest`：覆盖 24 组对比度与间距/形状/字阶/图表色板约束。

验证：

- 单元测试：app 139 + plugin-api 45 + dev-harness 8 = 192，0 失败。
- lint：0 errors / 6 warnings（均为既有项）。
- Release APK：7,564,038 字节，SHA-256 `F43D6DFCDF7CB00B0A7D1634A1F13342FB8D8FFE3710E72C65B0E4FAA3C35691`。
- 真机 SM-X610：浅色/深色主页、记账双栏、统计柱状图与环图、全局搜索页截图核对；主题已恢复为“跟随系统”。

剩余：第 3 批渲染器与通用组件、第 4 批动效、第 5 批主题能力。

### 2026-11-14 第 2 批：主页重构

已完成：

- 主页内容区改为最大 960dp 居中列，页面边距按窗口宽度切换 16/24/32dp。
- 头部改为 24sp 标题 + 14sp 副标题，移除多余字重；分区标题统一为 14sp + 弱化计数。
- “我的工具”排序改为 40dp FilterChip（默认 / 最近），保留 48dp 触控热区与持久化。
- 插件卡片：`surfaceContainerLow` + `outlineVariant` 描边 + 0 阴影；图标容器 44dp/12dp；描述 13/20；状态改 tonal 胶囊；主行动改 `FilledTonalButton`，仅“启用”用 `Button`；快捷新建与更多菜单 48dp。
- 平板 ≥1000dp 时插件卡片双列；分组只有 1 个插件时自动占满整行；≤1000dp 保持单列。
- 小组件：与插件卡片同一套表面与描边；标题 14/22/600；主数字改 19sp 等宽数字；进度条 8dp 全圆角；宽屏 1200dp 以上三列，其余两列。
- 空状态统一为图标 + 标题 + 说明的卡片，不再使用裸文本。
- 窗口宽度判断改用 `LocalWindowInfo.containerSize`，避免 `Configuration.screenWidthDp` 在分屏/多窗口下失真，并消除新增 lint 警告。

验证：

- 单元测试：app 139 + plugin-api 45 + dev-harness 8 = 192，0 失败。
- lint：0 errors / 5 warnings（较上一批减少 1 条）。
- Release APK：7,564,506 字节，SHA-256 `AF5EA2270E2FE0B7C2B97E96FEE43AFE49C25B8A3CC9283057077DACC6AE1554`。
- 真机 SM-X610：浅色/深色主页、双列插件卡片、三列小组件、排序 chip 切换与恢复、单插件分组占满整行、1.3 倍字体无截断；主题与排序均已恢复默认。

### 2026-11-14 第 3 批：插件渲染器与通用组件

已完成：

- 新增渲染器统一 `PluginCard`：`surfaceContainerLow` + `outlineVariant` 描边 + 0 阴影，覆盖指标、日期、周期导航、柱状图、环图、记录列表、普通列表与空状态。
- 页面宽度：单列页面最大 720dp；宽屏双列页面最大 960dp，避免内部 38/62 分栏被挤窄。插件详情与备份页同样居中限宽 720dp。
- 输入框统一 12dp 圆角与 16sp 正文；错误/辅助文字沿用 12sp label 规格。
- 指标卡主数字使用 24sp 等宽数字，次要指标 16sp 等宽数字；进度条 8dp 全圆角。
- 记录列表行最小高度 72dp（大卡片 88dp）；分组分隔线内缩 16dp；金额使用等宽数字并保持右对齐。
- 普通列表行最小高度 56dp，分隔线内缩 16dp；空状态与列表空态改为统一卡片。
- 次级按钮由 `OutlinedButton` 统一为 `FilledTonalButton`，主行动仍用实心 `Button`。
- 柱状图保留 48dp 触控单元与选中态，数值标签改用等宽数字；环图沿用主题 8 色板。
- 插件详情页版本信息卡改为表面色卡片；详情与备份页页面边距、按钮间距全部走 token。

验证：

- 单元测试：app 139 + plugin-api 45 + dev-harness 8 = 192，0 失败。
- lint：0 errors / 4 warnings（较上一批再减少 1 条）。
- Release APK：7,564,506 字节，SHA-256 `493B05C62F048AF5F8E5274FED5477D6F5F25A3A1470A8054D065A917840C132`。
- 真机 SM-X610：浅色/深色记账分栏、统计图表、任务清单与编辑页、插件详情、备份页截图核对；预算三列不换行；主题已恢复“跟随系统”。

### 2026-11-14 第 4 批：动效与反馈

已完成：

- 底部标签切换使用 120ms 交叉淡入；主页与插件页之间不再硬切。
- 插件页面推入/返回使用 220ms 淡入 + 水平位移；单列与宽屏分栏右侧统一使用 `AnimatedContent`，旧页面在转场结束后释放。
- 主页插件卡片使用 `animateItem()`，置顶/最近排序切换时卡片平滑移动。
- 柱状图柱体、环图扇区使用 320ms 增长动画；首次进入与数据刷新都会从 0 过渡到目标值。
- 新增 `rememberPressScale`：按钮按下缩到 98%、120ms 回弹；主行动按钮与插件页面按钮共用。
- 首次加载超过 400ms 才显示静态骨架卡片，避免快速加载时闪烁。
- 新增 `LocalReduceMotion`：读取系统动画时长缩放，关闭动画时页面、标签、图表与按压反馈直接到终态；第 5 批将加入应用内开关。

验证：

- 单元测试：app 139 + plugin-api 45 + dev-harness 8 = 192，0 失败。
- lint：0 errors / 4 warnings（与上一批持平）。
- Release APK：7,580,890 字节，SHA-256 `C1EC57BE46F82C44A16CE431608ECF97213A90FCB3D771E9B846E9F3652002EA`。
- 真机 SM-X610：页面推入中间帧无空白/重叠；关闭系统动画后页面直接切换；验证后系统动画时长与字体缩放均已恢复。

### 2026-11-14 第 5 批：主题能力与适配

已完成：

- 顶栏改为“表面色 + 50% 品牌绿”的淡绿层，并加 1dp 极浅分隔线；浅色顶栏实测 `#E9F6EC`，与 `#F7F8F6` 页面底色形成柔和但可感知的边界；深色顶栏使用 35% 绿色叠加。
- 外观偏好扩展为三项：主题模式、Android 12+ 跟随壁纸取色、减少动效；均持久化在 `lifehub_theme`。
- 动态取色开启时使用系统 `dynamicLightColorScheme` / `dynamicDarkColorScheme`，关闭时回落到雾林品牌色板；顶栏 tint 自动跟随动态主色。
- 应用内“减少动效”与系统动画时长缩放取“或”关系：任一开启即跳过页面转场、标签淡入、图表增长与按钮回弹。
- 2.0 倍字体回归：主页标题、小组件、排序 chip 与底部导航无截断/重叠；返回 1.0 后布局恢复。
- 外观关闭后确认偏好落盘为 `mode=system`、`dynamic_color=false`、`reduce_motion=false`。

验证：

- 单元测试：app 139 + plugin-api 45 + dev-harness 8 = 192，0 失败。
- lint：0 errors / 4 warnings（既有项）。
- Release APK：7,582,634 字节，SHA-256 `83A356C6233B9B73CCA87FE5A77396782880AE1E27ABABF53436D65FF3E99662`。
- 真机 SM-X610：浅色顶栏淡绿边界、外观对话框三项开关、动态取色开关、应用内减少动效、2.0 倍字体主页截图核对；字体缩放、系统动画与外观偏好均已恢复默认。
