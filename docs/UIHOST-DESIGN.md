# UiHost 设计

> 目标：把「宿主」这个当前不存在于框架中的概念补上，让新增界面不再需要重复实现事件转发、坐标换算、生命周期登记。

---

## 实施结果（2026-11）

计划已经落地，5 个提交：

| 提交 | 内容 |
|---|---|
| `cc88720` | `Layer` 接口；`UiPage` 实现它；删除 `PageTransform`——坐标换算改由宿主持有 |
| `c58ae0a` | `UiHost` 基类；`DevUiTestScreen` 迁移（549 → 489 行，输入方法 9 → 0）|
| `1fd7caf` | ClickGUI 顶部条成为层 |
| `cc4ce12` | 首页 + 模块 chrome 成为层；`Layer.contains` 支持非矩形命中区 |
| `f6d7c4d` | 设置列成为层；输入方法大幅简化 |

**最终状态**：`OneClickScreen` 的 5 个 pane 里 4 个走 `UiHost`，9 个输入方法从约 330 行降到约 60 行。剩下 `DevDrawer` 仍手工转发（16 处调用），原因见文末。

**没有按计划做的**：

- 阶段 3 原计划"迁移整个 `OneClickScreen`"，实际改为**一次一个 pane**。原计划低估了三个 pane 共存时的命中区形状（模块 chrome 是 L 形，不是矩形）。
- `HudPositionEditorScreen` 按计划**没有**迁移。

---



## 一、调查：宿主现在到底在做什么

调查了 3 个真实宿主（`OneClickScreen` 1427 行、`HudPositionEditorScreen` 1258 行、`DevUiTestScreen` 550 行），它们共同承担的职责是 6 类：

| # | 职责 | 证据 |
|---|---|---|
| 1 | **生命周期登记** | `onTextInputFocusChange` 只有 2/3 个宿主做了 |
| 2 | **输入事件分发** | 每个方法都是「按优先级问每一层」 |
| 3 | **坐标换算** | 屏幕 → 层内，每层不同 |
| 4 | **缩放管理** | `scaleX/scaleY` + `effectiveScale` + pose 三者必须同步 |
| 5 | **绘制编排** | 层的绘制顺序 + 裁剪 |
| 6 | **状态/导航** | 当前显示哪个页面、历史栈 |

**其中 1-5 是纯机械的，6 是业务。**

---

## 二、核心抽象：`Layer`

现有代码里，「层」是一个隐式概念。显式化它是整个设计的基础。

```java
/** One thing a host can show, at a screen position and scale. */
public interface Layer {
    /** Where this layer draws, in screen coordinates. */
    Rect boundsOnScreen();

    /** Design units -> screen units for this layer. */
    float scale();

    /** Whether this layer is currently showing. */
    boolean isOpen();

    /** Whether this layer takes input (a modal overlay may block lower layers). */
    boolean isModal();

    /** Draws at the given pose. The host has already translated and scaled. */
    void paint(McUiPainter painter);

    // ---- input, in layer-local coordinates ----------------------------------------
    boolean pointerDown(float x, float y, int button, int mods);
    boolean pointerMove(float x, float y, int mods);
    boolean pointerUp(float x, float y, int button, int mods);
    boolean wheel(float x, float y, float delta, int mods);
    boolean keyDown(KeyEvent event);
    boolean keyUp(KeyEvent event);
    boolean charTyped(String text);
    boolean preedit(String text, int caret);
}
```

### 为什么是接口，不是基类

现有三个「层」的实现方式完全不同：

| 层 | 实现 | 能否继承同一个基类 |
|---|---|---|
| `ModulesPage` / `SettingsPage` | 继承 `UiPage` | ✅ |
| `DevDrawer` / `HudSettingsCard` | 独立类，自己管树 | ❌ 不能 |
| `HudPositionEditorScreen` 的 11 个预览 | 根本不是树 | ❌ 不能 |

**接口能覆盖三者，基类不能。**

### 适配器优先

不改现有类，写适配器：

```java
/** Wraps a UiPage as a layer. Most layers are this. */
public final class PageLayer implements Layer {
    private final UiPage page;
    private final Supplier<Rect> bounds;
    private final float scale;

    public boolean pointerDown(float x, float y, int button, int mods) {
        return page.pointerDown(x, y, button, mods);
    }
    // ...
}

/** Wraps a hand-managed overlay (DevDrawer, HudSettingsCard). */
public final class OverlayLayer implements Layer { ... }
```

---

## 三、`UiHost` 的职责

