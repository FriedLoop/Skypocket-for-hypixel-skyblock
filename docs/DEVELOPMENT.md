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
├─ util/                 零依赖工具：ColorUtil（颜色运算）、Easing（缓动曲线）、
│                        GuiMetrics、MouseButtons、PickerMemory
│                        ⚠ 只能放不依赖本模组其他包的类，它是依赖图的最底层
├─ config/               ConfigManager（Gson JSON ↔ config/skypocket.json）+ ModConfig + ColorDefaults
├─ gui/                  ClickGUI 业务层：Feature/Module 契约与注册、各页面、布局常量、外壳绘制
│   ├─ setting/           Setting 数据模型（含全部子类）
│   └─ widget/            ClickGUI 主题与旧控件（ClickGuiStyle/GuiTheme/ThemeStore/…）
├─ feature/              FeatureModule SPI 与 35 个功能实现（modules/、config/ 子包）
│                        ⚠ 新功能一律走 FeatureModule，不要碰 gui.Module；见 §4 checklist
├─ crosshair/            准星组件与配置（CrosshairGrid/CrosshairPreview/CrosshairPreset…）
├─ ui/                   与业务无关的 UI v2 内核：元素树、布局、绘制抽象、输入事件
│   │                    ⚠ 改动或使用前先读 docs/UI-V2-CONTRACT.md（坐标系/绘制/输入/缓存的权威契约）
│   │                    ⚠ 不得 import dev.skypocket.gui / config / feature；不得取 Minecraft 服务
│   │                       两者都由 build 的 checkDocs / checkKernelPurity 拦截
│   ├─ input/            InputRouter + 各类输入事件
│   ├─ mc/               MC 平台绑定：McUiPainter、McRenderCache（唯一允许 import render 的地方）
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

### 依赖方向（只能向下）

```
feature / hud / crosshair          业务
        ↓
gui                                具体界面与主题
        ↓
ui                                 内核（无业务依赖）
        ↓
ui/mc                              平台绑定
        ↓
util                               零依赖
```

违反这条方向会有两类后果：`config ↔ render` 曾经互相 import，导致两者都无法单独理解；`ui` 曾经 import `gui` 的 `InputKeys`，形成循环。

**三个守卫都会在 `build` 时拦截违规**：

- `checkDocs`（`tools/check-docs.ps1`）：`ui/` 不得 import `gui` / `config` / `feature` 等上层
- `checkKernelPurity`（`tools/check-kernel-purity.ps1`）：`ui/` 不得取 `Minecraft` 服务（字节码级检查，见 §10.3）
- `checkIconAtlas`（`tools/check-icon-atlas.ps1`）：图标图集 PNG 的实际尺寸必须等于 `AtlasSize.java` 的声明

### 为什么 `checkIconAtlas` 存在

图标功能上线时**同一个 bug 出了两次，方向相反**：

| 次数 | 现象 | 原因 |
|---|---|---|
| 1 | 卡片图标被纵向拉长并截断 | `icons-large.png` 实际 1536×960，代码按正方形 1536×1536 算 v 坐标 |
| 2 | 侧边栏和齿轮被压扁 | 修复时把"图集必须是正方形"套用到两个图集，`icons.png` 变成 768×768，而代码期望 768×256 |

**两次都不是"代码写错"，而是"两个文件的数字必须一致，但没有机制保证"。** 而且两次的症状都是"图标变形"，不指向任何行号。

