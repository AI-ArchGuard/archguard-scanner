# Changelog

所有重要变更记录在此文件。版本遵循语义化版本；项目开发期从 `0.x.y` 开始。

## [Unreleased]

### Added

- 初始化仓库治理、协作和质量基线。
- 采用 Apache License 2.0，并在 CI 中固定标准许可证校验和。
- 完成阶段 1 S1 的本地实现与验证：Java 21/Maven Wrapper 聚合构建、五模块骨架、依赖禁用门禁和反应堆架构测试；远端验收待完成。
- 修复 Maven Wrapper 3.3.4 在 Windows 非符号链接 `.m2` 目录上的空目标索引问题。
