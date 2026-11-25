# 重复能力回收评估

第三件事：把散落在各页面、各已实现若干遍的能力收回到框架里，让页面只**调用**或 **override**，不再各自重写。

本文是**评估**，先列清"重复了什么、重复了几份、收回的代价"，再决定做哪些。

---

## 一、盘点结果

### 1.1 手工绘制递归 —— 重复 2 份（另有 1 份已成框架）

| 文件 | 行数 | 现状 |
|---|---|---|
| `SettingsPage.drawElement` ×2 重载 | 40 | **已被 `PaintPass` 完全覆盖** |
| `ModulesPage.drawElement` ×2 重载 | ~40 | 同上 |

`PaintPass.paintStatic` 的行为与它们逐行等价（同样 translate offset、同样 ScrollView 裁剪、同样 Painted/Text 分支、同样 viewport 剔除）。

**收益**：删 ~80 行
**风险**：低（纯等价替换）
**需实机**：是（两个页面各看一眼）

### 1.2 手工缓存键 —— 重复 4 份

| 文件 | 复杂度 | 说明 |
|---|---|---|
| `UiPage.cacheKey` | 简单 | 框架版：identity + renderRevision + scroll + configRevision |
| `SettingsPage.pageCacheKey` | 简单 | 与框架版等价 |
| `ModulesPage.pageCacheKey` | **复杂** | 除常规项外还混入 `hoverHash()` |
| ~~`OneClickScreen.shellCacheKey`~~ | — | **已删除**：它随 `shellCache` 一起移除，见 `DEVELOPMENT.md` 的外壳说明 |

**已查明 `ModulesPage` 为什么复杂**（原先的猜测是错的）：

```java
private long pageCacheKey() {
    long hash = System.identityHashCode(this) * 1_000_003L;
    hash = hash * 31 + renderRevision;
    hash = hash * 31 + Float.floatToIntBits(scroll == null ? 0f : scroll.scrollY());
    hash = hash * 31 + host.search().hashCode();
    hash = hash * 31 + (host.selected() == null ? 0 : host.selected().hashCode());
    hash = hash * 31 + ConfigManager.revision();
    hash = hash * 31 + (host.canGoBack() ? 1 : 0);
    hash = hash * 31 + (host.canGoForward() ? 1 : 0);
    hash = hash * 31 + Math.round(highlightY);
    hash = hash * 31 + hoverHash();                    // ← 关键
    hash = hash * 31 + (input.focused() == null ? 0 : input.focused().hashCode());
    return hash;
}

private long hoverHash() {
    // 遍历所有卡片/侧栏行/导航按钮/搜索框，把"处于 hover 或 pressed"的控件身份混进去
}
```

**`hoverHash()` 是一个框架级缺口的手工补偿。**

静态层是用 `neutralPaint` 烘焙的，而 `Control.background()` 和 `Button.paint` 在 `neutralPaint` 下**故意不画 hover**——这是对的，否则一帧的悬停会被冻进纹理。但代价是：**hover 状态改变时，静态层不会失效**。

`ModulesPage` 的解法是每次绘制前遍历整棵相关控件，把 hovered 的身份混进缓存键。这个方法：
- 正确（hover 变化 → 键变化 → 重烘）
- 但**每个需要 hover 的页面都要自己写一遍**
- 而且遍历成本随控件数增长

**这正是我的测试页菜单项没有高亮的深层原因**：我只是补了 `mouseMoved`（让 hover 状态被更新），但静态层不会因为 hover 变化而重烘。当时我以为补 `mouseMoved` 就够了，实际只解决了一半——菜单弹窗是 overlay（走 `PaintPass.paintStatic` 且**不**包 `neutralPaint`），所以能显示；而页面内的控件仍然不会有 hover 反馈。

### 1.3 手工浮层注册 —— 重复 3 份

| 位置 | 段数 |
|---|---|
| `SettingsPage` | 3 段（colorFields / dropdowns / customDropdowns）|
| `DevUiTestScreen` | 2 段（colorFields / dropdowns）|
| `OverlayHost` | 框架版 |

