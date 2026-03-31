package com.langdong.spare.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.langdong.spare.entity.AiTaskResult;
import com.langdong.spare.mapper.AiTaskResultMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 回调结果存储服务（双写：内存缓存 + 数据库持久化）
 * <p>
 * 内存缓存用于快速轮询，数据库用于持久化。
 * Java 重启后内存缓存清空，轮询请求会回落到数据库查询。
 */
@Service
public class PythonCallbackStoreService {

    private static final Logger log = LoggerFactory.getLogger(PythonCallbackStoreService.class);

    private final ConcurrentHashMap<String, Map<String, Object>> callbackStore = new ConcurrentHashMap<>();
    private final Deque<String> order = new ArrayDeque<>();
    private static final int MAX_ENTRIES = 5000;

    private final AiTaskResultMapper aiTaskResultMapper;
    private final ObjectMapper objectMapper;

    public PythonCallbackStoreService(AiTaskResultMapper aiTaskResultMapper, ObjectMapper objectMapper) {
        this.aiTaskResultMapper = aiTaskResultMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存回调结果：先写数据库，再写内存缓存。
     */
    public synchronized void save(String taskId, Map<String, Object> payload) {
        // 1. 持久化到数据库
        persistToDb(taskId, payload);

        // 2. 写入内存缓存
        if (!callbackStore.containsKey(taskId)) {
            order.addLast(taskId);
        }
        callbackStore.put(taskId, payload);

        while (callbackStore.size() > MAX_ENTRIES && !order.isEmpty()) {
            String oldest = order.removeFirst();
            callbackStore.remove(oldest);
        }
    }

    /**
     * 查询回调结果：先查内存缓存，miss 则回落到数据库。
     */
    public Map<String, Object> get(String taskId) {
        Map<String, Object> cached = callbackStore.get(taskId);
        if (cached != null) {
            return cached;
        }

        // 内存 miss，查数据库
        return loadFromDb(taskId);
    }

    /**
     * 定时清理 30 天前的旧记录（每天凌晨 3:30 执行）
     */
    @Scheduled(cron = "0 30 3 * * ?")
    public void cleanupOldRecords() {
        try {
            aiTaskResultMapper.deleteOlderThan(30);
            log.info("[CallbackStore] Cleaned up task results older than 30 days");
        } catch (Exception ex) {
            log.error("[CallbackStore] Failed to cleanup old records", ex);
        }
    }

    private void persistToDb(String taskId, Map<String, Object> payload) {
        try {
            AiTaskResult record = new AiTaskResult();
            record.setTaskId(taskId);
            record.setStatus((String) payload.getOrDefault("status", "UNKNOWN"));

            Object result = payload.get("result");
            if (result != null) {
                record.setPayload(objectMapper.writeValueAsString(result));
            }

            Object error = payload.get("error");
            if (error != null) {
                record.setErrorMsg(String.valueOf(error));
            }

            aiTaskResultMapper.insertOrUpdate(record);
        } catch (JsonProcessingException ex) {
            log.error("[CallbackStore] Failed to serialize payload for taskId={}", taskId, ex);
        } catch (Exception ex) {
            log.error("[CallbackStore] Failed to persist callback to DB for taskId={}", taskId, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadFromDb(String taskId) {
        try {
            AiTaskResult record = aiTaskResultMapper.findByTaskId(taskId);
            if (record == null) {
                return null;
            }

            Map<String, Object> result = new java.util.HashMap<>();
            result.put("task_id", record.getTaskId());
            result.put("status", record.getStatus());
            result.put("error", record.getErrorMsg());

            if (record.getPayload() != null && !record.getPayload().isBlank()) {
                try {
                    result.put("result", objectMapper.readValue(record.getPayload(), Object.class));
                } catch (JsonProcessingException ex) {
                    result.put("result", record.getPayload());
                }
            }

            // 回填内存缓存
            synchronized (this) {
                if (!callbackStore.containsKey(taskId)) {
                    order.addLast(taskId);
                }
                callbackStore.put(taskId, result);
            }

            return result;
        } catch (Exception ex) {
            log.error("[CallbackStore] Failed to load from DB for taskId={}", taskId, ex);
            return null;
        }
    }
}
