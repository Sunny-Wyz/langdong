---
name: langdong-docs
description: Use when writing, updating, renaming, or reviewing markdown docs in the langdong repo. Triggers: README, 文档导航, CLAUDE.md, 算法文档, 过期文档, 中文文件名.
---

# 朗动文档约定

## 现行入口

- 总览：根 `README.md`
- 索引：`docs/文档导航.md`（`docs/README.md` 只做跳转）
- 预测：`docs/AI_ALGORITHMS/两阶段Hurdle-Gamma与蒙特卡洛.md`

## 写正式文档时

1. **文件名用中文**（目录英文名可保留：`ARCHITECTURE/`、`API/` 等）。
2. 改完后更新 `docs/文档导航.md` 和根 README 的链接。
3. 把 RF/SBA、Vue 2、Java 17、`npm run serve`、`vue.config.js`、Python 默认 8001 写成**现状**的，改掉或标明历史。
4. `algo_type` 现行值：`TWO_STAGE` / `FALLBACK`。

## 不要改写成现行实现

| 路径 | 原因 |
|---|---|
| `docs/archive/` | 源稿与已下线算法 |
| `docs/paper-aaai2026/` | 论文 |
| `docs/schoolthesis/` | 毕业论文 |
| `function.md`、`QA.md` | 当时日志，不改历史条目 |
| `docs/archive/需求与草稿/` | 根目录迁入的需求稿、旧计划、提示词 |
| `docs/archive/旧原型/` | 根目录迁入的旧 Python 脚本 |

历史算法已在 `docs/archive/历史算法/`。若必须引用，文首写「已归档 / 非生产」。

## CLAUDE.md

给 Agent 看的现行地图。技术栈、端口、预测入口必须与代码一致；**不要改这个文件名**。
