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

### 1.5.1 控件内部必须自己扣掉祖先 offset ⚠️

**这一条我踩了最久，因为它只在弹窗里的控件上暴露。**

事件传到 `onPointerDown` 时，坐标仍是**design 坐标**（`InputRouter.bubble` 不转换）。但控件绘制基准是 `bounds()`，也就是**父局部坐标**。两者差着祖先链上的 `offset` 之和。

**正确写法**（`Slider`、`Segmented`、`ColorPickerPanel` 都是这样）：

```java
float localX = event.x() - ancestorOffsetX();
```

**`TextField` 曾经漏了这一行**，后果：

| 输入框位置 | 祖先 offset X | 表现 |
|---|---|---|
| 设置页内（在 ScrollView 里） | **0**（滚动只改 Y）| **正常**，bug 隐藏 |
| 下拉搜索框（在 popup 里） | 弹窗的水平位置，几百像素 | 光标偏右 5-6 个字符 |

**为什么难查**：`ScrollView` 的 `offset` 只在 Y 方向，而光标定位只关心 X，所以最常见的输入框反而看不出问题。只有放进 popup 的输入框才暴露。

**规则**：任何在 `onPointerDown` / `onPointerMove` 里读 `event.x()/y()` 的控件，第一件事是减 `ancestorOffsetX()/ancestorOffsetY()`。

### 1.5.2 坐标要减**两步**，不只是祖先 offset ⚠️

**这一条是 1.5.1 的补丁，因为 1.5.1 只说了一半。**

从 design 坐标到控件内部坐标要走三步：

```
design 坐标
  − ancestorOffsetX()      → 父容器的局部坐标
  − bounds().x()           → 控件自身的内部坐标（0 起）
  = 控件内部坐标
```

控件内部的绘制与命中计算（如 `zoneAt`、`presetX`、光标位置）都是按**0 起**写的，所以两步都必须减。

**`ColorPickerPanel` 曾经只减了第一步**：

```java
// 错误
float x = event.x() - ancestorOffsetX();
zoneAt(x, y);              // zoneAt 里用 PAD=8 这样的面板内部坐标

// 正确
float x = event.x() - ancestorOffsetX() - bounds().x();
```

**症状**：取色器的所有点击**向右下偏移**，偏移量正好是取色器在父容器里的位置。方块、色相条、预设全都点不准。

**为什么它不明显**：取色器在 `ColorField` 的 popup 里时，`bounds()` 接近 0（popup 内绝对定位到左上角），bug 隐藏了。只有把它嵌到别的容器中间（这次是抽屉），偏移才暴露。

**这与 1.5.1 是同一类错误的不同步骤**：`TextField` 漏了减祖先 offset，`ColorPickerPanel` 漏了减 bounds。两者的共同点是——**v2 没有提供统一的“事件坐标 → 控件内部坐标”的封装，每个控件都要自己写对三步**。

**自查方式**：如果你的控件里出现 `PAD`、`FIELD_X` 这类从 0 开始算的常量，而事件处理里只减了 `ancestorOffset`，那就是漏了 `bounds()`。

**另一个相关陷阱**：字体测量必须和绘制用同一字重。`painter.text()` 画的是 **Medium**，如果测量用默认（Regular），光标位置会差几个像素。`UiPlatform.install()` 在启动时指定字重，就是为了对齐这两者。

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

### 4.3.1 一个 `InputRouter` 只能有一个焦点，但一个屏幕可以有很多个 router ⚠️

**这是最容易踩的坑，因为它不会报错，只会让输入跑到错误的控件里。**

焦点是**每个 router 各自维护**的。当屏幕上并排放着多个独立模块时，每个模块有自己的 router，于是**它们可以同时各有一个聚焦的文本框**：

```
OneClickScreen
├─ modulesPage  (InputRouter A)  ← 可能有焦点
├─ moduleChrome (InputRouter B)  ← 可能有焦点
└─ statsPage    (InputRouter C)  ← 可能有焦点
```

