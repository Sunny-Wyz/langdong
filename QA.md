# 问答与问题解决记录 (QA.md)

Q - /api/ai/forecast/result?month=&partCode=&page=1&size=20 返回 400 - 根因是 month 参数为空字符串时在 AiForecastController 里被当作非 null 校验，未匹配 yyyy-MM 直接触发 badRequest；已将 month/partCode 先 trim 后空串归一为 null，再执行格式校验并传入 service，保持原有业务语义且兼容前端空参数。

Q - 修复代码后接口仍返回 400 - 根因是后端 Java 进程未重启，仍运行修复前类加载结果；重启 8080 服务后复测，`month=&partCode=` 返回 200，非法 `month=2026-3` 仍按预期返回 400。

Q - Python AI 服务环境部署时 `conda env create` 失败（YAML 解析错误 + SSL 超时） - 先修复 `environment.yml` 的 `pip` 缩进格式，再改为分步安装策略（先 `conda create -n langdong python=3.11`，再按 conda/pip 分批安装），最终完成 `langdong` 环境并通过 Python/Java 双端编译验证。

Q - 新建 FastAPI 补货接口返回字段不完整 - 根因是响应模型未声明 legacy 脚本中的 `suggestion`、`alert_message` 等字段，FastAPI 会按 `response_model` 过滤未声明字段；已补齐 schema 字段并复测导入通过，保证接口契约与现有脚本一致。
