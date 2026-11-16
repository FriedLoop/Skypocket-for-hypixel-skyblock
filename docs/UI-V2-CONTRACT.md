# UI v2 契约

本文是 UI v2（`dev.skypocket.ui`）的权威契约。它记录的是**代码实际行为**，不是设计意图；凡是文档与代码冲突，以代码为准并修正本文。

写它的直接原因：`DevDrawer` 迁移到 v2 曾连续失败三轮，根因都是不了解下面这些约定——尤其是 `bounds()` 的坐标系和 `offset()` 的含义。这些知识此前只存在于实现里。

---

## 1. 坐标系

这是最容易搞错的部分，先讲清楚。

### 1.1 三个坐标系

| 坐标 | 含义 | 谁写入 |
|---|---|---|
| **local** | 相对父元素内容盒左上角 | `LayoutEngine.layoutNode` 写进 `bounds()` |
| **design** | 相对 UI 树根（`root.bounds().x()` = 0） | 累加各级 `bounds()` 得到 |
| **screen** | 相对游戏窗口 | 宿主（`OneClickScreen`）施加 pose 变换 |

### 1.2 `bounds()` 是 local 坐标

```java
// LayoutEngine.layoutNode
node.bounds(new Rect(x, y, width, height));   // x/y 是"父内容盒内"的坐标
...
layoutNode(child, contentX + left, contentY + top, width, height, text);
```

`contentX = 节点 x + padding.left()`，所以**子元素的 `bounds().x()` 已经包含了它在父内的位置**。

### 1.3 `offset()` 是绘制期的额外平移

```java
// UiElement
/** Visual offset for this element and its descendants; layout bounds are not changed. */
public final UiElement offset(float x, float y)
```

`offset` **不改变 `bounds()`**，只在绘制和命中测试时叠加。

**只有三类使用者**：

| 场景 | 调用 | 含义 |
|---|---|---|
| 滚动 | `ScrollView.content.offset(0, -scrollY)` | 内容随滚动上移 |
| 浮层定位 | `ColorField.popup.offset(x, y)` | 绝对定位到屏幕某处 |
| 根定位 | 宿主给自己 root 设 offset | 把整棵树挪到屏幕位置 |

**普通流式子元素不要设 offset。** 设了就是双重偏移——这是 `DevDrawer` 第一版控件画出边界的原因。

### 1.4 绘制时的正确姿势

**关键事实：`LayoutEngine` 从不写 `offset`。** 全仓只有三处调用：

```java
ScrollView.java:72:  content.offset(0f, -scrollY);   // 滚动内容
ColorField.java:134: popup.offset(x, y);             // 浮层
Dropdown.java:185:   popup.offset(x, y);             // 浮层
```

所以**流式子元素的 `offset` 恒为 (0,0)**。这让两种绘制写法等价：

```java
// 写法 A：每节点 translate（SettingsPage 用的）
void drawElement(painter, element) {
    pose.pushMatrix();
    pose.translate(element.offsetX(), element.offsetY());  // 流式子元素是 translate(0,0)
    ...
    for (child : element.children()) drawElement(painter, child);
    pose.popMatrix();
}

// 写法 B：只根 translate，子节点不 translate
pose.translate(root.offsetX(), root.offsetY());
paintNode(painter, root);   // 递归内不 translate
```

**两者结果相同**，因为写法 B 只对根施加 offset，而写法 A 对每个元素施加——子元素的 offset 是 0，等价。

推荐 **写法 A**：它对"某个子元素有 offset"的情况（如滚动内容）自动正确，而写法 B 需要额外分支处理。

**真正要避免的是**：对同一个元素既在 bounds 里算了一次位置、又在绘制时 translate 它的 offset **两次**。因为 `bounds()` 已经是父内坐标，`offset` 是额外平移，各管一次是正好的。

### 1.5 命中测试的坐标

```java
// InputRouter.hit
float localX = x - node.offsetX();       // 先扣掉本元素的 offset
...
hit(children.get(i), localX, localY);    // 用同一个 localX 递归
return node.contains(localX, localY);
```

