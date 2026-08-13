# 密码加密与 AOP 日志拦截实现指南

**最后更新**: 2026-03-26 | **版本**: 1.0  

---

## 🔐 密码加密（BCrypt）

### 为什么使用 BCrypt？

| 特性 | BCrypt | MD5 | SHA-256 |
|-----|--------|-----|---------|
| 加盐（Salt） | ✅ 自动 | ❌ 需手动 | ⚠️ 可选 |
| 自适应困难度 | ✅ rounds 可调 | ❌ 固定 | ❌ 固定 |
| 彩虹表抵抗力 | ✅ 强 | ❌ 弱 | ⚠️ 中 |
| 性能 | ⚠️ 慢（意图） | ✅ 很快 | ✅ 快 |
| 生产推荐 | ✅✅✅ | ❌ 已弃用 | ⚠️ 需加盐 |

**原因**：BCrypt 采用迭代哈希，故意设计得较慢，防止暴力破解；每次加密自动生成不同 salt，同一密码多次加密结果不同。

---

### Spring Security BCrypt 集成

#### 1. Maven 依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<!-- Spring Security 已包含 BCrypt -->
```

#### 2. 配置 PasswordEncoder

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    /**
     * 注册 BCryptPasswordEncoder bean
     * strength = 10: rounds 数，1-31，值越大加密越慢
     *            10 = default（约 100ms/次）
     *            12 = 生产推荐（约 300ms/次）
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);  // 12 rounds = 约 300ms
    }
}
```

#### 3. 用户注册时加密密码

```java
@Service
@Transactional
public class UserService {
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private UserMapper userMapper;
    
    /**
     * 用户注册：密码加密存储
     */
    public void register(UserRegisterDTO dto) {
        // 验证密码强度
        validatePasswordStrength(dto.getPassword());
        
        // 加密密码
        String encodedPassword = passwordEncoder.encode(dto.getPassword());
        
        // 保存到数据库
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(encodedPassword);  // 存储的是加密后的字符串
        user.setCreatedAt(LocalDateTime.now());
        
        userMapper.insert(user);
        
        log.info("🔐 用户注册成功: {}", dto.getUsername());
    }
    
    /**
     * 用户登录时验证密码
     */
    public boolean verifyPassword(String username, String rawPassword) {
        SysUser user = userMapper.selectByUsername(username);
        if (user == null) {
            log.warn("⚠️ 用户不存在: {}", username);
            return false;
        }
        
        // BCrypt 会从数据库存储的 hash 中提取 salt，自动验证
        boolean matches = passwordEncoder.matches(rawPassword, user.getPassword());
        
        if (!matches) {
            log.warn("❌ 登录失败: 密码错误 [{}]", username);
        } else {
            log.info("✅ 登录成功: {}", username);
        }
        
        return matches;
    }
    
    /**
     * 用户修改密码
     */
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        SysUser user = userMapper.selectById(userId);
        
        // 1. 验证旧密码
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new InvalidPasswordException("原密码错误");
        }
        
        // 2. 验证新密码强度
        validatePasswordStrength(newPassword);
        
        // 3. 加密新密码
        String encodedNewPassword = passwordEncoder.encode(newPassword);
        
        // 4. 更新数据库
        userMapper.updatePassword(userId, encodedNewPassword);
        
        log.info("✅ 密码已修改: {}", user.getUsername());
    }
    
    /**
     * 密码强度验证（示例）
     */
    private void validatePasswordStrength(String password) {
        if (password.length() < 6) {
            throw new InvalidPasswordException("密码长度不得少于 6 位");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new InvalidPasswordException("密码必须包含大写字母");
        }
        if (!password.matches(".*[0-9].*")) {
            throw new InvalidPasswordException("密码必须包含数字");
        }
    }
}
```

---

### BCrypt 原理简析

```
输入明文: password = "Admin@123"

Step 1: 生成 salt（16 字节随机数）
    salt = bcrypt_gensalt(rounds=12)

Step 2: 迭代 hash
    for i = 0 to 2^rounds - 1:
        hash = bcrypt(password, salt)

Step 3: 输出序列化格式
    $2a$12$R9h/cIPz0gi.URWHD3/3Xe  // 头部（算法+rounds）
    aH4kMAiqcB3VNlp15d6U5O         // 实际密文
    
    = $2a$12$R9h/cIPz0gi.URWHD3/3XeaH4kMAiqcB3VNlp15d6U5O

Step 4: 验证密码
    matchers = BCrypt.checkpw(rawPassword, hashedPassword)
    // 自动从 hash 中提取 salt，重新计算，对比
```

**优势**：同一密码多次加密得到不同 hash，但都能验证成功

```
密码: "Admin@123"

加密 1: $2a$12$ABC...XYZ   ✅ 验证通过
加密 2: $2a$12$DEF...UVW   ✅ 同一密码，不同 hash
加密 3: $2a$12$GHI...RST   ✅ 验证通过

这使彩虹表攻击失效：无法预先生成键值表
```