```java
public abstract class UiHost extends Screen {
    private final List<Layer> layers = new ArrayList<>();

    protected final void register(Layer layer);      // 按 z 序
    protected final void unregister(Layer layer);

    // ---- lifecycle, implemented ONCE ------------------------------------------
    @Override protected void init() {
        minecraft.onTextInputFocusChange(this, true);   // ← 第 4 项，不可能再忘
        onInit();
    }
    @Override public void removed() {
        minecraft.onTextInputFocusChange(this, false);
        onRemoved();
    }

    // ---- input, implemented ONCE ----------------------------------------------
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        for (Layer layer : topDown()) {
            if (!layer.isOpen()) continue;
            Rect r = layer.boundsOnScreen();
            if (!r.contains(event.x(), event.y())) continue;
            layerIn(layer).pointerDown(
                    (event.x() - r.x()) / layer.scale(),
                    (event.y() - r.y()) / layer.scale(),
                    MouseButtons.canonical(event.button()),
                    InputBridge.modifiersOf(event));
            if (layer.isModal()) return true;   // 模态层吞掉后续
            if (consumed) return true;
        }
        return false;
    }
    // keyPressed / charTyped / preeditUpdated / mouseMoved / ... 同理，各写一次
}
```

**关键点**：

- **`for (layer : topDown())`** 取代 5 个手写的 `if (xxxActive())`
- **坐标换算在循环里做一次**，每个层只需声明 `boundsOnScreen()` 和 `scale()`
- **模态层吞事件**，取代 `if (modal == MODAL_NONE)` 这类散落判断

---

## 四、分阶段落地

**一次全改风险太高。分 4 步，每步独立可验证。**

### 阶段 1：`Layer` 接口 + `PageLayer`（不改变任何行为）

| 项 | 内容 |
|---|---|
| 新增 | `ui/host/Layer.java`、`ui/host/PageLayer.java` |
| 修改 | 无 |
| 验证 | 编译通过，且 `PageLayer` 有单元测试 |
| 风险 | 零 |

**这一步只定义接口，不接入任何宿主。**

### 阶段 2：`UiHost` 基类 + 迁移 `DevUiTestScreen`

**为什么从 `DevUiTestScreen` 开始**：它是开发者测试屏，**崩了不影响用户**。

| 项 | 内容 |
|---|---|
| 新增 | `ui/host/UiHost.java` |
| 修改 | `DevUiTestScreen` 继承 `UiHost`，550 → 约 300 行 |
| 验证 | 实机打开测试屏，所有交互正常 |
| 风险 | 低（不影响正式功能）|

### 阶段 3：迁移 `OneClickScreen`

**这是真正有价值的迁移**（1427 行里约 400 行是转发）。

| 项 | 内容 |
|---|---|
| 修改 | `OneClickScreen` 继承 `UiHost` |
| 新增 | 5 个 `Layer` 适配器（drawer / modules / chrome / stats / shell）|
| 验证 | **完整回归**：首页、模块页、设置页、抽屉、所有输入 |
| 风险 | **高** |

**这一步必须先有回归清单。**

### 阶段 4：`HudPositionEditorScreen`（可选）

**我建议不做。** 原因见下。

---

## 五、`HudPositionEditorScreen` 为什么不适用

它的层不是「页面」，而是「可拖拽的 HUD 元素」：

| 需求 | `UiHost` 能否满足 |
|---|---|
| 11 个元素各自由鼠标拖动 | ❌ 这是连续变换，不是点击 |
| 手柄命中测试（3 个方向） | ❌ 不是矩形包含判断 |
| 方向键微调选中元素 | ❌ 「选中」是屏幕自己的状态 |
| 逐元素不同的缩放 | ⚠️ 需要每个元素一个 layer，过度 |

**强行套 `UiHost` 会让它更长、更绕。** 它需要的只是 `InputBridge`（已完成）。

**唯一值得迁的是 `HudSettingsCard`**——它是真正的浮层。但那需要先把 `HudPositionEditorScreen` 拆出「浮层宿主」和「画布」，成本大于收益。

---

## 六、必须先回答的设计问题

### 问题 1：层的 z 序由谁决定？

**方案 A**：注册顺序即 z 序（后注册在上）
**方案 B**：层自己声明 `zIndex()`
**方案 C**：显式 `Layer.Priority`（MODAL > OVERLAY > PAGE）

**我倾向 C**：`OneClickScreen` 现在的判断其实是三档（浮层 / 页面 / 外壳），用枚举比整数明确。

### 问题 2：外壳怎么办？

`PanelRenderer`（tabs + footer）不是 `Layer`——它**没有 `UiElement` 树**，是纯手绘。

**选项**：
- **A1**：外壳也包成 Layer（适配器里直接调 `PanelRenderer.drawBackground`）
- **A2**：外壳留在宿主里，`UiHost` 提供 `paintChrome()` 钩子

**我倾向 A2**：外壳是「宿主自己的装饰」，不是「层」。这样 `UiHost` 的层列表只含真正的 v2 页面/浮层。

### 问题 3：动画（`animScale`）怎么办？

`OneClickScreen` 的 `localX/localY` 里包含**开合动画的缩放**：

```java
float a = animScale();
return ((screenX - cx) / a + cx - offX) / scaleX;
```

这不是「层」的职责，是**整个屏幕**的变换。

