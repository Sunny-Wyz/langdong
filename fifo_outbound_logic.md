# FIFO 出库批次处理核心代码示例

这个设计模式用于在有真实的 `stock_in_item` (入库批次明细) 表支撑的场景下执行先进先出(FIFO)的库存扣减算法。

## 场景前提

1. **数据库设计**: 需要一个真正的批次库存表，比如 `biz_stock_in_item` 包含 `spare_part_id`, `remaining_qty`, `in_time` 等字段。当剩余数量用尽时 `remaining_qty` 会减到 0。
2. **要求**: 扣减时按 `in_time ASC` 排序拉取 `remaining_qty > 0` 的可用批次列表。

## 核心 Java 事务层代码

```java
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class InventoryFifoService {

    private final StockInItemMapper stockInItemMapper;
    private final OutboundItemMapper outboundItemMapper;
    private final SparePartStockMapper sparePartStockMapper;

    public InventoryFifoService(StockInItemMapper stockInItemMapper, 
                                OutboundItemMapper outboundItemMapper, 
                                SparePartStockMapper sparePartStockMapper) {
        this.stockInItemMapper = stockInItemMapper;
        this.outboundItemMapper = outboundItemMapper;
        this.sparePartStockMapper = sparePartStockMapper;
    }

    /**
     * 按 FIFO 规则执行给定备件的出库扣减
     *
     * @param outboundRecordId 主出库单ID (如领用单ID)
     * @param sparePartId      要出库的备件ID
     * @param requiredQty      需要的出库总量
     */
    @Transactional(rollbackFor = Exception.class)
    public void processFifoOutbound(Long outboundRecordId, Long sparePartId, int requiredQty) {
        
        // 1. 查询当前备件可用总库存是否充足
        int totalAvailable = sparePartStockMapper.getAvailableQuantity(sparePartId);
        if (totalAvailable < requiredQty) {
            throw new RuntimeException("库存不足，无法满足出库需求");
        }
        // 2. 按入库时间升序加载所有有余量的入库明细批次 (FIFO 核心排序)
        List<StockInItem> pendingBatches = stockInItemMapper.findAvailableBatchesAsc(sparePartId);
        int remainingNeed = requiredQty;
        // 3. 遍历批次，逐步扣减剩余需求量
        for (StockInItem batch : pendingBatches) {
            if (remainingNeed <= 0) {
                 break; // 已满足需求，跳出循环
            }
            int batchRemaining = batch.getRemainingQty();
            int deductQty = 0; // 当前批次将要扣减的量
            if (batchRemaining >= remainingNeed) {
                // 当前批次足以满足剩余所有需求
                deductQty = remainingNeed;
                remainingNeed = 0;
            } else {
                // 当前批次不够扣，扣尽当前批次余量，并进入下一个批次继续找
                deductQty = batchRemaining;
                remainingNeed -= batchRemaining;
            }

            // --- 4. 落地扣减行为 ---
            
            // A. 扣减批次剩余量 (UPDATE biz_stock_in_item SET remaining_qty = remaining_qty - deductQty WHERE id = batch.getId())
            stockInItemMapper.deductBatchQuantity(batch.getId(), deductQty);

            // B. 记录批次维度的出库明细账本 (用于溯源这笔出库是从哪个入库批次走的)
            OutboundItemRecord detailRecord = new OutboundItemRecord();
            detailRecord.setOutboundId(outboundRecordId);
            detailRecord.setStockInBatchId(batch.getId());
            detailRecord.setSparePartId(sparePartId);
            detailRecord.setOutQty(deductQty);
            outboundItemMapper.insert(detailRecord);
        }

        // 5. 扣减备件主档的汇总总库存 (UPDATE spare_part SET quantity = quantity - #{requiredQty})
        sparePartStockMapper.addQuantity(sparePartId, -requiredQty);
    }
}
```

# 备件档案批量导入代码示例

如果未来需要实现基于 Excel 的备件档案批量导入功能（含自动发号与批量入库），建议使用 `Alibaba EasyExcel` 组件。以下为核心代码结构：

## 1. 引入依赖
```xml
<!-- 在 backend/pom.xml 中添加 -->
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>easyexcel</artifactId>
    <version>3.3.2</version>
</dependency>
```

## 2. 定义 Excel 数据传输模型 (DTO)
```java
import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class SparePartImportExcelDTO {
    @ExcelProperty("分类编码")
    private String categoryPrefix;
    
    @ExcelProperty("备件名称")
    private String name;
    
    @ExcelProperty("规格型号")
    private String model;
    
    @ExcelProperty("单位")
    private String unit;
    
    @ExcelProperty("初始库存")
    private Integer quantity;
    
    @ExcelProperty("单价")
    private BigDecimal price;
    
    // 省略其他字段如 供应商、备注、是否关键等
}
```

## 3. Controller 层接收上传请求
```java
// 在 SparePartController.java 中添加
import org.springframework.web.multipart.MultipartFile;
import com.alibaba.excel.EasyExcel;

@PostMapping("/import")
public ResponseEntity<?> importData(@RequestParam("file") MultipartFile file) {
    try {
        // 交由 Service 层或 Listener 处理数据
        sparePartService.importExcel(file);
        return ResponseEntity.ok("批量导入成功");
    } catch (Exception e) {
        return ResponseEntity.badRequest().body("导入失败: " + e.getMessage());
    }
}
```

