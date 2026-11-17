# UI 架构重构记录

这份文档记录**为什么**做这些改动，以及**为什么选这个方案而不是别的**。`DEVELOPMENT.md` 记录现状，`UI-V2-CONTRACT.md` 记录约定，本文记录决策过程。

**用途**：当有人（包括未来的我）问"为什么要额外多一层 `util/`"、"`ModulesPage` 为什么不套 `UiPage`"时，答案在这里，而不是靠重新推一遍。

---

## 目标

原始诉求：**统一架构、减少歧义、提高可维护度**。

这三件事在代码里对应三类问题：

| 症状 | 表现 |
|---|---|
| 架构不统一 | 同一个能力有 2-3 份实现，改一处忘另一处 |
| 歧义 | 同名变量/字段承担多种语义，读代码时无法确定 |
| 可维护性差 | 改一个功能要动 5 个文件，且不清楚影响范围 |

---

## 阶段 1：切断 `ui/` 的上行依赖

### 问题

依赖图显示（只统计 `import` 语句）：

```
ui -> gui, config, render
gui -> ui, ...
config -> render
render -> config
```

**最底层的内核依赖了所有上层。** 具体三处：

| 位置 | 问题 |
|---|---|
| `ui/UiPage` → `gui/InputKeys` | **循环**：`gui` 也依赖 `ui` |
| `ui/UiPage` → `config/ConfigManager` | UI 内核知道配置系统存在 |
| `ui/*` → `render/*` 8 处 | 内核直接引用渲染实现 |

### 决策

**不按"是否依赖业务"划分，而按依赖方向划分。**

我一度提出"ui 负责交互、gui 负责图形"，但被否掉了——因为 `ui/mc/McUiPainter` 本身就是图形实现。真正的问题是**箭头方向**，不是职责标签。

### 做法

1. **`InputKeys` → `ui/input/`**：它是平台键码到内核键码的适配器，逻辑上属于内核。
2. **`UiPage.revision()` 改为抽象方法**：缓存键确实需要版本号，但内核不该知道它从哪来。
3. **`CrosshairGrid` / `CrosshairPreview` → `crosshair/`**：它们依赖 `ModConfig`，被 7 个业务类使用，从来不是内核的一部分。
4. **抽 `RenderCache` / `UiText` 接口**：内核声明契约，`ui/mc/` 提供实现。

### 为什么抽接口而不是搬类

`ClickGuiFonts` 依赖 `config` + `mixin`（583 行的可配置字体服务）。把它搬进 `ui/` 会让 `ui` 继续依赖 `config`，方向错误。

`UiText` 用**静态安装点**而不是构造参数，因为字体栈全局唯一，穿过 6 个控件构造函数和所有调用方会带来大量噪音。

---

## 阶段 2：`util/` 包

### 问题

`config` 和 `render` **互相 import**，两个最底层的包无法单独理解。

### 调查结论：大部分不是真耦合

| 引用 | 性质 |
|---|---|
| `ModConfig` → `AntialiasingRenderer` | **只在 javadoc 里出现**，代码引用数 0 |
| `ModConfig` → `ColorGradeRenderer` | 同上 |
| `ModConfig` → `ColorUtil` | 真实调用（11 个静态方法）|

### 做法

把 5 个零依赖类迁入 `util/`：`ColorUtil`、`Easing`、`PickerMemory`、`GuiMetrics`、`MouseButtons`。

`util/` 成为**依赖图的最底层**，零依赖。

### 一个分类失误

`PanelGeometry` 看起来也是纯工具（无项目内 import），但实际引用 `PanelView` 和 `Feature`——**通过同包访问，所以不需要 import**。

**教训**：一个类没有项目内 import，只能说明它没有跨包引用，不能说明它无依赖。

---

## 阶段 3：解耦 `Setting` 与 UI 控件

### 问题

数据模型 `Setting` 持有：
- `ImmediateTextField search`（+ 公开 getter）
- 借用 `ImmediateColorPicker.CONTENT_H` 常量

### 调查结论

**这些几乎都是死代码**：

| 项 | 实际问题 |
|---|---|
| `searchField()` | **零调用** |
| `search` 字段 | 只在 `toggle()` 里 `setText`/`blur` |
| `toggleExpanded()` / `isExpanded()` | **零调用**，整条展开链已死 |

v2 `Dropdown` 自带搜索框，`Setting` 里这个是旧实现残留。

### 做法

- 删除 `search` 字段、`searchField()`、展开链
- `CONTENT_H` 改为本地常量（两个取色器都是 `8*2+132`）

### 保留的耦合

`V2CustomSetting.Host` 接口里的 `Box` / `Dropdown` / `WidgetPalette` **保留**——自定义行必须能接收 v2 容器，这是接口契约，不是实现泄漏。

---

## 测试基础设施

### 为什么现在建

`ui/` 的内核类**没有 `net.minecraft` 依赖**，这个特性之前没换来任何好处——每条布局规则都靠开游戏看。现在用它跑真实测试。

### 关键设计：`uiKernelSources` 白名单

```kotlin
val uiKernelSources = listOf("ui/Box.java", "ui/LayoutEngine.java", ...)
```

测试源集只编译这些类。**副作用是架构守卫**：如果 `ui/` 里某个类新增了 MC 依赖，测试编译失败并指出该类。

**这把分层规则从"文档约定"变成了"编译期强制"。**

