package com.example.blockly_executor_service.service.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisher {

    @Qualifier("writeJdbcTemplate")
    private final JdbcTemplate writeJdbcTemplate;
    private final KafkaTemplate<String, String> outboxKafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional(transactionManager = "writeTransactionManager")
    public void publishOutbox() {
        List<Map<String, Object>> batch = writeJdbcTemplate.queryForList(
                "SELECT id, tenant_id, event_type, payload::text AS payload " +
                        "FROM outbox_event WHERE published = false ORDER BY id LIMIT 100 FOR UPDATE SKIP LOCKED"); //FOR UPDATE SKIP LOCKED: Если запущены 3 инстанса executor - каждый берёт свою порцию строк, не блокируя друг друга. Без этого все 3 инстанса бы читали одни и те же строки.

        for (var e : batch) {
            String tenantId = (String) e.get("tenant_id");
            // отправляем синхронно, чтобы published=true ставился только после успеха
            try {
                String message = "{\"eventType\":\"" + e.get("event_type") + "\"," +
                        "\"tenantId\":\"" + tenantId + "\"," +
                        "\"payload\":" + e.get("payload") + "}";
                outboxKafkaTemplate.send("domain-events", tenantId, message).get();
                writeJdbcTemplate.update(
                        "UPDATE outbox_event SET published = true WHERE id = ?", e.get("id"));
            } catch (Exception ex) {
                log.error("Failed to publish outbox id={}: {}", e.get("id"), ex.getMessage());
                throw new RuntimeException(ex);
            }
        }

    }
}
