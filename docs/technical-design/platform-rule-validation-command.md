# Technical Design：Platform RuleSet 校验命令

- 状态：Implemented
- 日期：2026-09-19
- 对应阶段：阶段 2，切片 2C

## 目标

Platform 在持久化不可变 RuleSetVersion 前必须复用 Scanner 对 YAML `0.1.0` 的严格语法、Schema 和强类型语义校验，但不能导入 Scanner 内部类。Scanner `0.2.1` 因此增加：

```text
archguard validate-rules <rules.yaml>
```

## 行为

- 命令复用 `RuleConfigurationLoader`，不发现源码、不执行 Rule、不写报告。
- 合法配置输出固定成功摘要并返回 `0`。
- 缺少参数、非法路径、读取失败、Schema 或强类型配置失败均返回 `64`。
- 错误复用安全 Diagnostic，不输出主机绝对路径、源码、堆栈或凭据。
- 规则文件仍受 1 MiB、UTF-8、单文档、无 alias/tag、重复键和符号链接限制。

## 兼容性

该命令是 CLI 的向后兼容扩展。Scanner Result Schema 和 Rules Schema 保持 `0.1.0`，现有 `scan` 命令、退出码和黄金结果不变。发布版本提升为 `v0.2.1`，Platform 通过 JAR 版本和 SHA-256 固定消费者组合。

## 验证

- 有效配置返回 `0`，并报告规则数量。
- 无效 Schema、缺少参数和非法路径返回 `64`。
- 错误不包含临时目录绝对路径。
- 完整 Maven `verify` 继续覆盖五模块架构、八条规则和阶段 1 回归。