### 两个测试失败纠正了文档没记住的事

| 我的假设 | 实际 |
|---|---|
| 根节点 auto 尺寸 = 包裹内容 | **撑满 `availableHeight`** |
| 单独布局 `Text` 根节点按内容测量 | 同样被撑满，需作为子节点 |

这两个行为契约文档 §2.3 写过，但我写完就忘了。**现在被测试固定下来。**

---

## `GuiPanelCache` 调查：不需要修

### 原以为

`ModulesPage` 注释说"同帧缓存两个页面会让纹理竞争 target"，所以必须修 `GuiPanelCache` 才能做后续重构。

### 查证结果

| 事实 | 说明 |
|---|---|
| `bakePending()` 的 save/restore | 成对，在 `finally` 里恢复 |
| 每个 `Slot` 的 target / state / renderer | **完全独立** |
| 全局静态状态 | 只有 `SLOTS` 集合和 `nextSlotId` |

**同帧多 Slot 烘焙在机制上没问题。**

### 时间线证据

```
63b1f5d  1793946302  引入 withCards 绕过缓存（注释说纹理竞争）
4884dce  1793957617  给每个页面独立 Slot        ← 3 小时后
```

**`4884dce` 正是为解决那个冲突做的，但绕过逻辑和注释没清理。**

实验验证：让 `moduleChrome` 也走缓存，一切正常。

---

## `ModulesPage`：简化而非拆分

### 我最初的两个方案（都不好）

| 方案 | 代价 |
|---|---|
| 拆成 `ModulesSidebarPage` + `ModulesGridPage` | `OneClickScreen` 的 **24 处**协调逻辑要重构 |
| 套进 `UiPage` | 需要 4-5 个覆写钩子，框架变复杂 |

### 用户的判断改变了方向

> 把结构简化是否是一个好的方案，毕竟只是一个布局，而不是页面

### 查证支持这个判断

`rebuild()` 里所有组件都是**绝对定位的并列节点**，彼此无布局耦合：

```java
sidebar      → ABSOLUTE, left:0, 全高
search       → ABSOLUTE, 顶部
back/forward → ABSOLUTE, 顶部
grid+scroll  → ABSOLUTE, left:SIDEBAR_W   ← 只有 withCards 时才有
```

`withCards` 的真实语义就是**构建时是否包含卡片区**。

### 做法

- `withCards` 从 6 处使用降到 3 处（字段、构造、构建判断）
- 删除绘制层的 `!withCards` 分支（它与主路径**完全重复**）
- `refreshCards` 的 `!withCards || grid == null` 简化为 `grid == null`

**结果**：绘制路径从 2 条变 1 条。

---

## 单一焦点仲裁

### 问题

三个面板各有独立 `InputRouter`，**都能同时持有焦点**。`charTyped` 按固定顺序分发，侧栏排在前面，所以设置页的输入框永远抢不到字符。

### 我先做的错事

我一度想加 `lastTextFocus` 字段记录"最后点击的区域"来改分发优先级。**被用户拦下了**：

> 这个本来就是一个bug导致的另一个bug啊，他都不是根因，把两个输入框会被同时激活的bug修好了不就没有这个bug了？

### 正确做法

**在点击时交接焦点**，保证同时只有一个 router 持有焦点。

```java
private void focusOnly(int region) {
    if (region != TEXT_REGION_MODULES && modulesPage != null) modulesPage.clearFocus();
    if (region != TEXT_REGION_CHROME && moduleChrome != null) moduleChrome.clearFocus();
    if (region != TEXT_REGION_SETTINGS && statsPage != null) statsPage.clearFocus();
}
```

### 为什么这个方案可行（之前以为要等拆分）

查证发现两个关键事实：

1. **三个区域的条件互斥**：`modulesPageActive()` 与 `moduleChromeActive()` 条件相反
2. **`pointerDown` 是焦点唯一入口**：键盘/文本事件发给"已持有焦点"的控件

所以不需要合并 router，只需在点击时交接。

---

## 方法论记录

### 我反复犯的错

| 错误 | 发生次数 |
|---|---|
| 凭印象重写而不是对照现有实现 | 3 次（`intersects`、`root instanceof ScrollView` 两处）|
| 修 bug 不搜同类代码 | 2 次 |
| 写文档后不回头查 | 3 次（根节点布局、坐标转换、`touch()` 例外）|
| PowerShell `Replace` 静默失败 | 1 次（诊断代码三次没写进文件）|

### 被验证有效的做法

- **拿数据代替推理**：光标偏移问题推演了三轮都自相矛盾，加日志后一次定位
- **改完验证三处**：源码含关键字 → 编译成功 → **用 .NET 打开 jar 检查 class 内容**
- **先查证再动手**：`ModulesPage` 的注释、`GuiPanelCache` 的冲突，都是查证后发现有更简单的路

---

## 当前遗留

| 项 | 说明 |
|---|---|
| `DevDrawer` 重写 | 747 行硬编码坐标，阻塞 `ImmediateTextField`(403) + `ImmediateColorPicker`(608) 的删除 |
| `HudPositionEditorScreen` | 1253 行纯即时模式，与 v2 独立 |
| `ModulesPage` 的三处重复 | `drawElement` / `intersects` / `pageCacheKey` 与 `PaintPass` 重复 |
| 测试覆盖 | 只有 `LayoutEngine`，可加 `PaintPass` / `Style` / `InputRouter` |
