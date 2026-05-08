package com.example.blockly_executor_service.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionResult {
    private String requestId;
    private Object result;
    private String errorMessage;
    private ExecutionStatus status;
    private Long executionTime;
    private Instant startTime;
    private Instant endTime;

    private String logs;

    public enum ExecutionStatus{
        SUCCESS, ERROR
    }
}
