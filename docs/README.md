# 📚 备件管理系统文档导航索引

**最后更新**: 2026-03-26  
**文档版本**: 1.0  
**维护人**: AI 文档团队  

---

## 🗂️ 快速导航

### 🏗️ 架构设计（ARCHITECTURE）

| 文档 | 内容 | 适用角色 |
|------|------|--------|
| [系统总体架构](ARCHITECTURE/SYSTEM_OVERVIEW.md) | 9 个核心模块、数据流、参与者权限矩阵 | 所有人 |
| [模块详细流程](ARCHITECTURE/MODULE_DETAILS.md) | 各模块完整业务流程、交互说明 | 实施人员、测试人员 |
| [Use Case 用例](ARCHITECTURE/SYSTEM_OVERVIEW.md#4-参与者与权限矩阵) | 6 个角色 × 30+ 现实业务场景 | 需求分析、测试设计 |

### 💾 数据库（DATABASE）

| 文档 | 内容 | 适用角色 |
|------|------|--------|
| [数据模型速查](DATABASE/DATA_MODEL.md) | 20 个表、字段说明、查询模板 | DBA、后端开发 |
| [FIFO 批次表](DATABASE/DATA_MODEL.md#stock_in_item---入库批次fifo-核心) | `stock_in_item`、`biz_outbound_batch_trace` 详解 | FIFO 开发者 |
| [AI 算法表](DATABASE/DATA_MODEL.md#🤖-ai-表uc-ai) | 预测、分类数据表 | AI 工程师 |

### 🔌 API 文档（API）

| 文档 | 内容 | 适用角色 |
|------|------|--------|
| [API 参考手册](API/API_REFERENCE.md) | 核心接口、权限、状态码、入参出参规范 | 前后端开发、测试 |
| [API 联调示例](API/API_EXAMPLES.md) | curl 联调链路、错误示例、排查清单 | 联调人员、测试 |

### 🛠️ 实现指南（IMPLEMENTATION）

| 文档 | 内容 | 推荐顺序 |
|------|------|--------|
| [FIFO 出库完全指南](IMPLEMENTATION/FIFO.md) | 批次管理、追溯、并发安全、性能优化 | 🥇 |
| [密码加密与审计日志](IMPLEMENTATION/Security_Guide.md) | BCrypt 集成、AOP 日志拦截、审计表 | 🥈 |

### 🚦 运维与发布（OPS）

| 文档 | 内容 | 适用角色 |
|------|------|--------|
| [生产部署流程](OPS/DEPLOYMENT_PRODUCTION.md) | 生产发布、验收与回滚流程 | 后端、运维 |
| [灾备与恢复方案](OPS/DISASTER_RECOVERY.md) | RPO/RTO、备份恢复、演练机制 | 运维、DBA |
| [性能检查清单](OPS/PERFORMANCE_CHECKLIST.md) | 性能指标、压测验收、回归模板 | 后端、测试 |

### 🤖 AI 算法（AI_ALGORITHMS）

| 文档 | 内容 | 适用角色 |
|------|------|--------|
| [算法选型总结表](AI_ALGORITHMS/SUMMARY.md) | RF vs SBA vs FALLBACK 对比、ABC×XYZ 矩阵 | 产品、分析人员 🎯 |
| [需求预测链路](AI_ALGORITHMS/FORECASTING.md) | 特征工程、模型训练、补货建议触发 | AI 工程师 |
| [安全库存计算](AI_ALGORITHMS/SAFETY_STOCK.md) | 动态法 vs 固定法、参数调优 | AI 工程师、库存经理 |
| [RF vs SMA 对比](AI_ALGORITHMS/RESULTS/RF_vs_SMA.md) | 8 个月真实数据验证结果 | 审批人员 |

---

## 🎯 按角色进阅读指南

### 👨‍💼 产品经理
1. [系统总体架构](ARCHITECTURE/SYSTEM_OVERVIEW.md) — 理解功能定位
2. [AI 算法选型表](AI_ALGORITHMS/SUMMARY.md) — 理解智能特性

### 👨‍💻 后端开发

**入门路径**：
1. [系统总体架构](ARCHITECTURE/SYSTEM_OVERVIEW.md) — 全局认知
2. [数据模型速查](DATABASE/DATA_MODEL.md) — 表结构理解
3. [模块详细流程](ARCHITECTURE/MODULE_DETAILS.md) — 交互细节

**特定功能**：
- **FIFO 开发** → [FIFO 完全指南](IMPLEMENTATION/FIFO.md)
- **AI 预测** → [需求预测链路](AI_ALGORITHMS/FORECASTING.md)
- **用户认证** → [密码加密与审计](IMPLEMENTATION/Security_Guide.md)

### 🧪 测试人员
1. [系统总体架构](ARCHITECTURE/SYSTEM_OVERVIEW.md) — 理解模块交互
2. [模块详细流程](ARCHITECTURE/MODULE_DETAILS.md) — 理解业务流程
3. [FIFO 测试场景](IMPLEMENTATION/FIFO.md#-测试验证) — 参考测试用例

### 📊 数据库管理员
1. [数据模型速查](DATABASE/DATA_MODEL.md) — 全表导览
2. [FIFO 核心索引](DATABASE/DATA_MODEL.md#stock_in_item---入库批次fifo-核心) — 性能优化
3. [表关系图](DATABASE/DATA_MODEL.md)（含 FK） — 完整性约束

### 📈 库存经理 / 采购经理
1. [AI 算法选型](AI_ALGORITHMS/SUMMARY.md) — 理解补货逻辑
2. [需求预测链路](AI_ALGORITHMS/FORECASTING.md) — 理解预测过程

---

## 🧭 按业务场景跳转

| 业务场景 | 推荐入口 | 补充文档 |
|---|---|---|
| 新用户登录与权限问题排查 | [API 参考手册-登录](API/API_REFERENCE.md#21-登录) | [API 联调示例](API/API_EXAMPLES.md) |
| 领用出库后追溯批次来源 | [API 参考手册-FIFO追溯](API/API_REFERENCE.md#51-查询领用单明细的批次追溯) | [FIFO 完全指南](IMPLEMENTATION/FIFO.md) |
| 手动触发分类并查看 ABC×XYZ 结果 | [API 参考手册-分类接口](API/API_REFERENCE.md#31-手动触发分类重算) | [算法选型总结](AI_ALGORITHMS/SUMMARY.md) |
| 手动触发 AI 预测并查看结果 | [API 参考手册-预测接口](API/API_REFERENCE.md#33-手动触发-ai-预测) | [需求预测链路](AI_ALGORITHMS/FORECASTING.md) |
| 库存低于阈值后补货处理 | [API 参考手册-补货建议](API/API_REFERENCE.md#41-查询补货建议) | [安全库存计算](AI_ALGORITHMS/SAFETY_STOCK.md) |
| 联调时出现 401/403/422 | [API 联调示例-错误示例](API/API_EXAMPLES.md#3-常见错误示例) | [API 参考手册-错误码](API/API_REFERENCE.md#7-错误码参考) |
| 准备生产发布与回滚演练 | [生产部署流程](OPS/DEPLOYMENT_PRODUCTION.md) | [灾备与恢复方案](OPS/DISASTER_RECOVERY.md) |
| 线上性能退化定位与验收 | [性能检查清单](OPS/PERFORMANCE_CHECKLIST.md) | [jmeter_report/index.html](../jmeter_report/index.html) |

---

## 📊 文档矩阵

```
功能模块            文档位置                           完成度    验证状态
─────────────────────────────────────────────────────────────────
系统设计            ARCHITECTURE/SYSTEM_OVERVIEW.md      ✅ 100%    ✅ 已验证
数据设计            DATABASE/DATA_MODEL.md               ✅ 100%    ✅ 已验证
FIFO 实现           IMPLEMENTATION/FIFO.md               ✅ 100%    ✅ 已验证
密码加密            IMPLEMENTATION/Security_Guide.md     ✅ 100%    ✅ 已验证
需求预测            AI_ALGORITHMS/FORECASTING.md         ✅ 100%    ✅ 已验证
算法选型            AI_ALGORITHMS/SUMMARY.md             ✅ 100%    ✅ 已验证
安全库存计算         AI_ALGORITHMS/SAFETY_STOCK.md        ✅ 100%    ✅ 已验证
RF vs SMA 对比      AI_ALGORITHMS/RESULTS/RF_vs_SMA.md   ✅ 100%    ✅ 已验证
模块详细流程         ARCHITECTURE/MODULE_DETAILS.md       ✅ 100%    ✅ 已验证
API 参考手册         API/API_REFERENCE.md                 ✅ 100%    ✅ 已验证
API 联调示例         API/API_EXAMPLES.md                  ✅ 100%    ✅ 已验证
生产部署流程         OPS/DEPLOYMENT_PRODUCTION.md         ✅ 100%    ✅ 已验证
灾备恢复方案         OPS/DISASTER_RECOVERY.md             ✅ 100%    ✅ 已验证
性能检查清单         OPS/PERFORMANCE_CHECKLIST.md         ✅ 100%    ✅ 已验证
```

---

## 🔍 按主题查询

### FIFO & 批次管理
- [FIFO 完全指南](IMPLEMENTATION/FIFO.md) ⭐ 核心
- [stock_in_item 表](DATABASE/DATA_MODEL.md#stock_in_item---入库批次fifo-核心)
- [批次追溯实现](IMPLEMENTATION/FIFO.md#-批次追溯表)
- [FIFO 测试用例](IMPLEMENTATION/FIFO.md#-测试验证)

### AI 与智能补货
- [算法选型决策树](AI_ALGORITHMS/SUMMARY.md#算法选择决策树) ⭐ 读这个
- [需求预测完整链路](AI_ALGORITHMS/FORECASTING.md) ⭐ 开发参考
- [安全库存计算指南](AI_ALGORITHMS/SAFETY_STOCK.md)
- [RF vs SMA 对比结果](AI_ALGORITHMS/RESULTS/RF_vs_SMA.md)
- [RF 特征工程](AI_ALGORITHMS/FORECASTING.md#特征工程)
- [补货建议自动触发](AI_ALGORITHMS/FORECASTING.md#-补货建议触发)

### 库存与出库
- [仓储模块说明](ARCHITECTURE/SYSTEM_OVERVIEW.md#23-仓储管理uc-wh)
- [库存预警查询](DATABASE/DATA_MODEL.md#3-库存预警)
- [spare_part_stock 表](DATABASE/DATA_MODEL.md#spare_part_stock---总库存快照)

### 数据库设计
- [20 个核心表](DATABASE/DATA_MODEL.md) ⭐ 全清单
- [表关系（FK）](DATABASE/DATA_MODEL.md) ⭐ 完整性基础
- [查询模板](DATABASE/DATA_MODEL.md#📊-查询模板)

### 安全与审计
- [BCrypt 密码加密](IMPLEMENTATION/Security_Guide.md#-密码加密bcrypt)
- [AOP 审计日志](IMPLEMENTATION/Security_Guide.md#-aop-日志拦截)
- [安全最佳实践](IMPLEMENTATION/Security_Guide.md#-安全最佳实践)

### 发布与运维
- [生产部署流程](OPS/DEPLOYMENT_PRODUCTION.md)
- [灾备与恢复方案](OPS/DISASTER_RECOVERY.md)
- [性能检查清单](OPS/PERFORMANCE_CHECKLIST.md)

---

## 📝 文档约定

### 文档结构
- **标题**: 功能名称 + 版本日期
- **目录**: 快速导航目录（Markdown # 标题层级）
- **概述**: 业务背景、为什么重要
- **细节**: 实现代码、SQL、表设计
- **验证**: 测试结果、性能数据
- **维护**: 相关人员、下次更新时间

### 符号含义
- ✅ — 已完成、已验证、推荐
- ⏳ — 待完成、进行中
- 🔄 — 部分完成
- ⚠️ — 注意、有改进空间
- ❌ — 已弃用、不推荐
- ⭐ — 重要、必读

### 代码风格
- Java 示例遵循 Spring Boot 最新实践
- SQL 包含表设计和典型查询
- 使用代码块标注语言：```java, ```sql, ```xml

---

## 🚀 五分钟快速开始

**场景**: 我是新人，想快速上手这个系统

1. **5 分钟**: 读 [系统总体架构](ARCHITECTURE/SYSTEM_OVERVIEW.md#🎯-功能目标) — 了解系统有 9 个模块
2. **10 分钟**: 看 [FIFO 出库完全指南](IMPLEMENTATION/FIFO.md#-功能概述) 的前两节 — 了解核心创新
3. **5 分钟**: 浏览 [AI 算法选型表](AI_ALGORITHMS/SUMMARY.md#算法选择决策树) — 理解智能补货
4. **5 分钟**: 扫 [数据模型](DATABASE/DATA_MODEL.md#📋-表名导航) 的表导航 — 了解数据结构

**总耗时**: 25 分钟

---

## 📞 常见问题速查

| 问题 | 答案位置 |
|------|--------|
| FIFO 如何实现的？| [FIFO 完全指南→实现架构](IMPLEMENTATION/FIFO.md#-实现架构) |
| 如何计算补货点（ROP）？ | [需求预测→安全库存计算](AI_ALGORITHMS/FORECASTING.md#-安全库存与补货点计算) |
| 批次追溯怎么查？ | [数据模型→追溯查询](DATABASE/DATA_MODEL.md#批次追溯) |
| RF 和 SMA 哪个更好？ | [算法选型→表格](AI_ALGORITHMS/SUMMARY.md#需求预测算法对比) |
| 密码怎么加密的？ | [安全指南→BCrypt 集成](IMPLEMENTATION/Security_Guide.md#spring-security-bcrypt-集成) |
| 哪些操作会被记录？ | [安全指南→AOP 日志](IMPLEMENTATION/Security_Guide.md#-aop-日志拦截) |
| 接口 401/403 怎么排查？ | [API 联调示例→常见错误示例](API/API_EXAMPLES.md#3-常见错误示例) |
| 生产发布怎么执行？ | [生产部署流程](OPS/DEPLOYMENT_PRODUCTION.md) |
| 线上故障如何恢复？ | [灾备与恢复方案](OPS/DISASTER_RECOVERY.md) |
| 性能退化如何定位？ | [性能检查清单](OPS/PERFORMANCE_CHECKLIST.md) |

---

## 🔗 外部参考

| 技术 | 官方文档 | 备注 |
|------|--------|------|
| Spring Security | [docs.spring.io/...](https://docs.spring.io/spring-security/) | Password Encoder |
| Smile ML | [Smile中文文档](http://smile.readthedocs.io/) | Random Forest 算法库 |
| MyBatis | [mybatis.org](https://mybatis.org/mybatis-3/) | ORM 框架 |
| MySQL | [dev.mysql.com](https://dev.mysql.com/doc/) | 数据库索引优化 |

---

## 📅 维护计划

| 时间 | 任务 | 状态 |
|------|------|------|
| 2026-03-26 | 创建核心 5 份文档（架构、数据库、FIFO、安全、算法） | ✅ |
| 2026-03-26 | 完成模块详细流程、安全库存、算法对比文档 | ✅ |
| 2026-03-26 | 补充 API 文档、部署运维、灾备与性能清单 | ✅ |
| 2026-06-30 | 6 个月运营反馈，优化文档 | ⏳ |

---

## 💬 反馈与改进

如发现文档：
- ❓ 有误或不清楚 → 联系 AI 文档团队修正
- 📊 需要补充内容 → 提交改进需求
- 🐛 有代码示例错误 → 及时修复和下发

---

## 🗃️ 历史源稿归档说明

- 历史整合源稿已迁移至 `docs/archive/legacy_sources/`。
- 一次性工具脚本已迁移至 `docs/archive/tools/`。
- 当前维护与联调请以本 README 导航到的正式文档为准。

---

**最后更新**: 2026-03-26  
**下一次审阅**: 2026-06-30
