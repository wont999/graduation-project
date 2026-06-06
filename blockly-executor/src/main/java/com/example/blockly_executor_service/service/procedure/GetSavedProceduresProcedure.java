package com.example.blockly_executor_service.service.procedure;

import com.example.blockly_executor_service.service.SavedProcedureService;
import com.example.common.ProcedureExecutor;
import com.example.common.model.ExecutionMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component("getSavedProcedures")
@RequiredArgsConstructor
public class GetSavedProceduresProcedure implements ProcedureExecutor<Map<String, Object>, Object> {

    private final SavedProcedureService savedProcedureService;

    @Override
    public Object execute(Map<String, Object> parameters) {
        ExecutionMetadata metadata = (ExecutionMetadata) parameters.get("__metadata");
        String tenantId = metadata.tenantId();
        log.info("Getting saved procedures for tenant: {}", tenantId);
        return savedProcedureService.listByTenant(tenantId);
    }
}