package com.example.blockly_executor_service.repository;

import com.example.blockly_executor_service.model.entity.ScriptExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScriptExecutionLogRepository extends JpaRepository<ScriptExecutionLog, Long> {

}
