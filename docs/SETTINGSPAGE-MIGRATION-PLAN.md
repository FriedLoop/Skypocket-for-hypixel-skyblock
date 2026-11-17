# SettingsPage 迁移到 UiPage

P4 + 第四件任务的第一次实战。`SettingsPage` 是当前**唯一在生产使用**的 v2 页面（3 个调用方），迁移它同时验证框架的可用性。

---

## 一、现状对照

`SettingsPage`（705 行）与 `UiPage` 的字段和方法高度重叠：

| SettingsPage | UiPage | 说明 |
|---|---|---|
| `root` (Box) | `root` | 同名 |
| `input` (InputRouter) | `input` | 同名 |
| `pageCache` (Slot) | `cache` | 同名 |
| `renderRevision` | `renderRevision` | 同名 |
| `lastFocused` | `lastFocused` | 同名 |
| `renderScaleX/Y` | `renderScaleX/Y` | 同名 |
| `width` / `height` | `width` / `height` | 同名 |

**方法重叠**（10 项）：

| SettingsPage | 框架对应 |
|---|---|
| `layout(TextMeasurer)` | `UiPage.layout` |
| `draw(GuiGraphicsExtractor,...)` | `UiPage.draw` |
| `pageCacheKey()` | `UiPage.cacheKey()` |
| `drawBase()` | `UiPage.drawStaticLayer` |
| `drawDynamicControls()` | `UiPage.paintDynamicLayer` |
| `collectDynamic()` | `PaintPass.collectDynamic` |
| `drawElement()` ×2 | `PaintPass.paintStatic` |
| `intersects()` | `PaintPass.intersects` |
| `pointerDown/Move/Up` | `UiPage.pointer*` |
| `keyDown/Up` | `UiPage.key*` |

---

## 二、必须保留的公开 API

3 个调用方依赖这些，**签名不能变**：

```java
// 构造
SettingsPage(Feature, WidgetPalette, float width, float height)
SettingsPage(Feature, WidgetPalette, float width, float height, float columnWidth)

// 生命周期
void rebuild()
void layout(TextMeasurer)
void draw(GuiGraphicsExtractor, float scaleX, float scaleY, boolean animating)

// 输入（float 坐标，页面内已转换）
boolean pointerDown(float x, float y, int button, int modifiers)
boolean pointerMove(float x, float y, int modifiers)
boolean pointerUp(float x, float y, int button, int modifiers)
boolean wheel(float x, float y, float delta, int modifiers)
boolean keyDown(KeyEvent)     // MC 事件
boolean keyUp(KeyEvent)
boolean textInput(String)
boolean preedit(PreeditEvent)

// 状态查询
InputRouter input()
boolean isFocused()
float inputScaleX() / inputScaleY()
float contentHeight()
boolean hasOpenPopup()
void closePopups()
void clearFocus()
```

**注意**：现有的 `pointerDown(float,float,...)` 接收的是**已转换的设计坐标**（由 `OneClickScreen` 转换后传入）。迁移后保持这个签名，内部直接转给 `input`，不套 `PageTransform`。

---

## 三、迁移步骤

| 步 | 内容 | 验证 |
|---|---|---|
| 1 | `class SettingsPage extends UiPage` | 编译 |
| 2 | 删掉重复字段（`root`/`input`/`pageCache`/`renderRevision`/`lastFocused`/`renderScale`）| 编译 |
| 3 | 删掉 `layout`/`draw`/`pageCacheKey`/`drawBase`/`drawDynamicControls`/`collectDynamic`/`containsDynamic`/`drawElement`/`intersects` | 编译 |
| 4 | 实现 `build()`（原构造逻辑建树）| 编译 |
| 5 | 用 `trackPopup` 替换三类 popup 列表 | 编译 |
| 6 | 保留 `pointerXxx` 等公开方法，内部改为转发 | 编译 |
| 7 | 实机：模块设置页、HUD 设置卡、设置主标签页三处 | **必须** |

---

## 四、风险点

### 4.1 `width`/`height` 是 final 且构造时确定

`UiPage.bounds(w,h)` 是 setter，而 `SettingsPage` 的宽高在构造后不变。

**处理**：构造函数末尾调 `bounds(width, height)`。删掉 `final` 字段，改用继承的 `width()`/`height()`。

### 4.2 `customHost` 引用 `SettingsPage.this.rebuild()`

`UiPage.rebuild()` 是 `final`，不能覆写。

**处理**：`customHost` 直接调 `rebuild()`（继承的）。但 `SettingsPage.rebuild()` 当前还要清空 `colorFields`/`dropdowns`——这些列表迁移后由 `UiPage.popupOwners` 管理，需要确保重建时清空。

**`UiPage.trackPopup` 目前没有清空机制**，这是个缺口，要补 `clearPopups()`。

### 4.3 `columnWidth` 是业务参数

不属于框架，保留在 `SettingsPage`。

### 4.4 `hasOpenPopup()` / `closePopups()`

业务查询，保留。但要改为遍历 `popupOwners` 而非两个独立列表。

### 4.5 `contentHeight()`

被 `HudSettingsCard` 用，保留。

---

## 五、必须补的框架缺口

**`UiPage.trackPopup` 没有清空机制。** 重建页面时旧的 popup owner 会累积。

```java
// 需要补
protected final void clearPopups() {
    for (PopupOwner owner : popupOwners) {
        overlays.remove(owner.popup());
    }
    popupOwners.clear();
}

// rebuild() 里自动调用
public final void rebuild() {
    clearPopups();      // ← 新增
    root = build();
    ...
}
```

这个缺口在测试页没暴露，因为测试页的 popup owner 是固定两个（`testColor`/`testDropdown`），重建时 `trackPopup` 的 `contains` 检查挡住了重复。

`SettingsPage` 每次重建会创建**新的** ColorField/Dropdown 实例，不清理就会累积。

---

## 六、预期收益

| 项 | 迁移前 | 迁移后 |
|---|---|---|
| `SettingsPage` 行数 | 705 | **约 450** |
| 框架职责代码 | ~180 行 | **0** |
| 三类 popup 循环 | 3 段 | **0** |
| 手写绘制递归 | 40 行 | **0** |
| 手写缓存键 | 8 行 | **0** |

删掉的约 255 行全是框架职责。

---

## 七、不做的部分

`SettingsPage` 里这些是**真业务逻辑**，不迁移：

- `Feature` 依赖、`feature.settings()` 遍历
- 各 `Setting` 子类到控件的映射（`BooleanSetting` → `Switch` 等）
- `V2CustomSetting` 自定义行的构建
- 分组、header、subtitle 的排版
