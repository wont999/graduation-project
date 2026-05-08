package com.example.blockly_executor_service.event;

import com.example.blockly_executor_service.model.dto.ExecutionResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScriptExecutedEvent {

    //идентификатор запроса
    private String requestId;

    //кто выполнил (из metadata)
    private String  userId;
    private String  tenantId;

    //превью скрипта
    private String  scriptPreview;

    //результат
    private ExecutionResult.ExecutionStatus status;
    private String errorMessage;

    //время
    private Instant startTime;
    private Instant endTime;
    private Long executionTimeMs;
}
