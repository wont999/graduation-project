package com.example.blockly_executor_service.service.procedure;


import com.example.blockly_executor_service.model.dto.ExecutionRequest;
import com.example.blockly_executor_service.model.dto.ExecutionResult;
import com.example.blockly_executor_service.service.execution.ScriptExecutionService;
import com.example.common.ProcedureExecutor;
import com.example.common.model.ExecutionMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component("executeBlocklyScript")
@RequiredArgsConstructor
public class ExecuteBlocklyScriptProcedure implements ProcedureExecutor<Map<String, Object>, Object> {

    private final ScriptExecutionService scriptExecutionService;

    @Override
    public Object execute(Map<String, Object> parameters) {
        log.info("Executing Blockly script with parameters: {}", parameters);

        try {
            // извлекаем системные данные, добавленные BlocklyProcedureWorkerService:
            ExecutionMetadata metadata = (ExecutionMetadata) parameters.get("__metadata");
            String requestId = (String) parameters.get("requestId");

            String script = (String) parameters.get("script");

            Map<String, Object> scriptParams = (Map<String, Object>) parameters.getOrDefault("parameters", Map.of());
            Map<String, Object> headersFromParams = (Map<String, Object>) parameters.get("headers");
            Map<String, Object> headers = headersFromParams != null
                    ? new HashMap<>(headersFromParams)
                    : new HashMap<>();

            if (script == null) {
                throw new IllegalArgumentException("Script parameter is required");
            }

            if (metadata == null || metadata.userId() == null || metadata.userId().isEmpty()) {
                throw new SecurityException("User authentication required - metadata is missing");
            }

            String tenantId = metadata.tenantId();
            headers.put("tenantId", tenantId);

            if (requestId == null) {
                requestId = UUID.randomUUID().toString();
            }

            // Создаем ExecutionRequest из параметров
            ExecutionRequest request = ExecutionRequest.builder()
                    .script(script)
                    .params(scriptParams)
                    .headers(headers)
                    .requestId(requestId)
                    .build();

            // Выполняем скрипт
            ExecutionResult result = scriptExecutionService.executeScript(request);

            if (result.getStatus() == ExecutionResult.ExecutionStatus.SUCCESS) {
                Object scriptResult = result.getResult() != null ? result.getResult() : "undefined";

                log.info("Script executed successfully: {}", scriptResult);

                return scriptResult;

            } else {
                String errorMessage = result.getErrorMessage() != null ? result.getErrorMessage() : "Unknown error";
                log.error("Script execution failed: {}", errorMessage);


                throw new RuntimeException(errorMessage);
            }

        } catch (Exception e) {
            log.error("Error executing Blockly script: {}", e.getMessage(), e);

            throw new RuntimeException(e.getMessage());
        }
    }
}
