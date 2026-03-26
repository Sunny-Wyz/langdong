# API 调用示例与联调手册

最后更新: 2026-03-26

---

## 1. 最小联调链路

目标: 5 分钟完成一次从登录到业务查询的联调。

## 步骤 1: 登录获取 token

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'
```

预期: 返回 token 字段。

## 步骤 2: 带 token 查询补货建议

```bash
curl -s "http://localhost:8080/api/reorder-suggests?status=待处理" \
  -H "Authorization: Bearer <token>"
```

## 步骤 3: 触发一次 AI 预测

```bash
curl -s -X POST http://localhost:8080/api/ai/forecast/trigger \
  -H "Authorization: Bearer <token>"
```

---

## 2. 业务链路示例

## 2.1 分类链路

1. 触发分类重算
2. 拉取分类结果分页
3. 查看某备件分类历史

```bash
curl -s -X POST http://localhost:8080/api/classify/trigger \
  -H "Authorization: Bearer <token>"

curl -s "http://localhost:8080/api/classify/result?page=1&pageSize=20&month=2026-02" \
  -H "Authorization: Bearer <token>"
```

## 2.2 预测链路

1. 触发预测
2. 查询预测结果
3. 查看指定备件历史预测

```bash
curl -s -X POST http://localhost:8080/api/ai/forecast/trigger \
  -H "Authorization: Bearer <token>"

curl -s "http://localhost:8080/api/ai/forecast/result?page=1&size=20&month=2026-03" \
  -H "Authorization: Bearer <token>"
```

## 2.3 FIFO 追溯链路

1. 对领用明细查询追溯
2. 对入库批次查询使用记录

```bash
curl -s "http://localhost:8080/api/outbound-trace/requisition-item/10001" \
  -H "Authorization: Bearer <token>"

curl -s "http://localhost:8080/api/outbound-trace/stock-in-batch/8" \
  -H "Authorization: Bearer <token>"
```

---

## 3. 常见错误示例

## 3.0 快速错误矩阵（联调高频）

| HTTP | 常见触发点 | 典型根因 | 第一处理动作 |
|---|---|---|---|
| 400 | 登录/查询参数格式错误 | JSON 不合法、参数类型不匹配 | 对照 DTO 字段与类型 |
| 401 | 任意需认证接口 | token 缺失、无效或过期 | 重新登录并替换 token |
| 403 | 触发类接口（分类/预测） | 角色未分配权限点 | 检查角色菜单与 permission |
| 422 | 业务动作接口（出库/审批） | 业务校验不通过（已配置异常映射） | 检查库存、状态流、必填值 |

## 3.1 400 参数错误

请求示例（错误 JSON）:

```bash
curl -i -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":123456'
```

典型响应:

```json
{
  "code": 400,
  "message": "Bad Request"
}
```

排查要点:
- 请求体是否为合法 JSON
- 字段类型是否匹配后端定义（例如 password 应为字符串）

## 3.2 401 未授权

请求示例（缺少 token）:

```bash
curl -i "http://localhost:8080/api/reorder-suggests?status=待处理"
```

典型响应（已配置异常映射时）:

```json
{
  "code": 401,
  "message": "Unauthorized"
}
```

排查要点:
- 是否携带 Authorization: Bearer <token>
- token 是否来自最近一次登录

## 3.3 403 无权限

请求示例（低权限账号触发管理接口）:

```bash
curl -i -X POST http://localhost:8080/api/classify/trigger \
  -H "Authorization: Bearer <token>"
```

典型响应:

```json
{
  "code": 403,
  "message": "Forbidden"
}
```

排查要点:
- 当前账号是否绑定 classify:trigger:run 或对应菜单按钮权限
- 角色变更后是否重新登录

## 3.4 404 资源不存在

```json
{
  "code": 404,
  "message": "Not Found"
}
```

原因:
- 路径参数错误或数据不存在

## 3.5 409 状态冲突

典型场景:
- 已完成单据重复审批
- 已关闭工单重复变更

## 3.6 422 业务校验失败

请求示例（库存不足场景）:

```bash
curl -i -X PUT http://localhost:8080/api/requisitions/10001/outbound \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"items":[{"itemId":20001,"outQty":99999}]}'
```

典型响应:

```json
{
  "code": 422,
  "message": "业务校验失败"
}
```

排查要点:
- 出库数量是否超过可用库存
- 单据状态是否满足当前动作前置条件
- 必填字段是否齐全

备注:
- 若当前环境未配置统一业务异常映射，业务校验失败也可能返回 500，请结合后端日志确认。

---

## 4. 联调排查清单

1. 是否拿到最新 token
2. 请求头是否包含 Authorization
3. 请求体字段名是否与后端 DTO 一致
4. 日期/月度格式是否符合 yyyy-MM 或 datetime 要求
5. 分页参数是否从 page=1 开始
6. 当前账号是否具备对应权限
7. 是否命中前置状态约束

---

## 5. Postman 建议集合

建议建立如下 folder:
- Auth
- Classify
- Forecast
- Reorder Suggest
- FIFO Trace
- Dashboard Report

环境变量:
- baseUrl = http://localhost:8080
- token = <登录后自动写入>

---

维护人: 联调与测试团队
版本: 1.0
