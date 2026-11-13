# SkyPocket 开发文档

> 本文件是唯一权威开发文档，与代码一起版本化。开发新功能前先读本文件，不要重复探索整个代码库。
> 约定/架构变更时同步更新本文件。
> 专题参考：**`docs/SKYBLOCK_STATS.md`** —— SkyBlock 玩家数据从哪来、怎么抠（ActionBar / Tab / NBT / BossBar），含必须照抄的解析语义与踩坑。
> 数据表清单：**`docs/MAINTENANCE_TABLES.md`** —— 技能 XP、矿物强度/instamine、工具白名单、热点类型等需要手动维护的表。

## 1. 项目概览

- 类型：Minecraft Fabric **客户端**模组（`environment: client`）
- 定位：Hypixel SkyBlock 小工具集
- 版本：MC 26.2 / Java 25 / **Mojang 官方映射**
- 依赖：Fabric Loader 0.19.x、Fabric API、ModMenu（可选，`compileOnly`）
- 入口：`dev.skypocket.SkyPocketMod`（`ClientModInitializer`）
- 构建：`.\gradlew.bat build`（Gradle 9.5.1 wrapper，仓库内已就绪）

## 2. 包结构

```
dev.skypocket/
├─ SkyPocketMod          客户端入口：加载配置、注册 HUD/渲染器、命令、按键、tick
├─ SkyPocketLog          分区日志（SkyPocketLog.of("area")）
├─ config/               ConfigManager（Gson JSON ↔ config/skypocket.json）+ ModConfig + ColorDefaults
├─ gui/                  ClickGUI 业务层：Feature/Module 契约与注册、各页面、布局常量、外壳绘制
│   ├─ setting/           Setting 数据模型（含全部子类）
│   └─ widget/            ClickGUI 主题与旧控件（ClickGuiStyle/GuiTheme/ThemeStore/…）
├─ ui/                   与业务无关的 UI v2 内核：元素树、布局、绘制抽象、输入事件
│   ├─ input/            InputRouter + 各类输入事件
│   ├─ mc/               McUiPainter（唯一的 MC painter 实现）
│   └─ widget/           Control 家族（Switch/Slider/Button/Card/TextField/Dropdown/…）
├─ hud/                  每个 HUD 元素一个类 + HudPositionSync（位置迁移/自愈）
├─ skyblock/             SkyBlock 数据解析：tracker、静态数据表、物品字段
├─ render/               渲染器与绘制原语（含 GuiPixels 设备像素原语、字体、后处理）
├─ net/                  PingMonitor + SkyBlockDetector（服务器/场景判定）
├─ i18n/                 Localization（中文 key → 英文映射）
├─ chat/                 聊天相关（压缩、提及检测、文本工具）
├─ sound/                SoundManager + MentionSoundManager
├─ tooltip/              TooltipScroller
├─ diagnostics/          非 mixin 的运行时诊断（MixinTargetCheck）
└─ mixin/                **只能放 mixin 类**，见下方硬约束
```

### 2.1 每个包放什么、不放什么

| 包 | 只放 | 明确不放 | 依赖方向 |
|---|---|---|---|
| `config/` | 配置数据结构、Gson 持久化、迁移、默认值常量 | 渲染器、UI、任何 `gui`/`ui` 类 | 被几乎所有包引用；**自身应只依赖 JDK** |
| `gui/` | ClickGUI 的**业务**：功能契约与注册、页面、布局数值、外壳绘制 | 通用控件（在 `ui/widget`）、MC 渲染原语（在 `render`） | → `ui`/`gui.setting`/`gui.widget`/`config`/`render`/`i18n` |
| `gui/setting/` | `Setting` 及全部子类（纯数据模型，不创建控件） | 任何控件、任何 Screen | → `config` |
| `gui/widget/` | ClickGUI 主题 token、度量、主题持久化、旧版即时控件 | 新控件请放 `ui/widget` | → `render.ClickGuiFonts`/`GuiPixels` |
| `ui/` | **与业务无关**的 UI 内核：元素、样式、布局、绘制抽象、事件 | `Feature`/`Setting`/`ModuleRegistry`（内核不得知道"功能"是什么） | → 仅 `render`（字体/像素原语）与 `ui.*` |
| `ui/widget/` | 可复用控件：`Control` 及其子类 | 业务逻辑、配置字段名 | → `ui`、`render` |
| `ui/input/` | 输入事件与路由 | 任何绘制 | → `ui.UiElement` |
| `ui/mc/` | MC 侧的 `UiPainter` 实现 | 业务判断 | → `render.ClickGuiFonts`/`GuiPixels` |
| `hud/` | 每个 HUD 元素的注册与绘制、位置同步 | 数据解析（在 `skyblock`） | → `config`/`render`/`skyblock`/`net` |
| `skyblock/` | SkyBlock 数据的**解析与静态表**（tab/actionbar/NBT） | HUD 绘制、ClickGUI | → `config`/`net`/`chat` |
| `render/` | 渲染器、绘制原语、着色器链、字体 | 页面布局、命中检测、配置迁移 | → `config`/`skyblock`/`mixin`（Accessor） |
| `net/` | 服务器地址与场景判定、延迟统计 | 渲染、UI | → `config` |
| `diagnostics/` | 运行时自检，结果只写日志 | **不得放进 `mixin/`**（见下） | → `SkyPocketLog` |

已知的两处**反向依赖**（写在这里是为了不被当成范例）：

- `config/ModConfig` 依赖 `render.AntialiasingRenderer` / `ColorGradeRenderer`（字段默认值取自它们的常量，`sanitize()` 用它们校验取值）以及 `render.ColorUtil`（颜色运算）——`config` 依赖 `render` 违反了上表，属于历史遗留。
- `ui/widget/CrosshairGrid`、`CrosshairPreview` 直接 import `config.ModConfig`——`ui` 依赖 `config` 同样违反"内核不知道业务"。

新增代码**不要**扩大这两条反向依赖。

### 2.2 `mixin/` 包的硬约束

`skypocket.mixins.json` 用 `"package": "dev.skypocket.mixin"` 声明了这个包，**Mixin 会接管整包并禁止其中任何类被当作普通类直接引用**。后果分两种：

- 把一个**普通类**放进去并从别处调用 → 启动即崩：
  `IllegalClassLoadError: ... is in a defined mixin package ... and cannot be referenced directly`
- 把一个 mixin 类放进去但**忘了写进 `skypocket.mixins.json` 的 `client` 列表** → 它不会被应用，功能静默失效

因此：

1. **`mixin/` 下只放 mixin 类**（`@Mixin`/`@Accessor`/`@Invoker`），且每个都必须在 `skypocket.mixins.json` 里登记。
2. **辅助类、诊断类、任何要 new/静态调用的类，一律放别的包**（如 `diagnostics/MixinTargetCheck`）。这一条已踩过一次，代价是一次启动崩溃。
3. **`javac` 和 `gradle build` 都发现不了这类错误**——它们是合法的 Java 类。验证方式是读 jar 确认类路径，或直接跑一次游戏。

## 3. 核心契约与约定（必须遵守）

### 3.1 功能 = Feature 契约
- `Feature` 接口是唯一功能契约：`id()/name()/rawName()/category()/skyBlockOnly()/isEnabled()/setEnabled()/settings()/hasSettings()`（搜索、`hasSettings`、`settingsHeight` 为默认方法）。`skyBlockOnly()` 只用于“仅 SkyBlock”徽标和语义，不替代 `SkyBlockDetector.isActive()` 门控。
- `Module` 是默认实现。**新功能用 `Module`，不要自己实现 `Feature`。**

### 3.2 设置一律内联 ClickGUI
- 所有功能（含子功能）的设置都写在 ClickGUI 里。
- **唯一例外**：统一的 HUD 位置调整页 `HudPositionEditorScreen`（从 ClickGUI 底栏左下角入口按钮打开）可以新开 `Screen`。
- 颜色编辑用 `ColorSetting`，v2 `ColorField` 打开内联 `ColorPickerPanel`：SV 方块 + 色相条 + 透明度条 + 两列预设色卡，右侧 HEX/RGBA 输入框，**不要再开颜色选择 Screen**。

### 3.3 语言与文案
- UI 文案用中文字面量当 key，调用 `Localization.tr()`；英文翻译集中写在 `Localization.EN` 静态 Map。
- 语言默认跟随系统（`language` = `system`/`zh_cn`/`en_us`），运行时 `cycleLanguage()` 切换。

### 3.4 配置
- 配置集中在 `ModConfig` 公有字段（Gson 直序列化到 `config/skypocket.json`）。
- 字段初始值 = 默认值；数值字段在 `sanitize()` 里钳制范围。

## 4. 新增功能的固定步骤（checklist）

按顺序执行：

1. **配置**：`ModConfig` 加字段；数值字段在 `sanitize()` 补钳制。
2. **模块**：`ModuleDefinitions` 加 `registerXxx(cfg)`，并在 `registerAll()` 里调用。
   - 无子设置 → `List.of()`；有子设置 → `List.of(Setting...)`。
3. **实现**：实际逻辑写在 render/hud/net/tooltip 或新建类；优先 Fabric 事件，必要时用 mixin。
4. **i18n**：模块名 + 所有设置 label 都要在 `Localization.EN` 补英文。
5. **mixin**（若有）：写 mixin 并加入 `skypocket.mixins.json` 的 `client` 列表。
6. **构建验证**：`.\gradlew.bat build` 无报错。

## 5. ClickGUI / 设置系统速查

- 模块卡片：`hasSettings()` 为真 → 绿色「设置」按钮；为假 → 灰色「无设置」按钮。
- `Setting` 子类：`BooleanSetting` / `SliderSetting` / `ColorSetting` / `DropdownSetting` / `TextFieldSetting` / `GroupSetting` / `ChoiceSetting` / `ActionSetting` / `V2CustomSetting` / `Header`。
- **所有带设置的模块页面、“设置”主标签页和 HUD 右键设置卡都走 `SettingsPage`**（UI v2 元素树）。旧的设置视图与旧 widget 构建路径已删除；`Setting` 只保留数据模型，不再负责创建控件。
- **卡片规则**：`Header` 开始一张新卡片；`GroupSetting` 也是独立卡片，带开关的组把开关放在标题行右侧，不再折叠或嵌入上一张卡片。`V2CustomSetting` 提供基于 v2 `Box`/`Control` 的自定义行（准星网格与预览）。
- **设置控件**：Switch / Slider / ColorField / Dropdown / Segmented / Button / TextField。滑杆使用 `SliderSetting` 的真实 step 和 format；文本设置在绘制层自动本地化，标签在左、field 在右。
- **`Setting#visibleWhen(BooleanSupplier)`**：条件行必须成块且收尾；模式切换后调用 `rebuildAfterSettingChange()` 重建页面。`Header`、`GroupSetting`、`ChoiceSetting` 的显示/结构变化都依赖这次重建。
- **hint tooltip 已移除**：说明文字写在 Header 副标题里；`GroupSetting` 不再生成折叠状态，页面只按功能拆卡。
- **条件行（`visibleWhen`）必须成块且收尾**：一个 `List.of(...)` 里，无条件行在前，条件块在后；**不允许**"条件行 → 无条件行 → 条件行"的交替。否则切换开关时，夹在后面的无条件行会整体位移（"中途塌陷"）。控制这些条件行的那一行（如风格切换按钮）要放在它们**之前**。参考 `registerStatsDisplay`：风格 → 切换 → 原版 HUD → 颜色 → 文字行 → 经典块 → 现代块。
- **主题编辑是"实时抽屉"，不是"预览屏幕"**：`DevDrawer` 挂在 Click GUI 上，编辑直接写入实时调色板，打开时记一份快照，关闭未保存则整体还原；只有「保存」写文件。不要再为它做"第二份面板"（旧的独立主题工作室屏幕就是这么坏的：手工拼壳 + 页 + 行过滤，随真实面板演进持续漂移，已删除）。抽屉里的拾色器按 0.75 绘制（pose 缩放 + 命中反变换），设置页与卡片仍是 1:1。抽屉顶部的主题行是唯一的主题入口：`‹ ›` 切换（从磁盘载入，未保存改动丢弃）、`+` 另存为新主题、`-` 删除当前主题（**内置 Dark/Light 不可删**，删除即时写盘，并回落到 Dark）。\n- **层次靠明度 + 阴影，不靠描边**：所有控件、行、模块卡片、抽屉、浮窗与 tooltip 都不画描边（`border` / `cardBorder` / `switchBorder` 三个令牌已删除，Studio 里不再有它们）。深度由三件事给出：比背景略深/略浅的填充、`ClickGuiStyle.shadow(...)`（两层半透明叠加，此层没有模糊）、以及组内子行统一画在一张卡片上（`RowAt.inCard`）。新增面板类元素时**先想明度差**，不要再引入 1px 边框。\n- **配置结构变更必须走版本迁移**：`ModConfig.configVersion` + `ConfigManager.CURRENT_VERSION` + `MIGRATIONS`（第 i 步把 v{i} 升到 v{i+1}）。新增/改名/改类型字段时：改字段 → `CURRENT_VERSION++` → 在 `MIGRATIONS` 末尾加一步。**不要**依赖"解析失败回落到默认值"——那会静默丢掉玩家设置，并在下一次保存时覆盖原文件（已发生过一次）。配套保护：读取失败写 `skypocket.json.unreadable` + ERROR 日志；保存前复制 `skypocket.json.bak`；写入用临时文件 + 原子替换。
- **所有诊断/调试开关统一放进「调试」分组**（`buildPanelSettings` 末尾的 `GroupSetting("调试", …, false)`），不要再散落在主列表里：这些行只写日志、对玩家无用，放在一起既不打眼，也给下一个开关一个显然的落点。目前里面有：调试日志（信息日志总开关）、面板缓存统计（每 5 秒一行缓存命中/烘焙/停在哪个门）。
- **日志**：一律用 `SkyPocketLog.of("area")` 取 logger，调用写法仍是 `LOGGER.info(...)`，输出带 `[SkyPocket/area]` 前缀 —— MC 的日志格式只打印 `[线程/级别]`，不加前缀就无法在共享日志里定位我们的行（改全局 pattern 会连原版日志一起改，不做）。logger 名固定 `skypocket.<area>`（**点分**，级别继承才成立；斜杠形式不继承）。级别由「调试日志」控制：开 = DEBUG 及以上，关 = **WARN 及以上**（信息静默，但警告/异常永远留证）。
- **HUD 位置编辑器（`HudPositionEditorScreen`）的结构约定**：每个可编辑元素的 x/y/缩放读写集中在 `SPECS`（`EnumMap<Target, Spec>`，一个元素一条）；新增元素 = 枚举常量 + 表里一行 + `computeLines`/`label` 两个 switch 分支（这两个是 `switch` **表达式**，漏写编译不过）。`init()` 会自检"表项数 == Target 数"，缺项只 warn 不会静默。测量（`lines`/`bounds`）有**帧内缓存**，且缓存只在 `extractRenderState` 期间生效（鼠标处理走实时测量）。控件是**可拖动悬窗**，不再固定在屏幕底部（底部正是 HUD 元素常放的位置）；ESC 退出。**可见性**由 `visible(target, cfg)` 统一决定，规则是"功能开关已开 **且** 该风格下真的会画"：条形仅现代风格可见，生命/法力两条文字行仅经典风格可见（现代风格下这两个数由条形印出，否则两套元素重叠、没法调）；运行时条件（是否在 SkyBlock、有无数据）**不**参与——否则无法在数据出现前预先摆放。
- **位置/缩放/启用状态的映射各自只有一份**：`StatTextHudElement.FIELDS` 与 `StatsBarHudElement.FIELDS`（`EnumMap<Kind, Fields>`，一行一个 kind），编辑器通过它们的 `x/setX/y/setY/scale/setScale` 访问器委托，不再重复字段名。新增 kind = 枚举常量 + 表里一行。`HudPositionSync` 同样表驱动：`REPAIRED`（每个元素一行，含"跑到屏幕外时回到的默认坐标"）与 `BARS`（仅用于按比例缩放；越界修复是**逐条**的：只把越界那根放回它在列里的默认位置，另五根的位置/缩放/自定义尺寸不动）。新增元素要在这张表里加一行，否则窗口尺寸变化时它不会被按比例迁移、也不会被拉回屏幕内。
- **设置页的组织方式是"子功能 = 一张卡片"**（`GroupSetting`）：组标题行右侧是本功能的开关，组不再折叠。
  - **组的开关必须写真实的开关字段**（例如「瞄准目标变色」写 `crosshairEntityColorEnabled`，后处理的四个组写各自效果的 `xxxEnabled`），关掉要真的停掉功能。**只有参数簇（一组颜色、一个位置/旋转）才用不带开关的组**：它照样能折叠，只是没有东西可关 —— 装饰性开关比没有开关更糟。
  - **子设置保持整页宽度，不缩进**：组头的深色表面 + 折叠指示已经说明了归属，而缩进会迫使 `ColorPickerPanel`/预览网格这类按整宽调好的布局重算。
  - 组的高度 = 组头 + 展开时的所有子设置高度（`GroupSetting.height()` 递归求和），所以 `panelSettingsHeight()`/`isRowVisible()` 不用改；折叠时调 `relayout()`（下一 tick 重排，与 `ColorSetting`/`DropdownSetting` 同一路径）。
  - **带动画的 off/on 开关（`ui/widget/Switch`）**：动画状态放在 **Setting** 上（`ToggleAnimation`），不是控件上 —— 行每次滚动都会重建，控件自己存进度就会"每次都从终点开始"；而且它是**按时间推进**的（读时钟）而不是按绘制推进，否则离屏的开关会卡在半途、让缓存键永远不干净。**面板缓存键必须包含这个动画进度**（`ToggleAnimation` 静止时恒为 0，运动中随进度变化）——漏了它就是"开关卡在中间不动/动画被冻进缓存帧"。
- **所有滑条都必须有步进**（`SliderSetting` 不再接受"连续"）：不写时按量程自动取约百分之一（`defaultStep`），量纲明确的写死（像素大小步进 1、缩放 0.05、位置 0.01、旋转 1）。理由：无步进时拖动一个像素数字就跳一次、读数没法看，而且同一个量程的两个滑条行为不该不同。
- 分类：`Category` 仍是两级树模型，但**不设 `SkyBlock` 一级容器**。模组整体面向 SkyBlock，顶层直接按玩家活动领域分：`HUD / 挖矿 / 战斗 / 钓鱼 / ESP / 渲染 / 通知 / 移动`。空的领域不要保留占位，等真正有模块时再加；容器能力保留给未来的确有子分组的领域。
- 侧边栏：由 `ModulesPage` 建出的 v2 树渲染，一棵包含「全部 + 一级分类」的 `SidebarRow` 列表（`SidebarRow extends Control`，自己 `paint`）。过滤规则：`selected == null` 显示全部；叶子精确匹配；未来容器仍通过 `Category.contains()` 覆盖后代。卡片网格/搜索框都在侧边栏右侧的面板内。
- `skyBlockOnly` 标记：只有真正依赖 Hypixel SkyBlock 的模块才调用 `Module.markSkyBlockOnly()`，卡片显示 `SB` 徽标；模块逻辑仍必须自己调用 `SkyBlockDetector.isActive()`，不能靠分类或徽标门控。
- **面板几何数值只写一处，而且都在 `gui/PanelGeometry`**：面板尺寸 `PANEL_W/H`、圆角 `ROUND`、tab（`TAB_BAR_H/TAB_W/TAB_H/TAB_Y/TAB_GAP/TAB_MARGIN`、`tabX()`、`tab2X()`）、卡片（`CARD_W/H/GAP`、`ICON_SIZE`、`CARD_PAD`、`CARD_TITLE_*`、`CARD_BTN_*`、`CARD_STATUS_*`）、搜索框（`SEARCH_X/Y/W/H`、`NAV_SIZE/NAV_GAP/NAV_X`）、内容区（`CONTENT_TOP_MODULES/SETTINGS`、`contentTop(view)`、`contentWidth()`、`contentBottom()`、`contentBottomRow()`、`maxScroll(view, settingsHeight)`）、侧边栏（`SIDEBAR_W/SIDEBAR_ITEM_H/SIDEBAR_TOP`）、页脚（`FOOTER_H`、`FOOTER_BTN_*`、`footerBtnY()`）、卡片网格（`columns()`、`cardRowLeft(rowCount)`）、命中（`inPanel`、`inBody`）。
  - **这些值同时被绘制与命中检测使用**（`ModulesPage` 的树布局 ↔ `Control.contains()` 的命中 ↔ 缓存键）。以前两处各写一遍，改一处忘另一处会**静默**造成"看到的位置和能点的位置不一致"；现在改成只在一处定义。
  - 依赖状态的查询（`contentTop`、`maxScroll`）接收一个 `PanelView` 快照（`OneClickScreen.view()` 每帧构造，纯 record，零成本）；侧边栏/卡片的命中不再走"坐标 → 索引"的函数，而是**真实的控件对象**（`SidebarRow`、`ModuleCard`）挂在 v2 树里，由 `InputRouter` 按 `contains()` 命中。
  - **列表/网格位置不要自己算**：卡片列宽用 `PanelGeometry.columns()`，行首用 `cardRowLeft(rowCount)`，行距用 `CARD_H + CARD_GAP`（见 `ModulesPage` 的建树代码）。新增同类布局时也走这些函数，不要再写一遍 `rowW`/`left`/`cy`。
