package com.example.blockly_executor_service.event;

import com.example.blockly_executor_service.model.entity.ScriptExecutionLog;
import com.example.blockly_executor_service.repository.ScriptExecutionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptExecutedEventConsumer {

    private final ScriptExecutionLogRepository scriptExecutionLogRepository;


    /**
     * Слушает события выполнения скриптов из Kafka
     * и сохраняет логи в БД (мастер)
     */
    @KafkaListener(
            topics = "script-executed-event",
            groupId = "blockly-log-writer",
            containerFactory = "eventKafkaListenerContainerFactory"
    )
    @Transactional
    public void consumeScriptExecutedEvent(ScriptExecutedEvent event) {
        log.info("EVENT_RECEIVED - RequestId: {}, Status: {}",
                event.getRequestId(), event.getStatus());

        try {
            ScriptExecutionLog executionLog = new ScriptExecutionLog();
            executionLog.setRequestId(event.getRequestId());
            executionLog.setScriptPreview(event.getScriptPreview());
            executionLog.setStatus(event.getStatus());
            executionLog.setErrorMessage(event.getErrorMessage());
            executionLog.setStartTime(event.getStartTime());
            executionLog.setEndTime(event.getEndTime());
            executionLog.setExecutionTime(event.getExecutionTimeMs());

            // Сохраняем в БД через JPA (использует writeDataSource)
            scriptExecutionLogRepository.save(executionLog);
            log.debug("LOG_SAVED - RequestId: {} saved to database", event.getRequestId());
        } catch (Exception e) {
            log.error("LOG_SAVE_FAILED - RequestId: {}, Error: {}",
                    event.getRequestId(), e.getMessage(), e);
            throw e;
        }
    }
}