**症状**：在模块的设置页里打字，字符却出现在顶部搜索框。原因是 `charTyped` 按固定顺序分发，而侧栏排在设置页前面。backspace 走同样路径，于是"能打字但删不掉"。

**规则：屏幕必须在点击时交接焦点，保证同时只有一个 router 持有焦点。**

```java
// OneClickScreen：在分发 pointerDown 之前
private void focusOnly(int region) {
    if (region != TEXT_REGION_MODULES && modulesPage != null) modulesPage.clearFocus();
    if (region != TEXT_REGION_CHROME && moduleChrome != null) moduleChrome.clearFocus();
    if (region != TEXT_REGION_SETTINGS && statsPage != null) statsPage.clearFocus();
}
```

**为什么是 `pointerDown`**：键盘和文本事件都发给"已经持有焦点"的控件，所以只要在点击时让所有者唯一，后续所有事件自然正确。

**为什么放在屏幕层**：各模块之间无法互相协调——`SettingsPage` 不知道侧栏存在。只有屏幕能看到全部。

**新增一个可聚焦模块时，必须把它加进 `focusOnly`**，否则又会退化成"谁先被检查谁赢"。

### 4.3.2 浮层必须收到 `mouseMoved`，否则 hover 永远不更新 ⚠️

**症状**：控件的高亮只在"别的操作恰好触发了 hover 重算"时出现——比如滚动一下列表，行才高亮；鼠标单纯移上去没有反应。

**原因**：宿主的 `mouseMoved` 只把事件转发给页面，**漏了浮层**。浮层的 `InputRouter` 从不收到 `pointerMove`，所以 `hovered` 一直停留在旧值。

**规则**：宿主转发输入事件时，**浮层优先**：

```java
public void mouseMoved(double mouseX, double mouseY) {
    if (overlay.isOpen()) {
        overlay.mouseMoved(mouseX, mouseY, width, height);
        return;                       // 浮层在顶层，吞掉事件
    }
    // …页面的转发
}
```

**为什么容易漏**：`mouseDragged` 转发给了浮层（拖拽能工作），`mouseMoved` 却没有。两个都是指针移动，但走的是不同的宿主方法，漏一个不影响另一个。

**判据**：任何"hover 要等别的操作才刷新"的现象，先查 `mouseMoved` 的转发链。

### 4.3.3 宿主必须履行的四件事 ⚠️

**这是 §4.3.1 和 §4.3.2 的归纳：五次踩坑，四个不同的遗漏点。**

`ui/` 只定义了「节点」，没有定义「宿主」。于是每个 `Screen` 都自己实现一遍平台事件到 `InputRouter` 的转发，而没有任何两份相同。已确认的遗漏：

| # | 职责 | 漏了的后果 | 现状 |
|---|---|---|---|
| 1 | 键码转换 `InputKeys.canonical` | 26.3 的 Backspace/Esc/Enter 全部失效（26.2 是 GLFW 码，26.3 是 SDL 码） | `InputBridge.toKernel` |
| 2 | 修饰符传递（Ctrl/Shift/Alt） | Ctrl+A/C/V/X 全部失效，输入框里的全选/复制/粘贴/剪切都没了 | `InputBridge.modifiersOf` |
| 3 | 字符过滤 | 把控制字符送进输入框 | `InputBridge.textOf` |
| 4 | **`minecraft.onTextInputFocusChange(this, true)`** | **`charTyped` 完全不被调用** | ⚠️ **仍散落，无封装** |

**第 4 条最隐蔽，因为它只挡字符流，不挡按键。** 症状是"输入框半坏"：

| 操作 | 走的路径 | 是否受第 4 条影响 |
|---|---|---|
| 敲字符 | `charTyped` | ❌ 失效 |
| Ctrl+V 粘贴 | `keyPressed` → `keyDown` | ✅ 正常 |
| Ctrl+A 全选 | `keyPressed` | ✅ 正常 |
| 鼠标点击定位光标 | `mouseClicked` | ✅ 正常 |

