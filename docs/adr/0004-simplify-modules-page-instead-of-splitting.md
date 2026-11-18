# ADR-0004：`ModulesPage` 简化而不拆分

**状态**：已采纳（2026-11）

## 背景

`ModulesPage`（669 行）用 `withCards` 布尔值控制两种形态：

- `withCards=true`：侧栏 + 卡片网格（首页）
- `withCards=false`：只有侧栏（模块设置页左侧）

两个实例在**同一帧的不同位置**绘制。

## 考虑过的替代方案

**拆成两个类**（`ModulesSidebarPage` + `ModulesGridPage`）：

否决。`OneClickScreen` 里有 **24 处**协调逻辑（13 处 `modulesPageActive` + 11 处 `moduleChromeActive`），拆分后要判断"哪个类的哪个方法"，协调面只会更大。这与"减少歧义"的目标相反。

**套进 `UiPage`**：

否决。需要 4-5 个覆写钩子，框架会对每个简单页面都更难理解。

## 决定

**认清它只是一个布局，不是页面。**

`rebuild()` 里所有组件都是**绝对定位的并列节点**，彼此无布局耦合：

```java
sidebar      → ABSOLUTE, left:0, 全高
search       → ABSOLUTE, 顶部
back/forward → ABSOLUTE, 顶部
grid+scroll  → ABSOLUTE, left:SIDEBAR_W   ← 只有 withCards 时才有
```

所以 `withCards` 的真实语义就是**构建时是否包含卡片区**。改为只在构建时生效，绘制路径从 2 条合并为 1 条。

## 顺带发现的过时注释

原注释说"两个纹理竞争 cache target"。时间线：

```
63b1f5d  引入 withCards 绕过缓存（注释描述纹理竞争）
4884dce  给每个页面独立 Slot        ← 3 小时后
```

`4884dce` 正是为解决那个冲突做的，但绕过逻辑和注释没清理。实验验证：让 `moduleChrome` 也走缓存，一切正常。

## 后果

`withCards` 使用点从 6 处降到 3 处，`draw()` 只有一条路径。

**代价**：`drawElement` / `intersects` / `pageCacheKey` 三处仍与 `PaintPass` 重复。统一的前提是把它改造成标准页面形态，属独立工作。

---
