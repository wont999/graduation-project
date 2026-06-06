package com.example.blockly_executor_service.service.procedure;

import com.example.blockly_executor_service.service.SavedProcedureService;
import com.example.common.ProcedureExecutor;
import com.example.common.model.ExecutionMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component("deleteSavedProcedure")
@RequiredArgsConstructor
public class DeleteSavedProcedureProcedure implements ProcedureExecutor<Map<String, Object>, Object> {

    private final SavedProcedureService savedProcedureService;

    @Override
    public Object execute(Map<String, Object> parameters) {
        ExecutionMetadata metadata = (ExecutionMetadata) parameters.get("__metadata");
        String tenantId = metadata.tenantId();
        String name = (String) parameters.get("name");

        log.info("Deleting procedure '{}' for tenant: {}", name, tenantId);
        return savedProcedureService.delete(name, tenantId);
    }
}