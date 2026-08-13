---
name: langdong-conventions
description: Use when working in the langdong spare-parts repo on features, bugs, reviews, or onboarding. Triggers: Vue 2, Element UI, Vuex, RF, SBA, Java 17, npm run serve, vue.config.js, 8001 as default Python port.
---

# 朗动项目约定

细节以 `CLAUDE.md`、根 `README.md`、`docs/文档导航.md` 为准。本技能只锁**容易写错的现行口径**。

## 现行栈

| 端 | 用这个 | 不要再用 |
|---|---|---|
| 后端 | Java 21、Spring Boot 3.2、虚拟线程 | Java 17 |
| 前端 | Vue 3.4 + TS + Vite + Element Plus + Pinia | Vue 2、Element UI、Vuex、Vue CLI |
| 启动前端 | `cd frontend && npm run dev` | `npm run serve`、`vue.config.js` |
| 本地一键 | `./scripts/start_all.sh` | 各端端口自己猜 |
| Java | `:8080` | |
| 前端 | `:3000`（代理 `/api` → 8080） | |
| Python | 与 `AI_PYTHON_BASE_URL` 对齐，脚本默认 **8000** | 把 8001 写成现行默认 |

## 预测

生产主算法是 **两阶段 Hurdle-Gamma**（`algo_type=TWO_STAGE`），编排在 `forecast/`，训练/推理/蒙特卡洛在 Python。

已删除、禁止再当入口：`AiFeatureService`、`RandomForestServiceImpl`、`SbaForecastServiceImpl`。

## 记录

- 工作计划用中文。
- 排错写入 `QA.md`（`Q - 问题 - 解决方案`）。
- 新业务功能写入 `function.md`（`F - 功能描述 - 落实情况`）。
- 不要提交真实 DB 密码、JWT、回调 token。

## 文档

正式文档中文文件名。从 `docs/文档导航.md` 进。不要把 `docs/archive/`、论文、毕业论文、`function.md`/`QA.md` 历史条目改写成“当前实现”。
