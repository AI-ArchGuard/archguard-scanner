# S5 Technical Design：Spring、模块、组件、复杂度与注解规则

- 状态：Implemented（本地门禁通过；远端验收以 S5 PR 和合并后 `main` CI 为准）
- 日期：2026-09-17
- 对应需求：`archguard-docs/requirements/scanner-v0.2-feature-spec.md`
- 对应 Issue：`AI-ArchGuard/archguard-scanner#13`
- 对应阶段：阶段 1，切片 S5

## 目标与非目标

S5 在 S3/S4 的源码事实和统一规则执行边界上补充函数、限定注解、Maven 直接依赖和圈复杂度事实，并交付 `spring.controller-repository-access`、`archguard.internal-module-access`、`archguard.forbidden-component`、`archguard.complexity-threshold` 与 `archguard.required-annotation`。所有 Finding 继续引用最小 Evidence，使用稳定 ID、fingerprint 和排序。

S5 不实现 YAML、外部参数 Schema、最终用户 CLI、传递依赖解析、目标构建、网络访问、完整类型求解、运行时 Spring、Platform 集成或 S7 项目级黄金样例。Scanner Result Schema 保持 `0.1.0`；解析和规则 Diagnostic 仍是执行侧结果，由 S6 CLI 负责呈现。

## 事实与安全边界

- Java 方法和构造器映射为语言无关 `FUNCTION` Component。规范名由所属类型、声明名和去空白的源码参数类型组成，重载因而获得不同稳定 ID。
- 类型与函数通过 `java.*` 扩展携带包、声明种类、修饰符、所属类型、限定注解、注解解析完整性和生成代码标记。只有限定写法、显式 import、唯一的同包源码注解、唯一可确认的 wildcard import 和固定 JDK/Spring 注解可以解析；否则返回 `java.annotation.unresolved`，不按简单名猜测。
- 每个源码 Component 产生不含源码片段的声明 Evidence。Metric Evidence 只记录 scope、指标和值。
- POM 使用 JDK StAX 静态读取，DTD 和实体关闭；只读仓库内普通文件，不执行 Maven，不访问网络，不跟随根外父 POM。显式越界父路径返回 `maven.parent.outside-root`。
- 读取模块 G:A[:V]、本地属性和本地父属性，建模 `<project>/<dependencies>` 下的直接非 `test` 依赖；忽略 dependencyManagement、插件依赖和传递依赖。无法解析的 G:A 返回 Diagnostic，不创建猜测对象。
- 外部直接依赖映射为 `LIBRARY` Artifact 和代表性 `MODULE` Component，模块到依赖的边引用 POM 行位置的 `CONFIGURATION` Evidence。
- 没有新增生产依赖；`scanner-domain` 仍为零生产依赖，规则引擎仍只依赖领域模型。

## Metric 与规则输入

`JavaScanResult` 和 `RuleInput` 增加稳定排序的 Metric 列表。输入验证 Metric ID 唯一，scope 必须引用 Project、Artifact、Component 或 Dependency。`RuleEngineResult` 增加稳定排序、去重的 `RuleDiagnostic`；Diagnostic 不混入 Finding，也不改变 `0.1.0` 报告结构。

圈复杂度 key 为 `complexity.cyclomatic`、单位为 `count`。方法和构造器基数为 1，`if`、`for`、增强 `for`、`while`、`do`、`catch`、三元表达式、非 default switch 分支、`&&` 和 `||` 各加 1。lambda 中的分支归所属可执行体，嵌套类型体和初始化块不计。类型值为非生成函数之和，模块值为非生成类型之和；仅解析为标准 `@Generated` 限定名的声明被忽略。

## 五条规则

### Spring Controller 直接访问 Repository

只识别 `org.springframework.stereotype.Controller`、`org.springframework.web.bind.annotation.RestController`、`org.springframework.stereotype.Repository` 和 `org.springframework.stereotype.Service` 的直接限定注解事实。Controller 到 Repository 的直接 Dependency 形成 Finding；经 Service 的两段边不形成该 Finding，同名自定义注解不匹配。不推导运行时 stereotype、继承或任意元注解。

### 跨模块内部访问

模块以 Maven module repository path 标识。配置为每个目标模块声明 public API 包选择器；同模块边忽略，跨模块目标落在 public API 时允许，否则形成 Finding。目标模块没有边界配置时，每个目标 Artifact 返回一个稳定 `rule.module-boundary.missing` Diagnostic 并跳过判断。

### 禁止组件

选择器支持精确限定类型、S4 包选择器以及精确 Maven `G:A`/`G:A:V`。坐标匹配既覆盖仓库模块，也覆盖 POM 直接声明的非测试外部依赖。未解析坐标返回 Diagnostic；未知对象不匹配、不猜测。单条边即使命中多个选择器也只产生一个 Finding。

### 复杂度阈值

配置可分别设置函数、类型和模块阈值，至少设置一个。只有 Metric 严格大于阈值才形成 Finding，等于阈值通过。Finding 引用对应 Metric Evidence，并在扩展中记录 metric key 和阈值。

### 必要注解

每个要求由注解限定名、Component kind、声明种类和可选包选择器组成，支持类型、方法和构造器，只判断直接声明。适用对象存在注解时通过，缺少时引用声明 Evidence 形成 Finding；注解解析不完整时返回 `rule.annotation.unresolved` Diagnostic 并跳过对象。

五条规则版本均为 `0.1.0`。Spring、模块和禁止组件默认 `high`，复杂度和必要注解默认 `medium`；severity 只允许 `low` 至 `critical`，`info` 失败关闭。S6 的外部参数 Schema 必须复用这里冻结的强类型语义。

## 确定性、验证与兼容

组件、Artifact、Dependency、Metric、Evidence、Finding 和 Diagnostic 都在边界稳定排序；POM 多次声明的同一直接依赖按源/目标折叠，并对 scope 和 Evidence 排序。集合输入、规则配置和文件发现顺序变化不得改变最终 JSON 字节。

测试覆盖 Spring 直接/间接/同名注解，模块 public/internal/缺失边界，禁止类型/包/内部与外部坐标/未知坐标，复杂度计数、聚合、阈值边界和生成代码，以及必要注解的适用、存在、缺失和解析失败。POM 测试覆盖 DTD/实体、属性解析、直接非测试依赖、dependencyManagement 排除和未知坐标；完整 `verify` 继续执行 Schema、Enforcer、架构和 S1–S4 回归。

`0.1.0` Schema 未改变，现有消费者可以忽略新增 Component kind 实例、Metric 和命名空间扩展。兼容发布顺序为 Scanner PR、合并后 Scanner `main` CI、Docs 状态更新；Platform 不需要同步发布。回滚时先撤销 Docs 状态，再整体撤销 Scanner S5，S1–S4 仍可独立运行。