所以看起来像"这个输入框有点问题"，而不是"这个屏幕根本不能打字"。**HUD 位置编辑器因此长期无法在取色器里输入数字，却能用 Ctrl+V 粘贴出数字。**

**登记的时机**：`init()` 里登记、`removed()` 里注销。`init` 也会在窗口缩放时重跑，所以登记是幂等的。

**已验证的宿主**（`DevUiTestScreen`、`OneClickScreen` 有；`HudPositionEditorScreen` 曾长期没有）。

**自查**：新写一个 `Screen` 时，先搜 `onTextInputFocusChange`。没有它，文本输入一定不完整。

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

### 4.5 宿主：`Layer` 与 `UiHost`

页面之上还有一层：宿主。它持有若干个 `Layer`，把平台事件分发给它们。设计见 `UIHOST-DESIGN.md`，这里只记契约。

```java
public interface Layer {
    Rect boundsOnScreen();      // 宿主【设计】空间，不是屏幕像素
    boolean isOpen();
    float scale();              // 层内设计单位 → 宿主设计单位
    boolean contains(float x, float y);   // 默认用 bounds；非矩形命中区可覆写
    void paint(UiPainter painter);
    // 9 个输入方法，坐标是【层局部】空间
    boolean pointerDown(float x, float y, int button, int modifiers);
    ...
    boolean hasFocus();
    void clearFocus();
    boolean hasPointerCapture();
}
```

`UiHost` 一次实现 9 个输入方法。子类只提供：`layers()`、`toDesignX/Y()`、`ensureLaidOut()`、`paintHost()`。

#### 三条必须遵守的约定

**① `boundsOnScreen()` 报告的是宿主设计空间。** 宿主已经用 `toDesignX/Y` 把指针从屏幕坐标转成设计坐标，层再报告屏幕像素就会差一个减法——症状是每个控件都响应在光标左上，偏移量正好等于宿主原点。

**② 层只在真的消费了事件时才返回 true。** `keyDown` 无条件返回 true 会让宿主以为事件已被处理，于是它不再执行"没层要就清焦点"的兜底。`TopBarControls` 曾因此让搜索框里的第一次 ESC 完全无效。

例外：ESC 应该放行给宿主，除非层有非它不可的理由（`SettingsPage` 在弹窗打开时消费 ESC，否则宿主会关掉整个 GUI 并丢弃正在编辑的颜色）。

**③ 非矩形的命中区要覆写 `contains()`。** 模块 chrome 接受侧栏和设置页上方的窄条，但不接受设置页本身——那是 L 形，单个 `Rect` 表达不了。

#### ④ 不在 `layers()` 里的浮层，每个输入方法都要先问它

主题抽屉（`DevDrawer`）还不是层，但它盖在所有层上面。宿主的分发只遍历 `layers()`，所以抽屉必须由宿主自己在**每一个**输入方法里先调用。

**只改一个方法是不够的**——这个 bug 修了两次，第一次只改了 `mouseClicked`。症状很有辨识度：**抽屉与面板重叠的部分没反应，不重叠的部分正常**。因为 `super.mouseClicked` 之类只在指针落在某个层的命中区内时才返回 true，所以被拦下的恰好是重叠区。

| 方法 | 规则 |
|---|---|
| `mouseClicked` | 抽屉优先；外部点击返回 false 让它穿透（刻意设计）|
| `mouseDragged` | 抽屉优先。它打开时无条件返回 true，所以放在 `super` 之后等于永远收不到 |
| `mouseScrolled` | 抽屉优先 |
| `mouseReleased` | **抽屉无条件先收**，不能放在 `if (super...) return true` 之后 |

`mouseReleased` 是个陷阱：抽屉在这里清 `dragging` 和 `InputRouter` 的按下状态。让它排在后面，就意味着有的释放它收不到，下一次按下会表现为"点了没反应"——而点击本身看起来是好的。

#### 不在 `Layer` 里的东西