现在数字只在 `AtlasSize.java` 声明一次，`IconAtlas` / `ModuleIcons` 都从那里取值，渲染脚本 `tools/render-icons.mjs` 断言同一组数字，构建时再校验一次实际 PNG。改图集就要动这两处，脚本和构建都会提醒。

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
- **hint tooltip 已移除**：说明文字写在 Header 副标题里；`GroupSetting` 不再生成折叠状态，页面只按功能拆卡。- **条件行（`visibleWhen`）必须成块且收尾**：一个 `List.of(...)` 里，无条件行在前，条件块在后；**不允许**"条件行 → 无条件行 → 条件行"的交替。否则切换开关时，夹在后面的无条件行会整体位移（"中途塌陷"）。控制这些条件行的那一行（如风格切换按钮）要放在它们**之前**。参考 `registerStatsDisplay`：风格 → 切换 → 原版 HUD → 颜色 → 文字行 → 经典块 → 现代块。
- **主题编辑是"实时抽屉"，不是"预览屏幕"**：`DevDrawer` 挂在 Click GUI 上，编辑直接写入实时调色板，打开时记一份快照，关闭未保存则整体还原；只有「保存」写文件。不要再为它做"第二份面板"（旧的独立主题工作室屏幕就是这么坏的：手工拼壳 + 页 + 行过滤，随真实面板演进持续漂移，已删除）。抽屉里的拾色器按 0.75 绘制（pose 缩放 + 命中反变换），设置页与卡片仍是 1:1。抽屉顶部的主题行是唯一的主题入口：`‹ ›` 切换（从磁盘载入，未保存改动丢弃）、`+` 另存为新主题、`-` 删除当前主题（**内置 Dark/Light 不可删**，删除即时写盘，并回落到 Dark）。\n- **层次靠明度 + 阴影，不靠描边**：所有控件、行、模块卡片、抽屉、浮窗与 tooltip 都不画描边（`border` / `cardBorder` / `switchBorder` 三个令牌已删除，Studio 里不再有它们）。深度由三件事给出：比背景略深/略浅的填充、`ClickGuiStyle.shadow(...)`（两层半透明叠加，此层没有模糊）、以及组内子行统一画在一张卡片上（`RowAt.inCard`）。新增面板类元素时**先想明度差**，不要再引入 1px 边框。\n- **配置结构变更必须走版本迁移**：`ModConfig.configVersion` + `ConfigManager.CURRENT_VERSION` + `MIGRATIONS`（第 i 步把 v{i} 升到 v{i+1}）。新增/改名/改类型字段时：改字段 → `CURRENT_VERSION++` → 在 `MIGRATIONS` 末尾加一步。**不要**依赖"解析失败回落到默认值"——那会静默丢掉玩家设置，并在下一次保存时覆盖原文件（已发生过一次）。配套保护：读取失败写 `skypocket.json.unreadable` + ERROR 日志；保存前复制 `skypocket.json.bak`；写入用临时文件 + 原子替换。
- **所有诊断/调试开关统一放进「调试」分组**（`buildPanelSettings` 末尾的 `GroupSetting("调试", …, false)`），不要再散落在主列表里：这些行只写日志、对玩家无用，放在一起既不打眼，也给下一个开关一个显然的落点。目前里面有：调试日志（信息日志总开关）、面板缓存统计（每 5 秒一行缓存命中/烘焙/停在哪个门）。
- **日志**：一律用 `SkyPocketLog.of("area")` 取 logger，调用写法仍是 `LOGGER.info(...)`，输出带 `[SkyPocket/area]` 前缀 —— MC 的日志格式只打印 `[线程/级别]`，不加前缀就无法在共享日志里定位我们的行（改全局 pattern 会连原版日志一起改，不做）。logger 名固定 `skypocket.<area>`（**点分**，级别继承才成立；斜杠形式不继承）。级别由「调试日志」控制：开 = DEBUG 及以上，关 = **WARN 及以上**（信息静默，但警告/异常永远留证）。
- **HUD 位置编辑器（`HudPositionEditorScreen`）的结构约定**：每个可编辑元素的 x/y/缩放读写集中在 `SPECS`（`EnumMap<Target, Spec>`，一个元素一条）；新增元素 = 枚举常量 + 表里一行 + `computeLines`/`label` 两个 switch 分支（这两个是 `switch` **表达式**，漏写编译不过）。`init()` 会自检"表项数 == Target 数"，缺项只 warn 不会静默。测量（`lines`/`bounds`）有**帧内缓存**，且缓存只在 `extractRenderState` 期间生效（鼠标处理走实时测量）。控件是**可拖动悬窗**，不再固定在屏幕底部（底部正是 HUD 元素常放的位置）；ESC 退出。**可见性**由 `visible(target, cfg)` 统一决定，规则是"功能开关已开 **且** 该风格下真的会画"：条形仅现代风格可见，生命/法力两条文字行仅经典风格可见（现代风格下这两个数由条形印出，否则两套元素重叠、没法调）；运行时条件（是否在 SkyBlock、有无数据）**不**参与——否则无法在数据出现前预先摆放。
- **位置/缩放/启用状态的映射各自只有一份**：`StatTextHudElement.FIELDS` 与 `StatsBarHudElement.FIELDS`（`EnumMap<Kind, Fields>`，一行一个 kind），编辑器通过它们的 `x/setX/y/setY/scale/setScale` 访问器委托，不再重复字段名。新增 kind = 枚举常量 + 表里一行。`HudPositionSync` 同样表驱动：`REPAIRED`（每个元素一行，含"跑到屏幕外时回到的默认坐标"）与 `BARS`（仅用于按比例缩放；越界修复是**逐条**的：只把越界那根放回它在列里的默认位置，另五根的位置/缩放/自定义尺寸不动）。新增元素要在这张表里加一行，否则窗口尺寸变化时它不会被按比例迁移、也不会被拉回屏幕内。
- **设置页的组织方式是"子功能 = 一张卡片"**（`GroupSetting`）：组标题行右侧是本功能的开关，组不再折叠。
  - **组的开关必须写真实的开关字段**（例如「瞄准目标变色」写 `crosshairEntityColorEnabled`，后处理的四个组写各自效果的 `xxxEnabled`），关掉要真的停掉功能。**只有参数簇（一组颜色、一个位置/旋转）才用不带开关的组**：它没有东西可关，但仍然是一张独立卡片 —— 装饰性开关比没有开关更糟。
  - **子设置保持整页宽度，不缩进**：组头的深色表面已经说明了归属，而缩进会迫使 `ColorPickerPanel`/预览网格这类按整宽调好的布局重算。
  - 组的高度 = 组头 + 所有子设置高度（`GroupSetting.height()` 递归求和），所以 `panelSettingsHeight()`/`isRowVisible()` 不用改。
  - ⚠️ **组不可折叠**：`SettingsPage.buildSettings` 把 `GroupSetting` 渲染成一张独立卡片后**直接遍历 `children` 添加子项，从不读取折叠状态**。`GroupSetting` 里的 `expanded` / `isExpanded()` / `toggleExpanded()`、构造函数的 `defaultExpanded` 参数，都是折叠 UI 移除后遗留的**死代码**（`.hint` 那条老路径同理）。
    - 全仓**没有任何地方调用 `toggleExpanded()`**，所以 `expanded` 恒等于构造时传入的 `defaultExpanded`（默认 `true`）。`height()` 里那个 `if (expanded)` 分支因此永远成立 —— 它算出来的高度是对的，但这只是"默认值恰好为 true"的结果，不是折叠功能还在。**新增组时不要指望它们生效，也不要据此设计交互。**
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
  - **配置写入必须通知缓存**：`ConfigManager.revision()` 只在 `ConfigManager.save()` 里自增，而绝大多数设置是**裸写字段**（`cfg.x = v`），所以 `Setting`（`BooleanSetting`/`SliderSetting`/`ColorSetting`/`DropdownSetting`/`TextFieldSetting`/`GroupSetting`/`ChoiceSetting`，通过四个 `touch(...)` 重载）和 `Module.setEnabled` 都把 setter 包了一层 `ConfigManager.touch()`。**新增任何会改变面板外观的写入路径都必须走这层包装**，否则缓存不会失效：**实测症状**是"启用面板缓存后开关模块，按钮显示更新很慢（要等鼠标移动改变 hover 键、或 600 帧兜底才刷新），像卡了一下"；滑条同理（拖动过程中面板一直显示旧值）。
  - **失效条件集中在各页自己的缓存键**（`ModulesPage.pageCacheKey()` / `SettingsPage.pageCacheKey()` / `ColorPickerPanel.staticCacheKey(scaleX, scaleY)`）：翻页/选中分类/滚动/搜索/侧边栏高亮/配置 revision（`ConfigManager.revision()`）/内容 revision（语言、布局重建）/设备缩放/开合动画是否结束/聚焦控件 + **hover 签名**（按"光标在哪个元素上"而不是原始坐标，所以在一张卡片内移动鼠标不会使缓存失效；设置/功能详情页因为控件自己画 hover，退化到"聚焦控件 + 控件自身 hover 标志"）。
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
      - **"点到了什么"和"点了之后做什么"仍然分开，但分两层**：外壳 chrome 由 `gui/PanelInput.hit(lx, ly)` 解析成 `PanelInput.Hit`（`Kind` = `FOOTER_BUTTON` / `FOOTER_SETTINGS` / `NONE`；主 tab 已删除，设置入口移到底栏），**只做判断、不改状态**，`OneClickScreen.mouseClicked` 负责映射成动作（翻页、打开 HUD 编辑器）；页面内部（侧边栏行、搜索框、卡片、开关、导航按钮）则是**真实控件对象**，由 v2 的 `InputRouter` 命中并直接触发自己的 `onPointerDown`。新增面板可点元素时：先判断它属于外壳还是页面 —— 外壳加进 `PanelInput` 的 `Kind` 与 `switch`，页面就造一个 `Control` 挂到树里，**不要**再往事件方法里写布局算术。
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

