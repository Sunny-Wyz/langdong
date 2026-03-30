package com.langdong.spare.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PythonModelClient {

    private static final Logger log = LoggerFactory.getLogger(PythonModelClient.class);

    private final RestTemplate pythonRestTemplate;

    @Value("${ai.python.base-url:http://localhost:8001}")
    private String pythonBaseUrl;

    public PythonModelClient(RestTemplate pythonRestTemplate) {
        this.pythonRestTemplate = pythonRestTemplate;
    }

    public Map<String, Object> predictRul(Integer sparePartId) {
        String url = pythonBaseUrl + "/api/v1/rul/predict";
        Map<String, Object> body = new HashMap<>();
        body.put("spare_part_id", sparePartId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Map> response = pythonRestTemplate.postForEntity(
                    url,
                    new HttpEntity<>(body, headers),
                    Map.class
            );
            return response.getBody() == null ? new HashMap<>() : response.getBody();
        } catch (Exception ex) {
            log.error("[PythonModelClient] predictRul call failed: sparePartId={}", sparePartId, ex);
            throw ex;
        }
    }

    public List<Map<String, Object>> suggestReplenishment(List<Integer> sparePartIds) {
        String url = pythonBaseUrl + "/api/v1/replenishment/suggest";
        Map<String, Object> body = new HashMap<>();
        body.put("spare_part_ids", sparePartIds);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<List> response = pythonRestTemplate.postForEntity(
                    url,
                    new HttpEntity<>(body, headers),
                    List.class
            );
            return response.getBody() == null ? Collections.emptyList() : response.getBody();
        } catch (Exception ex) {
            log.error("[PythonModelClient] suggestReplenishment call failed: ids={}", sparePartIds, ex);
            throw ex;
        }
    }
}
