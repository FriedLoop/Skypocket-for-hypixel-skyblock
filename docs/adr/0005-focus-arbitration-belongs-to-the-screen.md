# ADR-0005：焦点仲裁由屏幕层负责

**状态**：已采纳（2026-11）

## 背景

三个面板各有独立 `InputRouter`，**都能同时持有焦点**：

```
OneClickScreen
├─ modulesPage  (router A)  ← 可能有焦点
├─ moduleChrome (router B)  ← 可能有焦点
└─ statsPage    (router C)  ← 可能有焦点
```

`charTyped` 按固定顺序分发，侧栏排在前面，导致模块设置页的输入框永远抢不到字符（backspace 同理，"能打字但删不掉"）。

## 考虑过的替代方案

**在分发时按"最后点击区域"改优先级**：

否决。这是修症状——两个焦点共存的问题依然存在，只是让最后一个赢。而且 `keyDown`/`preedit` 也需要同样的逻辑。

**合并三个 router**：

否决。三个面板是独立模块，合并会让它们互相依赖。

## 决定

**点击时交接焦点，保证同时只有一个 router 持有焦点。**

```java
// OneClickScreen，在分发 pointerDown 之前
private void focusOnly(int region) {
    if (region != TEXT_REGION_MODULES && modulesPage != null) modulesPage.clearFocus();
    if (region != TEXT_REGION_CHROME && moduleChrome != null) moduleChrome.clearFocus();
    if (region != TEXT_REGION_SETTINGS && statsPage != null) statsPage.clearFocus();
}
```

## 为什么这个方案可行（之前以为要等拆分）

两个关键事实：

1. **三个区域的条件互斥**：`modulesPageActive()` 与 `moduleChromeActive()` 条件相反
2. **`pointerDown` 是焦点唯一入口**：键盘/文本事件发给"已持有焦点"的控件

所以不需要合并 router，只需在点击时交接。

## 为什么放在屏幕层

各面板无法互相协调——`SettingsPage` 不知道侧栏存在。只有屏幕能看到全部。

## 后果

**新增可聚焦模块时必须加进 `focusOnly`**，否则又会退化成"谁先被检查谁赢"。这条已写入 `UI-V2-CONTRACT.md` §4.3.1。

---