## 7. MC 反编译踩坑记录 → 见 `docs/PITFALLS.md`

26.2 的字体、输入、渲染、HUD 位置、各类 SkyBlock 功能的实现方式，以及"按理说应该能工作但不行"的怪现象，全部移到了独立文档：

**`docs/PITFALLS.md`**（16 个主题，约 390 行）

**为什么拆出去**：这部分是**历史事件的记录**，会持续增长；而本文档描述的是**当前状态和规矩**。混在一起会让主文档越来越长，且新人要先读 400 行历史才能看到现状。

**什么时候读它**：遇到无法解释的行为时先搜那里；日常开发只需要看本文档。

---

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

## 9. 多版本适配 → 见 `docs/MULTIVERSION.md`

26.2 / 26.3 的差异表达机制（预处理器指令 vs 分支）、26.3 差异清单、Mixin 三种失配手法、新增版本 checklist，全部移到独立文档：

**`docs/MULTIVERSION.md`**（约 165 行）

**为什么拆出去**：这与主文档的其他章节是**不同的读者场景**——只有需要动版本差异时才看它。放在主文档里会让日常开发多翻 150 行。

**注意**：`build.gradle.kts` 的 `versioned()` 与 `//#if MC>=260300` 指令仍在主文档 §2 有简述，细节看新文档。

