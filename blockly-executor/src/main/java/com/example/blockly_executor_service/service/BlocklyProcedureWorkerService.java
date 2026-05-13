package com.example.blockly_executor_service.service;

import com.example.common.ProcedureExecutor;
import com.example.common.exception.ProcedureExecutionException;
import com.example.common.mapper.ProcedureMapper;
import com.example.common.model.ProcedurePayload;
import com.example.common.model.ProcedureResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;

import static com.example.common.exception.ExceptionMessages.PROCEDURE_NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlocklyProcedureWorkerService {

    final Map<String, ProcedureExecutor<?, ?>> procedures;
    final ObjectMapper objectMapper;
    final KafkaTemplate<String, ProcedureResponse<?>> responseKafkaTemplate;
    final ProcedureMapper procedureMapper;

    @KafkaListener(topics = "blockly-executor-procedures", groupId = "worker-blockly-executor", containerFactory = "blocklyKafkaListenerContainerFactory")
    public void handleBlocklyProcedure(ProcedurePayload<?> request) {
        log.info("Processing BLOCKLY-EXECUTOR procedure: {} (requestId: {})", request.procedureName(), request.requestId());

        try {
            ProcedureResponse<?> response = executeProcedure(request);
            sendResponse(request.replyTo(), request.requestId(), response);
        } catch (Exception e) {
            log.error("Error processing procedure: {}", e.getMessage(), e);
            ProcedureResponse<?> errorResponse = procedureMapper.toResponseError(request, e.getMessage());
            sendResponse(request.replyTo(), request.requestId(), errorResponse);
        }
    }

    public <P, R> ProcedureResponse<R> executeProcedure(ProcedurePayload<P> request) {
        ProcedureExecutor<P, R> executor = getExecutor(request.procedureName());

        P params = convertParameters(request.parameters(), executor);

        if (params instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> paramsMap = (Map<String, Object>) params;

            paramsMap.put("__metadata", request.metadata());
            paramsMap.put("__requestId", request.requestId().toString());

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