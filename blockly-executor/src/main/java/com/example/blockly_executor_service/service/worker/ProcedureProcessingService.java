package com.example.blockly_executor_service.service.worker;

import com.example.common.model.ProcedurePayload;
import com.example.common.model.ProcedureResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
public class ProcedureProcessingService {

    private final JdbcTemplate writeJdbcTemplate;
    private final BlocklyProcedureWorkerService workerService;
    private final ObjectMapper objectMapper;

    public ProcedureProcessingService(
            @Qualifier("writeJdbcTemplate") JdbcTemplate writeJdbcTemplate,
            BlocklyProcedureWorkerService workerService,
            ObjectMapper objectMapper) {
        this.writeJdbcTemplate = writeJdbcTemplate;
        this.workerService = workerService;
        this.objectMapper = objectMapper;
    }

    @Transactional("writeTransactionManager")
    public ProcedureResponse<?> process(ProcedurePayload<?> request) {
        UUID requestId = request.requestId();

        int inserted = writeJdbcTemplate.update(
                "INSERT INTO processed_request(request_id, status) VALUES (?, 'IN_PROGRESS') ON CONFLICT (request_id) DO NOTHING",
                requestId);

        if (inserted == 0) {
            String savedJson = writeJdbcTemplate.query(
                    "SELECT response_json FROM processed_request WHERE request_id = ? AND status = 'DONE'",
                    rs -> rs.next() ? rs.getString("response_json") : null,
                    requestId);

            if (savedJson != null) {
                log.warn("Duplicate requestId {}, returning stored response", requestId);
                return deserialize(savedJson);
            }
            throw new IllegalStateException(
                    "Request " + requestId + " is already in progress, will retry");
        }

        ProcedureResponse<?> response = workerService.executeProcedure(request);

        writeJdbcTemplate.update(
                "UPDATE processed_request SET status = 'DONE', response_json = ?, processed_at = now() WHERE request_id = ?",
                serialize(response), requestId);

        return response;
    }

    private String serialize(ProcedureResponse<?> response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize response", e);
        }
    }

    private ProcedureResponse<?> deserialize(String json) {
        try {
            return objectMapper.readValue(json, ProcedureResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize stored response", e);
        }
    }
}