---

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
| 宿主层（`ui/host/Layer`、`ui/mc/host/UiHost`、`gui/PaneLayer`） | ✅ 已落地；ClickGUI 的 4/5 个 pane 走它分发 |
| **外壳 chrome**（面板背景 / 底栏） | ⬜ `PanelRenderer` 手工绘制，但不再经 `shellCache` 烘焙——改为按顺序实时绘制。顶部条已是 v2 控件树 |
| 主题抽屉（`DevDrawer`） | ✅ 已落地，页面内零 `graphics.*` 直绘；**未接入 `UiHost`**（屏幕坐标 + 穿透 + 模态，见 `UIHOST-DESIGN.md` §9）|

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

## 10.3 自动化测试

`ui/` 的内核类大部分**没有 `net.minecraft` 依赖**，所以能脱离游戏跑 JUnit。

**位置**：`versions/<name>/src/test/java/dev/skypocket/ui/`
**运行**：`./gradlew :26.2-fabric:test`（`build` 也会跑）

### `uiKernelSources` 是"编译进测试的类清单"，**不是**架构守卫

```kotlin
// build.gradle.kts
val uiKernelSources = listOf("ui/Box.java", "ui/LayoutEngine.java", ...)
```

它的作用：决定 `build/bloom/test/` 下额外编译哪些源文件。

⚠️ **它拦不住 MC 依赖**。Gradle 的 `test` 源集**默认依赖 `main`**，所以 `main` 的全部编译产物始终在测试 classpath 上——即使某类不在白名单里，测试代码照样能引用它。

**实测**：往 `ui/Size.java` 注入 `net.minecraft.client.Minecraft`，测试**编译并全部通过**。

### 真正的守卫是字节码扫描

```
tools/check-kernel-purity.ps1   （由 build 的 checkKernelPurity 任务调用）
```

它读编译后的 `.class` 常量池，检查 `ui/` 下（除 `ui/mc/`）是否引用了平台类型。

**不是所有 `net.minecraft` 引用都违规**，它按三类判断：