三段同构：`layoutPopup` → `isOpen` → `addLayer` / `removeLayer`。

**收益**：`SettingsPage` 删 ~30 行，`DevUiTestScreen` 删 ~20 行
**风险**：中（`SettingsPage` 有三类浮层，其中 `customDropdowns` 有特殊来源）
**需实机**：是

### 1.4 输入桥接 —— 每个宿主手工构造事件

| 宿主 | 手工构造 | 说明 |
|---|---|---|
| `OneClickScreen` | PointerEvent / KeyEvent / WheelEvent / TextInputEvent | 还要 `canonicalKey` 转换 |
| `DevUiTestScreen` | 同上 + `toDesignX/Y` 坐标转换 | |
| `ModulesPage` | `pointerDown(float x, float y, int modifiers)` | 更简洁的桥 |

**这是最有价值的一项**：测试页里我漏了 `mouseMoved`、用错键码，都是因为**每个宿主都要手写这套桥接**。

如果框架提供 `UiPage` 的事件方法，这些错误不可能发生。

### 1.5 样式系统 —— 已评估过，不收回

`GuiTheme → ClickGuiStyle → Ui2Theme → WidgetPalette` 是**单向分层**，依赖方向正确。

`ClickGuiStyle` 的职责过多（数据 + 绘制 + 运算）是真实问题，但拆它会动 38 个调用点，风险大于收益。**本轮不做。**

---

## 二、优先级与方案

### P1：hover 进入动态层（框架缺口，最高价值）— 已实现

**问题**：静态层用 `neutralPaint` 烘焙，控件在那种模式下不画 hover（`Button.paint` 的判断是 `hovered && !pressed && !neutralPaint()`）。于是页面内的控件**没有任何持久的悬停反馈**。

**我最初的方案是错的**，实现时推翻了：

> ~~`UiPage` 记录当前 hovered，把它折进缓存键~~（照 `ModulesPage.hoverHash()` 的思路）

**为什么错**：折进缓存键会让 hover 出现，但代价是**每次 hover 变化都全页重烘**——鼠标划过 10 个控件就是 10 次 40 控件的重绘。而且它和动态层机制重复：动态层本来就是为"不改变配置但外观变化"的控件准备的。

更关键的是，**它只对了一半**。实测的失败模式是：

```
hover 进入帧：cacheKey 变 → 未命中 → 走 paintStatic（无 neutralPaint）→ 画出 hover ✓
下一帧：     cacheKey 不变 → 命中 → 只画动态层 → 动态层不含它 → hover 消失 ✗
```

结果就是**高亮闪一下然后消失**。

**最终方案**：`PaintPass.collectDynamic` 加入 `isHovered`，让 hovered 控件**每帧进动态层重绘**；`UiPage` 不需要做任何缓存失效。

```java
if (element instanceof Control control
        && (control.dynamicOverlay() || control.isFocused() || control.isPressed()
        || control.flashActive()
        || control.isHovered()          // ← 新增
        || ...)) {
    dynamic.add(control);
}
```

**收益**：
- 每个页面自动获得 hover 反馈，不需要手写 `hoverHash()`
- 只重绘真正变化的那一个控件，不是全页
- `ModulesPage.hoverHash()`（约 25 行）可以删除

**`ModulesPage` 为什么当初没这么做**：它有动态层机制（`collectDynamic` 是它先有的），但 `hoverHash` 和动态层是两套并行的思路——前者靠失效缓存，后者靠重绘控件。框架现在统一到后者。

### P2：输入桥接收进 `UiPage`

**问题**：每个宿主手写 5 个事件方法 + 键码转换 + 坐标转换。我的测试页因为漏了 `mouseMoved` 导致 hover 状态不更新，因为用错 `Keys.ESCAPE` 导致 ESC 和 backspace 失效。

**方案**：

