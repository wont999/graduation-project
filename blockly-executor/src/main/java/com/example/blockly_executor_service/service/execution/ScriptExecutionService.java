package com.example.blockly_executor_service.service.execution;

import com.example.blockly_executor_service.model.dto.ExecutionRequest;
import com.example.blockly_executor_service.model.dto.ExecutionResult;

public interface ScriptExecutionService {
    ExecutionResult executeScript(ExecutionRequest request);
    String validateScript(String script);

}
