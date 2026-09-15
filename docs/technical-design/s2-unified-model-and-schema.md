# S2 Technical Design：统一模型与结果 Schema `0.1.0`

- 状态：Implemented（本地门禁通过；远端验收以 S2 PR 和合并后 `main` CI 为准）
- 日期：2026-09-15
- 对应需求：`archguard-docs/requirements/scanner-v0.2-feature-spec.md`
- 对应 Issue：`AI-ArchGuard/archguard-scanner#4`
- 对应阶段：阶段 1，切片 S2

## 目标与边界

S2 冻结 Scanner 第一条语言无关结果兼容线：领域对象、稳定 ID、规范排序、契约 DTO、JSON 序列化、Draft 2020-12 Schema 以及有效/无效提供方测试。

S2 不实现 Java AST、文件发现、类型求解、规则执行、YAML、CLI、Platform 适配器、网络访问或目标项目构建。`scanner-parser-java`、`scanner-rule-engine` 和 `scanner-cli` 不增加生产实现。

## 模块所有权

```text
scanner-domain                         scanner-report
语言无关不可变模型                    契约 DTO 与双向映射
路径和稳定 ID 不变量          ──────▶  规范 JSON 序列化
零生产依赖                            0.1.0 JSON Schema
                                      Schema 与引用闭合校验
```

- `scanner-domain` 不含 Jackson 注解，不依赖 JSON Schema、解析器、Platform、数据库、Spring 或模型 SDK。
- `scanner-report` 拥有全部 JSON 字段名、枚举 wire value、Schema、映射、规范排序和失败关闭行为。
- Contract DTO 不复用 Platform DTO；消费者只依赖发布的 Schema 和示例。

## 统一模型

| 概念 | 核心身份和关系 | S2 不变量 |
|---|---|---|
| `Project` | ID、稳定项目 identity、名称、语言、仓库根 | 根路径使用 `.`；不包含主机路径 |
| `Artifact` | Project、kind、名称、语言、相对根路径、规范名称 | kind 为 module/source-set/package/namespace/library |
| `Component` | Artifact、kind、名称、语言、规范名称、可选位置 | 不出现 JavaClass、MavenModule 或 AST 类型 |
| `Dependency` | source/target Component、kind、Evidence 引用 | Evidence 引用非空、去重并排序 |
| `Metric` | scope、key、十进制 value、unit | 数字去除无意义尾随零；阈值仍由后续 Rule 判定 |
| `Finding` | fingerprint、Rule 引用、severity、subject、位置、Evidence | Finding 与 Evidence 引用必须闭合 |
| `Evidence` | kind、摘要、必需位置 | 不含源码正文、snippet 或 content 字段 |
| `SourceLocation` | 相对路径、1-based 起止行列 | 结束位置不得早于开始位置 |
| `RuleReference` | namespaced Rule ID、语义版本 | 只引用规则身份，不引入规则执行实现 |

语言特有信息只能进入 `extensions`。扩展 key 至少包含两段小写命名空间，例如 `java.modifiers`；value 是非空、去重、稳定排序的短字符串数组。未知核心字段和非法扩展失败关闭。

## 路径与隐私

所有输出路径在进入领域对象时执行以下规范化：