```java
// UiPage 新增，自动做坐标与键码转换
public final void pointerDown(double screenX, double screenY, int modifiers);
public final void pointerMove(double screenX, double screenY, int modifiers);
public final void pointerUp(double screenX, double screenY, int modifiers);
public final void mouseMoved(double screenX, double screenY);   // ← 不再可能漏
public final void wheel(double screenX, double screenY, double amount, int modifiers);
public final void keyDown(net.minecraft.client.input.KeyEvent event);   // 内部 InputKeys.canonical
public final void textInput(net.minecraft.client.input.CharacterEvent event);

// 坐标转换由宿主设置一次
public final void transform(Transform transform);
```

**收益**：
- 宿主不再碰 `InputKeys` / `canonical` / 坐标换算
- `mouseMoved` 成为框架的一部分，不可能漏
- 键码错误不可能发生

**代价**：改 3 个宿主

### P3：浮层托管收进 `UiPage`

**问题**：`SettingsPage` 有三段同构循环，测试页有两段，都是 `layoutPopup` → `isOpen` → `addLayer`。

**方案**：抽 `PopupOwner` 接口，`ColorField` / `Dropdown` 实现它：

```java
public interface PopupOwner {
    UiElement popup();
    boolean isOpen();
    void layoutPopup(TextMeasurer measurer, float viewWidth, float viewHeight);
    default void syncBeforePaint() {}
}
```

`UiPage` 提供 `trackPopup(PopupOwner)`，自动完成注册、布局、同步。

**收益**：`SettingsPage` 删 ~30 行，测试页删 ~25 行
**风险**：中（要改两个组件类声明 + 三个调用方）

### P4：绘制递归替换（低风险收尾）

把 `SettingsPage.drawElement` 和 `ModulesPage.drawElement` 换成 `PaintPass.paintStatic`。

**收益**：删 ~80 行
**风险**：低（行为等价）

---

## 三、执行结果与 ModulesPage 的结论

### 已完成

| 项 | 结果 |
|---|---|
| P1 hover 进动态层 | ✅ 删除 `ModulesPage` 之外所有手写 hover 补偿的思路 |
| P2 PopupOwner + trackPopup | ✅ 测试页零 popup 代码；`SettingsPage` 三类循环合并为一处注册 |
| P3 输入桥接 | ✅ 测试页零事件构造、零 `InputKeys` 引用 |
| P4 绘制递归 | ✅ `SettingsPage` 已用 `PaintPass`，删 40 行 |
| `SettingsPage` 迁移 | ✅ 704 → 512 行（-27%），8 个框架方法归零 |

### `ModulesPage` 评估结论：不迁移

查清后发现它**不是标准页面，是复合页面**，与 `UiPage` 的假设有 6 处冲突：

1. **两条绘制路径**：`withCards` 为真时缓存、为假时完全绕过缓存（因为同一帧里 `SettingsPage` 也在用全局缓存，两张纹理争抢缓存目标会导致侧栏被 blit 进设置面板）。
2. **高亮动画在缓存外单独绘制**：`drawHighlight` 画的是侧栏选中指示条的缓动动画，必须每帧更新。
3. **卡片区独立裁剪 + 水平位移**：`cardOffsetX` 和独立的 scissor 矩形。
4. **侧栏与卡片分开绘制**：为了不让 `scroll` 参与侧栏那一次递归，临时 `scroll.visible(false)` 再恢复。
5. **缓存键含导航状态**：`canGoBack`/`canGoForward`/`highlightY`。
6. **hoverHash**：为弥补静态层不含 hover 而做的全量扫描。

要把这些塞进 `UiPage`，需要加 4-5 个覆写钩子，框架会因此变复杂。**收益递减，不做。**

### 但 ModulesPage 的 `hoverHash` 可以删——需要先补动态层

`hoverHash()` 的作用是"hover 变化时让缓存失效"。框架的替代方案是"动态层每帧重绘 hovered 控件"，两者目的相同、手段不同。

**但 `ModulesPage` 不从 `UiPage` 继承**，所以享受不到那个机制。我尝试给它手工补动态层，发现 `paintDynamic` 会直接递归 `root`，而 `drawBase` 对 `scroll` 做了临时隐藏和独立裁剪——动态层不会做这些，画出来会错位。

