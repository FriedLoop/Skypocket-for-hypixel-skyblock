# ADR-0006：文档漂移作为构建失败

**状态**：已采纳（2026-11）

## 背景

这个项目已经产生过三次过时文档：

| 案例 | 后果 |
|---|---|
| `LayoutEngine` 类注释说"不实现绝对定位" | 实现早就有，注释过时，会让人写出错误代码 |
| `DEVELOPMENT.md` 包列表缺 `util/`、`feature/`、`crosshair/` | 找不到类在哪 |
| 文档说"没有测试页"，而 `DevUiTestScreen` 已存在 | 重复造轮子的风险 |

**纯靠自觉维护文档没有成功。**

## 决定

`tools/check-docs.ps1` 检查四类问题，接入 `check` 任务：

| 检查 | 失败示例 |
|---|---|
| 文档列出的包不存在 | `lists 'fakePkg/' but no such package exists` |
| 存在的包未列出 | `package 'util/' exists but is not listed` |
| `ui/` 违规依赖上层 | `imports dev.skypocket.gui - kernel must not depend upward` |
| 引用了不存在的文档 | `references nonexistent-doc.md which does not exist` |

**每种都反向验证过**：故意制造漂移，确认报错，再删除。

## 为什么值得

前三条中，"`ui/` 依赖上层"与 `uiKernelSources` 的编译期拦截至**故意重复**——一个只在白名单内生效，另一个覆盖全部文件。两者都保留，因为新增类可能忘记加进白名单。

## 代价

构建多一步（约 1 秒）。文档改动后如果忘了更新包列表，构建会失败——这是设计意图。

## 不做的事

不检查文档的**内容正确性**（如"某段描述是否与实际行为一致"），那需要语义理解。只检查**结构一致性**：包存在、引用有效、依赖方向正确。