- **弹窗**（取色器、下拉列表）属于层的内部状态，宿主看不见，也不该看见。
- **锚定绘制顺序**（谁会盖住谁）是宿主的业务，`layers()` 的返回顺序就是绘制顺序。

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

### 5.2.1 页面自有状态必须覆写 `revision()`

**这是最容易漏的一条，我已经踩过。**

`UiPage.revision()` 默认只返回 `ConfigManager.revision()`。它只覆盖"配置变了"这一种情况。

如果页面显示的某个值**不在配置里**，那么改变它不会触发任何缓存失效。典型例子：

```java
// 测试页里选中的下拉项
private int demoDropdown;
...
new Dropdown(label, options, () -> demoDropdown, index -> demoDropdown = index, ...)
```

点选「三」之后，`demoDropdown` 变成 2，但：

- `Dropdown.onChange` 只调 `onChange.accept(index)`，**不碰 `ConfigManager`**
- 静态层仍缓存着显示「一」的纹理
- 直到某个别的事件（重新打开下拉）让 `renderRevision++`，才重烘

**症状**：控件的值变了，但界面要等下一次交互才更新。

**正确做法**：把页面自有的可变状态折进 `revision()`。

```java
@Override
protected long revision() {
    return super.revision()
            + demoDropdown * 31L
            + demoMode * 1_009L
            + Double.doubleToLongBits(demoSlider)
            + (demoSwitch ? 7L : 0L)
            + demoText.hashCode()
            + demoColor;
}
```

**判断标准**：只要某个值影响绘制，且它**不是通过 `Setting` 的 setter 改的**（那些自带 `touch()`），就必须出现在 `revision()` 里。

这也解释了为什么 `SettingsPage` 没有这个问题：它的每个控件都连到 `Setting.XxxSetting` 的 setter，而那些 setter 都被 `touch()` 包装了。**直接连业务字段的控件没有这层保护。**

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
| `ChoiceSetting` | ✅ | 已修复：构造器里 `this.setter = touch(setter)` |

**8 个子类现在全部自动包装，没有例外。**

`ChoiceSetting` 曾是唯一的漏网之鱼：它直接存原始 setter，`select()` 里只是 `setter.accept(index)`。后果是切换选项**不 bump 配置版本号**，缓存可能继续显示旧值，直到别的事件让它失效。

它没有暴露成显眼 bug，是因为两个使用点碰巧各自遮掩了一半：

| 使用点 | 当时的写法 | 结果 |
|---|---|---|
| `FpsTpsPingFeature` | setter 里手写 `ConfigManager.touch()` | 正常，但那是调用方补偿 |
| `StatsDisplayFeature` | setter 里只调 `rebuildPanelAfterStyleChange()` | **实际缺少 touch**，只是重建顺带刷新了界面 |

**教训**：靠调用方记忆的规则，正确性取决于每个调用方是否记得。现在规则由构造器保证。

**仍然没有强制机制**：如果有人新增一个 Setting 子类，还是可能忘记。见 §7.3。

### 5.4 嵌套缓存与外层 pose 缩放的冲突 ⚠️

**组件内部的缓存假设"自己是唯一缩放内容的东西"。**

`ColorPickerPanel` 把色相条、棋盘格、预设烘进自己的纹理：

```java
float poseScaleX = mc.graphics().pose().m00();     // 读外层 pose 的缩放
staticCache.draw(..., CONTENT_W, CONTENT_H, poseScaleX, poseScaleY, ...)
```

`GuiPanelCache` 按 `panelSize × scale × guiScale` 算纹理尺寸。所以当外层有 `pose.scale(0.5)` 时，**纹理只创建了一半大小**，而内容仍按完整设计尺寸绘制 → **下半和右侧被裁掉**。

**症状的特征**：只在缓存命中时错误，缓存失效重烘的那一帧是对的。

**规则**：把一个自带缓存的组件放进有 pose 缩放的容器时，**要么禁掉它的内部缓存，要么让容器不在 pose 中缩放**。

