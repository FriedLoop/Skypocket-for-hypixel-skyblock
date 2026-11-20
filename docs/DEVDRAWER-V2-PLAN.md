# DevDrawer v2 重写计划

`DevDrawer`（747 行）是最后一块纯即时模式的大组件。它之前迁移失败过三轮，全部是同一个原因：**没有先搞清楚 v2 的契约就动手**。这次先写清楚再写代码。

---

## 一、现状：它是什么

**主题编辑器抽屉**，挂在 ClickGUI 上，实时编辑调色板。

```
┌─ 主题 · Dark ─────────────── [保存][还原][×] ─┐   TITLE_H = 28
├─ ‹  Dark  ›  [+]  [-]  ─────────────────────┤   THEME_ROW = 26
│                                              │
│  [选中 token 的编辑器]                        │
│    · 颜色 token → SV 方块 + 色相/透明条       │
│                  + hex/R/G/B/A 输入框         │
│    · 数值 token → 一个滑块                    │   METRIC_EDITOR_H = 44
│                                              │
│  [token 说明文字，最多 2 行]                   │
│                                              │
├──────────────────────────────────────────────┤
│  card                                        │   ROW = 24
│  button                             [色块]   │   每行一个 token
│  accent                             [色块]   │
│  ...（可滚动，带滚动条）                      │
│                                              │
│  [状态提示，3 秒后消失]                       │
└──────────────────────────────────────────────┘
  WIDTH = 340, PAD = 12
```

**关键行为**：
1. 打开时快照整个调色板，关闭未保存则还原
2. 点 token → 选中并显示对应编辑器
3. 色块点击 → 颜色立即应用到实时调色板
4. 主题行可切换/新建/删除主题
5. 删除需二次确认（覆盖整个抽屉）
6. 抽屉可拖动

---

## 二、v2 契约的对应关系

从我之前的失败里总结的检查表（`UI-V2-CONTRACT.md` §8）：

| 契约 | 本次要点 |
|---|---|
| **坐标** | `bounds()` 是父局部；传入 router 的是 design 坐标 |
| **offset** | 只有根和浮层用；流式子元素不设 |
| **绘制递归** | 用 `PaintPass.paintStatic`，不自写 |
| **背景** | 需要背景用 `Card`，`Box` 画不出 |
| **Text** | 不是 `Painted`，`PaintPass` 已处理 |
| **浮层** | 取色器是浮层还是内嵌？**见下方决策** |
| **缓存键** | 抽屉是实时编辑，**不缓存** |

### 决策 1：取色器内嵌，不做浮层

`ColorField` 的取色器是浮层（点击行后弹出）。但 `DevDrawer` 里取色器**常驻显示**在选中 token 下方。

**结论**：用 `ColorPickerPanel`（v2 的那个）直接内嵌在树里，不用 `ColorField`。

**陷阱**：`ColorPickerPanel` **只是半边**——hex/R/G/B/A 输入框由 `ColorField` 创建。所以我要自己创建这些 `TextField` 并绝对定位。

### 决策 2：不接 `UiPage`，用 `UiPage` 的组件但不继承

抽屉不是"页面"，它是叠加在 ClickGUI 上的浮层。`UiPage` 假设"一个页面 = 一棵树 + 一个缓存 + 一次绘制"。

**结论**：自己管理树 + `LayoutEngine` + `InputRouter` + `PaintPass`，但**不继承 `UiPage`**。

理由与 ADR-0004（`ModulesPage` 不套 `UiPage`）相同。

### 决策 3：不缓存

主题编辑是实时预览，每秒都在变。缓存只会带来失效管理的复杂度。

**结论**：每次 `draw` 都走 `PaintPass.paintStatic`，不接 `GuiPanelCache`。

---

## 三、分层设计

```
DevDrawer (保留对外接口)
├─ 状态：open/selected/themes/scroll/拖拽...
├─ 树构建：buildTree()
├─ 布局：LayoutEngine.layout(root, WIDTH, height, measurer)
├─ 输入：InputRouter + 各种事件转发
└─ 绘制：PaintPass.paintStatic(painter, root)
```

