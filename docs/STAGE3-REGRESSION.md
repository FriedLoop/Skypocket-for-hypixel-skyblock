# 阶段 3 回归清单：OneClickScreen 迁移到 UiHost

> 迁移前先写清要验证什么。`OneClickScreen` 是 1530 行、9 个输入方法、331 行转发逻辑，
> 全部输入都会经过新代码。**任何一项失败都必须回滚，不能带病提交。**

## 迁移前的层结构

| 层 | 类型 | 判定 | 当前坐标转换 |
|---|---|---|---|
| `devDrawer` | `DevDrawer` | `isOpen()` | 自己做（屏幕坐标）|
| `topBar` | `TopBarControls` | 恒开 | 自己（面板坐标）|
| `modulesPage` | `ModulesPage` | `viewingFeature == null` | 宿主减 `TAB_BAR_H` |
| `moduleChrome` | `ModulesPage` | `viewingFeature != null && transition 完成` | 宿主减 `TAB_BAR_H` |
| `statsPage` | `SettingsPage` | 有 feature 且有设置 | 宿主 `statsLocalX/Y` |

## 需要新建的 Layer 适配器

| 适配器 | 包装 | 关键点 |
|---|---|---|
| `DrawerLayer` | `DevDrawer` | 自己判断 `isOpen()`，坐标是屏幕坐标需转换 |
| `TopBarLayer` | `TopBarControls` | 恒开，bounds = (0,0,PANEL_W,TAB_BAR_H) |
| `PageLayer` | `ModulesPage` | bounds 由宿主决定，可能有 `withCards` 变体 |
| `SettingsLayer` | `SettingsPage` | 需要处理 `statsPageDrawLeft/Top` 的整数对齐 |

**`StatsPage` 有一个特殊点**：它的绘制原点被 `Math.round` 对齐到设备像素网格
（`statsPageDrawLeft()`），所以 `boundsOnScreen()` 必须返回**对齐后**的值，
否则命中与绘制会差 1px。

---

## 回归清单（按风险排序）

### A. 基础交互（全部走新分发）

- [ ] A1 首页：点击卡片 → 进入模块
- [ ] A2 首页：点击侧栏分类 → 过滤卡片
- [ ] A3 首页：点击侧栏展开箭头 → 展开/收起
- [ ] A4 首页：卡片 hover 高亮
- [ ] A5 首页：侧栏行 hover 高亮
- [ ] A6 首页：滚动卡片列表
- [ ] A7 首页：搜索框输入 → 卡片实时过滤
- [ ] A8 首页：← → 导航按钮

### B. 页面切换

- [ ] B1 首页 → 模块页（点卡片），动画正常
- [ ] B2 模块页 → 首页（← 返回），动画正常
- [ ] B3 **动画进行中**点击无响应（不应穿透）
- [ ] B4 模块页：侧栏仍可点击
- [ ] B5 模块页：内容区滚动
- [ ] B6 模块页：设置控件可交互

### C. 全局设置页（footer 齿轮）

- [ ] C1 点齿轮 → 进入设置页
- [ ] C2 设置页：侧栏仍显示且可点
- [ ] C3 设置页：点侧栏分类 → 离开设置页
- [ ] C4 设置页：← → 导航
- [ ] C5 设置页：滚动
- [ ] C6 设置页：所有控件（开关、滑块、下拉、取色器）

### D. 主题抽屉（浮层）

- [ ] D1 Ctrl+T 打开抽屉
- [ ] D2 抽屉**优先**接收点击（不穿透到页面）
- [ ] D3 抽屉外点击**穿透**到页面（刻意设计）
- [ ] D4 抽屉：拖动标题栏
- [ ] D5 抽屉：token 列表滚动、hover、选中
- [ ] D6 抽屉：取色器拖动、输入
- [ ] D7 抽屉：「+」「−」弹窗
- [ ] D8 抽屉：ESC 关闭抽屉（不关屏幕）

### E. 键盘与文本（最容易漏）

- [ ] E1 搜索框：输入、Backspace、Ctrl+A/C/V/X
- [ ] E2 搜索框：ESC 退出焦点（第一次），再 ESC 关屏幕（第二次）
- [ ] E3 取色器输入框：同上
- [ ] E4 26.3 环境下 Backspace/Esc 有效（键码转换）
- [ ] E5 中文输入法（preedit）在文本框内正常

### F. 窗口与缩放

- [ ] F1 窗口缩放后，点击位置仍准确
- [ ] F2 小窗口下（面板缩放 < 1），点击位置仍准确
- [ ] F3 ClickGUI 开合动画期间，点击无响应
- [ ] F4 动画结束后，点击正常

### G. 关闭与生命周期

- [ ] G1 ESC 关闭 GUI
- [ ] G2 关闭动画完整播放
- [ ] G3 关闭后重新打开，一切正常
- [ ] G4 关闭后文本输入焦点已释放（不再接收字符）

---

## 迁移策略

**分两步，每步独立验证：**

### 步骤 3a：只迁移**输入**，绘制不动

- `OneClickScreen extends UiHost`
- 实现 `layers()` 返回 5 个适配器
- 实现 `toDesignX/Y`（复用现有 `localX/localY`，含动画反转）
- **删除 9 个手写输入方法**
- `paintHost` 里仍然调用现有 `drawPanel`

**风险**：中。绘制未动，输入路径全新。

### 步骤 3b（可选）：统一绘制到 `paintLayer`

只在 3a 稳定后再做。收益小于 3a。

---

## 回滚方案

`git revert <3a commit>`。因为 3a 不涉及数据格式或配置，回滚是安全的。

---

## 已知风险点

| 风险 | 说明 |
|---|---|
| **动画反转** | `localX/Y` 包含 `animScale()`，必须完整保留在 `toDesignX/Y` |
| **焦点仲裁** | 现有 `focusOnly(4 区域)` 要改成 `UiHost.focusOnly(layer)` |
| **`statsPage` 像素对齐** | `boundsOnScreen` 必须返回对齐值 |
| **抽屉穿透** | 抽屉的 `isOpen()` 与“点击外部穿透”要正确配合 |
| **`moduleChrome` 与 `statsPage` 并存** | 两者同时可见，`layers()` 顺序决定谁先接收 |