1. Unicode NFC 规范化；
2. `\` 转换为 `/`；
3. 删除 `.` 段；
4. 拒绝盘符、根路径、空段和 `..`；
5. 保留仓库记录的字符大小写，并按大小写敏感身份处理，避免合并 Linux 上合法的不同路径。

结果不得包含 checkout 绝对路径。Evidence 只保留可定位范围与关系摘要，不保存完整源码；日志记录也不得通过异常消息回显源码。

## 确定性 ID

`StableIdGenerator` 按固定顺序连接以下六项规范化输入：

```text
schemaVersion
project identity
entity kind
language
repository-relative path
qualified name
```

各项使用换行符分隔并按 UTF-8 计算 SHA-256。输出为 `<entity-kind>_<64 lowercase hex>`。`language` 转为 `Locale.ROOT` 小写；路径遵循上一节规则；项目 identity、路径和 qualified name 使用 NFC 后的原始大小写。时间、耗时、线程、主机绝对路径和随机 UUID 均不参与 ID。

Finding fingerprint 由 Rule、subject 和规范 Evidence 身份等调用方选择的稳定输入通过同一 SHA-256 基元生成；S4/S5 在冻结 Rule 语义时补充具体组合，不改变 `0.1.0` 的字段形状。

## JSON 与 Schema

机器契约位于：

```text
scanner-report/src/main/resources/schema/scan-result-0.1.0.schema.json
```

Schema 使用 JSON Schema Draft 2020-12，根对象和所有嵌套对象均 `additionalProperties: false`。结构约束由 Schema 执行，位置范围和跨引用闭合由实现级校验补足。校验器固定 `Locale.ROOT`，使本地和 CI 的错误原因稳定；外部 Schema 获取保持关闭，运行不需要网络。

`ReportMapper` 把领域模型映射为明确的 Contract DTO，并在输出前执行：

- 所有顶层数组按 ID 升序；
- Dependency/Finding 的 Evidence ID 升序；
- extension key 和 value 升序；
- JSON 对象属性和 Map key 使用稳定顺序；
- Metric 的 `BigDecimal` 使用规范数值表达。

因此同一领域输入即使集合插入顺序不同，最终 UTF-8 JSON 字节仍完全一致。

## 依赖选择

| 依赖 | 版本 | 作用域 | 许可证 | 选择与替代 |
|---|---:|---|---|---|
| Jackson Databind/BOM | 2.22.2 | `scanner-report` production | Apache-2.0 | Java record 映射成熟、稳定排序配置明确；手写 JSON 会增加转义和字段漂移风险 |
| NetworkNT JSON Schema Validator | 2.0.7 | `scanner-report` production | Apache-2.0 | 支持 Draft 2020-12、Jackson 2 和离线 meta-schema；手写校验无法证明 Schema 兼容 |
| JUnit Jupiter | 6.1.1 | domain/report test only | EPL-2.0 | 复用 S1 测试基线，不进入生产制品 |

选择 NetworkNT 2.x 而不是 3.x，是因为 2.x 与本切片使用的 Jackson 2 兼容；3.x 面向 Jackson 3，会扩大一次切片内的迁移范围。没有向 `scanner-domain` 添加生产依赖。

Scanner 只处理 JSON 且 Schema 不使用日期格式，因此从 NetworkNT 排除 YAML/SnakeYAML 与 RFC 3339 `itu` 传递依赖，减少无用解析面；若未来契约引入 YAML 或日期 format assertion，必须在对应切片重新评审并恢复所需依赖。

## 验证与失败关闭

提供方测试覆盖：

- Schema 自身通过 Draft 2020-12 meta-schema；
- 完整报告和最小报告有效；
- 缺少字段、错误版本、未知枚举、非法 ID、非法位置范围、未知字段、非法扩展、绝对路径和悬空引用失败；
- JSON 往返后领域字段完全相等；
- 相同规范输入产生相同 ID；
- Unicode 组合形式和路径分隔符差异得到相同 ID；
- 打乱集合后输出字节完全相同；
- 领域 API、Schema 和 Evidence 不泄漏 Java 专属核心字段或源码正文；
- S1 Maven Enforcer 与三个反应堆架构测试继续执行。

## 兼容、发布与回滚

`0.1.x` 是第一条开发期兼容线。PATCH 不改变字段或枚举形状；破坏性字段、枚举或语义变化发布新的 `0.MINOR` 并保留并行迁移窗口。

发布顺序为 Scanner Schema/示例/提供方测试先合入，再更新 Docs 契约索引、Feature Spec 切片状态和路线图。Platform、Gateway 与 Evals 在 S2 不启动，也不消费本分支内部类。

在没有消费者采用 `0.1.0` 前，可整体撤销 S2 提交；已有消费者后不得静默改写 Schema，必须发布新兼容线并先切换消费者，回滚时再退 Scanner 制品。