### 复用清单

| 需要的东西 | 用什么 |
|---|---|
| 标题栏 | `Box` + `Text` |
| 保存/还原/关闭按钮 | `Button`（MENU 模式）|
| 主题切换行 | `Button` × 4 + `Text` |
| token 列表 | `ScrollView` + 每行一个 `Button`（MENU 模式）|
| 颜色编辑器 | `ColorPickerPanel` + 自建 `TextField` × 5 |
| 数值编辑器 | `Slider` |
| 说明文字 | `Text`（`PaintPass` 处理）|
| 删除确认 | `Card` + `Button` × 2，绝对定位覆盖 |
| 滚动条 | `ScrollView` 内置 |

**这些组件全都有，不需要新写。**

---

## 四、实施步骤

每步都必须编译通过，且可以独立验证。

| 步 | 内容 | 验证 |
|---|---|---|
| 1 | 骨架：树 + 布局 + 绘制，标题栏 + 主题行 | 抽屉出现，位置正确，有背景 |
| 2 | token 列表（`ScrollView` + 按钮）| 列表可滚动，可点击选中 |
| 3 | 颜色编辑器（`ColorPickerPanel` + 5 个 `TextField`）| hex/RGBA 可输入，色块可拖 |
| 4 | 数值编辑器（`Slider`）| 滑块可调 |
| 5 | 删除确认层 | 点删除后出现覆盖层 |
| 6 | 拖拽移动 + 关闭时还原快照 | 拖动正常，未保存关闭会还原 |
| 7 | 键盘：ESC 关闭、Ctrl+S 保存 | |

**每步之后：构建 → 部署 → 你实机确认。**

---

## 五、必须提前确认的风险

### 风险 1：`ColorPickerPanel` 的尺寸与内嵌

它的 `CONTENT_W`/`CONTENT_H` 是固定值（309×148）。抽屉宽 340，减去两侧 padding 24 = 316，**刚好放得下**。

但它内部用的是**绝对定位的固定坐标**，需要确认它在非浮层场景下也能正确布局。

### 风险 2：`TextField` × 5 的绝对定位

`ColorField` 里是这样的：

```java
new TextField(..., Style.builder()
    .position(Style.POSITION.ABSOLUTE)
    .left(ColorPickerPanel.FIELD_X)
    .top(y)
    .width(BASE_W)...
```

我要照抄这个模式，**不要自己算坐标**。

### 风险 3：滚动 + 点击的坐标

`ScrollView` 内的按钮，点击时 `InputRouter` 会用 `ancestorOffsetX/Y` 转换。之前 `TextField` 就栽在漏了这一步。**`Button` 走的是 `Control`，应该没问题，但要验证。**

### 风险 4：实时预览 + 无缓存

点色块立即改 `ThemeStore`，抽屉和 ClickGUI 都要跟着变。`ThemeStore` 有自己的失效机制吗？需要确认。

---

## 六、不做的事

- **不改 `ThemeStore`**：它是数据层，与绘制无关
- **不改 `ClickGuiStyle`**：38 个 token 的读取方，风险大于收益
- **不删 `ImmediateColorPicker`/`ImmediateTextField`**（第一次提交不删）：等抽屉跑通且稳定后再删，保留回滚余地
- **不加缓存**：实时编辑不值得

---

## 七、与之前三轮失败的区别

| 之前 | 这次 |
|---|---|
| 直接动手写 | 先读完整实现 + 写计划 |
| 用 `Style.left/top` 想定位 root | 知道 root 总在原点，用 `offset` |
| 每节点 `translate(offset)` | 知道 `bounds` 已是父局部 |
| 单独用 `ColorPickerPanel` 当取色器 | 知道它缺输入框，要自己补 |
| 自写绘制递归 | 用 `PaintPass.paintStatic` |

**这次的成功标准**：不只是"能跑"，而是"代码结构符合 v2 契约"，否则等于又造了一个技术债。
