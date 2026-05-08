package com.example.blockly_executor_service.model.entity;

import com.example.blockly_executor_service.model.dto.ExecutionResult;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "script_execution_log", schema = "blockly_schema")
@Getter
@Setter
public class ScriptExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String requestId;

    @Column(length = 2000)
    private String scriptPreview;

    @Column(length = 2000)
    private String parametersPreview;

    @Column(length = 2000)
    private String headersPreview;

    @Enumerated(EnumType.STRING)
    private ExecutionResult.ExecutionStatus status;

    @Column(length = 2000)
    private String errorMessage;

    private Instant startTime;
    private Instant endTime;
    private Long executionTime;


}
