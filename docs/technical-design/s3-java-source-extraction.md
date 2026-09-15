# S3 Technical Design：Java 源码发现与统一模型提取

- 状态：Implemented（本地门禁通过；远端验收以 S3 PR 和合并后 `main` CI 为准）
- 日期：2026-09-15
- 对应需求：`archguard-docs/requirements/scanner-v0.2-feature-spec.md`
- 对应 Issue：`AI-ArchGuard/archguard-scanner#9`
- 对应阶段：阶段 1，切片 S3

## 目标与非目标

S3 在不执行目标仓库代码、构建或插件的前提下，发现 Maven 标准布局的 Java 主源码，解析 Java 21 类型声明和可由源码确定的项目内类型关系，并映射为 S2 已发布的 `Artifact`、`Component`、`Dependency`、`Evidence` 和 `SourceLocation`。

S3 不实现规则、依赖图算法、Finding、Metric、CLI、完整类型求解、外部依赖下载、注解处理器、Spring 运行时语义或 Platform 集成。`0.1.0` Schema 的字段和枚举不变。

## 模块与数据流

```text
JavaScanRequest
      │
      ▼
JavaSourceDiscovery ── 相对路径、Maven 模块、资源与链接边界
      │
      ▼
JavaParser Core ────── Java 21 CompilationUnit（仅解析期间驻留）
      │
      ▼
JavaModelExtractor ─── 两遍声明索引、项目内引用解析、稳定 ID
      │
      ▼
JavaScanResult ─────── S2 领域对象 + 排序后的 JavaParseDiagnostic
```

- `scanner-parser-java` 是唯一允许依赖 JavaParser 的模块。
- `scanner-domain` 和 `scanner-report` 不引用 AST、JavaParser 类型或 Java 专属 DTO。
- AST 与 token 只在一次扫描调用内用于提取范围，不进入结果、日志、缓存或持久化。
- `JavaScanResult` 不依赖 `scanner-report`；组合根可以把无诊断的结果放入 `DomainReport`，继续使用 S2 规范序列化。

## 解析器选择

| 方案 | 许可证 | 能力与成本 | 决策 |
|---|---|---|---|
| JavaParser Core `3.28.2` | Apache-2.0 / LGPL-3.0 双许可证，本项目采用 Apache-2.0 | 活跃维护，支持 Java 21，核心包无须工作区或编译器；AST Range 适合 Evidence | 选择；只引入 `javaparser-core`，不引入 Symbol Solver |
| Eclipse JDT Core | EPL-2.0 | 绑定和增量工作区能力更强，但传递依赖、配置与替换成本更高 | S3 不需要完整绑定，暂不选择 |
| JDK Compiler Tree API | GPLv2 + Classpath Exception | 无外部依赖，但绑定编译器实现，容错和独立解析控制较弱 | 暂不选择 |

JavaParser 仅由 Scanner 构建从 Maven Central 获取。扫描目标仓库时不访问网络、不读取目标 classpath，也不执行 Maven/Gradle。若后续真实规则证明需要符号求解，必须单独评审依赖、缓存、失败语义和离线边界。

## 文件发现与 Maven 边界

发现器只接受一个本地目录根，并采用以下规则：

1. 根目录必须存在、可读、是目录且自身不是符号链接。
2. 遍历不启用 `FOLLOW_LINKS`；每个目录的 real path 必须保持在根 real path 内。
3. 在 Java 源码树外跳过 `.git`、`.gradle`、`build`、`out` 和 `target`；源码包可以合法使用这些目录名。
4. 只接受位于带 `pom.xml` 模块下的 `src/main/java/**/*.java`；不解析 POM、不执行 Maven。
5. 模块根映射为 `ArtifactKind.MODULE`，仓库根模块路径为 `.`，嵌套模块使用 `/` 分隔的仓库相对路径。
6. 文件、总字节和路径深度受 `JavaScanLimits` 硬限制；超过限制立即以稳定错误码失败。
7. 符号链接、特殊文件和不可读项被跳过并产生不含主机路径的稳定诊断。

默认限制为 10,000 个 Java 文件、单文件 2 MiB、总计 100 MiB、最大深度 32。调用方可以收紧但不能把深度扩大到 256 以上。

## 模型映射

### Artifact 与 Component

- 每个含主源码的 Maven 模块生成一个 `MODULE` Artifact，扩展记录 `maven.module-path` 和 `java.source-set=main`。
- 顶层和成员 class、interface、enum、record、annotation 生成语言无关的 `TYPE` Component。
- Java 声明种类、修饰符和嵌套关系只进入 `java.declaration-kind`、`java.modifiers`、`java.nesting` 扩展。
- Component 核心字段不出现 `JavaClass`、`MavenModule` 或 AST 类型。
- 同一 qualified name 出现多次时全部歧义声明失败关闭，不为它们生成 Component，并返回 `java.type.duplicate`。

### Dependency 与 Evidence

提取范围限于当前仓库中能唯一解析到已声明 Component 的关系：

- class/interface `extends` → `EXTENDS`；
- class/enum/record `implements` → `IMPLEMENTS`；
- 字段、参数、返回值、构造、泛型和注解等类型引用 → `DEPENDS_ON`。

解析顺序固定为完整 qualified name、封闭类型、同包、显式 import 和通配 import。未导入的跨包简单名称、未知外部类型和歧义引用不猜测、不产生虚假 Component 或 Dependency。类型参数不会被误当成项目类型。

每个引用位置生成 Evidence；Evidence 只包含关系摘要和 1-based 范围，不保存源码。相同 source/target/kind 合并为一个 Dependency，Evidence ID 去重并排序。

## 确定性

- 文件、模块、类型、诊断和最终领域集合都按规范路径或稳定 ID 排序。
- ID 继续使用 S2 的 `schemaVersion + project identity + entity kind + language + repository path + qualified name` SHA-256 规则。
- Dependency identity 使用 source qualified name、kind 和 target qualified name；Evidence identity再加入精确范围。
- 测试反转发现文件顺序后比较完整 `JavaScanResult`，并重复生成 S2 报告 JSON 比较字节。

## 诊断与安全失败

`JavaParseDiagnostic` 是 S3 解析边界的内部结果，不修改已发布的 `0.1.0` Schema。错误码、仓库相对路径、1-based 行列和安全消息稳定排序。JavaParser 原始错误文本不会透传，因为它可能包含源码或主机信息。

关键错误包括：非法 UTF-8、非法语法、重复类型、不可读项、符号链接、特殊文件和路径逃逸。文件数、单文件大小、总字节或深度超限属于整次扫描失败，使用 `JavaScanException`，不伪装成 Finding。

## 验证、兼容与回滚

测试覆盖单/多模块、class/interface/enum/record/annotation、成员类型、继承/实现/类型引用、跨模块关系、精确位置、非法语法、路径与资源上限、发现顺序扰动和规范 JSON 重复性。架构测试证明 JavaParser 不泄漏到 domain/report，Maven Enforcer继续限制 Platform、Spring、数据库和模型 SDK。

S3 不改变 `0.1.0` 契约，因此消费者无需迁移。发布顺序为 Scanner 实现与提供方测试先合入，再更新 Docs 的 Feature Spec 和路线图状态。回滚时先撤销 Docs 的 S3 状态，再整体撤销 Scanner S3；S2 Schema 和模型保持可用。