**传入的是 design 坐标**（或父链已扣减后的局部坐标），`contains()` 用 `bounds()` 判定。所以：

```java
// 正确：传 design 坐标
input.pointerDown(new PointerEvent(designX, designY, ...));

// 错误：传 screen 坐标（除非 root offset 恰好等于屏幕原点）
```

`visualX()` / `visualY()` = `bounds().x() + ancestorOffsetX()`，是**design 坐标**，弹窗定位用它。

---

## 2. 布局

`LayoutEngine.layout(root, availableWidth, availableHeight, measurer)`。

### 2.1 支持的能力

| 能力 | 说明 |
|---|---|
| 尺寸 | `width/height` 支持 auto / 固定值 / 百分比；`min/max` 约束 |
| 盒模型 | `padding`（内）、`margin`（外）；**border-box** 语义 |
| 方向 | `direction(ROW)` / `direction(COLUMN)` |
| 间距 | `gap` |
| 弹性 | `flexGrow` / `flexShrink` / `flexBasis` |
| 对齐 | `justify`（主轴：START/END/CENTER/SPACE_BETWEEN/AROUND/EVENLY）、`alignItems`（交叉轴：START/END/CENTER/STRETCH）|
| **绝对定位** | `position(ABSOLUTE)` + `left/right/top/bottom`，支持双向锚定与百分比 |

> ⚠️ `LayoutEngine` 的类注释写着"does not implement absolute positioning"——**这是过时的**。`layoutAbsoluteChildren()`（66 行）实现了它。以代码为准。

### 2.2 绝对定位的行为

```java
// LayoutEngine.layoutAbsoluteChildren
// 相对于最近的父内容盒（不是最近的定位祖先，没有 CSS 那样的 containing block 链）
float left = resolveOr(style.left(), contentWidth, NaN);
float right = resolveOr(style.right(), contentWidth, NaN);
float top = resolveOr(style.top(), contentHeight, NaN);
float bottom = resolveOr(style.bottom(), contentHeight, NaN);
```

规则：
- 同时给 `left` 和 `right` → 宽度自动撑满中间区域
- 只给 `left` → 从左边定位
- 只给 `right` → 从右边定位
- 都不给 → 贴左上角

**绝对定位元素不参与 flow**（被 `flow` 列表排除），所以不影响同级元素的排布。

### 2.3 root 的布局

```java
float rootWidth = root.style().width().isAuto() ? availableWidth : measured.width();
```

- root 尺寸为 auto → 填满 `availableWidth/Height`
- root 显式尺寸 → 用显式值

**root 总是被放在 (0,0)**（`layoutNode(root, 0f, 0f, ...)`）。要让它出现在屏幕别处，用 `offset`。

---

## 3. 绘制

### 3.1 两个绘制接口

```java
public interface Painted {
    void paint(UiPainter painter);       // 有自绘能力的元素
}
```

只有两个实现：`Card`（画背景+阴影）、`Control`（画控件背景）。

**`Box` 不实现 `Painted`**——它是纯布局容器，画不出任何东西。要背景就用 `Card`。

### 3.2 绘制递归的正确形态

```java
void paintNode(UiPainter painter, UiElement element) {
    if (!element.visible()) return;
    if (element instanceof Painted p) {
        p.paint(painter);
    } else if (element instanceof Text t) {
        painter.textWeighted(t.value(), t.bounds().x(), t.bounds().y(),
                t.color(), t.fontScale(), t.weight());
    }
    for (UiElement child : element.children()) {
        paintNode(painter, child);
    }
}
```

关键点：
- **`Text` 不是 `Painted`**，要单独分支
- `Text` 用 `bounds().x/y()` 作为绘制位置（因为 bounds 已是 local 坐标，pose 已由调用方建立）
- 不 translate 子元素（见 §1.4）

### 3.3 `UiPainter` 的能力