- 半透明背景约定：面板主体、顶栏、底栏、**侧边栏**的背景色都拼接 `clickGuiBackgroundAlpha << 24`，统一受「设置 → 背景透明度」影响；卡片、按钮、hover/选中高亮等前景元素保持不透明。新增深色背景条时记得同样拼接 alpha。
- **控件风格只有一个来源：`gui/widget/ClickGuiStyle`**（半径 + 配色 + `surface/button/field/pill`）。**所有按钮/输入框/列表底色都必须走它**：设置页全部 widget（`Control` 家族：`Switch`、`Slider`、`Button`、`ColorField`、`Dropdown`、`Segmented`、`TextField`）和面板侧的搜索框、卡片「设置/无设置」按钮、ENABLED 状态条、底栏按钮都已改。
  - 形状是**圆角**的 `surface`（= `GuiPixels.fillRoundedBorderedRect`：**在设备像素空间**把外圈吸附一次，内圈 = 同一矩形**内缩整数个设备像素**，半径同步减去该值）；卡片外框也用同一个 helper。半径**不是固定常量**：卡片用 `ClickGuiStyle.cardRadius()`、控件用 `controlRadius()`，两者都来自 `GuiMetrics.current()`（主题可调，`gui/widget/GuiMetrics` 是 record，带 `generation()` 让缓存失效）；滑条轨道用 `pill`（`PILL = 2`，并按高度折半）。
  - **边框必须这样画，不能写成"外圈圆角矩形 + 内缩 1 个设计单位的内圈"**：1 设计单位 = `step`（≈2-4）个设备像素，四条内边各自独立吸附 ⇒ 每边拿到 `floor(step)` 或 `ceil(step)`，四边不相等 —— **实测症状**是"按钮黑边右边明显比左边细"，而且不同按钮缺的方向还不一样（上下/左右都会）。设备像素内缩 + 半径同步是最好的解：环宽处处相等，包括圆弧段。环宽取 `round(step)`（≈仍等于 1 设计单位），所以厚度观感和原来一致，只是变均匀了。
  - 调色板（访问器形式，不再是全大写常量）：中性按钮 `button()/buttonHover()`（= 卡片按钮的灰）、主色 `accent()/accentHover()`（= tab/滑条填充/开关 ON 的绿）、`disabled()/disabledText()`、`switchOn()/switchOff()`、字段 `field()/fieldHover()/fieldInner()`、列表行 `listRowHover()/listRowCurrent()`、文字 `text()/textDim()/textMuted()/cardText()`、卡片 `card()/cardHover()/cardFloat()`。
  - **不要再写 `graphics.fill` + `graphics.outline` 的直角按钮** —— 那正是"设置页 widget 和卡片圆角按钮风格不一致"的来源（实测）。
  - **控件 hover 由 v2 输入层设置，控件只管读取（`Control.isHovered()`）**：命中由 `InputRouter.hitTest` 用 `UiElement.contains()` 在**页面局部坐标**里算，然后回调 `onHoverChanged(boolean)` 写进 `Control.hovered`。**不要**自己拿 `MouseButtonEvent` 的屏幕坐标去和控件 bounds 比，也不要依赖 MC 的 `AbstractWidget.isHovered()`——那个字段在 `extractRenderState` 里被设成 `containsPointInScissor(...) && areCoordinatesInRectangle(...)`，**额外要求该点落在 extractor 当前 scissor 内**，而面板的裁剪是在 `pushDeviceSpace()` 里推入的（设备像素，直绘帧还含面板原点平移），拿面板局部坐标去比没有意义；更糟的是直绘帧与缓存帧的裁剪数值不同（烘焙不带原点）⇒ **hover 会在两种帧之间反复翻转**。**实测症状**：开关/动作按钮高亮"亮一会就不亮、鼠标动一下又亮"。
  - 列表行（侧边栏、下拉选项）保持**直角平铺**：它们上下紧挨着排，逐行圆角会在行间留缺口；颜色取自 `ClickGuiStyle.listRowHover()` / `listRowCurrent()`。预览类控件（准星像素网格、准星棋盘格预览）是画布而非按钮，保持现状。
  - **画布类控件的例外写法**（`ColorPickerPanel` 的 SV 方块/色相条/透明度条，以及预览类）：直角、用 `fill`/`fillGradient` 直接画，**边框用"先铺一圈底色、再内缩 1 单位铺内容"**（两次 `fill`），不要用 `graphics.outline` —— 后者是四条独立 `fill`，在非整数面板缩放下四条边各自吸附、会一粗一细。外层容器仍走 `ClickGuiStyle.surface` 保证圆角观感统一。
  - **`ColorPickerPanel` 的排版与交互约定**：整行内容**居中**（`CONTENT_W` 与两侧均分的 slack，两侧留白对称）；方块 132（`SQUARE`）、色相/透明度条 18（`BAR_W`）、右侧两列 = 输入框列（hex + R/G/B/A，各 20 高，正好铺满方块高度）+ 预设色卡列（2×7，`PRESET_COLS/PRESET_W/H`）。方块/条是画布，预设色卡是按钮（走 `ClickGuiStyle.surface`，当前色的那格描主色边、hover 描白边）；点预设**只改 RGB、保留当前 α**，并把色相一起写回（否则拾色器不会跟着走）。
  - **"覆盖整行的控件"必须自己转交输入框的点击**：MC 的 `ContainerEventHandler.getChildAt` 是**正序遍历取第一个命中**（`Iterator` + `hasNext/next` + `isMouseOver`），也就是**先添加的（下层）控件赢得点击**。拾色器覆盖整行、且为了绘制在下层而先添加，于是 hex/通道输入框永远收不到点击。v2 的解法是把拾色器做成 `ColorField` 自己的**弹层 `Card`**（`popup`），输入框和拾色器是同一个 owner 下的兄弟节点，由 `InputRouter` 统一按 `Control.contains()` 分派，不再依赖旧的事件转发。**新增任何覆盖其它控件的整块控件（弹层、遮罩、色板）时，要用同样的"同级 owner + 弹层"结构，不要手工转发事件。**
  - **输入框显示值由外部同步（`ColorField.syncFields()`）**：hex 与 R/G/B/A 的"真值"是颜色本身，拖动方块/条、点预设时要把文本刷进去；同步时**只写未聚焦的字段**（`if (!hex.isFocused())`、`if (!channels[i].isFocused())`），否则会在用户打字时把光标下的文本换掉。`TextField.text(String)` 直接替换内容、**不触发 `onChange`**，所以不会形成回写循环。顺带得到一个好行为：**失焦后下一帧自动恢复规范文本**，所以非法/半截输入（`#E7`、`999`）既不改变颜色、也不会留在框里。解析规则见 `ColorField.applyHex`：去掉可选 `#` 后**必须正好 6 位十六进制**（即 `RRGGBB` 六位），α 沿用当前值的低 8 位；长度不符或解析失败直接返回、保持原色。通道输入框走 `applyChannel`：`Integer.parseInt` 失败则忽略，成功则钳制到 0–255。
  - **需要"横向渐变"时走 `UiPainter.fillHorizontalGradient(rect, left, right)`**：`GuiGraphicsExtractor.fillGradient` 只沿 y 插值，没有四角渐变。`McUiPainter.fillHorizontalGradient` 的做法是 `translate(x,y)` + `rotate(π/2)`（局部 `(u,v) → (x−v, y+u)`，所以覆盖目标矩形的局部区域是 `u∈[0,h]`、`v∈[−w,0]`，颜色顺序要反过来）。**不要用"逐列 1px 半透明 fill"凑横向渐变**：相邻半透明四边形的共享边落在小数设备像素上会双重混合，出现竖条纹。
  - **拾色器的光标位置来自"记忆"，绝不能从颜色反推**（`gui/widget/PickerMemory`，持有 h/s/v，由 `Setting.ColorSetting` 持有，因为行每次滚动都会重建 widget）。不过 `ColorPickerPanel` 自己**也**保留了一份 `hue/saturation/value` 状态，并从 `getter` 拉取颜色（`paint()` 里 `adopt(argb)`），其中 `adopt` 在 `delta < 1e-6`（灰阶）时**沿用旧 hue** 而不是重算 —— 这两处共同保证光标不抖。RGB 表达不了完整的 HSV：
    - **色相**在 s=0 或 v=0 时丢失（灰/黑/白都换算回 0）→ 不记忆就会"拖到方块角落时方块底色跳回红色"。
    - **饱和度**在 v=0 时丢失（黑色只有 RGB=0）→ 不记忆就会**实测症状**："拖动方块到底部时方块光标抖动、拖到右下极限时弹到左下角"（`(s, 1-v)` 里 s 一丢就归 0 = 最左）。**这一条是"光标不能由颜色反推"的真正原因**：v 很小时一次 8bit 取整就能让反推出来的 s 偏 0.04（约 5 像素）。
    - **色相是"角度"，不是标量**：360° 与 0° 是同一个颜色。v2 的 `hsvToArgb` 内部做 `((hue % 360) + 360) % 360` 归一，而色相条的读数直接用 `hue / 360f * SQUARE`（**不做取模**），所以拖到底得到 360、读数是满格而不是折回 0 —— **实测症状**是"色相条拖到最下方会自动弹回最上面"，根因就是对色相读数取模。
    - 同步规则（`ColorPickerPanel.adopt` 与 `paint`，三条缺一不可）：① **拖动中记忆值说了算**（`pressed` 为真时不从 getter 反同步，否则光标会在静止鼠标下抖动）；② **颜色与自己刚发出的值相同就什么都不做**（`argb != lastEmitted` 才 adopt —— 颜色是记忆值的取整结果，不留这个判断的话松手那一帧光标会被推一下）；③ **只在颜色"表达得出"该分量时才采纳**：灰阶时 `delta < 1e-6` 沿用旧 hue、黑色时 `max < 1e-6` 令饱和度为 0，而不是拿 0 反推。
  - **预设色卡用满饱和"本色"**：每个彩色预设必有一个通道是 255（红 `#FF0000`、黄 `#FFFF00`、橙 `#FF8000`…），点"红"就该得到纯红。点击**只改 RGB、保留当前 α**，并把 h/s/v 一起写进记忆（否则拾色器不跟着走）。
  - **成组输入框的标签列宽**：`TextField` 的 `labelOutside` 模式里值框起点是 `bounds().x() + 11f + labelWidth + 15f`（`outsideFieldBounds`），用的是**实际标签宽**。所以 `#` / `R` / `G` / `B` / `A` 这些单字符标签天然等宽、值框自动对齐；**给它们写不同长度的标签（如 `HEX` 对 `R`）就会让值框各走各的**，一列参差不齐。需要混用不同长度标签时，要么统一宽度、要么给这几个字段传同长标签。
  - 拖动必须靠 v2 输入层的指针捕获（`Control.onPointerMove` / `onPointerUp`）；拖出控件范围或页面后仍要继续调节，不要在拖动中重新做区域命中。
- **圆角必须走 `render/GuiPixels` 的 `fillRoundedRect/TopRect/BottomRect`**（设备像素原语统一放在 `GuiPixels`：widget 也要用它，若留在面板类里会让 `gui.widget` 反向依赖 Screen）：默认走 **`GuiCornerTextures` 预烘焙抗锯齿掩码 + 九宫格**（4 次 1:1 `blit` + 3 次直线填充，全部设备像素对齐、互不重叠），只有掩码建不出来时才回退到逐行 AA。掩码是白色 + 覆盖率 alpha（四象限一张图、按设备半径缓存），颜色由 blit 的 tint 给，所以面板透明度滑条拖动时不会反复重烘焙。**mask 只在 client tick 烘焙，绘制阶段只读未命中就回退逐行 AA；绝不能在绘制中创建/注册 `DynamicTexture`，否则会污染 `AtlasText` 的字形采样（见 §7.1.1）。**
  - **为什么不能把"逐行 AA"当默认**：它每次填充都是一次独立的 GUI 元素，成本 ∝ 设备半径（`r × deviceStep`，通常 8→16~24 行）× 4 角 × 2 次填充 ≈ 200 次/圆角矩形。屏幕上几十个卡片（每个还画"外框 + 内填充"两层）就是几千个元素，比字体几何量还大——实测就是这个性能回退。质量上也更差：逐行是行近似，掩码是精确 2D 覆盖率。
  - **不要在"设计单位"里做抗锯齿**：1 设计单位 = `renderScale × guiScale` 个设备像素（通常 ≈1.75-3），那样软化带宽会宽 2-3 倍（看着糊），而台阶仍是设计单位那么大（看着仍有锯齿）——实测过这个失败。设备像素空间的约定见 `pushDeviceSpace()`。
- 面板的**矩形件也要设备对齐**：侧边栏/分隔条用 `fillDevice()`、主体渐变用 `fillVerticalGradient()`（都是设计坐标入参、内部按同一 `round(x × step)` 吸附），这样它们与上下圆角条共享的边会落在**同一像素行**，半透明背景不会在接缝处漏出或不均。新增与圆角边相贴的矩形背景时同样要用这两个函数。
- **平面大背景用 `fillVerticalGradient` + `shade()`** 做轻微明暗（面板主体已用）。不要给"圆角块"逐行填渐变：行数 × 填充数会白涨开销（详见 §7.12 UI 性能与观感的取舍）。
- **面板整体渲染到离屏纹理缓存（`GuiPanelCache`）**：MC 26.2 的 GUI 是"状态树 → 每帧重新排版/建网格"，所以面板每帧的 `fill`/`text` 次数就是每帧开销（卡片/设置多的页面几千个元素）。缓存机制：面板内容录进私有 `GuiRenderState` → **在主 GUI pass 之前**（`GameRendererMixin` 注入 `render` 的 HEAD，此时没有 pass 打开）用自建 `GuiRenderer` 烘进离屏目标 → 之后每帧只 1 次 blit。
  - **烘焙时的两个临时替换**（都在 `finally` 里还原）：把 `GameRenderer.mainRenderTarget` 指向离屏目标；把 `GameRenderState.windowRenderState` 的 `width/height/guiScale` 改成"面板就是整个窗口"。GUI 的投影和裁剪区**都来自这个 state**，所以面板自身坐标直接落在纹理像素上，不需要任何坐标补偿，1 纹素 = 1 屏幕像素。
  - **配置写入必须通知缓存**：`ConfigManager.revision()` 只在 `ConfigManager.save()` 里自增，而绝大多数设置是**裸写字段**（`cfg.x = v`），所以 `Setting`（`BooleanSetting`/`SliderSetting`/`ColorSetting`/`DropdownSetting`/`TextFieldSetting`，通过三个 `touch(...)` 重载）和 `Module.setEnabled` 都把 setter 包了一层 `ConfigManager.touch()`。**新增任何会改变面板外观的写入路径都必须走这层包装**，否则缓存不会失效：**实测症状**是"启用面板缓存后开关模块，按钮显示更新很慢（要等鼠标移动改变 hover 键、或 600 帧兜底才刷新），像卡了一下"；滑条同理（拖动过程中面板一直显示旧值）。
  - **失效条件集中在各页自己的缓存键**（`ModulesPage.pageCacheKey()` / `SettingsPage.pageCacheKey()` / `OneClickScreen.shellCacheKey(lmx, lmy, scaleX, scaleY)` / `ColorPickerPanel.staticCacheKey(scaleX, scaleY)`）：翻页/选中分类/滚动/搜索/侧边栏高亮/配置 revision（`ConfigManager.revision()`）/内容 revision（语言、布局重建）/设备缩放/开合动画是否结束/聚焦控件 + **hover 签名**（按"光标在哪个元素上"而不是原始坐标，所以在一张卡片内移动鼠标不会使缓存失效；设置/功能详情页因为控件自己画 hover，退化到"聚焦控件 + 控件自身 hover 标志"）。
    - **面板状态来自 `PanelView`**（翻页/选中/滚动/搜索/features）与页面自己的字段；**新增会改变面板像素的状态时，必须加进对应页的 `pageCacheKey()`** —— 漏了就是"偶发一帧旧画面"。
    - **随时间变化的东西必须在键里，否则会冻住**：缓存只在键变化时重烘，所以任何**自己会动**的控件（动画开关、进度、拖拽中的拾色器、闪烁提示）都必须让键跟着变 —— 现有两种处理：把动画值本身放进键（`sidebarHighlightY` 这样做的），或在设置页只哈希"聚焦控件 + 控件自身的 hover 标志"（此时控件内部状态变化仍会重烘）。新加动画控件时先想清楚它属于哪一种。
  - **抖动保护**：缓存键连续两帧都变（拖滑条、开合动画、拖面板）时**不烘焙、直接直绘**，避免"每帧多烘一次"比直绘还贵；内容稳定两帧后才录一次，下一帧 HEAD 烘焙。另有 600 帧的强制重录兜底。
  - **三条"缓存帧必须和直绘帧长得一模一样"的硬约束**（踩过坑，别改）：
    1. **烘焙前把离屏目标清成透明**（`clearTarget()`）。GUI pass 不会替我们清（MC 自己是在别处清的），不清就会在上一帧的烘焙结果上再混一次，越烘越深。
    2. **blit 必须用预乘管线 + `BlitRenderState`**（`GUI_TEXTURED_PREMULTIPLIED_ALPHA`，照抄 `PictureInPictureRenderer.blitTexture`：`GuiRenderState.addBlitToCurrentLayer(new BlitRenderState(pipeline, TextureSetup.singleTexture(view, sampler), new Matrix3x2f(pose), 0, 0, w, h, 0, 1, 0, 1, -1, null))`）。面板是烘在**透明**底上的，纹理里是**预乘 alpha**；直通 alpha 的 `blit(view, sampler, …)`（= `GUI_TEXTURED`）会把 alpha 再乘一次 → 带 alpha 的像素（字形抗锯齿边、圆角边、图标边）覆盖率被平方、略显淡 → 进出缓存就"闪现"。用预乘 blit 后**整块面板（含半透明外壳）合成都是精确的**，所以不需要分层，只要各页的缓存键覆盖了所有输入。
       - **绝对不要用 `fill(RenderPipeline, TextureSetup, ...)` + `GUI_TEXTURED_PREMULTIPLIED_ALPHA`**：`fill` 生成 `ColoredRectangleRenderState`（只写位置+颜色），而该管线的顶点格式要求 UV2/Normal/LineWidth，MC 建网格时抛 `IllegalStateException: Missing elements in vertex` → **崩溃**（实测）。`blit` 生成的 `BlitRenderState` 才满足该格式。
       - `BlitRenderState` 的构造实参按 `(pipeline, textureSetup, pose, x0, y0, x1, y1, uv, uv, uv, uv, color, scissorArea)` 顺序传；**UV 必须照抄 `PictureInPictureRenderer`：`0, 1, 1, 0`** —— 渲染目标纹理的 V 方向与 GUI 纹理相反（GUI 以 V=0 为顶、渲染目标以 V=0 为底），传 `0, 0, 1, 1` 会把缓存的面板**上下翻转**（实测）。scissor 传 `null`（面板没有额外裁剪，MC 的 `executeDraw` 对 null 走 `disableScissor`）。
    3. **面板缩放吸附到"设备尺寸为整数"，原点吸附到"整设备像素"**（`OneClickScreen.layout`）：
       - `scaleX/scaleY` 取整到让 `PANEL_W × scaleX × guiScale` 与 `PANEL_H × scaleY × guiScale` 成为整数（缩放只改 <0.1%，肉眼不可见）——否则纹理内部的光栅化网格会和屏幕错开，越靠外越偏（最右约 1px）。
       - `offX/offY` 必须是**整 GUI 单位**（`Math.round((width - PANEL_W×scaleX) / 2)`），**不能**按 `1/guiScale` 吸附、更不能按 `1/(scale × guiScale)` 吸附。两条独立理由：
        - 整 GUI 单位自动让 `offX × guiScale` 也是整设备像素，于是**自身没做设备吸附的元素**（普通 `fill`：搜索栏、图标占位、侧边栏选中、设置页按钮）在直绘帧 `floor(原点 + 局部 + 0.5)` 与缓存帧 `round(原点) + floor(局部 + 0.5)` 结果相同；原点带小数时它们会整体偏 1px（实测过：缓存生效时向右上偏）。
        - **裁剪边界根本无法落在设备像素网格上**：`ScreenRectangle` 存的是整数、单位是 guiScale 单位，`enableScissor` 在**那里**取整（`ScreenRectangle.transformAxisAligned(pose)`，且是**调用当下**用当前 pose 变换）。直绘帧"先加原点再取整"、缓存帧"先取整再在 blit 时加原点"，只有原点为整 GUI 单位时两种顺序才完全一致；否则差最多 `guiScale` 个设备像素 —— **实测症状**：静止（缓存）时下边缘蒙版正确，鼠标一动（直绘）蒙版上跳/右移。
      - 这两条吸附（缩放吸附整设备尺寸、原点吸附整 GUI 单位）**不要动**；改完请按"静止 vs 鼠标移动"对比右边缘、下边缘蒙版。
      - **矩形件要吸附"两条边"，不是"偏移 + 宽度"**：统一走 `snapSize(x, w, step)`（= `round((x+w)·step) - round(x·step)`，即 `fillDevice` / `fillVerticalGradient` / 全部圆角 helper 内部用的那个）。分别取整会让共享边的两块差 1px：整宽条得到 `round(x) + round(w)`，而它的邻居从 `round(x+w)` 开始 —— 只要 `x × step` 非整数（面板缩放本来就非整数，所以基本总是）就会错开。**实测表现**：主体渐变从 `SIDEBAR_W` 起、宽 `PANEL_W - SIDEBAR_W`，右边缘比上下 tab 条短 1px（"面板右边缘没对齐"）；侧边栏 `w = SIDEBAR_W - 1` 与 1px 分隔条同理会漏一条缝。新增任何与已有块贴边的矩形背景，都必须用 `fillDeviceToRow`/`fillVerticalGradientToRow`/圆角 helper，且不要在外部自己算宽度取整。
      - **裁剪矩形的四条边必须吸附到 `guiScale` 的整数倍设备像素**（`scissorRow(design)`，`scissorContentArea` 已全部走它）。原因在 `ScreenRectangle.transformAxisAligned` 的字节码：它先用 pose 变换两个角点，然后 `new ScreenRectangle(floor(a.x), floor(a.y), floor(b.x-a.x), floor(b.y-a.y))` —— **宽高也 floor**。所以矩形尺寸不是整 GUI 单位时，右/下边会被 floor 掉最多 `guiScale − 1` 个设备像素：`guiScale=3` 时正好丢 2px，**实测症状就是"卡片蒙版下边缘比底 tab 上边缘低 2px"**。四条边都吸附后，位置与尺寸的 floor 都是精确的。
      - **裁剪的四条硬规则**（都是实测踩出来的）：
        1. **只能用 `scissorContentArea(...)`**，它内部 `pushDeviceSpace()` 后传整数设备坐标（`enableScissor` 是**调用当下**就用当前 pose 变换的，见字节码 `ScreenRectangle.transformAxisAligned(this.pose)`）。
        2. **边界必须是整设备像素**：直绘帧按真实屏幕 pose 变换、缓存帧按面板自身 pose 变换后再由 blit 平移，边界带小数时两条路径取整不同 → **"静止（缓存）时下边缘正确、鼠标一动就跳 1px"**。
        3. **四条边都必须落在 `guiScale` 的整数倍设备像素上**（`scissorRow` / `scissorFloor`）——理由见上一条 bullet（`transformAxisAligned` 连宽高一起 floor）。少这一条就会丢 1~(`guiScale−1`) px。
        4. **上/左/右必须向外取整（`scissorFloor`），绝不能裁进内容**：第一屏的控件正好布局在 `contentTop()`，若把上边界"四舍五入"到最近的网格行，有一半概率落到内容起始行之下，把最上面那个按钮的上边框切掉一条 —— **实测症状**："顶部按钮的黑色描边顶部比其它方向细"（两个设置页都会）。下边界相反，必须用 `contentBottomRow()`（= `scissorRow`）与底 tab 条的绘制行严格一致，否则不是留缝就是重叠。
      - **内容区下边界只有一个来源，而且必须是"设备行"**：`contentBottomRow()` 直接给出**设备行**（= 裁剪真正能落到的行，即 `guiScale` 的整数倍设备像素），`contentBottom()` 只是把它换算成设计单位供命中检测用。**底 tab 条（`fillRoundedBottomToRow`）、主体渐变（`fillVerticalGradientToRow`）、侧边栏件（`fillDeviceToRow`）、裁剪（`scissorContentArea`）全部共用这一个设备行**；与这条边界相接的新元素也必须用这组 `*ToRow` 函数，不要用设计坐标版。（`fillDeviceToRow` / `fillVerticalGradientToRow` / `fillRoundedBottomToRow` 与 `scissorRow` / `scissorFloor` / `snapSize` / `pushDeviceSpace` 都在 `render/GuiPixels`。）
        - **不要在"设计 ↔ 设备"之间来回换算**：`ScreenRectangle` 把裁剪量化到整 GUI 单位，所以"把该设备行换算回设计单位（int）再让绘制按设计坐标吸附"会**再取整一次**，必然差 1 个设备像素 —— **实测症状**就是"卡片蒙版下边缘比底部 tab 上边缘高 1px"（这是第二次踩：第一版直接把设计坐标给裁剪，差 1~2px；第二版换算回设计单位，仍差 1px）。要精确落到某设备行，绘制入口必须能接收设备行。
      - **交互必须用和裁剪同一套边界**：`PanelGeometry.inPanel`（面板矩形）/ `inBody`（面板内、上下两条半透明条之间）；页面内容的命中由 UI v2 树自己负责（`ModulesPage.pointerDown` → `InputRouter` → 控件 `contains()`），而 `ModulesPage` 只在 `ly >= TAB_BAR_H && ly < contentBottom()` 的区间内接管指针。**关键原因**：滚动后卡片会排到裁剪区之外（只是被裁掉、**没有**被移走），所以不能只按行/列区间比 y —— 否则就是"鼠标在 GUI 以外或底 tab 区域仍能选中卡片并交互"（实测）。`mouseReleased` / `mouseDragged` 故意不设限：拖拽要在面板外也能正常结束。
      - **"点到了什么"和"点了之后做什么"仍然分开，但分两层**：外壳 chrome 由 `gui/PanelInput.hit(lx, ly)` 解析成 `PanelInput.Hit`（`Kind` = `MODULES_TAB` / `SETTINGS_TAB` / `FOOTER_BUTTON` / `NONE`），**只做判断、不改状态**，`OneClickScreen.mouseClicked` 负责映射成动作（翻页、打开 HUD 编辑器）；页面内部（侧边栏行、搜索框、卡片、开关、导航按钮）则是**真实控件对象**，由 v2 的 `InputRouter` 命中并直接触发自己的 `onPointerDown`。新增面板可点元素时：先判断它属于外壳还是页面 —— 外壳加进 `PanelInput` 的 `Kind` 与 `switch`，页面就造一个 `Control` 挂到树里，**不要**再往事件方法里写布局算术。
        - **外壳判定顺序就是语义**（tabs → 底栏按钮），不要重排：`lx == SIDEBAR_W` 那一列属于侧边栏而不是卡片网格（两者边界相接），但这一判定现在在 `ModulesPage` 的树布局里，不在 `PanelInput` 中。
      - **紧挨着的行/列必须用半开区间**（`>= from && < from + 长度`）：两端都闭时，边界那一像素**同时属于上一行和下一行**（下一行起点正是上一行终点），于是 hover 高亮会盖住两个分类（实测："鼠标放在分类标签中间，选中预览显示在两个分类上"）。v2 里这条由 `UiElement.contains` 统一实现（`x < bounds.right() && y < bounds.bottom()`），`SidebarRow`、`Dropdown` 的行都用它 —— 自己写命中判定时必须照抄这个半开区间。卡片网格因为 `CARD_GAP = 24` 有间隔不会互相命中，但新增紧贴的行/列时必须注意。
    4. **blit 保持 1:1**：因为第 3 条，纹理尺寸正好等于 `panelWidth × scaleX × guiScale`（整数），blit 目标矩形（整数 pose 单位）落在同一个网格上，所以 `exactScale` 修正因子正好是 1（保留它作为兜底：万一尺寸/缩放不再对齐，它会自动把 blit 拉回 1 texel = 1 设备像素）。
  - 另外：**开合动画进行中不 blit**（`animating` 参数），因为缓存是 1:1 光栅化的，缩放采样既和直绘不一致、动画结束还会跳一下。
  - **失败降级**：任何异常（离屏 pass/纹理/sampler 出问题）→ 永久关闭缓存、打印 `[gui] panel cache disabled ...`、面板照旧直绘。看到那行日志就是缓存没生效（观感不变，只是没省开销）。改这里时要保留：`finally` 还原、异常降级、以及各页 `pageCacheKey()` 的输入项（漏一项 = 偶尔一帧旧画面）。
