# 归档说明

这里不是现行手册。读系统请从 [../文档导航.md](../文档导航.md) 进入。

| 子目录 | 原来在哪 | 内容 |
|---|---|---|
| `历史算法/` | `docs/AI_ALGORITHMS/` | 已下线的 RF/SBA、动态法 SS |
| `需求与草稿/` | 仓库根目录、`plan/`、部分 docs | 需求稿、过程笔记、旧提示词 |
| `旧原型/` | 仓库根目录 | `smart_replenishment.py`、`predictive_maintenance.py`、`analyze.py` |
| `legacy_sources/` | 一直在 archive | 算法实验源稿 |
| `fixtures/` | 仓库根目录 | `test_spare_parts.xlsx` |

现行数据库字段只维护 [../DATABASE/数据模型.md](../DATABASE/数据模型.md) 一份。FIFO 现行文是 [../IMPLEMENTATION/FIFO出库指南.md](../IMPLEMENTATION/FIFO出库指南.md)。

`python-ai-service` 的 `legacy_bridge.py` 仍从 `旧原型/` 加载那两个 py，不要随便改文件名。
