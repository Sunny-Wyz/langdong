---
name: troubleshoot-merge-issues
description: 解决多分支合并(Merge)后，新增模块菜单变灰色显示“开发中”、点击白屏或报错、前端路由丢失等集成报错问题。
---

# 模块合并后前端显示异常排查指南

在将远端分支（如 `master`）的大型新模块合并到本地开发分支后，可能会遇到原本在远端运行良好的页面，在本地启动后发生丢失、白屏、404 或菜单降级显示为“开发中”。本指南总结了此类问题的标准排查与修复流程。

## 1. 菜单显示为“开发中”或不生效

### 现象表现
侧边栏看到了新增的模块一级目录（例如“AI智能分析”、“采购管理”等），但没有展开任何子菜单，或者点击后标记为“开发中”，无法跳转。

### 原因分析
远端开发者提供的特定模块 SQL 脚本（如 `ai_module.sql`）在本地可能因为主键冲突、外键受限或由于之前执行失败产生残留数据，导致新的真实子菜单在 `menu` 和 `role_menu` 表中未能成功建立。如果后端传给前端的菜单数据是一个没有子路由的“空目录”，前端代码兜底将其渲染成了“开发中”状态。

### 解决办法
1. **排查并清理残留数据**：如果某模块的建库脚本执行曾报错退出（如报 `duplicate key role_menu.PRIMARY` 主键冲突），需手动通过路径前缀把残留的脏数据清理掉。例如重置 AI 模块：
   ```sql
   USE spare_db;
   DELETE FROM role_menu WHERE menu_id IN (SELECT id FROM menu WHERE path LIKE '/ai%');
   DELETE FROM menu WHERE path LIKE '/ai%';
   ```
2. **重新运行单独模块的 SQL**：清理完成后，单独把该新模块的 `xxx_module.sql` 拿到终端重新执行，确保没有抛出任何 `Warning` 或 `Error`。
   ```bash
   mysql -u root -p123456 --default-character-set=utf8mb4 spare_db < sql\ai_module.sql
   ```

## 2. 菜单加载正常但点击后变成白屏 (或 404)

### 现象表现
侧边栏的子模块路径能正常拼出并显示（例如 `/home/purchase-orders`），但点击后主内容区完全白屏，网络面板里没有发现任何请求，甚至导致整个前端卡死。

### 2.1 原因一：前端路由配置 (router) 在合并时丢失
在解决 Git 冲突时，前端的路由总线文件 `src/router/index.js` 合并有误。这会导致新模块写好的 `xxx.vue` 组件确确实实存在于磁盘的 `views` 目录下，但 Vue Router 并没有把它们注册进路由表里。由于匹配不到注册的组件，前端直接渲染成空白。

**解决办法**：
- 把远端分支的路由表检出用于对比（或者用代码合并工具）：`git show origin/master:frontend/src/router/index.js`
- 手动在本地的 `router/index.js` 中补齐缺失的那部分 `import` 引入代码，以及 `children: [...]` 内的具体路由字典。

### 2.2 原因二：缺失新模块引入的 npm 依赖 (导致 Webpack 编译错误)
合并的代码中引入了全新的第三方库（例如报表看板引入了 `echarts`），但是拉取代码时你只拿到了 `.vue` 源码，你本地物理机的 `node_modules` 并没有打包并下载这个库（或你忽略了 `package.json` 的更新直接启动）。此时 Vue 底层的 webpack 编译会抛出 `Module not found` 错误，一旦存在编译级错误，前端会白屏或拒绝更新。

**解决办法**：
1. **精准定位错误日志**：由于控制台信息一直在滚动，你需要仔细查看 `npm run serve` 的错误输出日志，寻找 `ERROR in ./src/views/...` 等提示，或者写一段脚本从日志文件里过滤出 `Can't resolve 'echarts'` 之类的缺失库名。
2. **补装缺少的依赖**：
   ```bash
   cd frontend
   npm install echarts --save
   ```
3. **彻底重启前端服务**：装完 npm 包后，部分热更新可能会失效，务必在终端里 `Ctrl+C` 杀掉原进程，然后再重新运行 `npm run serve`，确认控制台最终输出 `App running at: ...` 而不是任何 Error。
