package com.example.blockly_executor_service.service.worker;

import com.example.common.ProcedureExecutor;
import com.example.common.exception.ProcedureExecutionException;
import com.example.common.exception.RequestCapacityExceededException;
import com.example.common.mapper.ProcedureMapper;
import com.example.common.model.ProcedurePayload;
import com.example.common.model.ProcedureResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Map;

import static com.example.common.exception.ExceptionMessages.PROCEDURE_NOT_FOUND;

@Slf4j
@Service
public class BlocklyProcedureWorkerService {

    final Map<String, ProcedureExecutor<?, ?>> procedures;
    final ObjectMapper objectMapper;
    final KafkaTemplate<String, ProcedureResponse<?>> responseKafkaTemplate;
    final ProcedureMapper procedureMapper;
    final MeterRegistry meterRegistry;
    final ProcedureProcessingService processingService;

    public BlocklyProcedureWorkerService(
            Map<String, ProcedureExecutor<?, ?>> procedures,
            ObjectMapper objectMapper,
            KafkaTemplate<String, ProcedureResponse<?>> responseKafkaTemplate,
            ProcedureMapper procedureMapper,
            MeterRegistry meterRegistry,
            @Lazy ProcedureProcessingService processingService) {
        this.procedures = procedures;
        this.objectMapper = objectMapper;
        this.responseKafkaTemplate = responseKafkaTemplate;
        this.procedureMapper = procedureMapper;
        this.meterRegistry = meterRegistry;
        this.processingService = processingService;
    }

    @KafkaListener(topics = "blockly-executor-procedures", groupId = "worker-blockly-executor", containerFactory = "blocklyKafkaListenerContainerFactory")
    public void handleBlocklyProcedure(ProcedurePayload<?> request, Acknowledgment ack) {
        Timer.builder("blockly_listener_total")
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(() -> {
                    long receivedAt = System.currentTimeMillis();
                    log.info("Processing BLOCKLY-EXECUTOR procedure: {} (requestId: {})", request.procedureName(), request.requestId());

                    try {
                        long queueingMs = receivedAt - request.sentAt();
                        Timer.builder("blockly_kafka_queueing")
                                .publishPercentileHistogram()
                                .register(meterRegistry)
                                .record(Duration.ofMillis(queueingMs));

                        ProcedureResponse<?> response = processingService.process(request);
                        sendResponse(request.replyTo(), request.requestId(), response);
                    } catch (IllegalStateException retryable) {
                        log.warn("Deferring requestId {}: {}", request.requestId(), retryable.getMessage());
                    } catch (RequestCapacityExceededException e) {
                        log.warn("GraalVM pool exhausted for requestId: {}", request.requestId());
                        ProcedureResponse<?> errorResponse = procedureMapper.toResponseError(request, "POOL_BUSY: " + e.getMessage());
                        sendResponse(request.replyTo(), request.requestId(), errorResponse);
                    } catch (Exception e) {
                        log.error("Error processing procedure: {}", e.getMessage(), e);
                        ProcedureResponse<?> errorResponse = procedureMapper.toResponseError(request, e.getMessage());
                        sendResponse(request.replyTo(), request.requestId(), errorResponse);
                    }   finally {
                        ack.acknowledge(); 
                    }
                });
    }

    public <P, R> ProcedureResponse<R> executeProcedure(ProcedurePayload<P> request) {
        ProcedureExecutor<P, R> executor = getExecutor(request.procedureName());

        P params = convertParameters(request.parameters(), executor);

        if (params instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> paramsMap = (Map<String, Object>) params;

            paramsMap.put("__metadata", request.metadata());
            paramsMap.put("requestId", request.requestId().toString());

            log.debug("Added execution metadata for userId: {}", request.metadata().userId());
        }

        R result = executor.execute(params);

        log.info("Procedure {} completed successfully (requestId: {})", request.procedureName(), request.requestId());

        return procedureMapper.toResponse(request, result);
    }

    <P, R> ProcedureExecutor<P, R> getExecutor(String procedureName) {
        ProcedureExecutor<?, ?> executor = procedures.get(procedureName);

        if (executor == null) {
            throw new ProcedureExecutionException(PROCEDURE_NOT_FOUND.formatted(procedureName));
        }

        return (ProcedureExecutor<P, R>) executor;
    }

    <P> P convertParameters(Object parameters, ProcedureExecutor<P, ?> executor) {
        if (parameters == null) {
            return null;
        }

        Class<P> parameterType = getParameterType(executor);

        if (parameterType.isInstance(parameters)) {
            return (P) parameters;
        }

        P converted = objectMapper.convertValue(parameters, parameterType);
        log.debug("Successfully converted parameters to type: {}", parameterType.getSimpleName());

        return converted;
    }

    <P> Class<P> getParameterType(ProcedureExecutor<P, ?> executor) {
        for (Type genericInterface : executor.getClass().getGenericInterfaces()) {
            if (genericInterface instanceof ParameterizedType paramType
                    && paramType.getRawType().equals(ProcedureExecutor.class)) {
                Type[] typeArguments = paramType.getActualTypeArguments();
                if (typeArguments.length > 0 && typeArguments[0] instanceof Class<?> clazz
                        && clazz != Object.class) {
                    return (Class<P>) clazz;
                }
            }
        }

        log.warn("Could not determine specific parameter type for executor: {}, using Object.class",
                executor.getClass().getName());
        return (Class<P>) Object.class;
    }

    public void sendResponse(String replyToTopic, java.util.UUID requestId, ProcedureResponse<?> response) {
        log.info("Sending response for requestId: {} to topic: {}", requestId, replyToTopic);

        responseKafkaTemplate.send(replyToTopic, requestId.toString(), response)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send response for requestId: {} to topic: {}: {}",
                                requestId, replyToTopic, ex.getMessage(), ex);
                    } else {
                        log.debug("Response sent successfully for requestId: {} to partition: {}, offset: {}",
                                requestId,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}