---

## 📝 AOP 日志拦截

### 业务背景

维修工单涉及敏感操作（更新故障分析、工时费用等），需要完整的操作审计日志。

### AOP 注解定义

```java
/**
 * 自定义注解：标记需要审计日志的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {
    String value() default "";           // 操作描述
    String module() default "UNKNOWN";   // 模块名
    String action() default "UNKNOWN";   // 操作类型
}
```

### AOP 切面实现

```java
@Aspect
@Component
@Slf4j
public class AuditLogAspect {
    
    @Autowired
    private AuditLogMapper auditLogMapper;
    
    @Autowired
    private HttpServletRequest request;
    
    /**
     * 切点：所有带 @AuditLog 注解的方法
     */
    @Pointcut("@annotation(com.langdong.spare.annotation.AuditLog)")
    public void auditLogPointcut() {
    }
    
    /**
     * 环绕通知：方法执行前后记录日志
     */
    @Around("auditLogPointcut()")
    public Object auditLogAround(ProceedingJoinPoint joinPoint) throws Throwable {
        // 1️⃣ 获取方法信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        AuditLog auditLog = method.getAnnotation(AuditLog.class);
        
        // 2️⃣ 收集执行前信息
        long startTime = System.currentTimeMillis();
        String operator = getCurrentUser();
        String operatorIp = getClientIp();
        Object[] args = joinPoint.getArgs();
        
        String moduleName = auditLog.module();
        String actionName = auditLog.action();
        String description = auditLog.value();
        
        log.info("[AuditLog] 操作开始: {} - {} - {}", moduleName, actionName, description);
        
        // 3️⃣ 执行业务方法
        Object result = null;
        Throwable exception = null;
        
        try {
            result = joinPoint.proceed();
        } catch (Throwable e) {
            exception = e;
        }
        
        // 4️⃣ 计算执行耗时
        long duration = System.currentTimeMillis() - startTime;
        
        // 5️⃣ 保存审计日志
        OperationAuditLog auditLogRecord = new OperationAuditLog();
        auditLogRecord.setOperator(operator);
        auditLogRecord.setOperatorIp(operatorIp);
        auditLogRecord.setModule(moduleName);
        auditLogRecord.setAction(actionName);
        auditLogRecord.setDescription(description);
        auditLogRecord.setMethod(method.getName());
        auditLogRecord.setArguments(serializeArguments(args));
        auditLogRecord.setDuration(duration);
        
        if (exception != null) {
            auditLogRecord.setStatus("FAILED");
            auditLogRecord.setErrorMsg(exception.getMessage());
            auditLogRecord.setStackTrace(getStackTrace(exception));
            
            log.error("[AuditLog] ❌ 操作失败: {} - {} - 耗时 {}ms - 错误: {}", 
                moduleName, actionName, duration, exception.getMessage());
        } else {
            auditLogRecord.setStatus("SUCCESS");
            auditLogRecord.setResult(serializeResult(result));
            
            log.info("[AuditLog] ✅ 操作成功: {} - {} - 耗时 {}ms", 
                moduleName, actionName, duration);
        }
        
        auditLogRecord.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(auditLogRecord);
        
        // 6️⃣ 处理异常（若有）
        if (exception != null) {
            throw exception;
        }
        
        return result;
    }
    
    /**
     * 获取当前登录用户
     */
    private String getCurrentUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            return auth != null ? auth.getName() : "SYSTEM";
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
    
    /**
     * 获取客户端 IP
     */
    private String getClientIp() {
        try {
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty()) {
                ip = request.getRemoteAddr();
            }
            return ip;
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
    
    private String getStackTrace(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
    
    private String serializeArguments(Object[] args) {
        try {
            // 只记录前 5 个参数，每个参数最多 500 字符
            List<String> argStrings = new ArrayList<>();
            for (int i = 0; i < Math.min(5, args.length); i++) {
                String argStr = args[i].toString();
                if (argStr.length() > 500) {
                    argStr = argStr.substring(0, 500) + "...";
                }
                argStrings.add(argStr);
            }
            return String.join(" | ", argStrings);
        } catch (Exception e) {
            return "N/A";
        }
    }
    
    private String serializeResult(Object result) {
        try {
            String resultStr = result.toString();
            if (resultStr.length() > 500) {
                resultStr = resultStr.substring(0, 500) + "...";
            }
            return resultStr;
        } catch (Exception e) {
            return "N/A";
        }
    }
}
```

---

### 使用示例：工单服务