## 4. Service 层处理核心业务逻辑 (含自动发号)
```java
@Service
public class SparePartService {
    
    @Autowired
    private SparePartMapper sparePartMapper;
    @Autowired
    private SparePartCategoryMapper categoryMapper;

    @Transactional(rollbackFor = Exception.class)
    public void importExcel(MultipartFile file) throws Exception {
        // 读取 Excel 数据 
        List<SparePartImportExcelDTO> dtoList = EasyExcel.read(file.getInputStream())
            .head(SparePartImportExcelDTO.class)
            .sheet()
            .doReadSync();
            
        // 将准备插入的数据存入列表
        List<SparePart> insertList = new ArrayList<>();
        
        for (SparePartImportExcelDTO dto : dtoList) {
            String prefix = dto.getCategoryPrefix();
            // 在批量导入时，每次都需要重新获取该前缀的 Max Code 确保号码连贯
            String maxCode = sparePartMapper.findMaxCodeByPrefix(prefix);
            int nextNum = 1;
            if (maxCode != null && maxCode.length() == 8) {
                try {
                    nextNum = Integer.parseInt(maxCode.substring(4)) + 1;
                } catch (NumberFormatException ignored) {}
            }
            String generatedCode = prefix + String.format("%04d", nextNum);
            
            SparePart part = new SparePart();
            part.setCode(generatedCode);
            part.setName(dto.getName());
            // ... 拷贝其他字段 ...
            
            // 为了保证下一个循环中能拿到正确的 Max Code，我们必须立即写入数据库，或者手动在内存中自增维护一个映射 Map
            sparePartMapper.insert(part); 
            // 注意：若数据量极大，建议采用在内存中维护流水号并在循环体外统一 insertBatch() 以提升性能。
        }
    }
}
```

# 工单 AOP 日志拦截核心代码示例

如果在未来需要引入工单相关的操作日志（AOP 拦截记录），请参考以下标准实现模式：

## 1. 定义自定义注解
```java
import java.lang.annotation.*;

@Target({ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {
    /**
     * 业务模块名称 (如: "维修工单")
     */
    String title() default "";
    
    /**
     * 具体操作动作 (如: "派工", "完工确认")
     */
    String action() default "";
}
```

## 2. 定义切面类拦截注解
```java
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class WorkOrderLogAspect {
    
    private static final Logger log = LoggerFactory.getLogger(WorkOrderLogAspect.class);

    // [关键代码] 1. 定义切点：拦截加了 @OperationLog 注解的方法
    @Pointcut("@annotation(com.langdong.spare.annotation.OperationLog)")
    public void workOrderLogPointCut() {
    }

    // [关键代码] 2. 环绕通知：拦截工单模块的请求，实现前置/后置逻辑
    @Around("workOrderLogPointCut()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        long beginTime = System.currentTimeMillis();
        
        // 执行目标方法前：可解析 Request 获取入参信息
        String methodName = point.getSignature().getName();
        log.info("【工单模块】开始执行方法: {}", methodName);
        
        // === 执行实际方法 ===
        Object result = point.proceed();
        
        // 执行目标方法后：计算耗时并保存审计日志
        long time = System.currentTimeMillis() - beginTime;
        log.info("【工单模块】执行完毕: {}，耗时: {} ms", methodName, time);
        
        // TODO: 在此处可以将操作日志异步落库到 biz_work_order_log / sys_oper_log 等记录表中
        
        return result;
    }
}
```

## 3. 在 Controller 中使用注解
```java
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/work-orders")
public class WorkOrderController {

    // 只需要在关键接口上标注 @OperationLog 即可触发 AOP 逻辑
    @PostMapping("/{id}/assign")
    @OperationLog(title = "维修工单", action = "派工")
    public ResponseEntity<?> assign(@PathVariable Long id, @RequestBody WorkOrderAssignDTO dto) {
        // ... 原本的派工业务逻辑 ...
        return ResponseEntity.ok().build();
    }
}
```

# BCrypt 密码加密关键代码示例

本项目密码加密基于 Spring Security 内置的 `BCryptPasswordEncoder` 实现。以下为项目中实际使用的关键代码。

## 1. 全局注册 BCrypt 加密器 Bean（来自 SecurityConfig.java）
```java
// [核心配置] 向 Spring 容器注册 BCrypt 加密器，使其可被整个项目 @Autowired 注入
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

## 2. 创建用户时的密码加密（来自 UserController.java）
```java
@Autowired
private PasswordEncoder passwordEncoder;