| 方法 | 说明 |
|---|---|
| `fill(Rect, color)` | 实心矩形 |
| `fillRounded / fillRoundedTop / fillRoundedBottom` | 圆角填充 |
| `outline / outlineRounded / strokeRounded` | 描边 |
| `surface(Rect, radius, fill, border)` | 填充+描边一体 |
| `pill(Rect, color)` | 胶囊 |
| `shadow(Rect, radius)` | 投影 |
| `fillGradient / fillHorizontalGradient` | 渐变 |
| `text / textRaw / textScaled / textWeighted` | 文字 |
| `textWidth / lineHeight` | 测量 |
| `pushClip / popClip` | 裁剪 |

**`text` vs `textRaw`：**

- `text()` 走 `Localization.auto()`，**会翻译**
- `textRaw()` 不翻译，用于**用户输入**（文本框内容）和 ID

用错的表现：英文环境下输入框显示中文（因为输入内容被当成了 i18n key）。

---

## 4. 输入

### 4.1 事件流

```
宿主（Screen）
  ↓ 构造 PointerEvent / KeyEvent / WheelEvent / TextInputEvent
InputRouter.pointerDown / keyDown / wheel / textInput
  ↓ hitTest（layers 优先，然后 root）
命中元素
  ↓ bubble：从命中元素向上冒泡到 root
  ↓ 第一个返回 true 的节点成为 captured（拖拽捕获）
```

### 4.2 `InputRouter` 的层（layers）

```java
input.addLayer(popup);      // 浮层
input.removeLayer(popup);
```

- 层在 hitTest 中**优先于 root**，按**逆序**（后加的在上面）
- 层**不参与绘制**——绘制要宿主自己做
- 层**不参与布局**——位置要自己设 offset

这就是为什么每个用浮层的宿主都要手写"注册层 + 布局层 + 绘制层"三段。**目前这是重复实现的源头**（见 §7）。

### 4.3 焦点

```java
input.focused()              // 当前焦点元素
input.focus(element)         // 设置焦点
input.focusNext(backwards)   // Tab 导航，遍历 root + layers
```

`focusable()` 返回 true 的元素才能获得焦点。焦点变化会触发 `onFocusChanged()`。

### 4.4 控件基类 `Control`

提供：

| 能力 | 说明 |
|---|---|
| `acceptsPointer()` | 默认 true |
| `hovered / pressed / focused` | 自动维护 |
| `onPointerDown/Up` | 默认处理 pressed 状态 |
| `flashActivation()` | 键盘激活的闪烁反馈 |
| `dynamicOverlay()` | **返回 true 则绕过面板缓存，每帧重绘** ⚠️ |
| `neutralPaint` / `overlayPaint` | 静态层绘制时的状态抑制 |

**`dynamicOverlay()` 是性能陷阱**：返回 true 会让整个面板缓存失效，每帧重绘该控件。`CrosshairGrid` 曾因此每帧画 450 次，帧数从三位数掉到两位数。

---

## 5. 缓存

### 5.1 `GuiPanelCache.Slot`

```java
Slot.draw(graphics, cacheKey, width, height, scaleX, scaleY, animating,
          (g, mx, my, delta) -> drawBase(g),   // 烘焙回调
          offsetX, offsetY, z);
```

- 缓存键相同的调用直接 blit 上一帧纹理
- 键不同则调用烘焙回调重绘
- **返回 false 表示本次未命中**（已重绘），true 表示命中（用了缓存）

### 5.2 缓存键必须包含所有影响绘制的东西

`SettingsPage.pageCacheKey()` 的组成：

```java
System.identityHashCode(this) * 1_000_003L   // 页面身份
+ renderRevision * 31_415_926_535L            // 结构性重建次数
+ scrollHash                                   // 滚动位置
+ ConfigManager.revision() * 2_654_435_761L   // 配置变更次数
```

**漏掉任何一项都导致"改了但界面不刷新"。**

`ConfigManager.revision()` 的存在就是为了这个：任何配置写入后 `revision++`，缓存失效。

### 5.3 `ConfigManager.touch()` 的隐式契约 ⚠️

**这是当前最大的语义陷阱。**

