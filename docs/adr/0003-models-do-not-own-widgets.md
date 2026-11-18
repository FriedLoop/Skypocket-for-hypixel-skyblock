# ADR-0003：数据模型不持有 UI 控件

**状态**：已采纳（2026-11）

## 背景

`Setting`（设置数据模型）持有：

```java
private final ImmediateTextField search = new ImmediateTextField(...);
public ImmediateTextField searchField() { return search; }
return expanded ? rowHeight() + ImmediateColorPicker.CONTENT_H : rowHeight();
```

## 调查结论：这些几乎都是死代码

| 项 | 实际问题 |
|---|---|
| `searchField()` | **零调用** |
| `search` 字段 | 只在 `toggle()` 里 `setText`/`blur` |
| `toggleExpanded()` / `isExpanded()` | **零调用**，整条展开链已死 |

v2 `Dropdown` 自带搜索框，`Setting` 里这个是旧实现残留。

## 决定

- 删除 `search` 字段、`searchField()`、展开链
- `CONTENT_H` 改为本地常量（两个取色器都是 `8*2+132`，数值不变）

## 保留的耦合

`V2CustomSetting.Host` 接口里的 `Box` / `Dropdown` / `WidgetPalette` **保留**——自定义行必须能接收 v2 容器。这是**接口契约**，不是实现泄漏。

## 判据

数据模型可以依赖**接口**，不能依赖**具体控件实例**。

---
