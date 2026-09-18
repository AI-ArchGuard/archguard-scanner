# S6 Technical Design：CLI、YAML、退出码与报告写入

- 状态：Implemented（本地门禁通过后以 S6 PR 和合并后 `main` CI 为最终验收）
- 日期：2026-09-18
- 对应需求：`archguard-docs/requirements/scanner-v0.2-feature-spec.md`
- 对应 Issue：`AI-ArchGuard/archguard-scanner#15`
- 对应阶段：阶段 1，切片 S6

## 目标与非目标

S6 把 S3–S5 已有的 Java/Maven 事实、八条确定性规则和 `0.1.0` 报告组合为可执行命令：

```text
archguard scan <repository> --rules <rules.yaml> --output <report.json>
```

本切片冻结规则 YAML `0.1.0`、严格参数校验、错误分类、退出码、Diagnostic 呈现和原子报告写入。S6 不增加 Scanner Result 字段，不实现远程克隆、网络访问、目标构建、Platform 集成或 S7 的跨仓库样例、黄金 digest、性能基线和发布制品。

## 组合与边界

`scanner-cli` 是唯一组合根，按固定顺序执行：解析参数、读取并校验 YAML、扫描 Java/Maven 事实、构造 `RuleInput`、执行规则、映射 `DomainReport`、序列化并校验 `0.1.0` JSON、检查输出上限、原子写入、输出 Diagnostic、计算退出码。

- `scanner-domain` 不变且继续保持零生产依赖。
- `scanner-rule-engine` 不解析 YAML，继续只依赖 `scanner-domain`。
- `scanner-report` 新增与格式无关的 `ReportWriter`，在目标目录创建临时文件、强制落盘后使用同文件系统原子替换；不支持原子移动时失败并删除临时文件，不降级为可能留下半份报告的覆盖写入。
- Diagnostic 不进入 Scanner Result `0.1.0`。解析与规则 Diagnostic 在标准错误中按稳定顺序输出，内容只含仓库相对路径、稳定 ID 和安全消息。
- 报告在规则违规时仍写入；配置无效或扫描在报告生成前失败时不创建报告。

## CLI 与退出码

| 退出码 | 名称 | 语义 |
|---:|---|---|
| `0` | success | 扫描完成、无 Diagnostic，且没有达到 `failOn` 的 Finding |
| `2` | policy violation | 报告成功写入，至少一个 Finding 的 severity 达到 `failOn` |
| `64` | invalid input | 命令参数、仓库根路径、规则文件、Schema 或强类型配置无效 |
| `70` | scan failure | 路径/资源限制、解析或规则 Diagnostic、规则执行、报告校验/大小/原子写入或内部错误 |

退出码优先级为扫描失败高于规则违规。CLI 不把绝对主机路径、异常堆栈或源码写入标准输出/错误。`--help` 和 `--version` 返回 `0`。

## YAML 契约

规则文件必须是单个 UTF-8 YAML 文档，最大 1 MiB。根对象包含：

```yaml
version: 0.1.0
project:
  identity: example:application
  name: Example Application
failOn: high
limits:
  maxFiles: 10000
  maxFileBytes: 2097152
  maxTotalBytes: 104857600
  maxDepth: 32
  maxNodes: 100000
  maxEdges: 1000000
  maxFindings: 10000
  maxOutputBytes: 52428800
rules:
  - id: archguard.dependency-cycle
    severity: high
    parameters:
      scope: package
```

完整结构由 `scanner-cli/src/main/resources/schema/rules-0.1.0.schema.json` 所有。Schema 使用 Draft 2020-12，所有对象 `additionalProperties: false`，八个 Rule ID 分别拥有封闭参数结构。规则 ID 不得重复；数组和强类型配置继续由领域构造器执行语义校验。`info` 不允许用于规则 severity 或 `failOn`。

配置解析限制嵌套深度、字符串和数字长度，并启用重复键检测与尾随文档检测。YAML anchor、alias 和显式 tag 失败关闭，避免共享结构、实体式扩展和类型标签带来的歧义。规则文件不得是符号链接。YAML 最终转为普通 JSON tree 通过参数 Schema，不把 YAML 库对象传入规则引擎。

## 依赖选择

CLI 增加 `jackson-dataformat-yaml`，版本与仓库既有 Jackson BOM `2.22.2` 对齐。Jackson YAML 及其 SnakeYAML 依赖采用 Apache-2.0，项目活跃维护，并与已有 Jackson tree/JSON 模型直接兼容。手写 YAML 解析器无法可靠覆盖引号、注释、重复键和资源限制，替换成本和安全风险更高。

构建使用 Apache Maven Shade Plugin `3.6.2` 生成带 Main-Class 的 `scanner-cli/target/archguard-scanner.jar`。它只参与构建、不进入运行时依赖；替代方案是要求最终用户自行拼接 Maven classpath，不符合最终用户 CLI 的可复现性。

JSON Schema 校验器传递依赖 SLF4J API。CLI 增加与该 API 对齐的 `slf4j-nop` `2.0.17` 运行时 provider，避免框架自身的“没有 provider”警告污染稳定 Diagnostic；它采用 MIT 许可证，不记录或吞掉 Scanner 显式写入标准错误的信息。使用 `slf4j-simple` 会引入非契约日志，完全排除 SLF4J API 则会使校验器运行失败。

## 确定性、安全与验证

- 配置映射按 Rule ID 排序，复用 S4/S5 的强类型构造器和默认 severity。
- `failOn` 默认 `high`；severity 顺序为 `low < medium < high < critical`。
- 输出默认上限 50 MiB，允许配置范围为 1 KiB–100 MiB；超过上限返回 `70`，不替换既有文件。
- 输出文件的父目录必须已经存在；CLI 不隐式创建目录，也不接受符号链接或非普通文件作为输出目标。
- CLI 不跟随规则文件或报告目标符号链接。仓库发现继续沿用 S3 的根目录、深度、文件数、单文件和总字节限制。
- 相同仓库与 YAML 连续运行必须产生逐字节相同的 JSON。
- 测试覆盖八条规则映射、默认值、未知字段/规则、重复键/规则、多个文档、anchor/alias/tag、非法 severity/限制/选择器、四类退出码、报告 Schema、字节稳定性、输出上限、符号链接和原子替换。

兼容顺序为 Scanner PR、Scanner 合并后 `main` CI、Docs 状态 PR。Result Schema 仍为 `0.1.0`，因此 Docs 契约索引不变，Platform 无需同步发布。回滚时先撤销 Docs 状态，再整体撤销 S6；S1–S5 的模型、解析器和规则库仍可作为 Java API 使用。
