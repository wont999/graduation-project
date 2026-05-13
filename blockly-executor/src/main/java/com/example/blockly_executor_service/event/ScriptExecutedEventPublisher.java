package com.example.blockly_executor_service.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptExecutedEventPublisher {

    private final KafkaTemplate<String, ScriptExecutedEvent> eventKafkaTemplate;
    private static final String TOPIC = "script-executed-event";

    public void publishEvent(ScriptExecutedEvent event) {
        log.info("Publishing script executed event for requestId: {} to topic: {}", event.getRequestId(), TOPIC);

        eventKafkaTemplate.send(TOPIC, event.getRequestId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event for requestId: {}: {}", event.getRequestId(), ex.getMessage(), ex);
                    } else {
                        log.debug("Event published successfully for requestId: {} to partition: {}, offset: {}",
                                event.getRequestId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
