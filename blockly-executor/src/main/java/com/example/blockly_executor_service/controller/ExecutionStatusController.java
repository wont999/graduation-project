package com.example.blockly_executor_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/executions")
@RequiredArgsConstructor
public class ExecutionStatusController {

    @Qualifier("readJdbcTemplate")
    private final JdbcTemplate readJdbcTemplate;

    @GetMapping("/{requestId}")
    public ResponseEntity<?> getStatus(@PathVariable UUID requestId) {
        var rows = readJdbcTemplate.queryForList(
                "SELECT status, response_json, tenant_id, procedure_name FROM processed_request WHERE request_id = ?",
                requestId);

        if (rows.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "requestId", requestId.toString(),
                    "status", "PENDING"));
        }

        var row = rows.get(0);
        return ResponseEntity.ok(Map.of(
                "requestId", requestId.toString(),
                "status", row.get("status"),
                "tenantId", row.get("tenant_id") != null ? row.get("tenant_id") : "",
                "procedureName", row.get("procedure_name") != null ? row.get("procedure_name") : "",
                "response", row.get("response_json") != null ? row.get("response_json") : ""));
    }
}