- 底栏（footer）：面板底部深色条，左下角「HUD 位置调整」按钮打开统一的 HUD 位置编辑器；内容区（`contentBottom`）不会延伸进底栏。
- 字体：`ClickGuiFonts`（Inter + Noto Sans SC），运行时矢量烘焙（见 §7.1），文字绘制/测量一律走 `ClickGuiFonts.drawText/width/lineHeight`。文字阴影默认关闭（几何量翻倍），面板设置页可开。

## 6. Mixin 规范

- **优先 Fabric 事件**，避免 mixin。
- 每个 mixin 顶部 javadoc 写清「目的 + 注入方法 + 注入点」。
- 私有字段/方法访问用 `@Accessor`/`@Invoker`（如 `MinecraftAccessor`、`FontManagerAccessor`）。
- 清单见 `skypocket.mixins.json`。

### 6.1 `require` 分级：核心硬失败，其余降级

`skypocket.mixins.json` 的 `required` 是 **`false`**，且 `injectors.defaultRequire` 仍为 1——真正的分级写在每个 `@Inject` 上：

| 级别 | 写法 | 适用 | 失败后果 |
|---|---|---|---|
| **核心** | `require = 1`（显式或默认） | 失败会造成静默的视觉/状态损坏 | 启动时大声报错 |
| **增强** | `require = 0` | 单个化妆性/便利功能 | 该功能失效，游戏照常启动 |

目前共 **45 个 `@Inject`：42 个显式 `require = 0`，3 个保持 `require = 1`**。

**这 3 处必须保持 `require = 1`，改动前先读原因：**

1. **`GuiScreenScissorMixin` 的 3 个注入**（`<init>` / `enableScissor` / `disableScissor`）。它们驱动 `ScissorTracker` 的**栈**：`push` 与 `pop` 必须成对，只挂上一半会让栈永久失衡，此后 ClickGUI 会一直裁错矩形——一种比启动失败难查得多的静默视觉损坏。
2. **`GameRendererMixin#sky$bakePanelCache`**（`render` 的 HEAD）。它是面板缓存的烘焙时机；丢了它缓存不会崩，但会退化成只靠 600 帧兜底，表现为"面板更新很慢"，同样难归因。

**新增 mixin 时按这个判据选择**：如果"注入没挂上"会出现**看起来像 bug、而不是像功能没开**的现象，就用 `require = 1`；否则用 `require = 0`。

### 6.2 启动自检（`diagnostics/MixinTargetCheck`）

它放在 `dev.skypocket.diagnostics` 而不是 `dev.skypocket.mixin`——原因见 §2.2，那里是这个包级约束的正式说明。

`require = 0` 的代价是沉默：某个目标方法被改名后，对应功能悄悄失效，玩家无法区分"开关坏了"还是"钩子没挂上"。

`MixinTargetCheck.report()` 在客户端初始化末尾跑一次，用**类名 + 方法名字符串**（避免 `NoClassDefFoundError`）逐个探测 14 个 `require = 0` 的目标方法是否存在：

- 全部存在 → `debug` 一行（默认不输出）
- 有缺失 → **一条 `WARN`**，列出功能名与缺失的 `类#方法`

设计约束：

- **纯诊断，任何异常都吞掉**——诊断本身绝不能成为新的崩溃源。
- 探测走 `Class.forName` + `getDeclaredMethods()`（并向上遍历父类），**不读 Mixin 的内部字段**：`MixinProcessor.injections` 之类的私有状态在不同版本间会变，绑定它就是给自己造一个"升级即崩"的点。
- 新增 `require = 0` 注入时，**顺手在 `MixinTargetCheck.TARGETS` 里加一行**，否则那条静默失效仍然无人知晓。

### 6.3 已知崩溃面（mixin 之外）

- **`ThemeStore` 的反射路径**（`gui/widget/ThemeStore`）。原实现在读/构造 `GuiTheme`/`GuiMetrics` 记录时 `throw new IllegalStateException`，而调用链 `OneClickScreen.mouseClicked → DevDrawer.mouseClicked` **没有任何 try 兜底**，异常会直达 MC 事件循环。现在所有反射入口都是**全函数**：`withColor`/`withMetric`/`colors`/`metrics` 失败时**整体返回原值**（不做半空白拷贝），`invoke` 失败时返回调用方给的 fallback，并**只记一次 ERROR**（`reflectionFailureLogged` 去重，避免每帧刷屏）。代价是那次主题编辑丢失，收益是游戏不崩。
- **6 个 `static {}` 初始化块**（`HudPositionEditorScreen`、`StatsBarHudElement`、`StatTextHudElement`、`Localization`、`FontLab`、`MiningBlocks`）。当前内容都是纯数据填充，不会抛可预见异常；但 `<clinit>` 一抛就是 `ExceptionInInitializerError`。**往里加任何可能失败的操作（IO、解析、反射）前必须自行 try**。

## 7. 26.2 关键反编译结论（踩坑记录，务必先读）

### 7.1 字体
- `Font` 构造器里 `lineHeight` **硬编码为 9**，与烘焙尺寸无关。
- `GlyphBitmap.getTop()` **硬编码 7.0f** 上升部锚点（基线恒在绘制点下 7px）。
- 结论：自定义字体按 2x 烘焙、0.5x 绘制时——`width` 随烘焙尺寸翻倍需折半；`lineHeight` 用原值 9；基线需补偿 `7 * (1 - scale)`。见 `ClickGuiFonts`。
- **不要在 GUI 绘制中创建/注册/释放 `DynamicTexture`**：`AtlasText` 的字形页是自建纹理，但 GL 纹理命名空间是全局的；绘制中途注册或释放纹理可能让后续 font atlas 采样变成纯色方块。`GuiCornerTextures` 的圆角 mask 因此改为**首次在 client tick 预烘焙，绘制阶段只读，未命中时回退逐行 AA**（详见 §7.1.1）。
- **运行时矢量字体（`VectorFont`）必须满足两条不变量，否则文字会糊、阴影会错位**：
  1. **advance 取整**：MC 的 FreeType provider 里 bearing 是整数（`bitmap_left/top` 直接 `i2f`），但 advance 来自 26.6 定点 `/64`、**是小数**。小数 advance 会让每个字形的笔位落在不同的子像素相位上（表现为某些字母发虚、原版 +1px 阴影间距忽大忽小，圆字母如 `o` 最明显）。`IntegerAdvanceGlyphSource` 包一层 `GlyphSource` 把 advance 取整。
  2. **1 光栅像素 = 1 设备像素**：`pixelSize` 返回的整数既是光栅尺寸也是**屏幕上的设备像素尺寸**，绘制缩放固定为 `1 / (renderScale × guiScale)`（**不**随 scale 变，scale 只体现在 raster 尺寸上），绘制原点吸附到设备像素网格（`grid = 1 / (renderScale × guiScale)`）。因为 raster 就是屏幕上的字号，尺寸必须用 `Math.round` 贴近目标值（≈9 × scale 设计单位）：把尺寸量化成偶数（如 `(size/2)*2`）虽然仍满足 1:1，但会让字号最大偏差约 1px 并在拖动缩放滑条时按 2px 跳变（每个尺寸都要单独建 FontSet，所以省尺寸的动机只在字体数量，不在清晰度）。见 `ClickGuiFonts.vectorGlyphScale` / `drawTextScaled`。
  3. **构建必须切片分摊**：一个尺寸 = 2 次 `FT_New_Memory_Face`（Noto 是 10MB CJK）+ 一次 `FontSet.reload`（reload 只存 providers，字形查找是懒加载的）。实测把整个尺寸放在一个 tick 建完仍会掉帧，所以 `VectorFont.Builder.step()` 把构建切成「Inter face / Noto face / reload+发布」多片，由 `ClickGuiFonts.tick()`（`END_CLIENT_TICK`，从**进游戏就开始**）每 tick 推进一片；`SLICE_GAP_TICKS` 控制片间隔。**新增字体尺寸时不要再往单 tick 里塞整字体构建。**
  4. **只预建当前设置可达的尺寸**：`queueRange()` 只入队 2 个（正文 + 标题，按 `clickGuiScale × SCALE_BASE / guiScale` 算）；拖动缩放滑条走到的新尺寸由按需路径现建（`ensureFont` 会复用在建的同尺寸字体，不会重复解析 face）。**不要恢复成"把所有滑条档位都预建"**：那是 22 个尺寸、每个都要解析一次 10MB 的 CJK 面，会让每次启动卡几秒，而其中绝大多数永远用不到。

### 7.1.1 圆角色块首次绘制污染字形图集（已修复）

**症状**

- 进入 `crosshair` 设置页后滚动到 `Target Tint`（或首次显示带颜色色块的行），`ColorField` 第一次绘制
- 返回 ClickGUI 首页后，所有 `AtlasText` 文字变成方块
- 切换 custom glyph atlas 能恢复
- 关闭面板缓存仍复现；其他没有 `ColorField` 的页面不复现
- 日志出现 `GL_INVALID_OPERATION: Texture name does not refer to a texture object generated by OpenGL`

**根因**

`ColorField.paint()` 的颜色色块使用半径 3 的圆角：

```java
painter.fillRounded(new Rect(...), 3f, argb)
```

`GuiPixels` 默认走 `GuiCornerTextures`。半径 3 的 mask 第一次被请求时，`GuiCornerTextures.get(3)` 会在 **GUI 绘制过程中**：

```text
bake NativeImage
→ new DynamicTexture
→ TextureManager.release(id)
→ TextureManager.register(id, texture)
```

`AtlasText` 的字形图集是自建 `GpuTexture`，不经过 `TextureManager`，但它和这些动态纹理共用同一个 GL 纹理命名空间/状态。绘制中途注册或释放纹理后，后续字形 render state 采样到错误纹理，整页 glyph 变成纯色方块。不是字形缺失，也不是 atlas 页被回收；AtlasText 页创建/关闭日志正常。

**修复**

`GuiCornerTextures` 拆成两个阶段：

1. `get(radius)` 只在缓存命中且纹理有效时返回 mask；未命中时加入 `PENDING` 并返回 `null`
2. `GuiPixels` 在 `null` 时回退逐行 AA（当帧略慢，但正确）
3. `GuiCornerTextures.tick()` 在 `END_CLIENT_TICK` 安全烘焙 `PENDING`，下一次绘制再使用 mask

同时保留：

- 字形上传仍按原有路径立即执行；不要为此再引入延迟上传或 10 tick 延迟回收，否则会让新文字一帧/数 tick 后才出现
- `CrosshairRenderer` 的纹理必须按目标颜色 `render(color)` 烘焙；**不要**把它改成 tick 固定基础色，否则 Target Tint 会失效（曾经因诊断性 tick 改动回归过）

**约束**

- 任何 `DynamicTexture` / `TextureManager.register/release` 都不得在 `SettingsPage`、`GuiPanelCache`、`AtlasText` 的绘制路径中执行；`CrosshairRenderer` 的目标颜色纹理是例外，它必须跟随 `render(color)` 的当前目标色
- 新增圆角尺寸时只调用 `GuiPixels`，不要自己 `new DynamicTexture`
- 首次半径允许一帧逐行 AA；不要为了首帧观感把烘焙改回绘制期

### 7.1.2 HUD 字体按元素 scale 栅格化

HUD 文字不能再用固定的 `9 × guiScale` 位图去套元素 pose 的缩放，否则 scale 越大越是把位图放大，Regular 字重会明显发糊。

- `HudText.width(cfg, text, scale)` / `HudText.draw(..., scale)` 的 `scale` 必须是元素当前 pose 的缩放
- `ClickGuiFonts.hudPixelSize(scale) = round(9 × scale × guiScale)`
- `deviceFactor = scale × guiScale`，保证 1 个栅格像素 = 1 个设备像素
- 字重按元素 scale 分档：`< 1.0` Regular，`1.0–1.8` Medium，`>= 1.8` SemiBold；默认 scale = 1 使用 Medium
- `lineHeight` 仍是本地 GUI 单位 9，由 pose 负责缩放
- `MiningBreakRenderer` 的固定 1:1 debug 读数使用不传 scale 的重载，不要把它当成普通 HUD 字体路径

新增 HUD 文字时必须同时改 `width` 和 `draw` 的 scale，不能只改其中一个，否则框体、居中和行距会漂移。

### 7.2 输入事件
- `Screen.mouseScrolled` 是 `GuiEventListener` 接口的 **default 方法**，`@Mixin(Screen)` 注入不到；抓滚轮要注入 `MouseHandler.onScroll(JDD)V`。

### 7.3 渲染
- 自定义准星纹理不能原地 `setPixels` 换尺寸（会崩），要按固定最大尺寸烘焙后只 blit 实际区域。

### 7.4 HUD 位置
- HUD 位置存的是 **GUI 缩放后的绝对像素**（`clockX/Y`、`dayCountX/Y`、`fpsTpsPingX/Y`），默认值在 `ModConfig` 里。
- `HudPositionSync.sync()` 每帧在 HUD 渲染前调用：窗口/GUI 缩放变化时按新旧尺寸比例重映射位置，并把**完全出屏的位置重置回默认**（自愈），避免元素永久丢失在屏幕外。
- 位置只在三处写入：`HudPositionSync`（重映射/自愈）、`HudPositionEditorScreen`（拖拽/方向键微调）、`ModConfig.sanitize`（钳制 ±100000，无屏幕尺寸）。**新增可定位 HUD 时务必纳入 `HudPositionSync` 的映射与自愈分支。**

### 7.5 「实体发光（outline）」类功能实现方式（照此快速复制）

给某类实体加「可选发光 + 自定义颜色」的通用套路，分两步，缺一不可：

1. **强制发光**：`EntityMixin`（`@Mixin(Entity.class)`）注入 `isCurrentlyGlowing()`（HEAD, cancellable），命中目标实体时 `cir.setReturnValue(true)`。基类渲染会经 `Minecraft.shouldEntityAppearGlowing(entity)` 走到这里。
   - 例：玩家发光（第三人称限定）、末影龙发光（`instanceof EnderDragon`）。

2. **改颜色**：在**目标实体的渲染器** `extractRenderState` 注入 TAIL，设 `state.outlineColor = cfg.xxxColor`。
   - `outlineColor` 字段在基类 `EntityRenderState`（`0` = 不发光，非 0 = 发光；`appearsGlowing()` 即 `outlineColor != 0`）。基类 `EntityRenderer.extractRenderState` 在发光时设 `outlineColor = ARGB.opaque(getTeamColor())`，故 TAIL 覆盖即可。
   - 玩家 → `LivingEntityRendererMixin`；末影龙 → `EnderDragonRendererMixin`（龙渲染器**不是** `LivingEntityRenderer` 子类，需单独 mixin，注入 `extractRenderState(EnderDragon, EnderDragonRenderState, float)`）。

3. 配置 `xxxGlowEnabled` + `xxxGlowColor`；`ModuleDefinitions` 用 `ColorSetting("发光颜色", …, defaultExpanded=true)` 注册模块。

**新增某种实体发光时**：① `EntityMixin` 加一个 `isCurrentlyGlowing` 分支；② 若该实体渲染器是 `LivingEntityRenderer` 子类，直接复用 `LivingEntityRendererMixin` 加分支，否则新建对应渲染器的 mixin 覆盖 `outlineColor`。

### 7.6 SkyBlock 模块门控（只在 Hypixel SkyBlock 生效）