| 类型 | 处理 | 理由 |
|---|---|---|
| `GuiGraphicsExtractor` | ✅ 允许 | 绘制入口，`UiPainter`/`RenderCache`/`UiPage` 的签名必须接收它 |
| `KeyEvent` / `CharacterEvent` / `PreeditEvent` / `MouseButtonEvent` | ✅ 允许 | MC 触发的平台事件，`InputKeys` 就是用来翻译它们的 |
| `Minecraft` | ❌ **报错** | 取服务（窗口缩放、剪贴板），这类永远可以用接口消除 |

**验证方式**：注入 `Minecraft.getInstance().getGuiScale()` 到 `ui/Size.java` → 守卫报错；恢复 → 通过。

**已因此修掉的耦合**：`TextField` 的剪贴板、`ColorPickerPanel` 的窗口缩放，改为 `UiPlatform.clipboard()` / `UiPlatform.guiScale()`。

### 新增可测试类的步骤

1. 运行 `check-kernel-purity`，确认它不引用 `Minecraft`（`GuiGraphicsExtractor` 等边界类型是允许的）
2. 把路径加进 `build.gradle.kts` 的 `uiKernelSources`（否则测试编译时看不到源码）
3. 在 `src/test/java/dev/skypocket/ui/` 写测试

**加不进去通常是信号**：说明这个类应该放进 `ui/mc/`，或者抽一个 `UiPlatform` 服务。

### 当前覆盖：56 个用例

| 测试类 | 数量 | 覆盖 |
|---|---|---|
| `LayoutEngineTest` | 10 | 布局算术 |
| `InputRouterTest` | 15 | 命中测试、事件冒泡、拖拽捕获、焦点、层、Tab 导航 |
| `StyleTest` | 14 | `Dimension.resolve` 语义、盒模型、builder 复制 |
| `UiElementTest` | 11 | 三套坐标系、offset、树操作、输入钩子默认值 |
| `PaintPassTest` | 6 | 动态层选择规则（hover/flash/overlay）|

**几个被测试固定下来的反直觉行为**（都踩过）：

- **根节点 auto 尺寸会被撑满 `availableWidth/Height`**，不是包裹内容
- **单独的 `Text` 作为根节点时同样被撑满**，只有作为子节点才按内容测量
- **`Style` 默认 `direction` 是 `ROW`**，不是 COLUMN
- **`visualX()` 只含祖先 offset**，不含自身 offset
- **`acceptsPointer()` 默认 `false`**，指针捕获需要显式开启
- **层（layer）不参与布局**，`bounds` 由开启它的人设置

### 10.4 手工验证页 `DevUiTestScreen`

**入口**：设置 → 字体与渲染调试选项 → "UI v2 测试页"

它验证自动化测试覆盖不到的东西——真实渲染与交互。四个区块：

| 区块 | 内容 |
|---|---|
| 1. 布局 | flexGrow 1:2:1、justify 四种、alignItems 四种、**绝对定位三种锚定** |
| 2. 组件 | Button / Switch / Slider / Segmented / TextField / Dropdown / ColorField |
| 3. 样式 | v2 色块与 `ClickGuiStyle` 数值并排对照 |
| 4. 交互 | 点击/拖拽/滚轮/悬停，含一个 MENU 模式按钮验证 hover 重烘 |

**改动 v2 框架后应当跑一遍它**：自动化测试能保证布局算术，但渲染管线、缓存烘焙、浮层定位、输入路由只能实机看。

**新增控件时**：在区块 2 加一行，并在区块 3 加对应的颜色对照（如果有人读）。

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

**测试页**：迁移阶段的 `/skypocket uitest` 与 `Ui2TestScreen` 已删除。现在有 `DevUiTestScreen`（设置 → 字体与渲染调试选项 → "UI v2 测试页"），它验证 v2 框架的完整能力：布局（含绝对定位四种锚定）、全部控件、`ClickGuiStyle` 颜色对照、交互。**纯逻辑部分由 JUnit 覆盖**，见 §3.x 测试章节。

**崩溃修复（ScissorTracker）**：

- `ScissorTracker` 不再使用全局 ThreadLocal；现在按 `GuiGraphicsExtractor` 实例隔离（WeakHashMap），构造函数时清空该实例的栈，其他 mod 的 GUI 不会共享或继承我们的裁剪状态。
- `ScreenRectangle.intersection` 对不相交矩形可能返回 `null`；直接 `ArrayDeque.push(null)` 会 NPE，导致所有 GUI 崩溃。现在交集为空时降级为 `ScreenRectangle.empty()`，不再推入 null。
- mixin 只旁路观察 `enableScissor`/`disableScissor`，不取消、不修改参数和返回值；原版和其他 mod 的渲染行为不变。代价是每次 scissor 调用多一次 map 查找和 deque push/pop。

