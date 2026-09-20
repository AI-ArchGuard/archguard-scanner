# Changelog

所有重要变更记录在此文件。版本遵循语义化版本；项目开发期从 `0.x.y` 开始。

## [Unreleased]

## [0.2.1] - 2026-09-20

### Added

- 增加 `validate-rules <rules.yaml>` 命令，供 Platform 在保存 RuleSetVersion 前复用 Scanner 的严格 YAML 和强类型配置校验。

### Changed

- 将候选版本提升为 `0.2.1`；Scanner Result Schema 和 Rules Schema 均保持 `0.1.0`。

## [0.2.0] - 2026-09-19

### Added

- 初始化仓库治理、协作和质量基线。
- 采用 Apache License 2.0，并在 CI 中固定标准许可证校验和。
- 完成阶段 1 S1：Java 21/Maven Wrapper 聚合构建、五模块骨架、依赖禁用门禁和反应堆架构测试，并通过 PR 与合并后 `main` 的托管 CI 验收。
- 修复 Maven Wrapper 3.3.4 在 Windows 非符号链接 `.m2` 目录上的空目标索引问题。
- 实现阶段 1 S2：语言无关不可变模型、仓库相对路径约束、SHA-256 稳定 ID、规范排序、契约 DTO、严格 JSON 序列化和 `0.1.0` Draft 2020-12 Schema。
- 增加完整/最小有效报告，以及缺失字段、错误版本/枚举、非法 ID/位置、未知字段、非法扩展、绝对路径和悬空引用等失败关闭测试资源。
- 实现阶段 1 S3：Maven `src/main/java` 文件发现、Java 21 语法解析、模块/类型建模，以及项目内继承、实现和类型引用到 Dependency/Evidence 的确定性映射。
- 增加非法语法、UTF-8、路径/链接、文件数/大小/深度限制、单/多模块、嵌套类型、跨模块引用和重复扫描字节稳定性测试。
- 实现阶段 1 S4：只读确定性依赖图，以及非法包依赖、分层架构和组件/包/模块循环三条结构规则。
- 为 Java Component 增加 `java.package` 命名空间扩展，并增加图预算、严格强类型配置、稳定 Finding/fingerprint、Evidence 闭合和解析到契约 JSON 的端到端重复性测试；`0.1.0` Schema 保持不变。
- 实现阶段 1 S5：方法/构造器 Component、限定注解与声明 Evidence、安全 Maven POM 直接依赖、圈复杂度 Metric，以及剩余五条确定性规则。
- 增加规则 Diagnostic、默认 severity 与允许覆盖边界，并覆盖 Spring 同名注解、模块边界、类型/包/Maven 禁止选择器、复杂度阈值、生成代码、必要注解和 POM 失败关闭测试；`0.1.0` Schema 保持不变且未新增生产依赖。
- 实现阶段 1 S6：可执行 `scan` CLI、严格且版本化的 YAML 规则配置及参数 Schema、八条规则的强类型映射、稳定 Diagnostic 呈现，以及成功/违规/输入错误/扫描失败四类退出码。
- 增加输出大小门禁和同目录临时文件原子替换，生成可运行的 shaded JAR，并覆盖配置失败关闭、字节稳定性、Schema、退出码和写入安全测试；Scanner Result `0.1.0` Schema 保持不变。
- 完成阶段 1 S7：固定 `archguard-samples` 合并提交，验证三个合成项目、三个失败夹具、黄金 SHA-256、三次独立进程字节复现与单次性能上限。
- 增加可配置总扫描时限、`v0.2.0` release workflow、可执行 JAR 校验和、发布说明和回滚顺序；Scanner Result `0.1.0` Schema 保持不变。