- `SkyBlockDetector`（`net` 包）提供：`isOnHypixel()`（服务器地址含 `hypixel.net`，兼容直接连接）、`isOnSkyBlock()`（记分板 SIDEBAR 标题含 `SKYBLOCK`）、`isActive()`（门控：`!skyBlockModulesOnly || isOnSkyBlock()`）。
- 配置开关 `skyBlockModulesOnly`（默认 true），在 ClickGUI「设置」页用 `BooleanSetting("SkyBlock 模块仅在 SkyBlock 生效")` 暴露。
- **标记为 `skyBlockOnly` 的功能，实际逻辑必须用 `SkyBlockDetector.isActive()` 门控**（mixin/渲染器里 `cfg.xxxEnabled && SkyBlockDetector.isActive()`），否则开关不生效。新增 SkyBlock 模块时：注册 `Module` 后调用 `.markSkyBlockOnly()`，再确认渲染/事件路径有 `isActive()`。
- 出生点隐藏使用 Hypixel Hub 的硬编码点 `(0.5, 77, -0.5)`，半径可配置；NPC 判定复用 SBA 的启发式（UUID version 2 + 满血 + 未睡眠），位置缓存由 `SkyBlockNpcTracker` 每 tick 更新。该隐藏只作用于 `PlayerLookalike.isRealPlayer`，不会误隐 NPC 本身。
- 技能进度显示只解析 ActionBar 中当前获得经验的技能，4 秒（可配）后消失；未满级显示 `+gained Skill (current/total)`，`total=0` 的满级显示 `+gained Skill (current)`。模块设置里的 `仅显示百分比` 可强制使用 `+gained Skill (xx.xx%)`；关闭时百分比-only 的 ActionBar 用 `SkillXpTable` + `SkillLevelTracker` 反推 current/total，TabList 等级未知或已满级时保留百分比作为回退。六个技能图标直接使用原版物品图标：Farming 钻石锄、Mining 钻石镐、Combat 钻石剑、Foraging 钻石斧、Fishing 钓鱼竿、Enchanting 附魔台。图标随资源包变化，不额外维护贴图。表维护见 `docs/MAINTENANCE_TABLES.md`。

### 7.7 钓鱼热点（Hotspot）检测与覆盖层

- 热点中心 = 服务器生成的 `"HOTSPOT"` 标记实体位置（水面/岩浆通用）。**实测 Hypixel 用的是带自定义名称的盔甲架**（`ArmorStand.getCustomName()` 含 `HOTSPOT`），不是 `TextDisplay`；代码两种都兼容（盔甲架优先，`TextDisplayAccessor` `@Invoker getText` 兜底）。
- 半径 = 从服务器的**粒子环**估计：`ClientPacketListenerMixin` 在 `handleParticleEvent` **TAIL** 注入（HEAD 会跑在网络线程，该 handler 开头有同线程检查），把每个粒子的水平距离按最近中心采样进 3s 滚动窗口，半径取 **85 分位数**（环上粒子都在圆周上，高分位可滤掉圈内噪声如浮漂水花），样本 ≥10 才生效。
- 底面高度 = 窗口内粒子 Y 的中位数 − 0.1；渲染 = `RenderTypes.debugQuads()` 无顶/底面的圆柱侧壁（48 段）+ `RenderTypes.lines()` 上下边缘圆环线。
- 中心键按 0.25 格量化；标记每 200ms 重扫，热点失联 2s 移除。**计时一律用 `System.currentTimeMillis()`**，不要用 `ClientLevel.getGameTime()`（多人客户端该值只在服务器发时间包时才跳变，会卡住间隔判断）。做类似「粒子环检测」功能时照此模式。
- **半径只算一次并锁定**：热点大小在其生命周期内不变，首次算出可信半径后 `locked=true`、清空样本并停止采样，此后只随标记消失（TTL）移除。锁定后无任何每帧重算/采样开销。
- 类型与配色：`HotspotType` 枚举定义 5 种热点（按 HOTSPOT 标记旁堆叠的属性行文字匹配，如 `+5 Sea Creature Chance`，匹配范围 3 格取最近），每种颜色独立可调（默认对齐属性行文字颜色）；识别不到类型时用 `hotspotOverlayColor`（未知类型）兜底。

### 7.8 镜头效果（受伤抖动）

- 受伤镜头抖动在 `GameRenderer.bobHurt(CameraRenderState, PoseStack)` 里，且与**死亡镜头同处一个方法**（`isDeadOrDying` 分支）。
- 只关受伤、保留死亡：在 HEAD 注入、当 `!state.entityRenderState.isDeadOrDying` 时 `ci.cancel()`。**不要**整体 cancel（会连死亡镜头一起关），也**不要**把 `hurtTime` 设成 0（0 仍会触发 Y 轴 `hurtDir` 倾斜）。
- 相关字段在 `CameraRenderState.entityRenderState`（`CameraEntityRenderState`，公开字段 `hurtTime/hurtDuration/deathTime/hurtDir/isDeadOrDying/isLiving`）。

### 7.9 聊天提及提醒（音效 + 文本输入 + 文件选择）

- 聊天检测用 Fabric `ClientReceiveMessageEvents.CHAT`（原生签名聊天）+ `GAME`（Hypixel 把玩家聊天走系统消息），都做大小写不敏感 token 匹配；`CHAT` 用 `sender.name()` 排除自己发的消息。
- 防误触发（重要）：`GAME` 路径**没有 sender**，Hypixel 会把聊天回显成 `名字: 内容`、进服公告也会带自己名字。所以：① 用 `ClientSendMessageEvents.CHAT` 记录最近发出的消息，收到 `: <自己刚发的内容>` 结尾的回显时跳过；② `GAME` 消息必须含 `: `（玩家聊天分隔符）才参与匹配，从而过滤掉 `Welcome back FriedLoop!` 这类无冒号的公告。单人世界走 `CHAT`（有 sender），不受此影响。
- 音效：统一走 `SoundManager.play(id, volume, pitch)`——`minecraft:...` 前缀走原版 `SoundManager`（`SimpleSoundInstance.forUI(event, pitch, volume)`，仅渲染线程调用，支持**音量 0~200%**/音高 0.5~2；>100% 是**放大**原版增益而不是被钳制，所以三个音量设置的上限都是 200）；其余视为自定义 MP3 文件名，用 **JLayer**（`javazoom.jl.player.Player`）在单线程守护 executor 解码播放（不应用音量/音高）。默认音效 `minecraft:entity.experience_orb.pickup`；旧配置的 `ding` 在 `sanitize` 迁移为默认。音效下拉用 `Setting.DropdownSetting`（带搜索，选项 = `BuiltInRegistries.SOUND_EVENT.keySet()` + 自定义 MP3）。
- 自定义音频目录：`<gameDir>/skypocket/sound/`（`FabricLoader.getGameDir()`），`init()` 时 `createDirectories`。「快捷上传」用 AWT `FileDialog`（EDT 上 `EventQueue.invokeAndWait`），选中后 `Files.copy` 到目录。
- 文本输入：`ui/widget/TextField`（`Control` 子类）+ `Setting.TextFieldSetting`；输入由 `InputRouter` 按焦点路由（`focus(UiElement)` / `focused()`），点击别处/重建/关闭时调用 `clearFocus()` 清焦点。
- 下拉选择（可搜索）：`Setting.DropdownSetting` → `ui/widget/Dropdown`。选项列表是一个**独立的、可搜索的虚拟化弹层**：只有选项数超过 `MAX_VISIBLE_ROWS` 时才在弹层顶部加搜索框（`TextField` + `onChange(this::filter)`），列表本身用 `scrollRow` 做虚拟滚动、只绘制可见行。搜索文本和滚动位置都存在 `Dropdown` 实例上（页面重排不丢），弹层由 `SettingsPage` 注册进 `InputRouter` 的 layer 栈，所以滚轮/点击先到列表而不是面板。

### 7.10 挖矿技能提醒（tab list 假玩家解析）

- Hypixel 把镐技能状态渲染成一个**假玩家的 tab 显示名**，两行：`Pickaxe Ability:` / `<英文技能名>: <n>s`（冷却中）或 `<英文技能名>: Available`（就绪）。技能一开启冷却立即开始，只有冷却/就绪两态。
- `MiningAbilityTracker` 每 200ms 轮询 `mc.getConnection().getOnlinePlayers()` 的 `PlayerInfo.getTabListDisplayName()`，匹配 `Pickaxe Ability` + 六技能英文名白名单（`MiningAbility.matchName`），解析尾部 `Available` 或 `<n>s`。tab 条目消失后状态保留 10s（TTL）再清空。
- 提醒触发：冷却 → 就绪的转变（tab 解析），聊天 `<名> is now available!` 作为备份触发；两路共享 5s 去重窗口。触发时 `mc.gui.hud.setTimes/setTitle(空)/setSubtitle(两段各自着色)` + `SoundManager.play`。
- 冷却倒计时 HUD：`MiningAbilityHudElement`（可拖拽，已接入 `HudPositionSync` 与 `HudPositionEditorScreen` 的 `MINING_ABILITY` 目标），就绪后 30s 未使用自动隐藏，下次进入冷却重新显示。**新增可定位 HUD 时同样要接入这两处。**

### 7.11 挖矿破坏预测（高延迟下的破坏时刻补偿）

- 目标：高 ping 下「方块实际已破但客户端还在画、要等一个整程 RTT 才消失」，让玩家提前知道可以换目标。**纯客户端：只读包 + 渲染 + 音效，不发送任何包、不做自动化。**
- **破坏时长 = 服务器公式 + 4 tick 下限**：`ticks = max(4, ceil(BlockStrength × 30 / MiningSpeed))`（秒 = ticks / 20）。
  - 下限来自实测，不是猜的：撞下限的样本 `observed/model` 中位数 **1.05**（≈4.2 tick）；没有下限时模型会算出 1–3 tick，短块因此**提前 2–3 tick**（描边先走满、方块还在画面里）。
  - 取整方向是开关（`miningTickRounding`：`ceil` / `floor` / `round`，默认 `ceil`）。三种取整对 `≤4` 桶无差别，长块以 `ceil` 最稳 —— **不要凭 wiki 直接改成 floor**，用下面的分桶比值判。
  - **秒破是独立规则**，不是「`ticks <= 1`」：`BLOCK` = `30S+1`、`ORE` = `60S`、宝石/矮人金属 = `60S+1`（见 `MiningBlocks.Category` / `instantSpeed`）。旧写法会把 `900~1799` 速度下的矿石误判成秒破而**完全不显示**。
- **伪装识别是唯一需要推断的东西**：Hypixel 把自定义方块伪装成原版方块。
  - 精确表 `MiningBlocks.EXACT`（无需标定、立即可用）：netherrack 8、end_stone 30、obsidian 500、sponge/wet_sponge 500（Sulphur）、矿石 `*_ore` 30、Pure 系列 = 矿物块 600、clay 5600（Tungsten）、polished_diorite 2000（Titanium）、**light_blue_wool 1500（Mithril）**、terracotta/brown_terracotta/smooth_red_sandstone 5600（Umber）、packed_ice 6000（Glacite）、宝石 = 染色玻璃 + 玻璃片。
  - 候选集（观测反推、连续 2 次一致才写入 `miningResolvedStrength`）：`stone` {15,50}、`cobblestone` {20,50,5600}、`andesite`/`diorite`/`granite` {50}、`cyan_terracotta` {100,500}、其余染色陶瓦 {100,500,5600}、**`prismarine` / `prismarine_bricks` / `dark_prismarine` {800,1500,2000}**、**`gray_wool` {500,2000}**、**`infested_stone` {15,50} / `infested_cobblestone` {20,50,5600}**；未登记的方块用全表。
  - ⚠️ **秘银强度被推翻过一次，教训比数值重要**：按参考文档写成 800/1500 后，一度被「7.2~12 tick」的样本说服改成 2000；修掉测量问题后，干净样本是 **6.0–6.5 tick @8064 = 1500** —— 那些大值是**重复包 + 松手样本**造成的假数据。**先修测量，再改数值。**
  - **候选全部一致时照常预测**（`MiningStrengthTable.agreedTicks`）：高速下各档都被压到同一个下限，此时不需要知道是哪一档。
  - **换区域自动重认**：已解析值若连续 2 次与观测差 >1.5 tick，丢弃重投。
- **观测样本必须干净（三条守卫，缺一条就会得到假数据）**：
  1. 同一次破坏**只记一次**（block update 与 chunk update 会重复下发，第二份把时长拉长）；
  2. 循环内**松过左键**的样本丢弃（服务端可能继承进度，破坏会「变快」）；
  3. `settled` 速度与开始时一致（±2%）才采信（tab 滞后真实速度可达数秒）。
- **速度刻度按类别各自拟合**（`miningCategoryScale`，键 = `BLOCK` / `ORE` / `GEMSTONE` / `DWARVEN`）：

  ```
  scale(类别) = median((到包偏移 − 整程RTT + 半 tick) / 原始公式时长)
  ```

  - **半 tick 是取整修正**：模型用 `ceil`，真实 5.2 tick 会被算成 6，比值天然低 `1/(2T)`（6 tick 时 8%、30 tick 时 2%）。不补这半 tick，短块会把刻度越拉越低（实测 0.80 vs 修正后 0.98）。
  - **为什么按类别**：实测宝石比表值快约 18%，矮人金属几乎不差（≈1.0）；一个全局 `scale` 会在两类之间来回飘（实测一分钟内 0.63 → 0.92）。
  - 每类满 **8 个样本**才生效（4 个时中位数每隔一次就跳）；只用**精确强度**方块（解析值本身依赖刻度，不能自证）+ 未撞下限的样本；窗口 16；`scale ∈ [0.4, 2.5]`。
  - 预测速度 = `面板速度 / 该类 scale`（同时用于伪装解析）。
  - **关掉「自动校准」时连拟合也停止**（否则数字会继续飘，而它并不生效）。
- **断言的起点：三档「对准时刻」**（`miningAlignTarget`，默认 `zero`）：

  | 档 | 断言 | 含义 |
  |---|---|---|
  | `zero` | `T0 + ticks×50 + bias` | 假设没有 ping（本地时长），手感最跟手 |
  | `server` | `下一tick边界(T0 + RTT/2) + ticks×50 + bias` | 服务端真正完成的那一刻 |
  | `client` | `T0 + RTT + ticks×50 + bias` | 客户端看见方块消失的那一刻 |

  - 三者各差一个**单向延迟**（200ms ping = 2 tick）。早期「要 -2 tick 才准」不是公式错，而是**在用 bias 手工把断言拉回 `zero`** —— 所以现在把目标显式化成开关，`bias` 退回微调（默认 1，范围 -6~3，一步一个 tick）。
  - `ph`（网格相位）只对 `server` / `client` 有意义：`phase = median((到包 − RTT/2) mod 50)`，样本 ≥8 才启用。`zero` 档**不做**吸附（按下不对齐任何网格）。
- **安全不变量**：权威包到达前进度封顶 `0.99`，所以环满**不会早于客户端看到的事实**；断言后保持到「确认 +150ms」或「断言 +1500ms」超时，再 250ms 淡出。⚠️ `zero` / `server` 档下断言会**早于**客户端可见的消失时刻，这是设计目标而非缺陷。
- 权威确认 = `handleBlockUpdate` / `handleChunkBlocksUpdate` 命中目标坐标且新 id 不同（相同 id 是区块重发，不算破坏）。确认时回算 `D = (到包 − T0 − 整程RTT)`，交给 `MiningStrengthTable`。
- **RTT 用 `PingMonitor.latency()`（EMA，整程）**，单向 = 一半。早期写的 `minLatency()` 已不适用：现在三档会显式加减单向/整程，用最小值会让两档互相打架。
  - ⚠️ **整程与半程仍是两个量**：观测减**整程**、`server` 档起点加**半程**；混用会让 `D` 偏大半个 RTT → 刻度被抬高 → 模型多 1 tick → 提前量被吃光。
- **异常护栏（不要改回 `max`）**：模型比「该方块上次真实破坏」短 1.5 tick 以上才回退到上次实测值；写成 `max(模型, 上次实测)` 会系统性偏晚半个噪声宽度。
- 渲染：准星圆环（`MiningBreakHudElement`，颜色按进度插值）+ 方块轮廓（`MiningBreakRenderer`，12 条边各拆两半、从 8 个角向中点生长）。
- **调试 HUD 怎么读**（设置 → 调试信息）：
  - `scale BL/OR/GE/DW:x.xx(n)`：按类别的刻度与样本数；`n<8` 不生效（显示 1.00）；
  - `ph`：网格相位 + 样本数（仅 `server`/`client` 档参与）；
  - `model` / `assert` / `observed` / `error`：`observed` 已减整程 RTT，是**服务端时长**；
  - `implied`：按本次耗时反推的强度；与 `strength` 差很多 ⇒ 表错或刻度错；
  - `capped` / `dropped`：被下限抬起的次数、被丢弃的短样本数；
  - `obs/model ≤4 / 5-8 / 9-16 / 17-32`：**判案工具** —— 随桶递减 = 加性偏差（下限/取整/服务端多一 tick）；各桶持平且 ≠1 = 乘性偏差（速度刻度）。
  - `speed` 显示 `-` 时看日志 `[mining] unrecognised Mining Speed tab line:` 的原始行，按实际格式改 `MiningSpeedTracker.SPEED`。
- **两个 block overlay 的绘制顺序（必读，踩过坑）**：`MiningBreakRenderer` 的进度棱边和 render 分类的 `BlockOutlineRenderer` 会**同时**画在同一个方块的同一批棱边上。
  - Fabric 的挂载顺序（`LevelRenderEvents` 的阶段语义，实际注册在 `MiningBreakRenderer` / `BlockOutlineRenderer`）：`COLLECT_SUBMITS` ← 特征提交步骤（**早**）；`BEFORE_BLOCK_OUTLINE` ← `submitBlockOutline`（**晚**）；`BEFORE_GIZMOS` ← gizmo 收集（更晚）。所以把挖矿 overlay 放在 `COLLECT_SUBMITS` 会被 block outline **压在下层**，而且在两者深度相同时（还都参与透明排序）谁在上会随相机移动来回变 ⇒ **闪现**。
  - 解决：挖矿 overlay 注册到 **`BEFORE_GIZMOS`**（更晚的提交阶段 ⇒ 画在 outline 之上），**并且**把整块几何沿"相机→方块"方向平移 `DEPTH_BIAS = 0.005` 格（沿视线平移只改深度、不动轮廓 ⇒ 看不出位移），这样即使落在同一层、参与透明排序也永远赢深度测试。**新增任何与已有 overlay 重叠的线框/半透明特效时，同样要选更晚的提交阶段 + 加微小深度偏置。**

### 7.12 UI 观感与性能的取舍（26.2 GUI 管线边界）

- **GUI 侧没有自定义顶点提交**：`GuiGraphicsExtractor` 公开面只有 `fill` / `fill(RenderPipeline,…)` / `fillGradient` / `outline` / 横竖线 / `blit` / `blit(GpuTextureView,GpuSampler,…)` / `blitSprite` / `text*` / scissor / pose / stratum。要自写 R2D 只能走 `GuiRenderState.addGuiElement(GuiElementRenderState)` + 自注册 `RenderPipeline`（内部渲染状态类型，26.2 才引入、版本间易变）——**除非确有效果必须，不要走这条路**。
- **SDF 字体在 MC 里不可用**：文字管线是 `Font → BakedGlyph → 图集采样`，没有距离场求值的位置；而文字开销主要来自"每帧把每个文本元素/字形转成顶点"（与屏
  - ⛔ **结论已定：距离场文字功能已于 2026-10 按需求全部删除**（`SdfFont`、`GuiClip`、`sdf_atlas.*`、`sdf_text*.vsh/fsh`、生成器 `tools/SdfAtlasGen.java`、`ModConfig.sdfPanelText` 与对应设置项全部移除）。**不要重新实现**，除非能先解决下面第 1、2 条。
  - **曾经的实测（记录于此以免重试）**：距离场渲染本身在 26.2 **可以跑通** —— `RenderPipeline.builder()` 克隆 GUI 管线只换 shader、每字形一个 `BlitRenderState`、片元里用 `texture().rg`（R=距离场、G=字形内高度）做阈值与逐像素纵向渐变，标题 `SkyPocket` 已经这样画（`SdfFont`，受 `ModConfig.sdfPanelText` 控制，**默认关闭**）。但**面板文字不能迁**，三条实测证据：
    1. **裁切不生效**：手建 blit 的 `scissorArea` 字段对这些绘制**不被引擎采用**（13 参构造确认它进的就是 `scissorArea`），所以面板内滚动区/卡片的文字会漏到面板外；只能用几何裁切（按裁切边切四边形+UV），而裁切矩形必须**由面板自己给出**（面板本地单位）—— 靠监听 `enableScissor` 抓引擎状态会把 GUI 空间的矩形写进面板本地坐标系，直接让裁切失效。
    2. **烘焙与直绘不可能一致**：`GuiPanelCache` 烘焙时伪造 `WindowRenderState`，其中 **`guiScale` 是 `int`**（源码确认），而直绘用 `getGuiScale()` 的 float；非整数缩放下内容先按 `round(scale)` 渲染、贴回时再用 `exactScale` 拉伸 ⇒ 内容落在不同栅格。⇒ 已加保护：**非整数 GUI 缩放时不启用面板缓存**。
    3. **没有自定义顶点提交**：拿不到裁切/批次控制权（同下条），这与"GUI 侧没有自定义顶点提交"是同一件事的两面。
  - ⇒ **结论**：SDF 保留给**不受裁切、不参与面板缓存**的文字（目前只有标题）；面板/HUD 文字继续走向量字体，掉帧问题用下面的三条杠杆解决，**不要再尝试迁移面板文字**。
幕文字量成正比），**换字形格式不改变这个开销**。SDF 的唯一好处是"一套图集吃所有字号"，现在用"按尺寸烘焙 + 后台分片预建"已覆盖。→ 结论：不做。
- **圆角的正确做法**：在现有 `fill` 体系里做分数覆盖率 AA（见 §5），或预烘焙 AA 九宫格 sprite 走 `blitSprite`；不要为此上自定义管线。
- **文字开销的现实杠杆**（按收益排序）：① 减少文本元素数；② 静态文本渲染到纹理后只 blit（复杂度中等，动画/hover 需另画）；③ 减少预建字号数（只省内存/启动）。已做：1:1 像素对齐、默认关阴影、屏幕外行不构建、只预建当前设置的 2 个字号、整块面板缓存（见 §5）。

### 7.13 世界后处理效果（抗锯齿 / 画面调色）