`touch()` 让 `revision++`。但它被包在四个地方：

| 位置 | 覆盖范围 |
|---|---|
| `Setting` 的 4 个 `touch()` 重载 | 所有 Setting 子类的 setter |
| `Module` 构造器 | 功能开关 |
| `FeatureRegistry.setEnabled` | SPI 开关 |
| 手工调用（40+ 处）| 散落的直接写入 |

**逐个子类核实的结果**：

| Setting 子类 | 自动 touch | 说明 |
|---|---|---|
| `BooleanSetting` | ✅ | `this.setter = touch(setter)` |
| `SliderSetting` | ✅ | 同上 |
| `ColorSetting` | ✅ | 同上（`IntConsumer` 重载）|
| `DropdownSetting` | ✅ | 同上 |
| `TextFieldSetting` | ✅ | 同上 |
| `GroupSetting` | ✅ | 三元形式：`getter == null ? null : touch(setter)` |
| `ActionSetting` | N/A | 没有 setter，动作自己负责 |
| **`ChoiceSetting`** | ❌ | `select()` 直接 `setter.accept(index)`，**没有包装** |

**唯一的例外是 `ChoiceSetting`。** 用它时调用方必须自己调 `ConfigManager.touch()`，否则面板不刷新。

这是 `FpsTpsPingFeature` 里那行显式 `ConfigManager.touch()` 的原因。

**这条规则没有被强制，只能靠记忆。** 这是技术债，见 §7.3。

---

## 6. 组件清单

### 6.1 基础设施（`ui/`）

| 类 | 职责 |
|---|---|
| `UiElement` | 树节点：父子关系、bounds、offset、事件虚方法 |
| `Style` | 样式：尺寸/盒模型/flex/对齐/绝对定位/方向 |
| `LayoutEngine` | 布局：measure + layout 两阶段 |
| `Box` | 纯布局容器（不绘制）|
| `Text` | 文本节点 |
| `ScrollView` | 滚动容器（内部 content + 可选滚动条）|
| `Dimension` / `Insets` / `Rect` / `Size` | 几何值对象 |
| `UiPainter` | 绘制抽象（平台无关）|
| `Painted` | 可自绘接口 |
| `TextMeasurer` / `TextWeight` | 文本测量 |

### 6.2 输入（`ui/input/`）

| 类 | 职责 |
|---|---|
| `InputRouter` | 事件路由 + 焦点 + 层 |
| `PointerEvent` / `KeyEvent` / `WheelEvent` / `TextInputEvent` / `PreeditEvent` | 事件 |
| `Modifiers` / `Keys` | 常量 |

### 6.3 控件（`ui/widget/`）

| 类 | 说明 |
|---|---|
| `Control` | 控件基类 |
| `Button` | 按钮（`SurfaceMode`：CARD / MENU_*）|
| `Switch` | 开关 |
| `Slider` | 滑块 |
| `Segmented` | 分段选择 |
| `Dropdown` | 下拉（自带搜索 + 浮层）|
| `TextField` | 文本输入 |
| `ColorField` | 颜色行 + 浮层取色器 |
| `ColorPickerPanel` | 取色器面板（**只是半边，需配 `ColorField` 的输入框**）|
| `Card` | 卡片容器（唯一有背景的 `Box`）|
| `Scrollbar` | 滚动条 |
| `CrosshairGrid` / `CrosshairPreview` | 准星专用 |
| `WidgetPalette` | 调色板 record |

---

## 7. 已知缺口与技术债

审计确认的问题，按影响排序。

### 7.1 没有页面/框架层 ⚠️ 最大缺口

`ui/` 里有组件和输入路由，但**没有**：

| 缺失 | 后果 |
|---|---|
| 页面基类 | 每个页面自己写布局调用、绘制递归、输入桥接、缓存集成 |
| 绘制调度 | `SettingsPage.drawElement` 是唯一一份，其他页面各写各的 |
| 浮层托管 | 三层（注册/布局/绘制）要宿主手写，`SettingsPage` 写了三段 |
| 缓存集成 | `pageCacheKey` 的拼法要每个页面自己维护 |

