# ArchGuard Scanner

ArchGuard 的确定性代码分析平面。第一版深度扫描 Java，但公共模型、规则接口和结果契约保持语言无关。

## 当前状态

阶段 0 `v0.1.0-foundation` 已通过七仓库远端验收并正式关闭，当前处于阶段 1 `v0.2.0-scanner`。S1 的五模块骨架与边界测试、S2 的语言无关模型和 `0.1.0` 契约、S3 的 Java 静态提取均已通过 PR 与合并后 `main` CI 验收。S4 已在本地实现只读依赖图、非法包依赖、分层架构和依赖循环三条结构规则；最终验收仍以 S4 PR 与合并后 `main` CI 为准。最终用户 CLI 和其余五条规则尚未实现，当前不能声称具备完整扫描能力。阶段规范见 Docs 的 `requirements/scanner-v0.2-feature-spec.md`，实现设计见 [`docs/technical-design/`](docs/technical-design/)。

## 职责

- 把 Java 源码与工程结构映射为语言无关的 Artifact、Component、Dependency、Metric、Finding 和 Evidence。
- 执行非法包依赖、分层、循环、Controller→Repository、跨模块内部类、禁止组件、复杂度和必要注解等确定性规则。
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

完整构建：

```bash
./mvnw --batch-mode --no-transfer-progress verify
```

Windows 使用：

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress verify
```

当前构建验证五个模块、Java/Maven 版本、依赖收敛、禁止依赖、模块方向、S2 契约、S3 静态提取，以及 S4 依赖图、前三条结构规则、资源上限、Evidence 闭合和端到端字节确定性。`scanner-domain` 保持零生产依赖；`scanner-rule-engine` 生产代码只依赖 `scanner-domain`；Schema 由 `scanner-report` 在 [`scanner-report/src/main/resources/schema/scan-result-0.1.0.schema.json`](scanner-report/src/main/resources/schema/scan-result-0.1.0.schema.json) 发布。Scanner 只分析 Maven `src/main/java` 中可由源码确定的项目内类型关系，不执行构建、不下载目标依赖，也不提供完整类型求解。外部规则配置、最终用户 CLI、其余规则和项目级黄金样例将在 S5–S7 分步加入。

## 许可证

本仓库采用 [Apache License 2.0](LICENSE)。