**定位**：这一层是"廉价观感增强"，不是光影。它只对**已合成完成的成品图**做一趟（或几趟）全屏处理：不重绘几何、不建 shadow map、不增 gbuffer，因此**成本只随分辨率增长、与场景复杂度无关**（传统光影相反：gbuffers 重绘场景 + shadow map + deferred，常见 −30%~−60% FPS、显存 +几百 MB；我们这条 1~3 趟、5~20 次采样，≈0.2~1.0ms @1080p、+2 张 swap ≈16MB）。代价是**做不出真阴影/间接光/反射** —— 那些必须回到 gbuffer + shadow map，成本就回到传统光影量级。

- **为什么用后处理（也解释了与光影 AA 的冲突）**：光影包的 AA（多为 TAA）必须**在渲染过程中**介入，每个像素要有 gbuffer 分类和 motion vector；分类不到的东西（其他 mod 的方块高亮、世界空间文字）会被历史帧覆盖或直接丢弃，**表现为"高亮/悬浮文字消失"**。后处理只读成品图再写回去，**物理上不可能删掉已画上的元素**。所以用法是**替代 pack 自己的 AA**（用光影时请把 pack 的 AA/TAA 关掉）。
- **位置只在 `GameRenderer.renderLevel` 的 TAIL**（`GameRendererMixin` 的 `sky$worldPostEffects`，一个注入里按固定顺序跑完所有效果）：世界画完、vanilla 的菜单模糊与 HUD/GUI 之前。放 GUI 之后会把文字/准星/ClickGUI 一起糊；放更早则读不到世界。**不要**占用 vanilla 的 post-effect 槽（`setPostEffect` 属于 creeper/spider 视觉效果，`checkEntityPostEffect` 会覆盖它）。
  - 顺序约定：**抗锯齿先、调色后**（AA 的亮度边缘检测要看未调色的图；调色是"最后一道 look"）。
- **26.2 接线（照抄 vanilla 的 menu blur，已抽成 `render/WorldPostChain.process(chain)`）**：`chain.process(renderer.mainRenderTarget(), renderer.sky$resourcePool())`。`resourcePool` 是 `GameRenderer` 的私有字段，经 `GameRendererAccessor` 取（vanilla 传的就是它）。链从哪来是 `LivePostChain` 的事（下一节）。
- **新增一个效果的固定步骤**（抗锯齿 / 调色 / 泛光都是这套）：
  1. `assets/skypocket/shaders/post/<name>.fsh` —— **裸 `.fsh`**（`#version 330`），顶点用原版 `minecraft:core/screenquad`，**不需要** pipeline 定义文件（`PostPass` 在 `createPass` 里自己建 `RenderPipeline`；jar 里 `shaders/post/` 全是裸 `fsh`）。
  2. `assets/skypocket/post_effect/<name>.json` —— `targets.swap`（+ 需要的中间 target）。**一个 pass 不能同时读写同一个 target**（GL 的 feedback loop，非法），所以要么 `main → swap`（你的 shader）→ `swap → main`（复用 `minecraft:post/blit`），要么像抗锯齿那样第二趟直接由自己的 shader 读 `swap` 写 `main`（省掉 blit）。
  3. 采样器名固定 `InSampler`（JSON 里 `sampler_name: "In"`）；`layout(std140) uniform SamplerInfo { vec2 OutSize; vec2 InSize; }` **由链自动提供**（blur.json 没写它也照样有；`InSize` 是**第一个**输入**像素**尺寸，所以要按"第一次输入的尺寸"语义去用它）；**自定义** uniform 块名/字段要与 JSON 的 `"uniforms": { "<块名>": [{name,type,value}...] }` 完全一致，**字段顺序即 std140 布局顺序**，同一块名可以出现在多个 pass（`LivePostChain` 会整体替换它）。
  4. `render/<X>Renderer.process()`：开关 → `开界面时跳过` → `CHAIN.apply(参数)` → `WorldPostChain.process(CHAIN.chain())`，并在 `GameRendererMixin.sky$worldPostEffects` 里按顺序加一行。
  5. **档位/预设一律是代码里的参数表**（`AntialiasingRenderer.PRESETS`、`ColorGradeRenderer.PRESETS`），只留**一个**模板 JSON 只描述结构 —— 不要为每个档位复制一份 JSON：参数已经是 live 的，档位只是"往同一组参数里写一整组值"。

- ⚠️ **后处理参数要"实时可调"就必须自己管理链（`render/LivePostChain`），不能用 `ShaderManager.getPostChain`**：
  - **首选路径：接管 uniform buffer（可写）+ 每帧上传。** `PostPass` 构造时把 uniform 值编成一个 `USAGE_UNIFORM` 的 buffer，**读 `customUniforms` 这个 map 却是每帧都做的**（`addToFrame` 的 per-frame lambda 里 `setUniform(entry.getKey(), entry.getValue())`）。所以链首次加载后，把该 map 里我们这个块换成**自己创建的 `USAGE_UNIFORM | USAGE_COPY_DST` buffer**（`PostPassAccessor`/`PostChainAccessor` 两个 accessor mixin），之后改参数 = **上传几百字节** ✓。序列化用 MC 自己的 `Std140SizeCalculator` + `Std140Builder.intoBuffer(...)` + `UniformValue.writeTo/addSize`，**逐字节与引擎原路径一致**（size 要用 calculator 的（含 std140 padding），否则绑定尺寸不够会报错）。上传用 `RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), bytes)`。
    - 好处：滑杆**按帧率**响应（没有 100ms 防抖延迟）、没有建/删链与管线重链接，而且**"每帧都变"的参数（相机矩阵）才成为可能**（动态模糊就是靠这个）。
    - 失败时（链结构意外、分配失败）自动退回下面的重建路径，不会留下半接管的链。
  - **退回路径：重建链。** 引擎自己建的 `customUniforms` buffer 没有 `COPY_DST`（`map`/`writeToBuffer` 都会被拒），拿不到 map 时只能重建。
  - 所以改参数 = **用打过补丁的 `PostChainConfig` 重新 `PostChain.load`**：模板 JSON 只解码一次（`PostChainConfig.CODEC`），之后重建 `Pass`（record 可直接复制，只替换目标 uniform 块的 `List<UniformValue>`，值用 `new UniformValue.FloatUniform(v)`，**顺序必须与着色器/JSON 完全一致**）。`PostChain.load` 还需要 `ShaderManager` 私有的 `postChainProjection`/`postChainProjectionMatrixBuffer` → 走 `ShaderManagerAccessor`（**只**用于喂 `PostChain.load`；深度线性化**不要**用这个投影，它是屏幕空间正交矩阵，见下面那条）。
  - **重建必须限流**（`REBUILD_INTERVAL_MS = 100`）：拖滑杆每个台阶都会触发一次，不限流会疯狂建/删链。重建的主要开销是**重新链接管线**（`GlDevice.pipelineCache` 是 `IdentityHashMap`，每建一条新链都是新 `RenderPipeline` 对象），**GLSL 模块本身按"编译键"缓存**（`shaderCache`），所以不是重编着色器；旧链**必须 `close()`**，否则泄漏 GL 对象。
  - **资源重载必须让链失效**（`LivePostChain.installReloadHook()` 注册的 `PreparableReloadListener` → `invalidate()` 所有实例）：重载会重建编译好的程序，继续用重载前的对象就是未定义行为 —— vanilla 的 `ShaderManager` 也因此在重载时丢掉自己的链。
  - ⚠️ **一个效果一条链，别为了省一趟 pass 把新效果塞进已能用的链**：链加载失败时是"整条链什么都不画"，所以塞进去 = 新效果出问题会把老效果一起带走（**实测**：把锐化/雾/AO 加进调色链后，模块整体失效）。宁可多一趟全屏 pass，也要让新东西独立成链 + 独立模块 —— 抗锯齿/调色/泛光就是这么分的。反过来说，**同一模块内**的东西可以共享一个块/一条链（抗锯齿的锐化 pass 与 FXAA pass 共用 `FxaaConfig`）。
  - **参数改动即使"开界面时跳过"也要先 `apply`**（链跟参数、只有 `process` 受跳过开关影响），否则关屏后画面还是旧的。调色默认**不跳过**：ClickGUI 是半透明的，调参本来就要在屏幕打开时看到世界。

- 🧊 **深度类效果（距离雾 / 伪 AO）：做过、已回退** —— 代码已删除（原 `ImageDetailRenderer` + `post_effect/image_detail.json`），但结论留着，将来接着做时不必重新踩：
  - **`"use_depth_buffer": true` 本身可用**：同一个 target 既当颜色采样、再声明一个深度采样（`{"sampler_name": "Depth", "target": "minecraft:main", "use_depth_buffer": true}` → GLSL 里是 `DepthSampler`），范例是原版 `post_effect/transparency.json` ✓。
  - ⚠️⚠️ **线性化必须用世界投影，不是 `ShaderManager.postChainProjection`**：后者是**屏幕空间正交矩阵**（后处理 pass 自己是用它绘制的），拿它算距离会得到纯垃圾（**实测** `scale=0.001, offset=1.0001` = 正交 n≈0.1 / f≈2000）。正确来源全走 public 字段：`mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.projectionMatrix`（同一结构里还有 `depthFar` 可对照）。
  - **正确的反演式**（不需要知道深度范围是 [0,1] 还是 [-1,1]，也不需要知道哪端是近 —— 两者都在那两元素里）：
    ```glsl
    // d = clip.z / clip.w；透视下 clip.w = -z_view、clip.z = m22*z_view + m32 ⟹ d = m32/z − m22
    float viewDistance(float depth) { return m32 / (depth + m22); }
    ```
    MC 造反相 Z 的办法是调 JOML `setPerspective` 时**把 near/far 对调**（见 `Projection.getMatrix` 字节码），范围 [0,1]/[-1,1] 由 `DeviceInfo.isZZeroToOne()` 决定。（**实测 [0,1] 设备**：`m22 = 0.05/2048 = 2.44e-5`、`m32 = 0.05` ⟹ depth 1 = 0.05、depth 0 = 2048 ✓。）曾经的错误写法是在 ndc 上再套 `2d-1`（那是 [-1,1] 的换算式）—— 多减那个 1 之后**所有地形都算出约 5 万方块的距离**。
  - **两个"深度不属于世界投影"的坑**：① **天空**：清屏深度落在深度范围的一端（反相 Z 的远平面是 0），要按它排除，否则整片天空会被染成雾色；② **第一人称的手**：它用单独的 HUD 3D 投影画进主 target，深度拿世界矩阵反演必然是错的（**实测症状**：手被雾到/被 AO 压暗而变色）—— 手永远在 1 方块内，所以"1 方块内不做雾/AO"这道门正好把它挡掉。
  - **为什么放弃**：即使公式正确、参数齐全（雾强度/起止/颜色/天空开关 + AO 强度/半径），观感仍不像"距离雾"，尤其天空与手这两处边界，调参成本高于收益。**锐化**不需要深度，已经单独保留并搬进抗锯齿模块。以后再做建议直接上**高度雾 / 真正的 SSAO**（需要相机矩阵或法线，一次性把"世界坐标重建"做对），不要在这次的半成品上继续加。

- **一条链可以有多个 pass，但一个 pass 不能同时读和写 `minecraft:main`**：所以每种效果都以"…… → 内部 target → `minecraft:post/blit` → `minecraft:main`"收尾。另外 **内部 target 的 `width`/`height` 是绝对像素，不是缩放比**，想半分辨率就得在窗口尺寸变化时重建链（泛光是全分辨率的，正是为了免掉这件事）。多 pass 链照抄 `post_effect/bloom.json`（4 个 pass，演示了同一 uniform 块出现在三个 pass、两个内部 target、`bilinear` 输入）。
- **泛光的两条硬约定**（各自对应一次实测返工）：
  - **半径必须是"真半径"**：亮部提取 + 模糊合在一个 pass 里是等价的（模糊是线性的，逐 tap 做亮部判定 = 先亮部再模糊），但**采样范围决定它是"光晕"还是"整体变亮"** —— 13 个 tap 只铺开 6 像素时，白色区域只是变亮、没有晕。现在用 9 tap 高斯（σ≈2），最外两个 tap 落在 `BloomRadius` 像素处（默认 28，可到 64），中间靠双线性采样填缝。
  - **合成必须用 screen 混合而不是相加**（`1-(1-src)(1-glow)`）：相加会把**已经发白的区域内部**继续推过 1 并被截断成一片死白（实测症状："强度拉高之后白色的东西太亮了"），而 screen 混合下白色像素无论强度多高都不变、同时暗背景上的光晕仍是全强度 —— 这才是"晕"而不是"变亮"。亮部软膝盖 `excess/(excess+0.30)` 让"接近白"的中间调只拿到很少量。

- **FXAA 的质量差别主要在"子像素"那一项**：`fxaa.fsh` 是 FXAA 3.11 的形状 —— 3×3 九采样求 `lumaMin/lumaMax` 与梯度方向，两个候选混合（1/3 与 1/2 跨度），**外加子像素抗锯齿**：把"中心与 3×3 均值的差 / 局部对比"超过 0.25 的部分按 `Subpix` 混入均值。**这一项才是"效果不如光影自带 FXAA"的来源**：边缘滤波看不见比像素更细的东西（栅栏、远处树叶、高光上的闪烁），它们没有可跟随的梯度，只能靠"往局部均值靠"去掉。想更强就降阈值（`STRONG` 档 `EdgeThreshold = 0.0833`）或提高 `Subpix`，代价是整体更软 —— 三档只是这三个数加 `Sharpen` 的组合（`AntialiasingRenderer.PRESETS`）。
- **动态模糊（`motion_blur.fsh` + `MotionBlurRenderer`）的三个关键点**：
  - **逐像素重投影**，不是屏幕空间统一偏移：由深度 + `InvViewProj` 重建世界坐标，再用**上一帧的 view-projection** 投影回去，得到该像素上一帧在屏幕的位置，沿这条线段加权平均。必须这样是因为相机旋转对近处像素的位移远大于远处，平移的位移又是 `1/depth` —— 统一偏移会把所有距离糊成一样。
  - **帧率自适应 = `scale = clamp(曝光时长 / 帧时长, 0, 8)`**：重投影速度天然随帧时长增长，除以帧时长再乘曝光时长就把"每帧固定模糊量"变成"每段固定**时间**的模糊量"，30fps 与 240fps 观感一致 ✓ 代价：CPU 一次除法 + 一个 float uniform（≈0）✓。**上限**：单帧重投影最多只能表达"一帧内的位移"（想要更长的曝光就得做历史帧累积 = persistent 内部 target，会变成拖影）；所以 scale 有上限，极低帧率下也只是一帧的位移。
  - **两个必须的排除**：天空（清屏深度没有世界坐标）与 **1 方块以内**（那里是第一人称的手，它的深度来自 HUD 投影，重投影必然是错的）；另外矩阵异常/传送后会算出荒谬速度，用"位移 < 0.5px 或 > MaxRadius 就丢弃/夹住"兜住，**宁可不出效果也不要糊屏**。
  - 相机矩阵：`viewRotationMatrix` 就是 `rotation(orientation.conjugate())`（**只有旋转**），所以 `view = viewRotationMatrix × T(-camPos)`，`viewProj = projection × view`（JOML 的 `translate` 是右乘）。
- ⚠️ **抗锯齿会把"世界空间里的小字"糊掉，而且是双重打击**：FXAA 的①方向滤波靠对比度找边缘 —— 字形笔画正是最高对比度的细笔画，必被处理；②子像素混入（`Subpix`）把中心像素与 9 邻域均值混合 —— 它的适用条件就是"比一个像素还细的特征"，又恰好命中笔画。两项一起，小字被均值替换掉大半。
  - 可调的三样：**阈值**（`EdgeThreshold`/`EdgeThresholdMin` 越高 = 越"只处理强边缘"）、**`Subpix`**（越小越护字，代价是细几何的闪烁 —— 这是唯一的取舍旋钮，两者本质无法区分：细几何想闪、文字想清楚）、以及**趟序**。
  - ⚠️⚠️ **锐化必须放在 FXAA 之后**：锐化加的是高频，而 FXAA 的工作就是沿边缘削高频 —— 先锐化等于让 FXAA 把它刚加的东西原样抹掉，**且恰好抹在文字笔画上**（旧的 `antialiasing.json` 就是"先锐化、后 FXAA"，现象是"锐化滑杆对文字完全无效"）。改成 `FXAA → 锐化`（标准做法 AA→CAS）后滑杆才真正生效。
  - 注意：HUD/准星/ClickGUI 走 GUI 层、在 `renderLevel` TAIL 之后，**永远不受影响**；受影响的只有画进世界图里的字（`TextDisplay` 悬浮字、实体名牌）。
- ⚠️⚠️ **凡是"在分数像素位置采样"的 pass（模糊、FXAA、形态学），输入必须 `"bilinear": true`**。踩过的坑（**实测**）：动态模糊的输入写成 `bilinear: false` 后，采样点被吸附到整像素 —— 于是**真实的几像素位移平均下来还是同一两个 texel，看不出任何模糊**；而任何"速度算错、位移很大"的像素（第一人称的手就是：它的深度来自 HUD 投影，重投影必然错）却能铺开十几个 texel，**糊得非常明显**。两者叠加出来的现象就是"只有手持物品有动态模糊" —— 其实世界一直在正确地算速度，只是看不出来。同一个坑也解释了 FXAA"效果不如光影"：它的 1/3、2/3 混合采样点同样落在分数像素上。
  - 只有"严格按整 texel 偏移取样"的 pass 才用 `false`（例如锐化的 ±1 texel、双线性放大/缩小的 blit、深度采样）。
- **动态模糊（`motion_blur.fsh`）与别人的实现对照**：光影包（BSL / Complementary / Photon —— 后者就暴露 `intensity` + `maxVelocity` 两个参数）都是"保存上一帧的 view-projection、逐像素重投影"，客户端（Lunar / Badlion）则是"用相机的旋转/位移增量构造屏幕空间速度场"（通常对平移加 1/深度 项，让远处平移视差小、旋转整体糊）。两者都是**纯相机**的：**都没有 per-object 运动向量**，所以"跑动的实体自己糊"在那些实现里也不存在。我这边走的是光影包那条（更准：旋转对近处像素的位移更大、平移是 1/深度）。
  - ⚠️ **坐标约定不要猜**：`minecraft:core/screenquad` 的顶点着色器是 `texCoord = uv; gl_Position = uv * 2 - 1;` —— **两个轴都与裁剪空间线性对应、任何轴都不翻转**（不是 D3D 式自上而下的纹理坐标）。我一开始按"纹理 v 向下"写了 `1 - uv.y * 2`，结果整个重建是**上下镜像**的：速度全成垃圾 → 再被下面的守卫一律丢弃 → 现象是**设置完全没效果**（而在此之前"没有守卫"的版本表现为"只有位移最大的那点（手）在糊"）。一个符号错了就是这两种极端，所以这类 pass 一定要拿 `screenquad.vsh` 对一次。
  - **速度拆成两项**，这是"不会整条失效"的关键：**旋转项不需要深度**（足够远的点在相机平移下位移可忽略，重投影它就等于旋转造成的位移 —— 旋转会让近处远处一起糊，这才是肉眼最明显的部分），**视差项**才用深度（逐像素重投影与远场重投影之差）。深度不可用、或该像素的深度不来自世界投影（第一人称的手）时，只是**退化成纯旋转模糊**，而不是变成零。
    - 远场探测点用 `InverseView` 把视图轴的 `(0,0,-500)` 变回世界，再用真实 `VIEW_PROJ` 求它的 `z/w` 作为 `FarDepth` —— 全程走真实矩阵，不假设深度区间约定，也避开远平面（`w≈0`）。
    - 三项守卫都是"防垃圾"而非"开关"：`VELOCITY_MARGIN`（3.0，旋转/平移换算成屏幕位移时的放大裕度，配合 `MIN_BOUND = 0.02` 夹下限）、`FAR_PROBE_BLOCKS`（500，取景深探测距离，`min(500, max(depthFar * 0.5f, 32f))`）、`MIN_PLAUSIBLE_DEPTH`（1.5 方块，低于此深度视为不可信）。另有 `MIN_VELOCITY`（0.0015，低于它视为静止）。
  - **自诊断**：状态发生变化时打一行日志（`[render] motion blur running/idle ...`），把 `exposure/frame/scale/maxVelocity/chain=live(writable|rebuilt)` 一次说清。**沉默的空操作是这类效果唯一无法靠看画面排查的故障**（着色器要么糊、要么原样返回，两者看起来都是"开关没用"），所以这条日志是必需品而不是调试残留。
    - 实测收益：日志里 `chain=live(writable)` + `maxParallax` 等于下限（说明平移≈0、速度只可能来自旋转项）这两点，直接把"uniform 没写进去""矩阵不对""速度算成 0"三种猜测一次排除，剩下的唯一嫌疑就是下面那条逐拍剔除。
  - ⚠️ **逐拍剔除必须比"到相机的距离"，不能比"世界坐标距离"**：模糊路径上像素本来就在扫过场景，距离越远、重建出的世界坐标位移越大（100 方块处 32 px 的扫掠 ≈ 局部 4 方块），于是"同一块世界"的判据把**所有**采样点判掉 → 加权和退回中心像素 → 表现是"**pass 在跑、速度算得对、但画面上什么都没变**"。而第一人称的手离相机只有约 2 方块，判据轻松通过 → **只有手在糊**。两者叠加就是"只有手持物品有动态模糊"。现在比的是 `abs(tapDistance - hereDistance)`（到相机距离，容差 `max(1.0, 距离*15%)`）；**天空按"无穷远"参与这个判据**（清屏后没有深度可重建，但它是和别的表面一样的表面，转身时要能自我模糊，同时不会把地形混进来）。
    - 暴露量 > 一帧的语义（`scale = 曝光/帧时长`）：`scale=4.9` 表示把速度外推了约 5 帧的位移，物理上单帧只含 1 帧信息，所以超过 1 就是纯观感选择；再配上"最大半径"截断，快速转身时模糊长度基本恒等于半径。
  - **帧率无关**：`scale = clamp(曝光时长 / 帧时长, 0, 8)` —— 重投影速度本身随帧时长增长，除回去再乘曝光时长，得到的是"固定**时间**切片内的位移"。上限来自物理：单帧重投影最多只含一帧的信息。
  - **必须剔除"深度不来自世界投影"的像素**：第一人称的手（HUD 投影）重投影到任意位置。除了"1.5 方块以内不处理"，还由 Java 侧按**本帧相机实际能产生的最大屏幕位移**（旋转 `angle/fovY` + 平移 `move/depth/(2tan(fovY/2))`，取安全系数）算一个 `MaxVelocity`，超过就直接丢弃 —— 这样传送/矩阵异常也不会糊屏。
  - 参数里带一个 **「调试：显示速度」**：把逐像素速度画成颜色（红=x、绿=y、蓝=模长，黑=无运动）。排查"到底有没有算出速度"比看画面猜快得多。
