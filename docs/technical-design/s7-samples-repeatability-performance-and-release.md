# S7 Samples、可复现性、性能基线与发布设计

## 状态

- 状态：Implemented，最终验收以 Scanner PR、合并后 `main` CI 和 `v0.2.0` Release workflow 为准
- Scanner 版本：`0.2.0`
- Scanner Result Schema：`0.1.0`，本切片不修改
- Rules YAML Schema：`0.1.0`
- 固定 Samples 提交：`7b93248bf67619a6b26c1d428bcf92d1fe3a781b`
- 关联 Issue：<https://github.com/AI-ArchGuard/archguard-scanner/issues/17>
- Samples PR：<https://github.com/AI-ArchGuard/archguard-samples/pull/3>

## 目标与边界

S7 关闭阶段 1 的可发布性门禁：以公开、合成且无客户源码的项目固定八条规则的行为；对成功、策略违规和失败输入记录退出码与黄金报告；在独立进程中重复三次验证 JSON 字节一致；记录每次执行的观测时间并施加上限；最后从已验收的提交发布带校验和的 CLI JAR。

本切片不引入 Platform、Gateway、Evals、远程服务、外部类型求解、传递依赖解析或新的报告字段。性能数据是固定小型合成样例的回归门禁，不是大型仓库吞吐承诺。

## 样例所有权与固定方式

`archguard-samples` 拥有三个项目级样例和三个失败夹具：

- `java-clean-layered`：八条规则同时启用，退出码 `0`，零 Finding。
- `java-architecture-violations`：八条规则的代表性违规，退出码 `2`，13 个 Finding。
- `java-dependency-cycle`：两个稳定环，退出码 `2`，2 个 Finding。
- `invalid-syntax`：退出码 `70`，写入 Schema 有效的部分报告并输出稳定 Diagnostic。
- `unknown-rule`：退出码 `64`，不写报告。
- `resource-limit`：退出码 `70`，不写报告。

Scanner CI 不跟随 Samples 的浮动分支，而是检出已验收合并提交。升级样例必须先合并 Samples，再在 Scanner PR 中显式更新 SHA、重新生成证据并完成评审。

## 确定性与性能门禁

Samples manifest 固定 Scanner `0.2.0`、Result Schema `0.1.0`、预期退出码、Finding 数量、报告存在性、stderr code、重复次数和单次上限。验证器执行以下检查：

1. 静态验证 manifest、仓库相对路径、黄金 JSON、稳定排序与 SHA-256 digest。
2. 对四个会产生报告的场景运行三次独立 CLI 进程，逐字节比较输出并与黄金文件比较。
3. 对全部六个场景验证退出码、报告存在性、错误 code 与绝对路径不泄漏。
4. 每次运行由外层 `15s` 超时约束，并把最大观测时间写入 `target/s7-results.json`。

CI 的观测结果只用于发现明显回归。不同 runner 的硬件和负载不可直接横向比较，因此不声明平均吞吐或百分位延迟。

## CLI 总时限

Rules `limits.maxDurationSeconds` 接受 `1..3600`，默认 `300`。文件发现、Java/Maven 事实提取、规则执行和报告序列化在单个守护工作线程内完成；调用线程只在分析成功后执行原子报告写入。超时会中断工作、输出 `scanner.limit.duration`、返回 `70`，且不会创建或替换目标报告。这样即使底层库不能立即响应中断，超时任务也不能晚到写入报告，且不会阻止 CLI 进程退出。

外层样例超时独立于 CLI 配置，用于覆盖进程启动、配置读取和退出全过程。

## 发布与供应链

合并后 `main` CI 成功才创建轻量 tag `v0.2.0`。tag 触发的 release workflow 从 tag 重新构建并运行完整 Maven 与固定 Samples 门禁，生成 `archguard-scanner.jar.sha256`，然后发布 JAR、校验和和仓库内 release notes。工作流只使用按完整提交 SHA 固定的官方 GitHub Actions；发布 job 是唯一拥有 `contents: write` 的工作流。

## 安全与隐私

- 样例源码全部为公开合成内容，不包含真实客户代码、个人数据或密钥。
- 黄金报告仅包含仓库相对路径和声明位置，不保存完整源码。
- CI 不执行样例 Maven、不下载样例依赖，也不解析传递依赖。
- 超时、资源门禁和输出大小门禁失败关闭，错误信息不暴露宿主绝对路径。

## 兼容与回滚

兼容顺序为 Samples 合并提交 → Scanner CI 固定 SHA → Scanner `main` CI → `v0.2.0` tag/Release → Docs 验收状态。Result Schema 保持 `0.1.0`，因此既有消费者无需迁移。

回滚时先撤销 Docs 的已发布状态；如 Release 内容有问题，标记 Release/tag 不可用并整体回滚 Scanner S7 提交；最后仅在样例本身错误时回滚 Samples。禁止只修改黄金报告来掩盖行为变化。
