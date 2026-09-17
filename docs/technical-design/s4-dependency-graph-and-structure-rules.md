# S4 Technical Design：依赖图与首批结构规则

- 状态：Implemented（本地门禁通过；远端验收以 S4 PR 和合并后 `main` CI 为准）
- 日期：2026-09-17
- 对应需求：`archguard-docs/requirements/scanner-v0.2-feature-spec.md`
- 对应 Issue：`AI-ArchGuard/archguard-scanner#11`
- 对应阶段：阶段 1，切片 S4

## 目标与非目标

S4 在 S3 的确定性 Java Fact 上建立只读依赖图，交付 `archguard.illegal-package-dependency`、`archguard.layered-architecture` 和 `archguard.dependency-cycle` 三条结构规则，并把 Finding 与触发边的 Evidence 闭合到 S2 `0.1.0` 报告契约中。

S4 不实现 Spring、内部模块可见性、禁止组件、复杂度或注解规则，不实现 YAML/JSON 配置绑定、外部参数 Schema、最终用户 CLI、网络访问、Platform 集成或 LLM 判断。外部规则文件及其严格参数 Schema 属于 S6；S4 通过不可变强类型配置对象冻结规则语义并失败关闭。

## 模块与数据流

```text
JavaScanResult
     │  java.package 扩展 + S2 领域对象
     ▼
RuleInput ── 引用闭合、重复 ID、契约版本检查
     │
     ▼
DependencyGraph ── 节点/边预算、稳定邻接索引、强连通分量
     │
     ├── IllegalPackageDependencyRule
     ├── LayeredArchitectureRule
     └── DependencyCycleRule
              │
              ▼
       RuleEngineResult ── 稳定排序 Finding + 原始 Evidence 引用
```

- `scanner-rule-engine` 生产代码只依赖 `scanner-domain`，不访问解析器 AST、Report DTO、Platform、网络、文件系统或时钟。
- `scanner-parser-java` 为每个类型增加单值 `java.package` 扩展；默认包使用 `<default>`。语言无关 Component 核心字段和 `0.1.0` Schema 不变。
- `StructureRuleEngine` 是统一执行边界：拒绝同一 Rule 的重复配置，应用图和 Finding 硬上限，并按 Rule ID 和 Finding ID 排序。

## 依赖图

`DependencyGraph` 以 Component ID 为节点、Dependency 为有向边。创建时验证 Component/Dependency ID 唯一、边端点存在，并建立稳定的出边和入边索引。默认预算为 100,000 个节点、1,000,000 条边和 10,000 个 Finding；调用方只能使用正数且不能放大默认硬上限。

强连通分量使用非递归 Kosaraju 算法，避免深图导致调用栈耗尽。节点、邻接点、边、分量成员和分量列表均按稳定 ID 排序；循环分量定义为成员数大于一，或 Component 作用域下包含自环的单节点分量。

## 规则语义

### 非法包依赖

`PackageSelector` 支持三种明确匹配：

- `com.example.api`：仅精确包；
- `com.example.api.*`：仅直接子包，不包含基包和更深后代；
- `com.example.api.**`：基包及任意深度后代；
- `<default>`：仅默认包。

同包边忽略。命中的 Policy 中，deny 优先；存在 allow 列表时，目标未被该 Policy 允许也形成违规。Policy 没有命中时不猜测、不报错。S6 的外部配置 Schema 将沿用这些已冻结语义。

### 分层架构

Layer 列表按允许依赖方向从上到下声明。层内依赖允许；向后续相邻层依赖允许；向前层依赖为 `reverse-layer-dependency`。`allowLayerSkipping=false` 时，跨过一个或多个中间层为 `skipped-layer-dependency`。未归层组件忽略；组件同时匹配多个层时以 `rule.config.layer-overlap` 失败关闭，不任意选择。

### 依赖循环

循环规则支持 `COMPONENT`、`PACKAGE` 和 `MODULE` 三种作用域。包作用域按 `java.package` 折叠，模块作用域按 Artifact ID 折叠；折叠后节点内部边不是跨节点循环，因此仅 Component 作用域报告真实自环。每个循环强连通分量生成一个 Finding，Evidence 是分量内部所有原始边 Evidence 的有序并集。

## Finding、Evidence 与确定性

- Rule ID 和版本固定为 `archguard.*` 与 `0.1.0`，severity 由强类型配置显式提供。
- 边违规 fingerprint 输入包含 Rule、版本、项目、违规类型、源/目标/边种类及 Dependency ID。
- 循环 fingerprint 输入包含作用域、稳定节点 ID 集合和内部 Dependency ID 集合。
- Finding ID 继续使用 S2 SHA-256 规则；位置取排序后首个 Evidence 的仓库相对位置。
- Finding 不复制源码，Evidence 只保留 S3 的安全摘要和位置；所有输出在进入 Report 前稳定排序。
- 相同输入、配置顺序扰动和集合顺序扰动必须产生相同 Finding 及完全相同的报告 JSON 字节。

## 失败与安全边界

重复实体、悬空 Artifact/Component/Dependency/Evidence、缺失或多值 `java.package`、重复 Rule、层重叠和预算超限都抛出稳定的命名空间错误码，不降级成部分 Finding。规则不执行目标构建或代码、不读取源码文件、不访问网络，也不把本机绝对路径带入结果。

## 验证、兼容与回滚

测试覆盖图索引、顺序扰动、两点/多点循环、自环、无环、包选择器边界、allow/deny、分层正向/反向/跳层、层重叠、Evidence 闭合、重复配置和节点/边/Finding 上限。组合测试从多模块 Java 夹具执行解析、三条规则、S2 Schema 验证和重复 JSON 字节比较；Maven Enforcer 和既有架构测试继续证明依赖方向与禁止依赖。

S4 不改变 `0.1.0` Schema，现有消费者可忽略新增 Finding 和 `java.package` 扩展。兼容发布顺序为 Scanner 实现与提供方测试先合入，合并后 `main` CI 成功后再更新 Docs 状态；Platform 不需要同步发布。回滚时先撤销 Docs 的 S4 状态，再整体撤销 Scanner S4，S1–S3 的模型、Schema 和 Java 提取仍可使用。