- **后处理顺序固定在一处**（`GameRendererMixin#sky$worldPostEffects`）：抗锯齿 → 动态模糊 → 调色 → 泛光。抗锯齿**必须第一**（它的亮度边缘检测要看未调色、未泛光的图），泛光**必须最后**（亮部提取要看最终画面，否则把曝光调高不会让亮部泛光）。
- **调色本身的两个要点**：① **不要**在这一层套 HDR 色调映射算子（Reinhard/ACES）—— 输入已经是 MC 调色后的 LDR 图，套上去只会整体变暗变灰；正确的做法是 pivot 对比度 + lift/gamma + 保亮度的饱和度（见 `grade.fsh` 注释）。② 六个参数在中性值下必须等于原画面（exposure/contrast/saturation/gamma=1、lift=0、temperature=0.5、vignette=0），这样每一档都只是"小幅偏移"，容易回退。
- 与光影共存：允许（我们的 pass 在 pack 合成之后、GUI 之前），但**必须关掉 pack 自己的 AA**，否则两层叠加；`光影下也生效` 开关是兜底（Iris 的最终合成时机在不同版本可能不同，观感异常就关掉它）。「精妙（Fabulous）」图形模式下 vanilla 自己的后处理也在跑，若出现异常可先排除该模式。
- `开界面时跳过`：屏幕打开时世界在 GUI/模糊之后，效果看不出来，直接省这一趟。
- ⚠️ 后续可做（按收益）：**半分辨率泛光**（质量更好、更省，代价是窗口尺寸变化时要重建链）；**3D LUT 调色**（查表纹理 + 同样的"参数变化 → 重建链"通道）；**进一步提 AA 质量**（把 FXAA 换成 SMAA 1x 的形态：模式检测 + 面积混合，代价是采样数翻倍；或者在全屏 pass 之外再加一趟只在边缘处生效的高质量路径）—— 深度类效果见上面那条"已回退"，别在半成品上继续加。

### 7.14 挖矿委托（tab list 区块解析，用于 HUD）

- 只有 **Dwarven Mines / Crystal Hollows / Glacite Tunnels** 会出现 commissions；tab list 里是 `Commissions:` 标题行 + 每行一条任务：`<名字>: <n>%`（进行中）或 `<名字>: DONE`（已完成）。**条目数随槽位变化（初始 2 → 完成 100 个后 3 → Peak of the Mountain 后 4）**，所以不按固定条数解析。
- ⚠️ **解析必须与"行的位置"无关**：Hypixel 把标题行和每条任务都渲染成**各自独立的假玩家条目**，而 `ClientPacketListener.getOnlinePlayers()` 返回的是 `Map` 的**迭代顺序（不是显示顺序）**，所以"标题后面那几行"完全不可依赖 —— **实测症状**：HUD 一行都不显示。`CommissionTracker` 因此把 `Commissions:` 只当**门控**（是否在矿区；大小写不敏感、冒号可有可无），任务行**各自匹配自己的形状**（value 是百分比或含 `DONE`），最多收 4 条（= 槽位上限）。这与 §7.10 技能提醒踩的是同一个坑（那条也是"每个条目独立解析，`<技能名>: <n>s` 自身足够特异"）。区块消失（离开区域 / tab 未同步）时 `entries()` 变空，HUD 自动隐藏。
- ⚠️ **条目顺序必须按 `PlayerInfo.getTabListOrder()` 排，不能直接用 `getOnlinePlayers()`**：`playerInfoMap` 是 `Maps.newHashMap()`（HashMap），`getOnlinePlayers()` 返回的是它的 `values()` —— 顺序与 tab 的显示顺序**毫无关系**。vanilla 的 `PlayerTabOverlay` 同样按 `getTabListOrder()` 排序渲染，所以 `TabListText.orderedPlayers()` 照做。不排的**实测症状**：一个跨多行的区块（委托列表）读出来是乱序的，"保持 tab 原顺序"更是无从谈起。
- ⚠️ **多行区块必须用 `TabListText.allLines`，不能用 `all`**：`all` 会把 tab 条目里的换行**压成空格**（它的消费方各自只要一个值），整块委托会变成一行而无法再拆开。`allLines` 按 `\n` 拆开，两种排版（每行一个假玩家 / 一个条目里堆多行）都能解析；`entry()` 因此拆成 `rawEntry()`（保留换行）+ 扁平化包装。
- HUD：`CommissionHudElement`（可拖拽，已接入 `HudPositionSync` 的重映射/自愈与 `HudPositionEditorScreen` 的 `COMMISSION` 目标）。标题行沿用 tab list 原文 `Commissions:`（游戏术语，不翻译）、颜色默认蓝；**任务行颜色即进度**：未完成色 `commissionsStartColor`（默认红）→ 完成色 `commissionsDoneColor`（默认绿），**在 HSV 里插值（色相走短弧 + s/v 线性）**，所以默认红→绿会经过真正的黄（0%→`#FF5555`、25%→`#FFAA55`、50%→`#FFFF55`、75%→`#AAFF55`、100%→`#55FF55`）；若按 RGB 逐通道插值，中点是发暗的橄榄色 `#AAAA55`，**不要改回去**。DONE 天然落在 100% = 绿色，不需要额外标记。设置：隐藏已完成（全完成后整块隐藏）、未完成置顶（`Comparator.comparing(done)` 稳定排序，不开启时保持 tab 原顺序）、三个颜色。**SkyBlock 分类下渲染必须用 `SkyBlockDetector.isActive()` 门控。**

### 7.15 玩家数据获取（Stats）—— 铁律速查

细节与正则全表见 **`docs/SKYBLOCK_STATS.md`**（外部实现提取 + 我们的落点）。要写任何读取玩家数值的功能前，先记住这六条：

1. **不要读客户端实体属性**：`getHealth()` / `getAbsorptionAmount()` / `getActiveEffects()` / `getArmorValue()` 在 SkyBlock 都是错的（血量/防御/法力由服务端结算，护盾与减伤不体现在实体上）。个人数值一律从 **ActionBar 文本**抠。唯一例外 `abilities.walkingSpeed`（速度），且必须标注为「客户端预测」。
2. **解析服务端文本必须保留 `§` 颜色码**：颜色即语义（`§c` 正常血 / `§6` 溢出、`§b` 法力 / `§3` 溢出法力）。⚠️ 项目现状是解析器**只用 `getString()`**（`MentionAlert`、`MiningAbilityTracker`），颜色已丢 → 做 stats 前先补一个 `Component → 带 § 字符串` 助手。
3. **ActionBar 有两个来源**：`ClientboundSetActionBarTextPacket` + system chat 的 `overlay=true` 消息（`ClientReceiveMessageEvents.GAME` 的 `overlay` 参数，项目里已有用法，**零 mixin**）；只接一种会漏字段。一律 `SkyBlockDetector.isActive()` 门控。
4. **缺值保留旧值**：Hypixel 只在数值变化时才带该字段，「没匹配到」**≠** 0。派生值（有效生命）必须在同一包内、依赖字段解析之后再算；清零是非对称的（溢出法力→0、血量/防御/法力→保留）。
5. **必须实现 `reset()`**：离开 SkyBlock / 换世界时清空（照 `TabListText.clear()` 的先例），否则 HUD 会显示过期值 —— 外部实现漏了这一条。
6. **标注可信度**：🟢 服务端口径 / 🟡 客户端预测（位置推断、本地 tick、`abilities`、音效 pitch、背包轮询）/ 🟠 推断（lore + tick）。🟡🟠 只用于提醒与显示，**不要用于不可逆操作**。

另有两个已知缺口（需要时再补）：`TabListText` 目前**每次调用都现算**（无脏标记缓存），且 `ClientPacketListenerMixin` 只监听了 tab 的 header/footer，**没有监听 `handlePlayerInfoUpdate`**。

### 7.16 SkyBlock 数值显示（生命 / 法力 / 防御 / 速度 / 活力）

**数据从哪来**：只能是 ActionBar 文本（客户端实体在 SkyBlock 是错的，见 `docs/SKYBLOCK_STATS.md`）。采集在 `skyblock/PlayerStatsTracker`：

- 事件用 `ClientReceiveMessageEvents.GAME` 且 `overlay == true`（actionbar 就是 overlay 消息，**零 mixin**）；门控 `SkyBlockDetector.isActive()`。
- ⚠️ **必须用带 `§` 的文本**：`§c` 正常血 / `§6` 溢出（吸收）、`§b` 法力 / `§3` 溢出法力 —— 同文本不同含义。因此新增了 `chat/ChatText.formatted(Component)`；26.2 的 `ChatFormatting` **不再暴露 code 字符与 RGB**，所以它是「取 `Style` 的 TextColor，按 RGB 找十六色最近邻」，格式标志用 `style.isBold()` 等，每段前缀 `§r` 保证互不继承。
- **缺值 = 不变，不是 0**（Hypixel 只在变化时发该字段）；唯二的例外是 **溢出法力（匹配不到归零）** 与 **活力（匹配不到视为隐藏）**。
- ⚠️ **吸收血量 = current − max，不是单独一个字段**：实测 actionbar 显示 `7,552/6,360❤`（金色 = 溢出状态），**当前值已经把吸收算进去了**，所以红色心只画 `min(current, max)`，超出的部分才是叠加上去的吸收。原来按文档去匹配「单独的金色数字」永远匹配不到（症状：完全看不到吸收心）。
- ⚠️ **"没这行" ≠ "为 0"，溢出法力与活力同样如此**：服务器**只在某个 stat 正被消耗时才把它印在 actionbar 上**，不消耗时那一段会整段消失。所以解析的默认语义是**保留上一次读到的值**（`accept()` 只在匹配到时写入）。⚠️ 这一条**故意偏离**参考实现（它把溢出法力归零、把活力标成隐藏）：那会让显示在两次施法之间闪成空。真正需要清零的只有离开 SkyBlock（`StatsOverlayRenderer.active()` 每帧驱动 `reset()`）。
- **数值的读法：当前值是"总量"，不显示 `+xxx`**。血量那一边 actionbar 本来就把吸收算进 current（实测 `5852/4660`），所以法力必须同构：**溢出法力直接加进 mana 当前值**（`mana + overflow / maxMana`），吸收与溢出只体现在**视觉**上（经典风格的第二层图标 / 现代条的第二层填充）。两套风格、两条路径共用这一条约定，别在某一处单独印 `+xxx`。
- ⚠️⚠️ **位置编辑器的"读"是 `switch` 表达式、"写"曾是 `switch` 语句 —— 这个不对称会造成"能选中、框也对、就是拖不动"**。实测症状：现代风格的条在编辑器里**能选中、绿框能显示**，但拖动/滚轮/大小滑块**全部无效**；而同一批数值在 **ClickGUI 里改缩放是好的**。根因是 `setX/setY/setScale` 里**没有 `BAR_*` 分支**：`switch` **语句**对未列出的枚举常量**默认什么都不做**（不报错、不警告），而 getter 用的 `switch` **表达式**必须穷举所以"读"永远正确 —— 于是选中的框画得完全正确、写回却落到空处。ClickGUI 之所以没事，是因为它的滑杆**直接赋值 `cfg.statsXxxBarScale`**，根本不经过编辑器。⇒ 编辑器里的每一个 `Target` 都要保证"读、写"成对；现在三个 setter 都先走 `StatsBarHudElement.kindOf(...)` 的委托分支，并且每个 setter 结尾做一次 **read-after-write 自检**（`verify`，不生效就 `warn` 一行，同类只报一次）。
- **命中容差（同一次排查中顺手修的、确实存在的另一半）**：条的默认高只有 6px，六条彼此只差 9px，所以"取第一个包含光标的框"既会**点不中**也会**点错条**。现在改为**最小包含框优先** + **8px 近似命中**（`GRAB_SLACK`）：点空处也会选中 8px 内最近的元素，选中框画绿色，是排错时的可视证据。
- **文字只在 `HudText` 一处测量与绘制**（HUD 侧）。字体切换功能已删除（见上），现在它只转发原版字体 —— 但保留这条规则：**测量与绘制必须同一字体**，否则所有居中/堆叠的东西（数值行、委托列表、编辑器框）会错位。
  - ⚠️ **绝不在帧内构建向量字体**：`VectorFont` 构建会解析 TTF（CJK 那份 10MB）。HUD 用的栅格尺寸（`DESIGN_SIZE × GUI 缩放`）已一并纳入后台预构建队列（`queueRange`）；`hudVector()` 只做查表、**从不按需构建**，未就绪时 `hudFontAvailable()` 返回 false ⇒ 全体 HUD 文字自动回退原版字体（就是"打开开关后过一两秒才变字体"的原因，可接受且无卡顿）。
  - HUD 的坐标是 GUI 单位、且没有自带 pose，而面板是设计单位 + pose 缩放：同一份向量字体需要不同映射 —— HUD 侧栅格尺寸取 `DESIGN_SIZE × guiScale`、绘制缩放取 `1/guiScale`，于是文字正好 `DESIGN_SIZE`（9）GUI 单位高 = **与原版字体行高一致**，HUD 布局不会因换字体而漂移。
- ⛔ **已移除（2026-10，按需求删除；以下三条保留为历史记录，勿据此实现）** —— ClickGUI 字体的两个自定义效果（黑色描边 + 上下对比）本质都是"同一次文字绘制多画几遍"，加在唯一出口 `ClickGuiFonts.paint` 上**（面板与 HUD 共用），所以开销是可解释的：
  - **描边** = 把同一串文字按环状偏移（每级 8 次）用黑色再画一遍；**上下对比** = 把同一串文字用 scissor 切成上半/下半，分别提亮与压暗。两者都在 `enableScissor` 的两段里各画一次。
  - ✅ **实测（字节码）`GuiGraphicsExtractor#enableScissor` 不切换 GPU 状态也不 flush** —— 它只是把矩形 `ScreenRectangle.transformAxisAligned(pose)` 后压进内部的 `ScissorStack`，真正的批次切分发生在后面。所以"上下对比"多出来的两个裁切**不是**两次渲染状态切换，只是顶点量与（至多）每段一个批次。
  - 结论：**HUD 侧十几行文字可忽略**；**ClickGUI 本体**每帧数百串文字、每串 ×(1+8×描边级数) 顶点，全开时开销明显 ⇒ 两项默认关闭，按需开。
  - ⚠️⚠️ **面板的基线约定 ≠ HUD 的基线约定，补偿只能给面板**：面板的 `y` 是它自己选定的"设计位置"（靠 `GLYPH_BASELINE*(1-s)` 补偿让基线落在那里）；**HUD 元素的 `y` 是按原版字体定的**（原版 ink 顶≈`y`，实测向量字形的 ink 顶也落在绘制原点）。把面板那套补偿搬给 HUD，会把每行压低 `7*(1-1/GUI缩放)` 像素（缩放 2 ≈ 3.5px）⇒ 现象是**位置编辑器的框看起来和文字错位**（框是按 `y` 算的）。**结论：两条路径的 y 约定必须各自保持，别"顺手统一"。**
  - ⚠️⚠️ **上下对比的裁切框必须"明显大于字形"，且切分线相对绘制原点量**：裁切框若取"行框大小"，字形（升部/降部）会被削掉一截 —— 描边再外扩 1px 后更明显（现象："文字上下被截取"）。另外切分线**不能**按裁切框顶边算：裁切框放大后，整行会全落进"亮"的那一半，现象是**文字只是发白、没有渐变**。正确做法：裁切框取 `±(lineHeight + margin)` 级别的宽松矩形（裁切只决定"允许画什么"，放宽是免费的），切分线取"绘制原点往下 `0.45 × 行高`"（ink 从原点开始、约一行框高，所以视觉中点略高于行框中点）。
- ⚠️ **已删除：把 ClickGUI 字体套用到全局/HUD（`allFontsClickGui`、`hudClickGuiFont`）**。原因是**方案不适配**，而不是没做完：**一个 `Font` 只有一份图集**，所以"锐利"要求按设备像素栅格化 ⇒ 度量也是设备单位 ⇒ 而原版每个调用点把度量当 **GUI 单位**读 ⇒ 字号/行距会按 GUI 缩放放大；反过来按设计尺寸栅格化则字号对、但被上采样 ⇒ 糊。两者不可兼得（要做到既锐利又字号正确，需要"按尺寸栅格 + GUI 单位度量"，那正是 MC 自己的字体栈内部在做的事）。⇒ 结论：**换字体这条路不要再走了**，HUD 文字统一用原版字体 + `HudText` 这一入口（保留"测量与绘制必须同源"的规则）。
- ⛔ **已移除（2026-10）——以下为历史记录**：描边/上下对比要作用于"任意字体"（含原版字体）时，拦截点是 `GuiGraphicsExtractor` 的三个 `text` 重载**（GUI 文字的汇聚处，也是唯一还拿得到 scissor 的地方）：`@Inject(HEAD, cancellable)` → 自己画（`ClickGuiFonts#applyToEffectsEverywhere`）→ `cancel`。两条护栏必需：**① 重入计数**（自己画的那几趟会再次进入同一个方法）；**② 按"字体身份"跳过我们自己创建的向量字体**（面板/HUD 的文字已在各自的绘制处上过效果，不排除就会描边两次）。⚠️ 边界：**世界空间文字（告示牌/名牌/全息字）走的是字体直绘路径，不经过这里，因此覆盖不到** —— 这是限制而不是遗漏。
- ⚠️ **原版 HUD 部件优先用 `HudElementRegistry.replaceElement` 接管，不要写 mixin**：26.2 把原版 HUD 拆成了可替换元素（`VanillaHudElements.*`），已接管的两个是 **准星**（`CROSSHAIR`）与 **手持物品名提示**（`HELD_ITEM_TOOLTIP` —— 即切换物品栏后淡入在热键栏上方的那行物品名，绘制逻辑在 `Hud#extractSelectedItemName`，淡出由 `Hud.toolHighlightTimer` / `lastToolHighlight` 驱动）。
  - **"改位置/大小"最保真的做法：把原版那一次绘制包在 pose 里**（`pushMatrix → translate(轴心+偏移) → scale → translate(-轴心) → 调原元素 → popMatrix`），**不要自己重画** —— 淡出计时、稀有度颜色、自定义名斜体、文字背景、以及"何时才画"的条件全部原样保留，我们只改落点与大小。并且：**"启用但偏移=0、缩放=1"必须直接透传**，启用这个功能本身不能改变观感。
  - **原版定位公式（实测字节码）**：`x = (guiWidth - 文本宽)/2`（水平居中）、`y = guiHeight - 59`。⇒ **缩放轴心用屏幕水平中心就行，不必去测文本宽** —— 两者是同一个点（`(W-w)/2 + w/2 == W/2`）。这很值钱：文本带稀有度/斜体等样式，宽度难以精确复现。
  - ⚠️ **"属于热键栏"的元素要存偏移、不要存绝对坐标**：它必须跟着热键栏走（窗口/GUI 缩放变化），相对偏移天然满足；绝对坐标则靠 `HudPositionSync` 等比换算 —— 所以**它绝不能进 `HudPositionSync` 的列表**（否则每次缩放都会把偏移又乘一遍）。编辑器里它的写回是**增量式**（`offset += 请求位置 − 当前盒子`），与盒子如何计算无关，天然通过 read-after-write 自检。
- **经典风格的数值文字走与其他行完全相同的路子**：生命/法力各是一个 `StatTextHudElement`（`Kind.HEALTH` / `Kind.MANA`）—— 可拖拽、可缩放、进位置编辑器、颜色取自同一套调色板，**不再贴在心/法力格旁边**（那是第一版的写法：那样既不能拖也不能缩放）。它们**只在经典风格下绘制**（现代风格的条自带数值），防御/速度/活力三行则两套风格都显示。默认坐标是**绝对值**（血量 `100,221`、法力 `335,221`，按典型 GUI 尺寸给的），所以它们**不跟随图标移动** —— 想要贴回去，在位置编辑器里拖一下即可。
- ⚠️ **满格判据要留浮点容差**（`reaches()`，千分之一格）：`(slot+1) * (max/20)` 不总能精确落在 `max` 上（`max=1127` 时乘积会略高），于是"正好等于上限"的值被判成差最后一格 —— **实测症状就是"满蓝仍差半格"**。全程都是 float，**没有整数取整**；容差只有几点的量级，盖不住真正的半格。
- ⚠️ **正则的锚点用颜色，不要用符号**：`❈` / `♨` 这类字形是**资源包画法**，不是协议字符（文档给的是那套实现的字形表，换个包就不成立）。实测 defense 因此读到 0（虽然 actionbar 就显示着 `1,251❈`）。现在：防御 = 「绿色数字，且后面不是 `%` / 小数点 / `/` / `s`」，活力 = 「`§4` 的数字对」，字形都变成可选。
- 速度取自 `player.getAbilities().getWalkingSpeed() * 1000`（`walkingSpeed` 字段在 26.2 是 **private**），是唯一的客户端预测值。
- `reset()` 在 `StatsOverlayRenderer.active()` 里每帧判定：不在 SkyBlock 就清空 —— **外部实现漏了这条，症状是回主城后 HUD 还在显示过期值**。
- 首次捕获会打 3 行 `[skyblock] action bar N: ...` 日志，用来**核对真实格式**（颜色语义是我们按文档推断的，若 absorption/overflow 对不上，改正则即可）。

