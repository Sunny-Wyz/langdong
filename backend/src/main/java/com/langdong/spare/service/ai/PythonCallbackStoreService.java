package com.langdong.spare.service.ai;

import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PythonCallbackStoreService {

    private final ConcurrentHashMap<String, Map<String, Object>> callbackStore = new ConcurrentHashMap<>();
    private final Deque<String> order = new ArrayDeque<>();
    private static final int MAX_ENTRIES = 5000;

    public synchronized void save(String taskId, Map<String, Object> payload) {
        if (!callbackStore.containsKey(taskId)) {
            order.addLast(taskId);
        }
        callbackStore.put(taskId, payload);

        while (callbackStore.size() > MAX_ENTRIES && !order.isEmpty()) {
            String oldest = order.removeFirst();
            callbackStore.remove(oldest);
        }
    }

    public Map<String, Object> get(String taskId) {
        return callbackStore.get(taskId);
    }
}
