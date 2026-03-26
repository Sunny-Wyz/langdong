# API 参考手册（v1）

最后更新: 2026-03-26
适用范围: 备件管理系统后端 REST API

---

## 1. 通用约定

Base URL:
- 开发环境: http://localhost:8080
- 统一前缀: /api

认证方式:
- Header: Authorization: Bearer <token>
- 登录成功后返回 token，后续接口携带该 token

时间格式:
- 日期时间统一 ISO 或 yyyy-MM-dd HH:mm:ss（按接口实际返回）
- 月份参数使用 yyyy-MM

分页约定:
- page: 从 1 开始
- size: 建议 10/20/50

通用响应建议:
- 成功: HTTP 200/201
- 客户端错误: 400/401/403/404/409/422
- 服务端错误: 500

---

## 2. 认证与授权

## 2.1 登录

- 方法: POST
- 路径: /api/auth/login
- 认证: 否

请求示例:
```json
{
  "username": "admin",
  "password": "123456"
}
```

成功示例:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "admin"
}
```

失败码:
- 401: 用户名或密码错误

请求字段:

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| username | string | 是 | 登录用户名 |
| password | string | 是 | 登录密码 |

响应字段:

| 字段 | 类型 | 说明 |
|---|---|---|
| token | string | JWT 访问令牌 |
| username | string | 当前登录用户 |

---

## 3. 智能分类与预测

## 3.1 手动触发分类重算

- 方法: POST
- 路径: /api/classify/trigger
- 权限: classify:trigger:run

成功:
```json
{
  "code": 200,
  "message": "重算任务已启动，请稍后刷新查看最新分类结果"
}
```

失败码:
- 401: 未登录
- 403: 无权限

请求字段:

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| body | object | 否 | 当前实现可空请求体 |

响应字段:

| 字段 | 类型 | 说明 |
|---|---|---|
| code | number | 业务状态码，当前实现成功为 200 |
| message | string | 触发结果描述 |

## 3.2 查询分类结果（分页）

- 方法: GET
- 路径: /api/classify/result
- 权限: classify:result:list
- 参数: abcClass, xyzClass, partCode, month, page, pageSize

成功示例:
```json
{
  "total": 50,
  "list": [
    {
      "partCode": "SP20001",
      "abcClass": "A",
      "xyzClass": "X",
      "safetyStock": 12,
      "reorderPoint": 20
    }
  ]
}
```

请求字段（Query）:

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | number | 否 | 页码，从 1 开始 |
| pageSize | number | 否 | 每页条数（默认 20） |
| abcClass | string | 否 | ABC 分类过滤（A/B/C） |
| xyzClass | string | 否 | XYZ 分类过滤（X/Y/Z） |
| month | string | 否 | 分类月份，yyyy-MM |
| partCode | string | 否 | 备件编码（支持模糊） |

响应字段:

| 字段 | 类型 | 说明 |
|---|---|---|
| total | number | 总记录数 |
| list[].partCode | string | 备件编码 |
| list[].abcClass | string | ABC 分类（A/B/C） |
| list[].xyzClass | string | XYZ 分类（X/Y/Z） |
| list[].safetyStock | number | 安全库存 SS |
| list[].reorderPoint | number | 补货点 ROP |

## 3.3 手动触发 AI 预测

- 方法: POST
- 路径: /api/ai/forecast/trigger
- 权限: ai:forecast:trigger

请求字段:

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| body | object | 否 | 当前实现可空请求体 |

响应字段（建议口径）:

| 字段 | 类型 | 说明 |
|---|---|---|
| code | number | 业务状态码 |
| message | string | 触发结果描述 |

## 3.4 查询预测结果（分页）

- 方法: GET
- 路径: /api/ai/forecast/result
- 参数: month, partCode, page, size

请求字段（Query）:

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| month | string | 否 | 预测月份，yyyy-MM |
| partCode | string | 否 | 备件编码（支持模糊） |
| page | number | 否 | 页码，从 1 开始 |
| size | number | 否 | 每页条数 |

响应字段（建议口径）:

| 字段 | 类型 | 说明 |
|---|---|---|
| total | number | 总记录数 |
| list[].partCode | string | 备件编码 |
| list[].forecastMonth | string | 预测月份 |
| list[].predictQty | number | 预测需求量 |
| list[].lowerBound | number | 区间下界 |
| list[].upperBound | number | 区间上界 |
| list[].algoType | string | 算法类型（RF/SBA/FALLBACK） |
| list[].mase | number | 精度指标（可空） |

---

## 4. 补货建议

## 4.1 查询补货建议

- 方法: GET
- 路径: /api/reorder-suggests
- 参数: status

请求字段（Query）:

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| status | string | 否 | 建议状态，默认 待处理 |

响应字段（建议口径）:

| 字段 | 类型 | 说明 |
|---|---|---|
| [] | array | 当前返回为数组列表 |
| [].id | number | 建议 ID |
| [].partCode | string | 备件编码 |
| [].suggestMonth | string | 建议月份 |
| [].currentStock | number | 当前库存 |
| [].reorderPoint | number | 补货点 |
| [].suggestQty | number | 建议采购量 |
| [].status | string | 建议状态 |

## 4.2 忽略补货建议

- 方法: PUT
- 路径: /api/reorder-suggests/{id}/ignore

请求字段（Path）:

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | number | 是 | 补货建议 ID |

响应字段（建议口径）:

| 字段 | 类型 | 说明 |
|---|---|---|
| code | number | 业务状态码 |
| message | string | 处理结果 |

---

## 5. 领用与 FIFO 追溯

## 5.0 领用出库确认

- 方法: PUT
- 路径: /api/requisitions/{id}/outbound
- 权限: req:outbound:confirm

请求字段（Path）:

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | number | 是 | 领用单 ID |

请求字段（Body）:

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| items[].itemId | number | 是 | 领用明细 ID |
| items[].outQty | number | 是 | 出库数量 |

## 5.1 查询领用单明细的批次追溯

- 方法: GET
- 路径: /api/outbound-trace/requisition-item/{reqItemId}

成功示例:
```json
[
  {
    "stockInItemId": 8,
    "deductQty": 9,
    "outboundTime": "2026-03-26 10:20:00"
  },
  {
    "stockInItemId": 9,
    "deductQty": 31,
    "outboundTime": "2026-03-26 10:20:00"
  }
]
```

  请求字段（Path）:

  | 字段 | 类型 | 必填 | 说明 |
  |---|---|---|---|
  | reqItemId | number | 是 | 领用明细 ID |

  响应字段:

  | 字段 | 类型 | 说明 |
  |---|---|---|
  | [].stockInItemId | number | 入库批次 ID |
  | [].deductQty | number | 本次扣减数量 |
  | [].outboundTime | string | 出库时间 |

## 5.2 查询入库批次被使用情况

- 方法: GET
- 路径: /api/outbound-trace/stock-in-batch/{stockInItemId}

请求字段（Path）:

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| stockInItemId | number | 是 | 入库批次 ID |

响应字段（建议口径）:

| 字段 | 类型 | 说明 |
|---|---|---|
| [].reqItemId | number | 领用明细 ID |
| [].deductQty | number | 扣减数量 |
| [].outboundTime | string | 出库时间 |

---

## 6. 预警与报表（核心）

常用路径（按现有前端调用口径）：
- GET /api/report/kpi
- GET /api/report/maintenance/cost-by-month
- GET /api/report/consumption/trend
- GET /api/warnings

说明:
- 上述接口用于看板页核心卡片、图表和预警表格
- 权限控制按角色菜单配置执行

---

## 7. 错误码参考

| HTTP | 场景 | 处理建议 |
|---|---|---|
| 400 | 参数错误 | 校验请求体与 query 参数 |
| 401 | 未登录/Token 无效 | 重新登录获取 token |
| 403 | 无权限 | 检查角色权限绑定 |
| 404 | 资源不存在 | 检查路径参数 id 是否正确 |
| 409 | 状态冲突 | 检查单据状态流转是否已变更 |
| 422 | 业务校验失败（启用统一异常映射时） | 检查库存、数量、时间范围 |
| 500 | 服务器异常 | 查后端日志并保留 trace |

---

## 8. 文档维护规则

1. 新增或修改接口必须同步更新本文件
2. 涉及权限变更必须写明 permission
3. 涉及状态流转接口必须写明前置状态
4. PR 合并前由开发自检 API 示例可执行

---

维护人: 后端团队
版本: 1.0
