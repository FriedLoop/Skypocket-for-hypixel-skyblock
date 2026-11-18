# ADR-0002：`util/` 是零依赖层

**状态**：已采纳（2026-11）

## 背景

`config` 和 `render` **互相 import**，两个最底层的包无法单独理解。`ModConfig`（数据模型）import 了 `AntialiasingRenderer` 和 `ColorGradeRenderer`。

## 调查结论：大部分不是真耦合

| 引用 | 性质 |
|---|---|
| `ModConfig` → `AntialiasingRenderer` | **只在 javadoc 里出现**，代码引用数 0 |
| `ModConfig` → `ColorGradeRenderer` | 同上 |
| `ModConfig` → `ColorUtil` | 真实调用（11 个静态方法）|

## 决定

新建 `util/` 包，只放**不依赖本模组任何其他包**的类：`ColorUtil`、`Easing`、`PickerMemory`、`GuiMetrics`、`MouseButtons`。

`util/` 成为依赖图的最底层，零依赖。

## 一个分类失误及教训

`PanelGeometry` 看起来也是纯工具（无项目内 import），但实际引用 `PanelView` 和 `Feature`——**通过同包访问，所以不需要 import**。它留在了 `gui/`。

**教训**：一个类没有项目内 `import`，只能说明它没有跨包引用，不能说明它无依赖。

## 后果

`config → util`、`render → util`，循环消失。

**新增 util 类的判据**：把它单独放进空项目能编译，才算零依赖。

---