@PostMapping
@Transactional
public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
    // ... 省略用户名/姓名/状态字段构建 ...

    String rawPassword = (String) body.get("password");
    if (rawPassword == null || rawPassword.trim().isEmpty()) {
        rawPassword = "123456"; // 未传密码时，使用默认初始密码
    }
    
    // [关键代码] 明文密码经 BCrypt 哈希后存储，每次生成的哈希值都不同（内含随机 salt）
    user.setPassword(passwordEncoder.encode(rawPassword));
    userMapper.insert(user);
    // ...
}
```

## 3. 修改密码时的加密逻辑（来自 UserController.java）
```java
@PutMapping("/{id}")
public ResponseEntity<User> update(@PathVariable Long id, @RequestBody User user) {
    if (user.getPassword() != null && !user.getPassword().isEmpty()) {
        // [关键代码] 有传新密码时重新加密后更新
        user.setPassword(passwordEncoder.encode(user.getPassword()));
    } else {
        // 未传密码时不覆盖，保留原有密码
        user.setPassword(null);
    }
    userMapper.update(user);
    return ResponseEntity.ok(user);
}
```

## 4. 登录时密码校验（AuthController.java 推荐实现方式）
```java
// passwordEncoder.matches(rawPassword, storedHash) 用于比较明文与数据库中的哈希
// [关键代码] BCrypt 会将 rawPassword 和哈希中内嵌的 salt 组合后再哈希，比较结果
boolean isMatch = passwordEncoder.matches(rawPassword, user.getPassword());
if (!isMatch) {
    return ResponseEntity.status(401).body("密码错误");
}
```

# 随机森林（RF）预测算法核心伪代码

本项目 AI 预测模块基于 Java Smile ML 库实现了随机森林回归算法（`RandomForestServiceImpl.java`），用于备件需求量的预测。以下是按照实际代码逻辑整理的伪代码（含行为说明注释）：

## 算法概述

> 适用场景：需求历史规律型（ABC 分类中 A 类，数据点 ≥ 4）的备件。  
> 特征设计：使用 **滞后特征（lag）** + **滚动均值（rolling mean）** 构造输入矩阵。

## 伪代码（附注释）

```java
/**
 * 随机森林预测算法伪代码
 * 对应实现文件：RandomForestServiceImpl.java
 */
function randomForestPredict(ctx: PredictContextDTO) -> AiForecastResult:

    demands = ctx.getDemandHistory()   // 获取近 12 个月的历史月需求量列表

    // === 第一步：数据校验 ===
    // 若历史中有效数据点（>0的月份）少于最低要求（4个），则降级为统计兜底方案（均值）
    if count(demands where d > 0) < MIN_DATA_POINTS:
        return buildFallbackResult(ctx)

    n = demands.length                  // 历史数据总数
    window = 3                          // 滑动窗口大小（用 t-1, t-2 和滚动均值3期）

    // === 第二步：构造监督学习样本（特征矩阵）===
    // 从第 window 期开始，每一个时间点 i 构造一个样本：
    //   特征 x = [lag1=d[i-1], lag2=d[i-2], roll3=avg(d[i-1..i-3])]
    //   标签 y = d[i]
    for i in [window, n):
        lag1[i-window] = demands[i-1]              // 上一期需求量（滞后1阶）
        lag2[i-window] = demands[i-2]              // 上两期需求量（滞后2阶）
        roll3[i-window] = avg(demands[i-1..i-3])   // 最近3期滚动均值（平滑趋势）
        y[i-window]   = demands[i]                  // 当期真实需求（预测目标）
    
    // 最终得到 n-3 个训练样本

    // === 第三步：训练随机森林回归模型 ===
    // 使用 Smile ML 库，公式为 y ~ lag1 + lag2 + roll3
    // 超参：ntrees=50 棵决策树，max_depth=5，防止少样本时过拟合
    trainData = DataFrame.of(lag1, lag2, roll3, y)
    model = RandomForest.fit(formula="y ~ .", trainData)

    // === 第四步：构造"下一期"的预测特征并推理 ===
    // 用历史数据的最后 3 个月构造预测用特征
    nextLag1  = demands[n-1]
    nextLag2  = demands[n-2]
    nextRoll3 = avg(demands[n-1..n-3])

    predictedValue = max(0, model.predict([nextLag1, nextLag2, nextRoll3]))
    // max(0, ...) 防止预测结果为负数（需求不能为负）

    // === 第五步：计算置信区间（90%）===
    // 对训练集进行回测，计算均方根误差 RMSE
    trainPredictions = model.predict(trainData)
    RMSE = sqrt( mean( (y - trainPredictions)^2 ) )
    
    // 90% 置信区间（Z = 1.645）
    lowerBound = max(0, predictedValue - 1.645 * RMSE)
    upperBound = predictedValue + 1.645 * RMSE

    // === 第六步：计算 MASE（平均绝对尺度误差）===
    // MASE 用于衡量模型精度，MASE < 1 说明比朴素预测（持平上期）更好
    MASE = calcMASE(actualDemands, trainPredictions)

    // === 第七步：组装并返回预测结果对象 ===
    result.partCode      = ctx.partCode
    result.forecastMonth = ctx.forecastMonth
    result.predictQty    = predictedValue       // 下月预测需求量
    result.lowerBound    = lowerBound           // 置信区间下限
    result.upperBound    = upperBound           // 置信区间上限
    result.algoType      = "RF"                 // 算法标识
    result.mase          = MASE                 // 模型精度评价指标
    
    return result
```