**这正是"复合页面不该套标准框架"的证据**：动态层机制依赖了 `drawBase` 的绘制顺序假设，`ModulesPage` 不满足。

**结论**：`hoverHash` 保留，直到 `ModulesPage` 被重构为标准页面（那是独立的一件事）。

---

## 四、建议的后续顺序

| 步骤 | 内容 | 风险 | 可独立验证 |
|---|---|---|---|
| 1 | `UiPage` 记录 hovered 并折进缓存键 | 低 | 是（测试页加个带 hover 的按钮）|
| 2 | `ModulesPage` 删 `hoverHash()`，改用框架机制 | 低 | 是 |
| 3 | `PopupOwner` 接口 + `UiPage.trackPopup` | 中 | 是（测试页 + 设置页）|
| 4 | `UiPage` 输入桥接（含 `mouseMoved`）| 低 | 是 |
| 5 | `DevUiTestScreen` 简化（用它验证框架是否真的好用）| 低 | 是 |
| 6 | `SettingsPage` 迁移到 `UiPage` | 中 | 是 |
| 7 | `ModulesPage` 迁移到 `UiPage` | 中 | 是 |

**第 5 步是自检**：如果测试页迁移后代码明显变短、且不再需要手写任何框架逻辑（不碰 `InputKeys`、不写 `layoutPopup`、不处理 hover），说明回收成功。

---

## 五、不做的事

| 项 | 理由 |
|---|---|
| 拆 `ClickGuiStyle` 的三种职责 | 38 个调用点，风险大于收益 |
| 统一 `GuiTheme` / `WidgetPalette` | 依赖方向健康，是数据源与消费格式的区别 |
| 动 `OneClickScreen` 的 shell 缓存 | 它是壳层而非页面，缓存粒度不同（含鼠标位置，用于 hover 反馈）|

---

## 六、这次评估纠正的三个误判

记录在此，因为它们说明了"先查清再动手"的必要性。

**误判一**：我以为 `ModulesPage.pageCacheKey` 里的 identity 混入是"防御式冗余"。
实际是 `hoverHash()`，一个针对框架缺口的补偿。按我原来的判断直接删掉，会让整个模块页失去 hover 反馈。

**误判二**：我以为测试页菜单项无高亮只是"漏了 `mouseMoved`"。
补上后菜单弹窗确实有了高亮，但那是因为 overlay 不走 `neutralPaint`。**页面内的控件仍然没有 hover 反馈**。

**误判三（方案的，不是诊断的）**：我给 P1 提的解法是"把 hovered 折进缓存键"——照抄 `hoverHash()` 的思路。
实测失败：高亮闪一帧就消失。因为缓存失效只在那一帧让页面走非 `neutralPaint` 路径，下一帧缓存命中就又把它盖掉了。

正确解法是**进动态层**，不是失效缓存。两者的区别不只是性能（一个重绘 1 个控件，一个重绘全页），而是**根本的正确性**：静态层被设计成不含 hover，那么 hover 就只能由动态层提供。

这条修正的教训是：**照抄现有实现未必正确**——`hoverHash()` 是在没有别的机制可选时的权宜之计，框架补齐动态层后它就该退役，而不是被推广。

---

## 七、P1 实施暴露的一个通用陷阱

修 bug 时要**搜索同类模式**，而不是只看报错那行。

这轮同一个错误犯了两次：

```java
// UiPage.cacheKey —— 先发现
if (root instanceof ScrollView scroll) { ... }   // root 是 Box，永远 false

// UiPage.layout —— 后才发现
if (root instanceof ScrollView scroll) { ... }   // 同一份错误
```

修第一处时没有全局搜 `instanceof ScrollView`，导致滚动失效多存在了一轮。

同样，`PaintPass.intersects` 的坐标系错配也是同一类问题：我从 `SettingsPage` 提取逻辑时**凭理解重写**，而不是逐行对照，于是丢了 `visualX/Y`。

**规则**：修完一个 bug，用 `grep` 搜同类代码结构；提取逻辑时逐行对照原实现。
