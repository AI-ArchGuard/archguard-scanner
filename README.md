# ArchGuard Scanner

ArchGuard 的确定性代码分析平面。第一版深度扫描 Java，但公共模型、规则接口和结果契约保持语言无关。

## 当前状态

阶段 0 `v0.1.0-foundation` 已通过七仓库远端验收并正式关闭，当前处于阶段 1 `v0.2.0-scanner`。S1–S5 已通过 PR 与合并后 `main` CI 验收。S6 已在本地实现最终用户 CLI、严格 YAML 配置、退出码、Diagnostic 呈现和原子报告写入；最终验收仍以 S6 PR 与合并后 `main` CI 为准。跨仓库合成样例、黄金 digest、性能基线和发布文档属于 S7，当前不能声称 `v0.2.0-scanner` 已发布。阶段规范见 Docs 的 `requirements/scanner-v0.2-feature-spec.md`，实现设计见 [`docs/technical-design/`](docs/technical-design/)。

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

构建后运行 CLI：

```bash
java -jar scanner-cli/target/archguard-scanner.jar scan ./my-java-project \
  --rules ./rules.yaml \
  --output ./report.json
```

最小规则文件：

```yaml
version: 0.1.0
project:
  identity: example:application
  name: Example Application
failOn: high
rules:
  - id: archguard.dependency-cycle
    parameters:
      scope: package
```

退出码：`0` 表示成功，`2` 表示报告已写入但存在达到门槛的 Finding，`64` 表示参数或规则配置无效，`70` 表示扫描、Diagnostic、资源或报告写入失败。完整 YAML 结构、八条规则参数和安全限制见 [S6 Technical Design](docs/technical-design/s6-cli-yaml-and-report-writing.md) 及随 CLI 发布的 `schema/rules-0.1.0.schema.json`。

当前构建验证五个模块、Java/Maven 版本、依赖收敛、禁止依赖、模块方向、`0.1.0` 契约、Java/Maven 静态事实、八条确定性规则、严格 YAML、四类退出码、原子报告写入、资源上限、Evidence 闭合和端到端字节确定性。`scanner-domain` 保持零生产依赖；`scanner-rule-engine` 生产代码只依赖 `scanner-domain`；报告 Schema 由 `scanner-report` 在 [`scanner-report/src/main/resources/schema/scan-result-0.1.0.schema.json`](scanner-report/src/main/resources/schema/scan-result-0.1.0.schema.json) 发布。Scanner 静态分析 Maven `src/main/java` 和 POM 直接非测试依赖，不执行构建、不下载依赖、不解析传递依赖，也不提供完整类型求解。项目级黄金样例、性能基线和发布证据属于 S7。

## 许可证

本仓库采用 [Apache License 2.0](LICENSE)。