**放在哪（原版 HUD 几何，实测常量）**：底行 `y = 屏高 - 39`，左列 `x = 屏宽/2 - 91`（生命），右列从 `屏宽/2 + 91` 向左排（饥饿位），槽尺寸 9×9、间距 8（相邻重叠 1px）、每行 10 槽。于是：

| 行 | 左侧 | 右侧 |
|---|---|---|
| `y` | 生命第 1 行 | 法力第 1 行（原饥饿位） |
| `y - 10` | 生命第 2 行 | 法力第 2 行（原空气位） |
| `y - 20` | 护甲条（原版，不动） | 空气泡（自绘，上移一行） |

**1 格 = max / 20**（生命与法力都是，共 2 行 × 10 格）；满格判据 `值 ≥ (i+1)*每格`、半格 `值 ≥ (i+0.5)*每格`（等价于原版心的 `health ≥ (i+1)*2` 逻辑）。吸收/溢出是**叠在同一批格子上**（同一套判据、原版 `absorbing_*` 与自绘 `mana_overflow_*` 都是透明背景）——所以第三行右侧那格空着，护甲在左侧，两者不冲突。

**纹理规范**（实测机制，不是约定俗成）：

- 路径 `src/main/resources/assets/skypocket/textures/gui/sprites/<子目录>/<名字>.png`，引用 id = 去掉 `gui/sprites/` 与 `.png`（例：`skypocket:mana/mana_full`）。**必须**在 `gui/sprites/` 下（旧的 `textures/gui/xxx.png` 不生效）。
- **不需要 atlas JSON**：原版 `atlases/gui.json` 用的是 `minecraft:directory` 源，其 `DirectoryLister` 走 `FileToIdConverter.listMatchingResources`，**跨命名空间**扫描 —— 丢进去就会被 `minecraft:gui` 图集收录。
- 绘制：`graphics.blitSprite(RenderPipelines.GUI_TEXTURED, id, x, y, w, h)`（GUI 单位）。**改图后 F3+T 重建图集**，不用重启。
- 规则：图标类 9×9 且描边在外侧列（否则 8px 间距下咬合不上）；`half` 与 `full` 同尺寸、右半透明；overlay（吸收/溢出）**背景必须全透明**，否则会盖掉底图；底板不透明；32 位 RGBA；**不要留透明内边距**（对齐会崩）；不要做 @2x/@3x。
- `.mcmeta` 只在两种情形需要：`{"gui":{"scaling":{"type":"nine_slice","width":W,"height":H,"border":B}}}`（可任意拉伸，`border` 支持整数简写）或 `{"type":"tile","width":W,"height":H}`（重复平铺）。尺寸固定就不需要它。
- 风格：**灰度/白色 + 代码染色** 能让一份图服务多个属性（现代条的填充就走这条）；原版心那种带描边高光的图必须逐张画。
- vanilla 风格**不需要新图**：血量沿用 `minecraft:hud/heart/*`（含 `absorbing_full|half`），空气沿用 `hud/air*`；只有法力需要 6 张自绘（`mana_container` / `mana_container_blink` / `mana_full` / `mana_half` / `mana_overflow_full` / `mana_overflow_half`）。

**两套风格**（`render/StatsStyle` 的两个实现，`render/StatsOverlayRenderer` 分发）：模块本身挂在顶层 `Category.HUD`，并标记为 `skyBlockOnly`。

- **`Setting#visibleWhen(BooleanSupplier)`**：给设置系统新增的能力，用来把「只属于某个模式」的行整行隐藏（两套风格的专属设置就靠它，否则切到经典风格还挂着一堆条形设置）。接线只有两处：`OneClickScreen` 构建循环里 `continue` 掉不可见的行、`Feature#settingsHeight()` 同样跳过（卡片高度要跟着变）。⚠️ **行是构建一次而不是每帧重建**，所以切换后必须自己触发重排：`ConfigManager.touch()` 走普通 setter 是不够的，要 `mc.schedule(() -> screen.rebuildAfterSettingChange())`（`contentRevision++` 会同时让面板缓存失效）。

- `VanillaStatsStyle`：用原版与自绘的图标画上表；法力不足两格时按原版节奏（`gameTime % 10 < 5`）闪 `mana_container_blink`。
- **现代风格 = 六个独立 HUD 元素**（`hud/StatsBarHudElement`，每条一个 `Kind`）：注册进 `HudElementRegistry`，于是在**位置编辑器里每条都有自己的拖拽目标、滚轮/滑块缩放与存档坐标**，与时钟/FPS 完全同机制。绘制仍是"描边 → 轨道 → scissor 裁剪的填充 → 叠加层"，只是搬进元素内部，并在 `pose` 里 `translate` + `scale` 到自己的位置。将来换纹理时，同一批矩形就是 `nine_slice`/染色填充的落点。
  - 唯一共享的量是 `statsBarWidth/Height`（一组条粗细不一看起来像故障而不是配置）与 `statsBarShowValues`；**每条另有独立开关与大小**（模块设置），位置则归编辑器。⚠️ 旧的 `条间距`/`距底部` 栈式设置**已删除**：位置改为绝对值，`HudPositionSync` 仍会在 GUI 缩放变化时按比例重缩放，越界则由 `resetToDefaults` 把整组复位（模块里另给「重置条位置」按钮兜底）。
  - 默认位置刻意放在**任何 GUI 尺寸下都在屏幕内**的地方（中右偏下一列）：HUD 元素一旦生成在屏幕外就点不到，只能靠越界自愈救回来。
  - 条只在**现代风格选中时**绘制（`statsModernStyle`），所以两种风格不可能同时画出同一批数字；`StatsOverlayRenderer` 现在只负责经典风格的图标。
  - **防御没有满条值 → 直接全填充**（它是个指示条，不是比例条）；**速度固定以 400 为满**，但条上的数值按服务器原值显示 —— 部分装备会把上限提到 500，那时玩家要看的是数字（`SPEED_FULL` 常量，不做成设置）；**活力的上限直接读 actionbar 发来的那一对**（`X/Y`，与生命/法力同构），所以它也不做成设置。
  - ⚠️ **活力是整数**：服务器发布的精度就是 1，早期按文档做的小数容错会在 HUD 上显示成 `12.0/25.0` —— 那是在**替服务器编造精度**。现在解析统一走整数（容忍多余小数点，四舍五入而不是丢弃整段）。
- 注入：`mixin/HudMixin` 在 `HEAD` 取消 `extractHearts` / `extractFood` / `extractAirBubbles`（**仅模块启用且在 SkyBlock 内**，出界后原版 HUD 原样保留），并在 `extractPlayerHealth` 的 `TAIL` 画我们的东西 —— 此时原版护甲已画完，我们的行叠在成品之上。⚠️ 代价：原版空气泡的「气泡破裂音效」在其方法内部触发，随之失效。
  - **每个原版片段各有开关**（`statsHideVanillaHealth/Food/Air/Armor` + `statsHideVanillaActionBar`）：经典风格画在原版位置，不隐藏就是两层叠一起；现代风格画在别处，玩家可能反而想留原版心。默认隐藏被本模块接管的三样，**盔甲条默认不隐藏**（它和我们的布局不冲突）。`extractArmor` 是 `private static`，注入的处理器也必须是 `static`。
  - **隐藏原版动作栏**（`extractOverlayMessage`）只跳过**绘制**：数值来自 `ClientReceiveMessageEvents.GAME`，与画不画无关，所以隐藏后显示照常更新。
  - **经验条没有开关**：它由 `ContextualBar` 画（`extractExperienceLevel` + 背景 + `extractRenderState` 三段），而同一个 contextual bar 还负责跳跃条/定位条，隐藏它会连带隐藏那两个 —— 要做得单独讨论。

**现代条换成自有纹理时的规则**（程序化版本已经是"描边 → 轨道 → scissor 裁剪的填充 → 叠加层"，纹理只是把这三层换成 sprite）：

1. **外框**：九宫格 sprite —— `bars/bar_frame.png` + 同名 `.mcmeta`：
   `{"gui":{"scaling":{"type":"nine_slice","width":48,"height":12,"border":4,"stretch_inner":true}}}`
   `width`/`height` 写**图的原始尺寸**（`blitSprite` 给目标尺寸时会缩放），`border` 是**圆角半径**（四边等厚，也可写成 `{"left":..,"top":..,"right":..,"bottom":..}`）。`stretch_inner: true` = 内部拉伸；**不写它是平铺**（想要重复花纹/刻度就靠这条）。最小可读尺寸 = `2*border + 1`。
2. **填充**：按**满条形状**画整条（左右两端都圆）—— 裁剪由 `enableScissor` 完成，所以半满时右端被切平、满条时两端都圆。❌ **不要**做"左圆右平"的专用填充图：那样 100% 时右端仍是平的，条尾会有缺口。**高度必须等于条高**（竖向渐变/高光才对得上），宽度随意（横向会被拉伸）。
3. **染色**：填充画成**纯白/浅灰**，颜色由代码给（`blitSprite(pipeline, id, x, y, w, h, argb)` 的最后一个 int 就是颜色，原版染色 sprite 走的就是它）⇒ **一张填充图服务全部属性与叠加层**。
4. 四角严格对称、**不要留透明边距**（会破坏对齐）；弧上允许半透明像素。
5. **只有外框需要 `.mcmeta`**；填充是"拉伸 + 裁剪"，不需要。
6. 最小纹理清单：`bar_frame.png`(+`.mcmeta`)、`bar_fill.png`（白，任意宽 × 条高）；可选 `bar_fill_gloss.png`、`bar_notch.png`。
- 防御/速度/活力是三个独立 HUD 文字元素（`hud/StatTextHudElement`），与时钟同机制：`HudElementRegistry` + `HudPositionSync` + 位置编辑器（各占一个 `Target`），颜色默认就是 ActionBar 的原色。

## 8. 模块清单（现状）

| 分类 | 模块 | 有设置 | SB |
|---|---|---|---|
| HUD | 玩家数值显示 stats_display（经典 / 现代两套风格） | 有 | 是 |
| HUD | 时钟 clock | 有 | |
| HUD | 世界天数 day_count | 有 | |
| HUD | FPS/TPS/Ping fps_tps_ping | 有 | |
| HUD | 手持物品名 held_item_name | 有 | |
| HUD | 技能进度显示 skill_display | 有 | 是 |
| HUD | 雷瓶显示 thunder_bottle | 有 | 是 |
| HUD | 物品拾取记录 item_pickup_log | 有 | 是 |
| 挖矿 MINING | 挖矿技能提醒 mining_ability_reminder | 有 | 是 |
| 挖矿 MINING | 钻头燃料条 drill_fuel_bar | 有 | 是 |
| 挖矿 MINING | 挖矿委托 commissions | 有 | 是 |
| 挖矿 MINING | 挖矿破坏预测 mining_break_predict | 有 | 是 |
| 战斗 COMBAT | 伤害飘字 damage_splash_compact | 有 | 是 |
| 钓鱼 FISHING | 钓鱼热点覆盖层 fishing_hotspot_overlay | 有 | 是 |
| 钓鱼 FISHING | 传奇海怪警告 legendary_sea_creature_warning | 有 | 是 |
| ESP | 人物发光 player_glow | 有 | |
| ESP | 掉落物发光 item_glow | 有 | 是 |
| ESP | 末影龙发光 dragon_glow | 有 | 是 |
| ESP | 隐藏附近玩家 hide_players | 有 | |
| ESP | 隐藏出生点玩家 hide_spawn_point_players | 有 | 是 |
| ESP | 隐藏 NPC 附近玩家 hide_players_near_npcs | 有 | 是 |
| ESP | 隐藏其他玩家礼物 hide_other_players_presents | 有 | 是 |
| ESP | 碰撞箱 hitbox | 有 | |
| ESP | 隐藏落地箭矢 hide_landed_arrows | 无 | |
| ESP | 隐藏插体箭矢 hide_stuck_arrows | 无 | |
| 渲染 RENDER | 自定义准星 crosshair | 有 | |
| 渲染 RENDER | 后处理 post_process（抗锯齿 / 动态模糊 / 画面调色 / 泛光 四个分组） | 有 | |
| 渲染 RENDER | 方块描边 block_outline | 有 | |
| 渲染 RENDER | 第三人称穿墙 third_person | 无 | |
| 渲染 RENDER | 受伤镜头不抖动 no_hurt_camera | 无 | |
| 渲染 RENDER | 手持物品动画 hand_item | 有 | |
| 渲染 RENDER | 滚动提示框 scrollable_tooltips | 有 | |
| 通知 NOTIFICATION | 聊天提及提醒 mention_alert | 有 | |
| 通知 NOTIFICATION | 聊天消息压缩 chat_compact | 有 | |
| 移动 MOVEMENT | 自动疾跑 auto_sprint | 无 | |

## 9. 多版本适配（26.2 / 26.3）

### 9.1 目录与构建

```
SkyPocket/
├─ settings.gradle.kts      include :26.2-fabric / :26.3-fabric（projectDir = versions/<名>）
├─ root.gradle.kts          根项目：multiversion-root 插件，声明版本节点
├─ build.gradle.kts         所有版本共享：按版本取依赖 + Bloom 改名规则
├─ gradle.properties        版本化键：fabric_version.26.2 / .26.3、java_version.<ver> …
└─ versions/
   ├─ mainProject           纯文本，内容 = 26.2-fabric（手写源码所在版本）
   ├─ 26.2-fabric/src       唯一手写源码树（可含 //#if）
   └─ 26.3-fabric/build/preprocessed  构建时由 26.2 解析生成；仓库不保留 src 副本
```

- 命令：`gradlew :26.2-fabric:build` / `gradlew :26.3-fabric:build`，产物在 `versions/<名>/build/libs/skypocket-<名>-<mod_version>.jar`（版本号取自 `gradle.properties` 的 `mod_version`）。
- **源码只改 `versions/26.2-fabric/src`**。`versions/<名>/build/preprocessed/` 是配置期生成的解析结果（每次构建重建），不要手改。

### 9.2 两条差异表达机制

| 机制 | 适用 | 位置 |
|---|---|---|
| **Bloom 改名规则** | 纯改名：包搬家、类/字段/方法/常量重命名 | `build.gradle.kts` 的 `bloom { if (atLeast("26.3")) { replacement("旧","新") } }` |
| **`//#if` 条件编译** | 签名变化、结构变化、行为不同 | 手写源码内，构建期解析 |

`//#if` 用法：

```java
//#if MC>=260300
        poseStack.rotateDegrees(axis, degrees);
//#else
        poseStack.mulPose(axis.rotationDegrees(degrees));
//#endif
```

规则（实现见 `build.gradle.kts` 的 `resolveDirectives`）：

- 条件只认 `MC` 与整数字面量，支持 `>= <= == != > <`；支持嵌套与 `//#elseif`；
- 指令必须是**整行**（前面可有缩进），`//#if` 与条件之间必须有空格；
- 两个分支**都可以是真实代码**：26.2 保留 `#else` 分支、26.3 保留 `#if` 分支，因此"同名方法两套实现"合法；
- `MC` 取值 = 版本节点号（`26.2` → `260200`，`26.3` → `260300`）。

> ⚠️ **不要用插件的 `preprocessCode`**：这个 fork 不生成跨版本源码（删掉目标文件后重跑也不会重建，跑主工程直接失败）。解析放在**配置期**自行完成，Gradle 9 也不会报"输出未声明依赖"。
### 9.3 26.3 差异清单（已处理）

| 面 | 26.2 | 26.3 | 处理 |
|---|---|---|---|
| 窗口/输入 | GLFW | SDL（依赖里没有 glfw） | `GLFW.GLFW_KEY_*` → `InputConstants.KEY_*`；`glfwGetKey(h,k)` → `InputConstants.isKeyDown(k)` |
| 修饰键/回车 | `KEY_LEFT_SHIFT` / `KEY_ENTER` / `KEY_KP_ENTER` | `KEY_LSHIFT` / `KEY_RETURN`（无 KP_ENTER） | Bloom 改名 |
| 纹理包 | `blaze3d.textures.*` | `renderpearl.api.textures.*` | Bloom 整包改名 |
| buffer | `blaze3d.buffers.GpuBuffer` / `TransientMemory` | `renderpearl.api.buffers.*`（`Std140Builder` **不搬**） | Bloom **精确到类名**，不要整包替换 |
| 渲染管线 | `blaze3d.pipeline.RenderPipeline` / `DepthStencilState`、`blaze3d.platform.CompareOp` | `renderpearl.api.pipeline.*` | Bloom 改名 |
| 鼠标按键号 | 左 0 / 右 1 | 左 **1** / 中 **2** / 右 **3** | `MouseButtons.isLeft/isRight`（`//#if` 各一份），Bloom 把 18 处字面量判断改走它 |
| 实体类 | `EnderMan` | `Enderman` | Bloom 两条（**全限定名 + 裸名**，只改 import 对 usages 无效） |
| 手持渲染类 | `ItemInHandRenderer` | `FirstPersonHandsAndItemsRenderer`（`applyItemArmTransform`/`swingArm` 仍是私有同名方法） | Bloom 只改 `import` 与 `@Mixin(...)`，**不要改自己的 Mixin 类名**（类名必须等于文件名） |
| 粒子包 | `getParticle()/getX()/getY()/getZ()` | record 访问器 `particle()/x()/y()/z()` | `//#if` |
| `NativeImage.read` | `read(Format, InputStream)` | `read(InputStream)` | `//#if` |
| `TextureTarget` 构造 | `(String,w,h,boolean,format)` | `(String,w,h,GpuFormat color,GpuFormat depth)` | `//#if`，深度取 `main.getDepthTexture().getFormat()` |
| `PoseStack` 旋转 | `mulPose(Axis.X.rotationDegrees(v))` | `rotateDegrees(Axis.X, v)` | 收敛成 `rotate(...)` helper，`//#if` 只写一次 |
| 挥动 API | `swingingArm` + `getAttackAnim` + `ItemStack.getSwingAnimation()` | `getCurrentSwing()` + `getSwingAnimation()` + `ItemStack.getAttackAnimation()` | 见 9.5 |

### 9.4 Mixin 的三种失配与对应手法

| 失配 | 报错 | 手法 |
|---|---|---|
| 目标改名 / 返回类型变 | `could not find any targets matching 'swing(...;Z)V'` | 整个 `@Inject` 用 `//#if` 分版本（handler 签名一起改） |
| 目标**多了**参数 | `Invalid descriptor ... Expected (...DDDF...) but found (...DDD...)` | 只给 handler **参数表**加 `//#if`，方法体共用 |
| 目标**少了**参数 | `Expected (CallbackInfo)V but found (DeltaTracker,Z,CallbackInfo)V` | 同上，把多余参数在 26.3 分支去掉 |

参数表分版本（`//#if` 夹在参数列表中间，解析后就是合法参数表）：

```java
    private void sky$hideNearbyPlayers(Entity entity, Frustum frustum, double x, double y, double z,
//#if MC>=260300
                                      float partialTick,
//#endif
                                      CallbackInfoReturnable<Boolean> cir) {
```

- 能共用方法体就共用，只分派**参数表或目标描述符**；
- 注入目标被删（如 `getAttackAnim`）时，通常要换到新入口，而不是硬改描述符。
### 9.5 手持挥动：两个版本是两套实现

- **26.2**：`getAttackAnim(float)` 是唯一入口，没法改时长 ⇒ 只能**自跑时钟**（`SwingSpeedAnimator`）并在 `RETURN` 覆盖返回值，`swing()` 里 `restart()`。
- **26.3**：`swing(hand, SwingAnimation, updateSelf)` **把动画作为参数传入**，状态与计数器都按这个时长建立 ⇒ 只要在入口把时长改掉，原版自己走完：

```java
    @ModifyVariable(method = "swing", at = @At("HEAD"), argsOnly = true, index = 2)
    private SwingAnimation sky$lengthenSwing(SwingAnimation animation) {
        int ticks = SwingSpeedAnimator.customDurationTicks();   // 原版时长 / 速度
        return ticks <= animation.duration() ? animation
                : new SwingAnimation(animation.type(), ticks);
    }
```

- **不要在 26.3 再覆盖 `getSwingAnimation` / `getCurrentSwing`**：那是读侧补丁，会和原版计数器形成**两条时间线**（相位差约 1 tick），表现为**手抖**与末段跳变（实测 `vanilla=0.85 / custom=0.76` 交错）。
- 结论：**时长在写侧改一次，进度交给原版**；`SwingSpeedAnimator` 在 26.3 只负责"记录手 + 算时长"。

### 9.6 着色器（26.3 编译到 SPIR-V）

自定义后处理着色器（`assets/skypocket/shaders/post/*.fsh`）必须同时满足两条，否则 `PostChain` 加载失败、效果**静默不生效**：

```glsl
#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
```

- SPIR-V 要求显式 `location`；而 GLSL 330 的 **fragment input** 只有在该扩展下才允许写 `layout(location=)`。缺扩展会报 `SPIR-V requires location`，加扩展前先加 location 又报 `location qualifier on input not supported`。
- 排查依据：日志出现 `com.mojang.renderpearl.util.ShaderCompileException` 与 `[SkyPocket/postChain] failed to load post chain`。

### 9.7 新增一个 MC 版本（checklist）

1. `settings.gradle.kts` 的 `listOf(...)` 加 `"<ver>-fabric"`；
2. `root.gradle.kts` 加 `createNode("<ver>-fabric", <verCode>, null)` 并 `link()` 到相邻版本。**mappings 必须传 `null`**：本工程用官方映射（非混淆），传 `"srg"` 会抛 `Project appears to not be obfuscated but ... mappings set`；
3. `gradle.properties` 补齐该版本全套键（`java_version.<ver>`、`loader_version.<ver>`、`fabric_version.<ver>`、`modmenu_version.<ver>`），缺哪个都会在配置期报 `gradle.properties needs ...`；
4. `gradlew :<ver>-fabric:compileJava`，按 9.4 处理 mixin 失配、按 9.3 补 Bloom 规则与 `//#if`；
5. 运行期重点验：mixin 是否全部应用、Click GUI 能否点击、后处理是否生效、手持动画是否完整。

