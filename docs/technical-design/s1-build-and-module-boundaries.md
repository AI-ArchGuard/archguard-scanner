# S1 Technical Design：构建与模块边界

- 状态：Implemented locally（远端验收待完成）
- 日期：2026-09-10
- 对应需求：`archguard-docs/requirements/scanner-v0.2-feature-spec.md`
- 对应阶段：阶段 1，切片 S1

## 目标

建立可重复的 Java 21/Maven 聚合构建、五个真实模块和持续执行的模块依赖关卡，不在 S1 提前设计 S2 统一模型或制造占位业务类型。

## 模块与允许依赖

```text
scanner-domain
   ↑        ↑        ↑
parser   rule-engine report
   \        |        /
            ↓
           cli
```

精确规则：

- `scanner-domain` 没有生产依赖。
- `scanner-parser-java`、`scanner-rule-engine`、`scanner-report` 只能依赖 `scanner-domain`。
- `scanner-cli` 是组合根，可以依赖其余四个模块。
- 所有 Scanner 模块禁止依赖 Platform、Spring、数据库驱动和模型 SDK。
- S1 只创建模块 POM 和边界测试；生产源码从 S2/S3 的已接受设计开始加入。

## 构建选择

| 组件 | 版本/范围 | 用途 | 许可证 | 选择与替代 |
|---|---|---|---|---|
| Java | 21 | 编译与运行基线 | GPLv2 + Classpath Exception（Temurin 分发） | 与 Platform 一致；Java 17 会造成双基线维护 |
| Maven Wrapper | 3.3.4，Maven 3.9.16，SHA-256 固定 | 可重复构建且不依赖本机 Maven | Apache-2.0 | 复用已验证的 Platform 版本；Gradle 会增加第二套构建约定 |
| Maven Enforcer | 3.6.3 | Java/Maven 版本、依赖收敛和禁用依赖关卡 | Apache-2.0 | POM/CI 手写脚本更脆弱，且无法统一作用于每个模块 |
| JUnit Jupiter | 6.1.1，仅 test scope | 执行反应堆结构和模块依赖测试 | EPL-2.0 | 可用 Maven Invoker 替代，但会增加测试项目和插件复杂度 |
| Compiler/Surefire | 3.15.0 / 3.5.5 | Java 21 编译和 JUnit Platform 执行 | Apache-2.0 | 使用 Maven 隐式默认版本不可重复 |

S1 没有运行时第三方依赖。JUnit 只存在于 `scanner-cli` 测试作用域，不进入 Scanner 制品传递依赖。

## 边界验证

`ReactorArchitectureTest` 从 POM 读取事实并验证：

1. 根反应堆恰好声明规范规定的五个模块。
2. 内部模块依赖只沿允许方向出现。
3. `scanner-domain` 没有生产依赖。
4. 所有模块没有 Platform、Spring、数据库或模型 SDK 生产依赖。

Maven Enforcer 在 `validate` 阶段再次对每个模块执行 Java/Maven 版本、依赖收敛、重复声明和禁用依赖检查。两层关卡分别保护反应堆设计和最终解析后的依赖图。

## 失败与回滚

- Wrapper 下载必须校验 Maven 分发 SHA-256；校验失败时构建失败关闭。
- 任一模块增加禁止依赖时 `mvn verify` 失败，不能通过跳过测试进入主分支。
- S1 不含数据库、外部接口或持久化迁移；回滚只需整体撤销 Scanner 构建骨架，不影响其他仓库运行时。

## 验收

- Windows 和 Linux 入口分别为 `mvnw.cmd verify` 与 `./mvnw verify`。
- 反应堆包含父工程加五个模块，全部构建成功。
- 架构测试进入 Surefire 报告并全部通过。
- CI 使用 Java 21 执行同一 Wrapper 命令。
- README、CHANGELOG 和阶段状态准确区分 S1 本地实现、远端验收和 S2 尚未开始。

## 实施验证

- `mvnw.cmd --batch-mode --no-transfer-progress verify`：通过。
- `mvnw.cmd --offline --batch-mode --no-transfer-progress verify`：通过，证明缓存齐全后可离线复验。
- 反应堆：父工程和五个模块全部成功。
- `ReactorArchitectureTest`：3 tests，0 failures，0 errors，0 skipped。
- Maven Enforcer：Java/Maven 版本、依赖收敛、重复依赖声明和禁止依赖规则全部通过。
- Windows 脚本沿用 Platform 已验证的 `.m2` 符号链接目标空值保护；不改变 Maven 分发地址或 SHA-256 校验。
