# ADR-0001：依赖方向由「是否依赖业务」改为「箭头只能向下」

**状态**：已采纳（2026-11）

## 背景

`ui/` 是"与业务无关的 UI 内核"，`gui/` 是 ClickGUI 具体实现。这条分层写在文档里，但从没被检查过。

用 `import` 语句画出真实依赖图后，发现：

```
ui     -> gui, config, render      ← 最底层依赖了所有上层
gui    -> ui, ...
config -> render
render -> config                   ← 互相 import
```

三处具体违规：

| 位置 | 问题 |
|---|---|
| `ui/UiPage` → `gui/InputKeys` | **循环**：`gui` 也依赖 `ui` |
| `ui/UiPage` → `config/ConfigManager` | 内核知道配置系统的存在 |
| `ui/*` → `render/*`（8 处） | 内核直接用渲染实现 |

## 考虑过的替代方案

**按职责划分**："`ui` 负责交互与状态，`gui` 负责图形化实现"。

**否决原因**：`ui/mc/McUiPainter` 本身就是图形实现，按这个标准它应该搬到 `gui/`，但那样 `ui` 就无法描述"如何绘制"了。职责标签无法决定一个类属于哪层。

## 决定

**按依赖方向分层，箭头只能向下：**

```
业务（feature / hud / crosshair）
    ↓
gui（具体界面与主题）
    ↓
ui（内核，无业务依赖）
    ↓
ui/mc（平台绑定）
    ↓
util（零依赖）
```

具体做法：

- `InputKeys` → `ui/input/`（它是平台键码到内核键码的适配器，逻辑上属于内核）
- `UiPage.revision()` 改为**抽象方法**（缓存键需要版本号，但内核不该知道它从哪来）
- `CrosshairGrid`/`CrosshairPreview` → `crosshair/`（依赖 `ModConfig`，被 7 个业务类使用）
- 抽 `RenderCache` / `UiText` 接口，实现放 `ui/mc/`

## 后果

`ui/` 现在只依赖 `render`，且仅限 `ui/mc/` 内的 4 处。

**这条规则现在有编译期强制**：`build.gradle.kts` 的 `uiKernelSources` 白名单只编译无 MC 依赖的类，新增的 MC 依赖会让测试编译失败并指出类名。

**代价**：`UiText` 需要启动时安装（`UiText.install`），因为字体栈全局唯一，穿过 6 个控件构造函数会带来大量噪音。

---