**选项**：
- **B1**：`UiHost` 提供 `transform()` 钩子，子类可覆盖
- **B2**：动画期间禁用输入（简单但不精确）

**我倾向 B1**：`UiHost` 暴露 `screenToLocal(x, y)` 供子类补充动画变换。

### 问题 4：`InputRouter` 与 `UiHost` 的关系

现在每个层有自己的 `InputRouter`（`UiPage` 内部创建一个）。

`UiHost` 分发到层，层再分发到自己的 router。**两级分发**。

**这不重复吗？** 不。`UiHost` 管「屏幕级」的路由（哪个层），`InputRouter` 管「层内」的路由（哪个控件）。**职责不同，都需要。**

---

## 七、明确不做的

| 项 | 原因 |
|---|---|
| 让 `HudPositionEditorScreen` 用 `UiHost` | 它不是页面容器，是画布 |
| 统一「外壳」到 Layer | 外壳无元素树，包成 Layer 收益低 |
| 消灭 `InputRouter` | 层内路由仍然需要 |
| 一次迁移所有宿主 | 风险不可控 |

---

## 八、验收标准

阶段 1 完成后：
- `Layer` 接口能同时表达 `UiPage`、`DevDrawer`、纯手绘平面
- `PageLayer` 有单元测试

阶段 2 完成后：
- `DevUiTestScreen` 行数下降 ≥ 40%
- 所有交互正常

阶段 3 完成后：
- `OneClickScreen` 行数下降 ≥ 25%
- **完整回归清单全绿**
- 新增一个页面时，不再需要改 9 个输入方法

---

## 八点五、阶段 2 实战暴露的三条硬约定

阶段 2 把 `DevUiTestScreen` 迁到 `UiHost`，改动本身很小，但实机测试暴露了三个设计缺口。它们都不是实现 bug，而是接口没把话说清楚——所以写在这里，作为 `UiHost` 契约的一部分。

### 1. 布局必须能在输入之前发生

初版只在 `paintHost` 里做 `bounds()` + `layout()`。第一帧绘制之前到达的点击，读到的是零尺寸的 `boundsOnScreen()`，`accepts()` 一律返回 false。

`UiHost` 因此有 `ensureLaidOut()`：每个输入方法和绘制入口都调用，实现必须幂等。**子类不可能忘记，因为它是抽象方法。**

这条推翻了设计文档第一版里的一个假设——绘制会先发生。MC 的 Screen 在 `init()` 之后、首次 `extractRenderState` 之前就可以收到输入。

### 2. 坐标空间只有一个：宿主设计坐标

`boundsOnScreen()` 报告的范围、分发的比较、层内部的坐标，**全部是宿主设计空间**，不是屏幕像素。

初版让 `UiPage.boundsOnScreen()` 返回 `(originX, originY, w, h)`——屏幕坐标。而分发此时已经把指针转成了设计坐标，两套空间差一个减法，症状是**每个控件都响应在光标左上，偏移量恰好是宿主原点**。

正确的分工只有一次转换：

```
屏幕像素 --toDesignX/Y()--> 宿主设计坐标 --boundsOnScreen()--> 层局部坐标
```

宿主负责第一段（它拥有 pose 和动画状态），`UiHost` 的 `toLocalX/Y` 负责第二段（减层原点、除层缩放）。

### 3. ESC 是宿主的职责，但层优先

`UiHost.keyPressed` 先让有焦点的层消费 ESC（通常是退出文本框），没有层接手才 `onClose()`。迁移时漏了这段，ESC 就完全失效——而层收到 ESC 也不会做任何事，因为普通页面不该自己关屏幕。

### 4. 一个类里不要有两套'层'机制

初版同时提供了字段 `private final List<Layer> layers` 和抽象方法 `layers()`。分发用了字段（永远空，因为没人调 `addLayer`），而子类覆写的是方法。**编译通过、构建成功、守卫全绿，所有输入静默失效。**

教训：同名的字段与方法并存时，读代码的人和编译器都会选错那个。删掉字段是唯一解法。

## 九、剩余工作

### `DevDrawer` 没有迁移，这是有意的

它是唯一一个同时具备三件事的层：用**屏幕坐标**而不是面板设计坐标、允许**点击穿透**到下层、内含真正的**模态弹窗**。

`UiHost` 的分发假设层的 bounds 在宿主设计空间里，而抽屉的原点是屏幕像素。要迁就得让 `Layer` 知道两种坐标空间的差异，而那会让接口为了一个特例变复杂。

**代价**：它留下 16 处手工转发。**收益**：其他四层的分发已经统一，抽屉的行为不需要重新验证。

如果将来要迁，最有希望的做法是让抽屉自己把设计坐标转成屏幕坐标再转发——它本来就有 `designX(mx, x)` 这套换算，内聚性不会丢。

### `HudPositionEditorScreen` 仍然不适用

理由在 §5，没有变化。它需要的只是 `InputBridge`，已经用上了。