**26.2 / 26.3 输入差异（高风险，必须按平台适配层处理）**：

- 26.2 的输入后端是 **GLFW**；26.3 是 **SDL3**。这不是单纯换一个库：`Screen` 事件里的语义相同，但原始数字不同。
- 26.2 的 `KeyEvent.key()` 是 GLFW 键码；26.3 的 `key()` 是 SDL 键码，直接用 `256/257/258` 判断会导致 26.3 的 Esc、Tab、Enter 全部失效。
- 跨版本必须使用 `InputWithModifiers` 的语义方法：`isEscape()`、`isCycleFocus()`、`isConfirmation()`，再转换成 `InputRouter` 自己的 canonical keys。
- **鼠标按键也重新编号**：26.2 左/右是 `0/1`，26.3 是左/中/右 `1/2/3`。任何 `event.button() == 0/1` 都必须走 `MouseButtons.isLeft/isRight`；`PointerEvent` 内部使用 canonical 左 0/右 1，边界适配必须保持一致。
- **不要直接比较 GLFW 常量或 GLFW 数字**（Esc/Backspace/Delete/Home/End/方向键/Shift/Ctrl）。平台边界统一走 `gui/InputKeys`：`canonical(KeyEvent)` 把 Screen 事件转成 `ui.input.Keys`。`OneClickScreen`、`HudPositionEditorScreen`、`DevDrawer`、HUD 设置卡和键位注册均已迁移；新增代码不得再写 `GLFW.GLFW_KEY_*` 或 `event.input() == 32/42/44/74/76/77`。文本输入统一走 `TextField`，它自己处理 `InputConstants` 与 `CharacterEvent`，不要手写。
- **文本输入走 `Screen.charTyped(CharacterEvent)` / `preeditUpdated`**，不要自己注册 GLFW char callback，也不要假设 26.3 的 SDL text-input/IME 时序与 GLFW 相同。`CharacterEvent.codepointAsString()` 和 `isAllowedChatCharacter()` 是当前可移植入口；IME 预编辑和提交时机仍要两版分别测。
- **每个需要文本输入的 `Screen` 必须在 `init()` 调 `minecraft.onTextInputFocusChange(this, true)`，并在 `removed()` 调 `(this, false)`。** 漏了这一行的症状极其误导：`charTyped` 完全不被调用，但 Ctrl+A/C/V/X 和鼠标点击都正常，看起来像"这个输入框有点毛病"。`HudPositionEditorScreen` 因此长期无法在取色器里打字，却能粘贴。自查方式：新写 Screen 时先搜 `onTextInputFocusChange`。
- **宿主的事件转发有四件事必须做对**：键码转换（`InputBridge.toKernel`）、修饰符传递（`InputBridge.modifiersOf`）、字符过滤（`InputBridge.textOf`）、以及上面那条登记。前三条已收进 `dev.skypocket.ui.mc.InputBridge`；第四条属于 Screen 生命周期，尚无封装。详见 `UI-V2-CONTRACT.md` §4.3.3。
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
- 迁移阶段的 `Ui2TestScreen` 已删除，替代品是 `DevUiTestScreen`（见上）。

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
- **仍留在 v2 之外的是外壳的两块背景**：面板背景（`PanelRenderer.drawBackground`）和底栏（`drawFooter`）。它们由 `OneClickScreen` 用 `GuiGraphicsExtractor` 手工绘制，命中走 `PanelInput.hit`。外壳是静态的、不参与布局，所以未迁移不影响正确性，只是与页面层风格不统一。
- **外壳不再烘成纹理**：`shellCache` 已删除。它缓存的内容是两个圆角矩形、两段文字和一个手绘齿轮，却要一张 1348×810 的离屏纹理、一个二十行的缓存键（其中一半是为了让被冻住的 hover 能失效）和一个 `nextStratum()` 补丁来防止那次 blit 被重排到实时背景下面。改为按顺序实时绘制后，补丁也不需要了
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
