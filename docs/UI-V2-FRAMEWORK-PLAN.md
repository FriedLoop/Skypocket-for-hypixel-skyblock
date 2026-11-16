# UI v2 框架层设计

本文说明第二件事要补什么、为什么这样补。契约文档（`UI-V2-CONTRACT.md`）记录**现状**，本文记录**补完后的目标**。

---

## 1. 现状：缺少的四块

`SettingsPage`（661 行）是唯一把 v2 组件真正用起来的页面。它自己实现了四件事，**这些应该属于框架**：

| 现在在 `SettingsPage` 里 | 行数 | 问题 |
|---|---|---|
| `drawElement` 递归 | 40 | 只此一份；新页面要复制 |
| `pageCacheKey` | 8 | 键的组成靠记忆 |
| `drawBase` + `drawDynamicControls` | 50 | 静态层/动态层分离逻辑 |
| 三段 popup 绘制循环 | 19 | **同构代码重复三次** |

`OneClickScreen`（1225 行）和 `HudPositionEditorScreen`（1175 行）没有用这些，各自实现。

---

## 2. 目标：一个页面基类 + 一个浮层宿主

### 2.1 `UiPage` —— 页面基类

```java
public abstract class UiPage {
    // 宿主设置
    public final void bounds(float width, float height, float scaleX, float scaleY);

    // 生命周期
    public void layout(TextMeasurer measurer);      // 调 LayoutEngine + overlays
    public void draw(GuiGraphicsExtractor graphics); // 缓存 + 静态/动态 + overlays
    public InputRouter input();

    // 子类实现
    protected abstract UiElement build();            // 构建树（或返回已建好的 root）
    protected long contentRevision();                // 内容变更计数（默认 ConfigManager.revision()）

    // 子类可覆盖
    protected void onRebuild() {}
}
```

职责：

1. **布局**：`LayoutEngine.layout(root, w, h, measurer)` + 浮层布局
2. **绘制**：缓存命中判断 → 静态层烘焙 或 blit → 动态层 → 浮层
3. **缓存键**：`identity + contentRevision + scroll + ConfigManager.revision()`
4. **输入**：暴露 `InputRouter`

**子类不再需要写任何绘制/缓存代码。**

### 2.2 `OverlayHost` —— 浮层宿主

```java
public final class OverlayHost {
    void attach(InputRouter input);
    void add(UiElement overlay);        // 注册到 input.layers
    void remove(UiElement overlay);
    boolean isEmpty();
    List<UiElement> overlays();

    void layout(TextMeasurer measurer); // 按 Style.position/left/top 布局
    void paint(McUiPainter painter);    // 按 z 序绘制
}
```

**关键点**：浮层的定位用 `Style.position(ABSOLUTE)` + `left/top`，**依靠 `LayoutEngine.layoutAbsoluteChildren`**——它在父内容盒内定位。

但浮层需要的是**屏幕/页面级绝对定位**，不是父内容盒。所以 `OverlayHost` 的做法是：

```java
// 把 overlay 的 offset 设为它在页面里的位置
LayoutEngine.layout(overlay, w, h, measurer);
overlay.offset(style.left().resolve(0), style.top().resolve(0));
```

这样浮层的 `left/top` 是**相对页面原点**的，符合直觉。

### 2.3 统一绘制递归

```java
// ui/PaintPass.java
public final class PaintPass {
    public static void paint(UiPainter painter, UiElement element);
    public static void paintDynamic(McUiPainter painter, UiElement element, Set<UiElement> dynamic);
    public static Set<UiElement> collectDynamic(UiElement root);
}
```

替代 `SettingsPage` 里那 90 行私有方法。

---

## 3. 回收重复：三段 popup 循环

现状（`SettingsPage.draw`）：

```java
for (ColorField field : colorFields) { if (field.isOpen()) drawElement(painter, field.popup()); }
for (Dropdown dropdown : dropdowns)   { if (dropdown.isOpen()) drawElement(painter, dropdown.popup()); }
for (Dropdown dropdown : customDropdowns) { if (dropdown.isOpen()) drawElement(painter, dropdown.popup()); }
```

三段同构，只是列表不同。`OverlayHost` 统一后变成：

```java
overlays.paint(painter);   // 一行
```

**这是第三件事（回收重复能力）的第一个受益点。**

---

## 4. 实现顺序

| 步骤 | 内容 | 验证方式 |
|---|---|---|
| 1 | `PaintPass`（提取绘制递归） | 编译通过；`SettingsPage` 改用它后行为不变 |
| 2 | `OverlayHost`（浮层三段合一） | 同上 |
| 3 | `UiPage`（页面基类） | `SettingsPage` 继承它，删除自己的绘制代码 |
| 4 | 测试页 | 独立屏幕，验证全部能力 + 复刻原 ClickGUI 样式 |

---

## 5. 测试页的设计

要求：**验证 v2 能力完整 + 能完整复刻原 ClickGUI 样式**。

放在 `gui/DevUiTestScreen.java`（开发用，通过调试键打开）。

内容分四块：

### 5.1 布局能力

- ROW / COLUMN
- gap / padding / margin
- flexGrow / flexShrink / flexBasis
- justify 五种 / alignItems 四种
- 百分比尺寸
- **绝对定位**（left/right/top/bottom 双向锚定）

### 5.2 组件清单

- `Button`（CARD / MENU 三种模式）
- `Switch`
- `Slider`
- `Segmented`
- `TextField`（含 IME preedit）
- `Dropdown`（含搜索）
- `ColorField`（含浮层取色器）
- `Card`（含阴影）
- `ScrollView` + `Scrollbar`

### 5.3 样式复刻

用 `ClickGuiStyle` 的全部 38 个 token 渲染色板，与真实面板并排对比：

```
┌─ 左：v2 渲染 ─┐  ┌─ 右：即时模式渲染 ─┐
│  card         │  │  ClickGuiStyle.card()│
│  button       │  │  ...                 │
└───────────────┘  └──────────────────────┘
```

**逐色对比，视觉上必须一致。**

### 5.4 交互验证

- 点击/拖拽/滚轮
- Tab 焦点导航
- 浮层：Dropdown 展开、取色器展开
- 缓存：静态层命中、动态层重绘（用帧计数显示）

---

## 6. 风险

| 风险 | 缓解 |
|---|---|
| `UiPage` 抽象过早，实际页面不匹配 | 先用 `SettingsPage` 验证，它是最大的 v2 使用方 |
| 缓存键漏项 | 从现有 `pageCacheKey` 逐项复制，不重新设计 |
| 浮层定位语义 | 测试页显式验证四种锚定情况 |
| 改动破坏现有面板 | 每一步编译 + 部署 + 实机验证 |

---

## 7. 不做的事

- **不重写 `LayoutEngine`**：它已完整（含绝对定位）
- **不重写 `InputRouter`**：它已完整
- **不重写组件**：14 个 v2 组件都已实机验证
- **不动 `ClickGuiStyle`**：样式系统依赖方向正确，只在测试页做对比

只补**页面/浮层/绘制/缓存**这四块缺失的框架能力。