```java
@Service
@Slf4j
public class WorkOrderService {
    
    @Autowired
    private WorkOrderMapper workOrderMapper;
    
    /**
     * 更新工单维修记录（需要审计）
     */
    @AuditLog(
        value = "更新工单维修分析和工时费用",
        module = "WORK_ORDER",
        action = "UPDATE_REPAIR_RECORD"
    )
    @Transactional
    public void updateRepairRecord(Long workOrderId, WorkOrderUpdateDTO dto) {
        
        // 1. 加载工单
        BizWorkOrder workOrder = workOrderMapper.selectById(workOrderId);
        if (workOrder == null) {
            throw new EntityNotFoundException("工单不存在");
        }
        
        // 2. 更新维修分析
        workOrder.setFailureAnalysis(dto.getFailureAnalysis());
        workOrder.setRepairNotes(dto.getRepairNotes());
        
        // 3. 更新费用（敏感数据）
        workOrder.setLaborHours(dto.getLaborHours());
        workOrder.setLaborCost(dto.getLaborCost());
        workOrder.setMaterialCost(dto.getMaterialCost());
        
        // 4. 标记完工
        workOrder.setStatus("COMPLETED");
        workOrder.setCompletedAt(LocalDateTime.now());
        
        // 5. 保存（AOP 会自动记录操作日志）
        workOrderMapper.update(workOrder);
        
        log.info("✅ 工单 {} 维修记录已更新，总费用: ¥{}", 
            workOrderId, 
            workOrder.getLaborCost() + workOrder.getMaterialCost());
    }
    
    /**
     * 关闭工单（需要高级权限，需要审计）
     */
    @AuditLog(
        value = "关闭工单，防止再修改",
        module = "WORK_ORDER",
        action = "CLOSE_ORDER"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    @Transactional
    public void closeWorkOrder(Long workOrderId, String closureReason) {
        
        BizWorkOrder workOrder = workOrderMapper.selectById(workOrderId);
        workOrder.setStatus("CLOSED");
        workOrder.setClosureReason(closureReason);
        workOrder.setClosedAt(LocalDateTime.now());
        
        workOrderMapper.update(workOrder);
        
        log.warn("⚠️ 工单 {} 已关闭，原因: {}", workOrderId, closureReason);
    }
}
```

---

### 审计日志查询

```java
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {
    
    @Autowired
    private AuditLogMapper auditLogMapper;
    
    /**
     * 查询某个工单的所有操作日志
     */
    @GetMapping("/work-order/{workOrderId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public Response<List<OperationAuditLog>> getWorkOrderAuditLogs(
            @PathVariable Long workOrderId) {
        
        List<OperationAuditLog> logs = auditLogMapper.selectByModule("WORK_ORDER")
            .stream()
            .filter(log -> log.getArguments().contains(workOrderId.toString()))
            .collect(Collectors.toList());
        
        return Response.ok(logs);
    }
    
    /**
     * 审计日志统计报表
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public Response<AuditStatistics> getStatistics(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String operator) {
        
        AuditStatistics stats = auditLogMapper.statistics(module, operator);
        return Response.ok(stats);
    }
}
```

---

### 审计日志表设计

```sql
CREATE TABLE operation_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    operator VARCHAR(50) NOT NULL COMMENT '操作者',
    operator_ip VARCHAR(50) COMMENT '操作者 IP',
    
    module VARCHAR(50) NOT NULL COMMENT '模块（WORK_ORDER/REQUISITION/...）',
    action VARCHAR(50) NOT NULL COMMENT '操作类型（UPDATE/DELETE/CREATE/...）',
    description VARCHAR(255) COMMENT '操作描述',
    
    method VARCHAR(100) COMMENT '被调用的方法名',
    arguments VARCHAR(2000) COMMENT '方法参数',
    result VARCHAR(500) COMMENT '执行结果',
    
    status VARCHAR(20) COMMENT 'SUCCESS/FAILED',
    duration BIGINT COMMENT '执行耗时（ms）',
    
    error_msg VARCHAR(500) COMMENT '错误信息',
    stack_trace LONGTEXT COMMENT '完整堆栈',
    
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_operator (operator),
    INDEX idx_module (module),
    INDEX idx_action (action),
    INDEX idx_status (status),
    INDEX idx_created (created_at)
);
```

---

## 🔒 安全最佳实践

| 实践 | 做法 | 原因 |
|-----|------|------|
| **密码加密** | 使用 BCrypt(12 rounds) | 防暴力破解 |
| **密码长度** | 最少 8 字符 | 符合业界标准 |
| **密码复杂度** | 大写+小写+数字+符号 | 增加密码强度 |
| **HTTPS** | 所有通信 TLS | 防中间人攻击 |
| **操作审计** | AOP 日志所有敏感操作 | 事后追溯 |
| **访问控制** | @PreAuthorize 角色检查 | 防未授权访问 |
| **日志脱敏** | 不记录密码、秘钥等 | 防泄密 |
| **定期备份** | 审计日志月度备份 | 防日志丢失 |

---

**维护人**: 安全与架构团队  
**版本**: 1.0 (2026-03-26)  
**下次更新**: 添加OWASP 漏洞防护指南
