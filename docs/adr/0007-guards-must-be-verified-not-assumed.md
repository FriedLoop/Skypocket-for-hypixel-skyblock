# ADR-0007：守卫必须验证真实结果，而不是声称的意图

**状态**：已采纳（2026-11）

## 背景

`build.gradle.kts` 里有一段被我称为"架构守卫"的配置：

```kotlin
named("test") {
    java.include(uiKernelSources.map { "dev/skypocket/$it" })
}
```

配套的注释和文档都声称：**如果 `ui/` 里某个类新增了 `net.minecraft` 依赖，测试编译会失败并指出这个类。**

它在文档里存在了一整天，被反复引用为"分层规则从约定变成了编译期强制"。

## 它是无效的

实测方式：往白名单里的 `ui/Size.java` 注入

```java
public static final Class<?> PROBE = net.minecraft.client.Minecraft.class;
```

然后跑测试——**编译通过，56 个测试全部通过**。

## 为什么无效

Gradle 的 `test` 源集**默认 `testImplementation` 依赖 `main`**，所以 `main` 的全部编译产物始终在测试 classpath 上。`include` 列表只决定 `build/bloom/test/` 下额外复制编译哪些**源文件**，管不到 classpath 上已有什么。

而且 `net.minecraft` 本来就在编译 classpath 上（`compileOnly`/`implementation` 都有），所以即使真的单独编译这些类，MC 类型依然能解析。

**这个配置从来没可能拦住 MC 依赖。**

## 决定

新增 `tools/check-kernel-purity.ps1`，**扫描编译后的 `.class` 字节码常量池**，找 `net/minecraft/` 引用。

```powershell
$text = [Text.Encoding]::ASCII.GetString([IO.File]::ReadAllBytes($file))
[regex]::Matches($text, 'net/minecraft/[\w/$]+')
```

### 不是所有引用都违规

第一次运行报出 9 个违规，逐个查证后分为三类：

| 类型 | 处理 | 理由 |
|---|---|---|
| `GuiGraphicsExtractor` | ✅ 允许 | 绘制入口。`UiPainter`/`RenderCache`/`UiPage` 的签名必须接收它，否则无处开始绘制 |
| `KeyEvent` / `CharacterEvent` / `PreeditEvent` / `MouseButtonEvent` | ✅ 允许 | MC 触发的事件；`InputKeys` 的职责就是翻译它们 |
| `Minecraft` | ❌ 报错 | 取服务（窗口缩放、剪贴板），**这类永远可以用接口消除** |

**判据是"是否可避免"**：接口签名里的平台类型不可避免；`getInstance()` 永远可避免。

### 验证方式

注入 `Minecraft.getInstance().getGuiScale()` 到 `ui/Size.java` → 报错并指出类名 → 恢复 → 通过。

## 顺带修掉的真实耦合

守卫一上线就查出两处可避免的服务式调用：

| 位置 | 原来 | 现在 |
|---|---|---|
| `TextField` | `Minecraft.getInstance().keyboardHandler.getClipboard()` | `UiPlatform.clipboard().get()` |
| `ColorPickerPanel` | `Minecraft.getInstance().getWindow().getGuiScale()` | `UiPlatform.guiScale()` |

抽出了 `Clipboard`、`GuiScale` 接口，与原有的 `TextMeasurer` 合并到 `UiPlatform` 统一安装，取代了单独存在的 `UiText`。

## 后果

`build` 现在跑三个守卫：`checkDocs`（依赖方向 + 文档漂移）、`checkKernelPurity`（字节码）、`test`（56 个用例）。

## 教训

**"我加了一个守卫"和"这个守卫会拦住它该拦的东西"是两件事。**

前者的证据是配置存在、注释写得像那么回事；后者的证据只有一种——**故意制造违规，看它是否真的报错**。

这条同样适用于其他守卫：

- `checkDocs` 的四种漂移（包不存在、包未列出、引用悬空、ADR 编号跳跃）**逐一反向验证过**
- `checkKernelPurity` 反向验证过
- 而 `uiKernelSources` 这条**从未验证过**，所以它错了一整天没人发现

**规则**：任何声称提供保障的机制，都必须有一个"故意让它失败"的验证记录。没有这个记录的守卫，按不存在处理。