`DevDrawer` 的选择是前者——`ColorPickerPanel.editor(..., cacheStaticLayer = false)`，代价是每帧多几百个 `fill`，但只在选中颜色时才发生。

**为什么 `SettingsPage` 里的取色器没这个问题**：它的 pose 是 1:1，`m00()` 为 1，缓存尺寸正确。

### 5.5 同层多个缓存槽：可以并存

`GuiPanelCache` 的每个 `Slot` 有自己的 `RenderTarget`、`GuiRenderState`、`GuiRenderer`，`bakePending()` 的全局状态替换（`mainRenderTarget`、窗口宽高缩放）在 `finally` 里成对还原。

**同帧多个 Slot 烘焙是安全的。** `ModulesPage` 曾有一段注释说"两个纹理竞争 target"，那是早期版本（全局单缓存）的约束，`4884dce` 之后已不成立——但绕过缓存的代码留了下来，直到实验证明可以删掉。

**判据**：如果你看到"同帧两个缓存冲突"之类的注释，先看它写于什么时候，再看每个 Slot 是否已经独立。

---

## 6. 组件清单

### 6.1 基础设施（`ui/`）

| 类 | 职责 |
|---|---|
| `UiElement` | 树节点：父子关系、bounds、offset、事件虚方法 |
| `Style` | 样式：尺寸/盒模型/flex/对齐/绝对定位/方向 |
| `LayoutEngine` | 布局：measure + layout 两阶段 |
| `Box` | 纯布局容器（**不绘制**，要背景用 `Card`）|
| `Text` | 文本节点（**不是 `Painted`**，绘制要单独分支）|
| `ScrollView` | 滚动容器（内部 content + 可选滚动条）|
| `Dimension` / `Insets` / `Rect` / `Size` | 几何值对象 |
| `UiPainter` | 绘制抽象（平台无关）|
| `Painted` | 可自绘接口（只有 `Card` 和 `Control` 实现）|
| `TextMeasurer` / `TextWeight` | 文本测量 |
| `UiPage` | **页面基类**：布局 + 缓存 + 绘制序列 + 输入桥接 |
| `PaintPass` | 静态层 / 动态层绘制 |
| `OverlayHost` | 浮层注册与绘制 |
| `PopupOwner` | 浮层所有者的契约 |
| `PageTransform` | 屏幕坐标 → 设计坐标 |
| `RenderCache` | 纹理缓存契约（实现在 `ui/mc/`）|
| `UiPlatform` | **平台服务安装点**：字体测量 + 设备缩放 + 剪贴板 |
| `Clipboard` / `GuiScale` | 平台服务契约（实现在 `SkyPocketMod.onInitializeClient`）|
| `TextMeasurer` | 文本测量契约 |

### 6.1.2 平台服务的安装

三个东西内核需要但无法自己实现，全部通过 `UiPlatform` 安装一次：

```java
// SkyPocketMod.onInitializeClient()
UiPlatform.install(textMeasurer, guiScaleSupplier, clipboardImpl);
```

| 服务 | 谁需要 | 为什么不能自己取 |
|---|---|---|
| 文本测量 | `TextField` 定位光标 | 需要 `ClickGuiFonts`（依赖 config）|
| 设备缩放 | `ColorPickerPanel` 算缓存键 | 需要 `Minecraft.getInstance()` |
| 剪贴板 | `TextField` 的 Ctrl+C/V | 同上 |

**未安装时各自有中性默认值**（测量为 0、缩放 1、剪贴板空），所以无头测试或启动早期的控件不会崩。

**为什么是静态安装点而不是构造参数**：三者都是进程唯一的，穿过 6 个控件构造函数和所有调用方只会增加噪音。

⚠️ **不要在这里加"随时会变"的状态**。安装点是给不变的平台事实用的；会变的东西应该走 `revision()`。

### 6.1.1 平台绑定（`ui/mc/`）

**唯一允许 import `dev.skypocket.render` 的地方。**

| 类 | 职责 |
|---|---|
| `McUiPainter` | `UiPainter` 的 MC 实现 |
| `McRenderCache` | `RenderCache` 的 MC 实现（含烘焙时的 scissor 重置）|

