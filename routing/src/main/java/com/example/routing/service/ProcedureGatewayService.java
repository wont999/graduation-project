package com.example.routing.service;

import com.example.common.exception.ExceptionMessages;
import com.example.common.exception.KafkaSendException;
import com.example.common.mapper.ProcedureMapper;
import com.example.common.model.ProcedurePayload;
import com.example.common.model.ProcedureRequestDto;
import com.example.common.model.ProcedureResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Основной сервис для маршрутизации процедур.
 * Отправляет запросы в соответствующие топики на основе типа клиента и ждет ответ.
 * Маршрутизация настраивается динамически через ClientTypeTopicMapper.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProcedureGatewayService {
    final KafkaTemplate<String, ProcedurePayload<?>> kafkaTemplate;
    final ResponseStorage responseStorage;
    final ClientTypeTopicMapper clientTypeTopicMapper;
    final String instanceId;
    final ProcedureMapper procedureMapper;

    @Value("${gateway.request.timeout:30}")
    long requestTimeoutSeconds;

    /**
     * Выполняет процедуру: отправляет в Kafka и синхронно ждет ответ
     */
    public ProcedureResponse<?> executeProcedure(ProcedureRequestDto<?> requestDto, String userId,  String organizationId) {
        var payload = procedureMapper.toPayload(requestDto, instanceId, userId, organizationId);

        var responseFuture = responseStorage.createPendingRequest(payload.requestId());

        var targetTopic = clientTypeTopicMapper.getTopicForClientType(requestDto.clientType());
        log.info("Sending procedure '{}' to topic '{}' with requestId: {}", requestDto.procedureName(), targetTopic, payload.requestId());

        sendToKafka(targetTopic, payload, responseFuture);

        return awaitResponse(payload.requestId(), responseFuture);
    }

    /**
     * Выполняет процедуру асинхронно: отправляет в Kafka и возвращает CompletableFuture
     * Метод выполняется в отдельном потоке благодаря @Async
     */
    @Async
    public CompletableFuture<ProcedureResponse<?>> executeProcedureAsync(ProcedureRequestDto<?> requestDto, String userId,String organizationId) {
        var payload = procedureMapper.toPayload(requestDto, instanceId, userId, organizationId);

        var responseFuture = responseStorage.createPendingRequest(payload.requestId());

        var targetTopic = clientTypeTopicMapper.getTopicForClientType(requestDto.clientType());
        log.info("Sending async procedure '{}' to topic '{}' with requestId: {}", requestDto.procedureName(), targetTopic, payload.requestId());

        sendToKafka(targetTopic, payload, responseFuture);

        return responseFuture.orTimeout(requestTimeoutSeconds, TimeUnit.SECONDS)
                .whenComplete((response, ex) -> {
                    if (ex != null) {
                        log.error("Async request {} failed: {}", payload.requestId(), ex.getMessage());
                        responseStorage.timeoutRequest(payload.requestId());
                    } else {
                        log.info("Async request {} completed successfully", payload.requestId());
                    }
                });
    }

    /**
     * Отправляет запрос в Kafka
     */
    void sendToKafka(
            String targetTopic,
            ProcedurePayload<?> payload,
            CompletableFuture<ProcedureResponse<?>> responseFuture
    ) {
        kafkaTemplate.send(targetTopic, String.valueOf(payload.requestId()), payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send request {}: {}", payload.requestId(), ex.getMessage());
                        String message = String.format(ExceptionMessages.KAFKA_SEND_FAILED, payload.requestId(), targetTopic);
                        responseFuture.completeExceptionally(new KafkaSendException(message, ex));
                    } else {
                        log.debug("Request {} sent successfully to {} (partition: {}, offset: {})", 
                                payload.requestId(), targetTopic, 
                                result.getRecordMetadata().partition(), 
                                result.getRecordMetadata().offset());
                    }
                });
    }

    /**
     * Ожидает ответ от worker'а с обработкой ошибок
     * Преобразует checked exceptions в unchecked для глобальной обработки
     */
    @SneakyThrows
    ProcedureResponse<?> awaitResponse(UUID requestId, CompletableFuture<ProcedureResponse<?>> responseFuture) {
        try {
            var response = responseFuture.get(requestTimeoutSeconds, TimeUnit.SECONDS);
            log.info("Received response for requestId: {}", requestId);
            return response;
        } catch (TimeoutException e) {
            log.error("Request {} timed out after {} seconds", requestId, requestTimeoutSeconds);
            responseStorage.timeoutRequest(requestId);
            String message = String.format(ExceptionMessages.REQUEST_TIMEOUT, requestId, requestTimeoutSeconds);
            throw new TimeoutException(message);
        }
    }
}