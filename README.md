# ArchGuard Scanner

ArchGuard 的确定性 Java 源码、字节码、依赖图和架构规则分析器。

## 当前状态

M0 仓库基线已建立，Scanner 工程骨架和扫描契约尚未初始化。

## 职责

- 解析 Java 源码、字节码及依赖关系，构建可重复的分析图。
- 执行循环依赖、禁止依赖等确定性架构规则。
- 发布稳定、版本化且与 Platform 内部实现无关的 ScanRequest/ScanResult 契约。
- 为正常、违规、循环和失败场景维护黄金样例与性能基线。

## 非职责

- 不管理用户、项目、任务状态、权限或业务数据库。
- 不负责 GitHub Webhook、MCP 工具治理或部署编排。
- 不使用 LLM 决定确定性违规，也不依赖 `archguard-platform` 内部类。
- 不保存或输出不必要的完整源码。

## 依赖与契约

- 向 `archguard-platform` 提供版本化扫描契约；破坏性变更必须有迁移和兼容计划。
- 测试样例来自 `archguard-samples` 或本仓库的最小合成夹具。
- 跨仓库架构与工程规范以 [archguard-docs](https://github.com/AI-ArchGuard/archguard-docs) 为准。

## 本地验证

当前基线可执行：

```bash
git diff --check
git status --short
```

初始化 Maven Wrapper 后运行 `./mvnw verify`；Windows 使用 `.\mvnw.cmd verify`。契约和黄金测试尚未建立，因此当前不能声称扫描验证已通过。
