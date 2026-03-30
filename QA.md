# 问答与问题解决记录 (QA.md)

Q - /api/ai/forecast/result?month=&partCode=&page=1&size=20 返回 400 - 根因是 month 参数为空字符串时在 AiForecastController 里被当作非 null 校验，未匹配 yyyy-MM 直接触发 badRequest；已将 month/partCode 先 trim 后空串归一为 null，再执行格式校验并传入 service，保持原有业务语义且兼容前端空参数。

Q - 修复代码后接口仍返回 400 - 根因是后端 Java 进程未重启，仍运行修复前类加载结果；重启 8080 服务后复测，`month=&partCode=` 返回 200，非法 `month=2026-3` 仍按预期返回 400。