> 教训：多版本下**编译通过 ≠ 能跑**。mixin 描述符、鼠标按键编号、着色器 location 这三类问题都只在运行时暴露，必须实机过一遍。
### 9.8 26.3 运动模糊顿挫：定位、修复与遗留

**现象**：26.3 下关闭 vsync 时运动模糊"一顿一顿"，26.2 不明显；开 vsync 则正常。

**测量**（临时 250ms 窗口日志，已移除）：

| 场景 | calls/s | 帧时长 | `scale` 区间 |
|---|---|---|---|
| vsync On 静止/甩动 | 320 | 3–4ms | 4.0 ~ 6.9 |
| vsync Off 静止 | 2084 | 0.5ms | 4.5 ~ 8.0 |
| vsync Off 甩动 | 1447 ~ 1730 | 0.6ms（含尖峰） | **1.24 ~ 8.0（6.5 倍）** |

`scale = 曝光(16ms) / 单帧时长`，所以 `scale` 反推即帧时长：`1.24` ⇒ **12.9ms 的尖峰**混在 0.5ms 的帧里。

**根因一（已修）**：强度与**瞬时**帧时长成反比 ⇒ 一次尖峰就让模糊从"很糊"塌到"几乎不糊"，下一帧弹回。

- `frameTime` 走 EMA（`FRAME_TIME_SMOOTHING = 0.15`，≈7 帧时间常数）；
- 参与计算的帧时长设 1ms 下限（`MIN_FRAME_SECONDS`）；
- `scale` 每帧最多变化 ±15%（`SCALE_STEP`），残留台阶也不会闪。

修后：Off 甩动 `7.86 ~ 8.00`、On `4.9 ~ 5.2`，**脉动消失**。

**根因二（部分修）**：无位移时也在每帧跑 10-tap 全屏 pass。

- `maxVelocity < MIN_VELOCITY (0.0015)` ⇒ 直接跳过 `CHAIN.apply` 与 `process`；
- 但 `PREVIOUS` / `lastCameraPos` / `hasHistory` **仍每帧更新**，否则"静止→转身"会攒出一次突兀强模糊。

**遗留问题（未解决，留档）**：

1. **2000fps 无 vsync 时仍有帧尖峰**（calls/s 从 2084 掉到 1447）。0.48ms/帧的预算下，任何 1ms 额外开销都等于"这一帧翻倍"，属渲染循环/驱动/GC 层，模组只能降低自身开销，无法根除。实际运行环境帧率远低于此，**暂不处理**。
2. 降开销的可选项（未做）：`parameters(...)` 每帧新建 `List<UniformValue>` 可复用；高帧率（`frameTime < 1ms`）时隔帧执行 pass（方案 C，会轻微改变时基）。
3. 未验证：uniform 是否晚一帧生效（`PREVIOUS` 的采样时间点）。若将来在正常帧率下看到拖影方向错位，**先查这里**，而不是调强度常数。

**教训**：后处理强度**不要直接用瞬时帧时长**。反比关系会把任何帧尖峰放大成可见的强度跳变；必须平滑 + 钳位，否则调参永远调不稳。

## 10. UI v2（已落地，当前分支 `main`）

**目标**：把 ClickGUI 的字体、布局、输入和缓存收敛成一套可长期维护、可复用的 UI 基础设施。UI 内核、输入、控件、设置页和**首页模块网格**均已在 `main` 落地；本节保留最初的设计与实验记录，不再表示"尚未实现"。

**当前落地边界**（写清楚以免误判进度）：

| 层 | 状态 |
|---|---|
| UI 内核（`ui/`：元素树、`Style`、`LayoutEngine`、`ScrollView`、`UiPainter`） | ✅ 已落地 |
| 输入（`InputRouter`、key/pointer/text/preedit/wheel 事件） | ✅ 已落地 |
| 控件（`Control` 家族：`Switch`/`Slider`/`Button`/`Card`/`ColorField`/`Dropdown`/`Segmented`/`TextField`/`Scrollbar`） | ✅ 已落地 |
| 设置页（`SettingsPage`） | ✅ 已落地 |
| 首页模块网格（`ModulesPage` + `SidebarRow` + `ModuleCard` + `NavButton`） | ✅ 已落地，页面内零 `graphics.*` 直绘 |
| **外壳 chrome**（面板背景 / 顶部主 tabs / 底栏） | ⬜ 仍是 `PanelRenderer` 手工绘制 + `shellCache` 整块烘焙 |
| 主题抽屉（`DevDrawer`） | ⬜ 仍用 `ImmediateColorPicker` / `ImmediateTextField` 旧控件 |

**已定方向**：

- 参考 GuiLib 的思路，但**不引入、不复制 GuiLib**，也不引入 Kotlin。
- 不做完整 CSS/DOM/React 框架；先做元素、样式、Flex 布局、输入和文本内核。CSS 只作为后续可选的语法层。
- **不做 SDF/MSDF**。
- 字体走：共享 FreeType Face + 动态字形图集 + 小数字形笔位 + 文本 run 缓存；不再要求"一个光栅像素固定对应一个设备像素"。
- 不再把整块面板烘成纹理作为核心性能手段；布局、文本 run、绘制命令分别缓存。
- UI 内核不得依赖 `Feature` / `Setting` / `ModuleRegistry`，后续再抽成独立 Gradle 模块。
- 26.2 / 26.3 的身体共享，渲染、输入、鼠标按键等差异集中在平台适配层。

**顺序**（每步验收后再进入下一步，禁止同时重写四层）：

1. 字体观感实验：小数 advance、亚像素原点、2x 光栅、真实字重，确定默认策略。
2. UI 内核：元素树、样式/主题、盒模型、Flex、布局缓存。
3. UI 内核：输入、焦点、拖拽捕获、滚动、弹层、IME。
4. 文字 v2：动态图集、真实 metrics、文本 run 缓存。
5. 垂直切片：迁一个最复杂的设置页，关闭整块面板缓存并记录性能。
6. 全量迁移 ClickGUI，退役旧缓存。
7. 抽离可复用模块，补测试、文档和扩展示例。

**固定验证**：双版本 build；面板缩放 0.5/0.75/1.0/1.25/1.5；GUI Scale 2/3；中英数混排；键盘焦点/拖拽/滚动；帧率、内存、首开耗时。

### 10.1 字体实验结论（2026-11）

已实测并否决"继续调 MC 原生 `Font` 管线"，证据如下：

- `GlyphRenderState.textureSetup()` **硬编码** `FilterMode.NEAREST`；字体图集的采样模式不在字体对象上，改 `FontTexture` 的 sampler 也无效。
- `FontTexture.add()` 的 UV 内缩写的是 **`0.01` 归一化 UV**，在 256px 图集上等于 **2.56px**，不是 0.01px；同时字形单元之间**没有 gutter**。
- 只用 NEAREST：字形清晰但强整数步进，缩放会换一套字形，逐字号调参无解。
- 切 LINEAR + 小数 advance：观感明显接近 SBO，但相邻字形渗色产生外侧噪点，边缘也会被 UV 内缩裁掉。
- 给字形加 2–4px 透明 gutter 只能减轻噪点，不能根除。
- 试图用 `GlyphBitmap.getLeft/right/top/bottom` 补偿 2.56px UV 内缩会造成字号大幅缩小（quad 与 UV 的耦合和假设不一致），**已回退，不要再试**。
- 结论：**要同时做到无噪点、可线性采样、连续缩放，必须绕开 `FontTexture`，使用自研字形图集 + 自己的 UV/quad/linear sampler**。MC 原生字体管线到此为止。

当前可用 fallback：**关闭线性采样、1x 光栅、整数 advance、Light/ Normal hinting**。字体实验旋钮只存在于调试组，正式迁移前删除。后续文字 v2 的验收必须包含：多缩放下无台阶、无外侧噪点、无缺边、首开不卡顿。

### 10.2 自研图集原型（已跑通，2026-11）

`AtlasText` 已作为可切换的文字后端接入 `ClickGuiFonts`，26.2 / 26.3 均已实机验证。当前选定的默认组合是：**自研图集 + 1x 光栅 + 整数 advance + Device 原点 + Hinting None + Regular + Gamma 1.0**；调试组提供「应用最佳预设」「应用 2x 平滑」「恢复原生字体」三个入口。

**实现**：

- 独立 1024×1024 RGBA 图集页，每字形 3px 透明 gutter，页间留 2px。
- 每个字形 cell 先整块写透明，再写覆盖率；整页创建时也先上传全透明底。**不能依赖 `NativeImage(false)` 的默认内容。**
- 共享 `FT_Face`，按 `(字体, 字号, 字符, hinting)` 懒加载；Noto 作为 CJK fallback。
- 文本 run 按字符串 + 字号 + 设备比例缓存；相同文本不重复排版。
- 字形用 `u0/v0/u1/v1` 显式提交，线性 clamp 采样。
- 上传走队列，在 `run` 构建后和绘制前 `flush`；客户端 tick 可能因界面暂停而不可靠，不能只依赖 tick。
- 自研图集通过自定义 `GuiElementRenderState` 提交，该状态会进入 `GuiPanelCache.record()` 使用的 `GuiRenderState`，并由同一个 `GuiRenderer` 烘焙和回放；缓存命中时文字已经是烘焙像素，不需要重新排版。当前字体后端与整块面板缓存兼容，不再绕过缓存。

**关键教训**：

- 不要用 `graphics.blit` 提交自研字形：即使日志中的 UV 正确，`BlitRenderState` 路径仍可能让所有字形取到第一个 cell。必须用自定义 `GuiElementRenderState` + `GpuTextureView` + 显式顶点 UV。
- 自定义文字状态必须带 `scissorArea`。`enableScissor` 的参数是 **`x1,y1,x2,y2`**，不是 `x,y,w,h`，并且要按当前 pose 做 `transformAxisAligned`。用 `ScissorTracker` 维护裁剪栈，并每帧在 `extractRenderState` 开头清空，否则文字会溢出到 header、footer 和面板外。
- 不要试图用 `GlyphBitmap.getLeft/right/top/bottom` 去补偿 MC 的 2.56px UV 内缩，会造成字号缩小；自研图集不要复用 `FontTexture`。
- 图集调试导出 `TextureUtil.writeAsPNG` 的第一参数是**目录**，输出名是 `name_mip.png`；调试导出已从正式代码移除。

**下一步**：保持自研图集作为可选实验，先对比多缩放下的观感；确认后把文字 run 批处理、页面淘汰、真实 metrics 和 UI 内核一起收敛，并决定是否彻底移除整块面板缓存。

## 11. UI v2 布局内核（第一阶段）

**位置**：`dev.skypocket.ui`，纯 Java，不依赖 Minecraft、业务配置或渲染代码，后续可整体抽成独立模块。

**已实现**：

- `Dimension`：auto / 固定值 / 百分比。
- `Insets`、`Size`、`Rect`：盒模型与边界值。
- `Style`：不可变样式；宽高为 **border-box**；padding 在内、margin 在外；支持 min/max、gap、row/column、justify、align、flexGrow、flexShrink、flexBasis。
- `UiElement` / `Box` / `Text`：保留式元素树；文本通过 `TextMeasurer` 解耦。
- `LayoutEngine`：测量 + Flex 布局；支持 padding/margin、gap、grow/shrink、justify、start/end/center/stretch、百分比和 auto 尺寸。
- `Theme`：命名颜色和尺寸 token 的纯容器，不绑定 MC 类型。

**已用最小示例验证**：

- row：padding 10、gap 5、align center、flexGrow(1) 正确分配剩余宽度。
- column：百分比宽度、自动交叉尺寸、grow 正确分配剩余高度。
- space-between 和 flexShrink 的边界结果正确。

**当前限制**：

- 不支持 wrap、Grid、绝对定位、baseline、CSS 层叠/选择器。
- 文本不支持自动换行；由 `TextMeasurer` 决定测量结果。
- 已接入渲染器、输入层、焦点、滚动和完整 ClickGUI。
- 根节点 auto 宽高会填满宿主；显式宽高按样式执行。

**现状**：布局内核、输入内核和控件库均已完成，并作为 `SettingsPage` 与 HUD 设置弹窗的底层。

## 12. UI v2 输入内核（第一阶段）

**位置**：`dev.skypocket.ui.input`，同样不依赖 Minecraft。

**已实现**：

- `PointerEvent` / `WheelEvent` / `KeyEvent` / `TextInputEvent` / `PreeditEvent`
- `InputRouter`：命中测试、事件冒泡、焦点、拖拽捕获、hover 切换、层叠根、Tab/Shift+Tab 焦点遍历、滚轮/键盘/文本/IME 路由
- `UiElement`：父子关系、visible/enabled、clipChildren、焦点/文本/预编辑钩子
- `Box` 改为可继承，供后续控件复用布局能力

**已验证**：

- 点击命中、按下后拖出原控件仍由捕获元素接收移动/抬起
- 点击可把焦点交给最近的 focusable 祖先
- Tab 在 focusable 元素间循环
- 滚轮从命中元素向祖先冒泡

**已完成**：

- ScrollView 控件与滚动偏移渲染
- MC 输入适配层（26.2/26.3 鼠标键、键盘、IME）
- 控件库（Button、Switch、Slider、TextField、Dropdown、ColorField、Segmented）

**测试页**：迁移阶段的 `/skypocket uitest` 与 `Ui2TestScreen` 已删除；正式 ClickGUI 自身覆盖布局、滚动、点击/拖拽捕获、Tab 焦点和输入回归。

**崩溃修复（ScissorTracker）**：

- `ScissorTracker` 不再使用全局 ThreadLocal；现在按 `GuiGraphicsExtractor` 实例隔离（WeakHashMap），构造函数时清空该实例的栈，其他 mod 的 GUI 不会共享或继承我们的裁剪状态。
- `ScreenRectangle.intersection` 对不相交矩形可能返回 `null`；直接 `ArrayDeque.push(null)` 会 NPE，导致所有 GUI 崩溃。现在交集为空时降级为 `ScreenRectangle.empty()`，不再推入 null。
- mixin 只旁路观察 `enableScissor`/`disableScissor`，不取消、不修改参数和返回值；原版和其他 mod 的渲染行为不变。代价是每次 scissor 调用多一次 map 查找和 deque push/pop。

**26.2 / 26.3 输入差异（高风险，必须按平台适配层处理）**：

- 26.2 的输入后端是 **GLFW**；26.3 是 **SDL3**。这不是单纯换一个库：`Screen` 事件里的语义相同，但原始数字不同。
- 26.2 的 `KeyEvent.key()` 是 GLFW 键码；26.3 的 `key()` 是 SDL 键码，直接用 `256/257/258` 判断会导致 26.3 的 Esc、Tab、Enter 全部失效。
- 跨版本必须使用 `InputWithModifiers` 的语义方法：`isEscape()`、`isCycleFocus()`、`isConfirmation()`，再转换成 `InputRouter` 自己的 canonical keys。
- **鼠标按键也重新编号**：26.2 左/右是 `0/1`，26.3 是左/中/右 `1/2/3`。任何 `event.button() == 0/1` 都必须走 `MouseButtons.isLeft/isRight`；`PointerEvent` 内部使用 canonical 左 0/右 1，边界适配必须保持一致。
- **不要直接比较 GLFW 常量或 GLFW 数字**（Esc/Backspace/Delete/Home/End/方向键/Shift/Ctrl）。平台边界统一走 `gui/InputKeys`：`canonical(KeyEvent)` 把 Screen 事件转成 `ui.input.Keys`；需要原始字母的即时文本框走 `InputConstants.KEY_*`。`OneClickScreen`、`HudPositionEditorScreen`、`DevDrawer`、`ImmediateTextField`、HUD 设置卡和键位注册均已迁移；新增代码不得再写 `GLFW.GLFW_KEY_*` 或 `event.input() == 32/42/44/74/76/77`。
- **文本输入走 `Screen.charTyped(CharacterEvent)` / `preeditUpdated`**，不要自己注册 GLFW char callback，也不要假设 26.3 的 SDL text-input/IME 时序与 GLFW 相同。`CharacterEvent.codepointAsString()` 和 `isAllowedChatCharacter()` 是当前可移植入口；IME 预编辑和提交时机仍要两版分别测。
- `KeyMapping` / `InputConstants` 的注册可能仍使用 GLFW 常量；修改键位相关代码时必须在 26.2 和 26.3 都实际按键验证。
- `GuiGraphicsExtractor.fill` 参数是 `x1,y1,x2,y2`，而 `outline` 是 `x,y,width,height`；混用会把焦点框画成巨大的矩形。
- 滚轮事件的正 delta 表示向上；`ScrollView` 用 `scrollY - delta * step`，不要直接相加。

**结论**：输入后端差异是长期维护风险，不是一次迁移就能忽略的差异。所有新输入代码都视为跨平台代码，至少回归 26.2/26.3 的点击、滚轮、Esc/Enter/Tab、退格、字符输入和 IME。

**基础控件（第一批）**：

- `UiPainter`：控件绘制抽象（fill/outline/text/clip），与 MC 解耦。
- `WidgetPalette`：第一批控件配色。
- `Control`：focus/pressed、指针、Enter/Space 激活；键盘激活有 120ms 的短暂按下高亮，焦点描边独立保留。控件不绘制 hover 填充，避免鼠标停留导致激活后残留高亮；鼠标与键盘激活后的最终视觉一致。
- `Button`：动态文案 + action。
- `Switch`：BooleanSupplier/Consumer。
- `Slider`：拖拽捕获、左右键微调、数值显示。
- 迁移阶段的 `Ui2TestScreen` 已随回归入口一起删除。

- `TextField`：单行编辑、光标、Backspace/Delete、左右/Home/End、IME 预编辑接口。
- `Dropdown`：独立 popup layer，通过 `InputRouter.addLayer` 接收点击；选项由 Button 组成，菜单首/尾项只圆外侧角，中间项直角，避免每个选项像独立卡片。
- `Scrollbar`：`ScrollView` 的绝对定位子控件，支持点击轨道和拖动滑块。
- `LayoutEngine`：新增 `position: absolute` 与 left/right/top/bottom；绝对定位子元素不参与流式测量和 flex 分配。
- `Ui2Theme`：把现有 `ClickGuiStyle` 的颜色 token 映射到 `WidgetPalette`。
- `McUiPainter`：唯一的 MC 生产 painter，复用 `GuiPixels`/`ClickGuiStyle` 的圆角、pill、阴影与设备像素吸附；测试页当年的私有 painter 已随页面一起删除。
- `UiPainter`：新增 `fillRounded` / `outlineRounded` / `surface` / `pill` / `shadow`。
- `WidgetPalette`：增加 `radius` / `pillRadius`，由 `Ui2Theme.palette()` 映射自 `ClickGuiStyle.controlRadius()` 与 `ClickGuiStyle.PILL`。
- 控件表面不带常驻边框环，使用明度、按下色和轻微阴影建立层次；焦点是背景填充加 accent 圆角描边，不覆盖内容。
- `TextField`：已接入 `preeditUpdated`，支持 IME 预编辑显示；提交仍通过 `charTyped`。

**已完成**：MC 平台 Painter 封装、TextField 点击定位光标、Dropdown 外部点击关闭、长下拉虚拟列表与滚动位置指示。

**已决定不做**：滚动条 hover 反馈（会触发页面缓存重烘，保持无 hover）。

## 13. 统一 UI v2 设置页

**入口**：统一 ClickGUI 的模块详情页和主“设置”标签页。

**宿主**：`OneClickScreen` 提供面板壳、导航和标题；`SettingsPage` 负责设置元素树、`InputRouter`、滚动、颜色/下拉弹层和页面级缓存。主设置标签页通过 synthetic feature 复用同一个 `SettingsPage`。

**当前状态**：

- 所有带设置的模块都使用 v2 页面；无设置模块不可打开
- 设置主标签页使用自己的全宽几何和可变卡片列
- 页面缓存为“静态底图 + 动态控件层”，作用范围是页面而非整个面板
- 长下拉使用搜索 + 8 行虚拟窗口，滚轮翻页
- ChoiceSetting 使用带滑动动画的 Segmented
- 颜色弹层、文本设置、准星自定义行均走 v2
- HUD 右键设置弹窗也内嵌 `SettingsPage`
- 首页（模块网格）**整个页面已是 v2 树**：`ModulesPage` 构建 `Box`/`ScrollView`/`TextField`/`NavButton`，侧边栏和卡片是真实的 `SidebarRow`/`ModuleCard`（`extends Control`）节点，命中由 `InputRouter` 按 `Control.contains()` 分派。该文件里已无任何直接 `graphics.fill/blit/text` 调用
- **仍留在 v2 之外的是"外壳 chrome"三块**：面板背景（`PanelRenderer.drawBackground`）、顶部主 tabs（`drawTabs`）、底栏（`drawFooter`）。它们由 `OneClickScreen` 用 `GuiGraphicsExtractor` 手工绘制，整体经 `shellCache` 烘成一张纹理，命中走 `PanelInput.hit`。外壳是静态的、不参与布局，所以未迁移不影响正确性，只是与页面层风格不统一
- `OneClickScreen` 里唯一残留的直接绘制调用是标题栏 logo 的那一次 `graphics.blit`
- 旧的设置视图与旧 widget 构建路径已删除

**设置卡片规范（强制）**：

- 每张设置卡片必须有 header（标题）。
- Header 下方必须有解释文字，说明该卡片控制的子功能。
- 卡片结构以现有 Player Stats 设置页为参考。
- 字重层级：
  - 卡片标题 / header：`SemiBold`
  - 正文、行标签、控件文字：`Medium`
  - Header 下的解释文字：`Regular`
- 新增或修改设置页时必须满足以上结构；现有页面逐步补齐，不允许新增无 header 或无解释的卡片。

**过渡动画**：模块之间切换时保留 outgoing `SettingsPage` 快照，不再回退到旧设置渲染。

**P0/P1 已完成**：旧渲染器与旧控件已删除，文本光标定位、长下拉性能、滚动指示、HUD v2 弹窗和小窗口适配均已落地。

**已完成**：`GuiPanelCache` 多缓存槽、tabs/footer 壳缓存、颜色弹层静态层缓存、真正的 Inter Regular 400；`Ui2TestScreen` 已删除。