**证据：没有一个纯 v2 的页面。** 所有页面（`OneClickScreen` / `HudPositionEditorScreen` / `SettingsPage` / `ModulesPage`）都是即时模式或混用。

### 7.2 `ColorPickerPanel` 不独立

它只画 SV 方块 + 色相条 + 透明度条 + 预设色块。**hex/R/G/B/A 输入框由 `ColorField` 单独创建并绝对定位叠加。**

单独用 `ColorPickerPanel` 会得到"没有输入框、也没有可命中控件"的取色器——这是 `DevDrawer` 迁移失败的第二个原因。

### 7.3 `touch()` 无强制机制

见 §5.3。**实测确认：8 个 Setting 子类里 7 个自动包装，唯一例外是 `ChoiceSetting`。**

这个例外无法通过类型系统发现——`ChoiceSetting` 和其他子类的用法看起来一样，忘记调用 `touch()` 的后果是"切换选项后界面不刷新"，而且只在特定页面出现。

**建议修复方向**：把 `ChoiceSetting.setter` 也改成 `touch()` 包装，或让 `select()` 内部调用 `readyForChange()` 钩子。改动小但需要验证每个 `ChoiceSetting` 的使用点不会重复 touch（重复调用无害，只是计数多加）。

### 7.4 类注释过时

`LayoutEngine` 说"不实现绝对定位"，实际实现了。这类过时注释比没有注释更危险——它会让人写出错误的代码。

### 7.5 样式系统四层但职责分散

```
GuiTheme (14 字段)        ← 数据源
    ↓
ClickGuiStyle (219 行)    ← 语义 getter + 绘制方法 + 颜色运算  ← 职责过多
    ↓
Ui2Theme (29 行)          ← 适配器（依赖方向正确）
    ↓
WidgetPalette (18 字段)   ← v2 消费格式
```

依赖方向是单向的、健康的。问题是 `ClickGuiStyle` 混了三种职责：
- 38 个语义 getter（数据）
- 5 个绘制方法（`surface`/`shadow`/`button`/`field`/`pill`）
- 4 个颜色运算（`hover`/`mix`/`alpha`/`fillFor`）

---

## 8. 迁移检查表

把即时模式页面迁到 v2 时，逐项确认：

- [ ] **坐标**：传入 `InputRouter` 的是 design 坐标，不是 screen 坐标
- [ ] **offset**：只对根和真正需要绝对定位的元素使用；流式子元素不设
- [ ] **绘制递归**：根 translate 一次，子节点用 bounds，不重复 translate
- [ ] **背景**：需要背景用 `Card`，`Box` 画不出东西
- [ ] **Text**：不是 `Painted`，要单独分支，用 `bounds()` 定位
- [ ] **浮层**：注册到 `InputRouter.layers` + 自己布局（设 offset）+ 自己绘制
- [ ] **缓存键**：包含 identity + revision + scroll + `ConfigManager.revision()`
- [ ] **touch()**：所有配置写入路径都触发（特别是 `ChoiceSetting`）
- [ ] **dynamicOverlay**：除非必要不返回 true
- [ ] **绝对定位**：`position(ABSOLUTE)` + `left/top` 相对于**父内容盒**，不是定位祖先

---

## 附：一次真实失败的复盘

`DevDrawer`（683 行即时模式）迁移到 v2 失败三轮，每次的根因：

| 轮次 | 症状 | 根因 |
|---|---|---|
| 1 | 抽屉在左上角、文字堆叠 | 用 `Style.left/top` 想定位 root，但 root 总被放在 (0,0)。正解是 `offset()` |
| 2 | 控件画出边界、点击位置错位 | 每个节点都 `translate(offset)`，流式子元素虽为 0 但根被 translate 两次 |
| 3 | 取色器无输入框、无法交互 | 单独用 `ColorPickerPanel`，没有配 `ColorField` 的输入框 |

这三次都不是"能力不足"，而是**契约没有文档化**。本文就是为了让第四次不必再试错。