### 6.2 输入（`ui/input/`）

| 类 | 职责 |
|---|---|
| `InputRouter` | 事件路由 + 焦点 + 层 |
| `PointerEvent` / `KeyEvent` / `WheelEvent` / `TextInputEvent` / `PreeditEvent` | 事件 |
| `Modifiers` / `Keys` | 常量 |
| `InputKeys` | **平台键码 ↔ 内核键码**的适配器（26.2 用 GLFW，26.3 用 SDL3）|

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

按影响排序。**§7.1 曾经记录的最大缺口已经补齐**，保留在这里是因为它解释了几个类的由来。

### 7.1 页面框架层（已补齐 ✅）

`ui/` 原来只有组件和输入路由，**没有**：

| 缺失 | 当时的后果 | 现在 |
|---|---|---|
| 页面基类 | 每页自己写布局、绘制、输入、缓存 | `UiPage` |
| 绘制调度 | `SettingsPage.drawElement` 是唯一一份 | `PaintPass` |
| 浮层托管 | 三层（注册/布局/绘制）宿主手写 | `OverlayHost` + `PopupOwner` |
| 缓存集成 | `pageCacheKey` 拼法各自维护 | `UiPage.cacheKey` |

补齐后 `SettingsPage` 从 704 行降到 512 行，8 个框架职责方法归零。`DevUiTestScreen` 只实现 `build()` 和 `revision()`。

**仍未使用框架的**：`ModulesPage`（自带布局与缓存，见 §7.6）、`OneClickScreen`（壳层）、`HudPositionEditorScreen`（纯即时模式，与 v2 无关）。

### 7.2 `ColorPickerPanel` 不独立

它只画 SV 方块 + 色相条 + 透明度条 + 预设色块。**hex/R/G/B/A 输入框由 `ColorField` 单独创建并绝对定位叠加。**

单独用 `ColorPickerPanel` 会得到"没有输入框、也没有可命中控件"的取色器——这是 `DevDrawer` 迁移失败的第二个原因。

### 7.3 `touch()` 仍无编译期强制（但已无例外）

见 §5.3。**8 个 Setting 子类现在全部自动包装**，包括曾经遗漏的 `ChoiceSetting`。

**剩余风险**：新增一个设置子类时，仍然可能忘记套 `touch()`。这无法通过类型系统发现——写法和别的子类看起来一样，后果是"改值后面板不刷新"，而且只在特定页面出现。

**可以考虑的进一步措施**：让 `Setting` 提供一个 `protected final IntConsumer touch(IntConsumer)` 之外的统一入口，或在构造器里强制要求传入经过 `touch()` 的 setter（当前是可选调用，靠自觉）。

**不建议**：给 `set()` 加反射检查或运行期断言，代价大于收益。

### 7.6 `ModulesPage` 自带布局（不打算迁移）

它有 6 处与 `UiPage` 的假设冲突，其中 5 处曾是历史包袱，现在只改了 `withCards`：

| 项 | 状态 |
|---|---|
| `withCards` 双绘制路径 | ✅ 已改为**仅构建时生效**，绘制路径合并为一条 |
| `drawElement` / `intersects` / `pageCacheKey` | 未改，与 `PaintPass` 重复 |
| `hoverHash` | 未改，是它对"静态层不含 hover"的补偿 |
| 高亮动画、侧栏/卡片分区绘制 | 本质需求，保留 |

**为什么不套 `UiPage`**：它有两个实例在同一帧的不同位置绘制（`modulesPage` 画侧栏+卡片，`moduleChrome` 只画侧栏），而 `UiPage` 的模型是"一个页面 = 一棵树 + 一个缓存 + 一次绘制"。强行套需要 4-5 个覆写钩子，框架会因此对每个简单页面都更难理解。

**代价**：`drawElement` 等三处逻辑与 `PaintPass` 重复。如果将来要统一，前提是先把它的绘制拆成标准页面形态。

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
