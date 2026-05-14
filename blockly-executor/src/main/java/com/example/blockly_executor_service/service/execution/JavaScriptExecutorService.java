package com.example.blockly_executor_service.service.execution;

import com.example.blockly_executor_service.dao.CqrsDatabaseAccessor;
import com.example.blockly_executor_service.event.ScriptExecutedEvent;
import com.example.blockly_executor_service.event.ScriptExecutedEventPublisher;
import com.example.blockly_executor_service.model.dto.ExecutionRequest;
import com.example.blockly_executor_service.model.dto.ExecutionResult;
import com.example.common.exception.ProcedureExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@EnableAsync(proxyTargetClass = true)
public class JavaScriptExecutorService implements ScriptExecutionService {

    private static final Engine GRAAL_ENGINE = Engine.newBuilder("js").build();
    private final JdbcTemplate writeJdbcTemplate;
    private final JdbcTemplate readJdbcTemplate;
    private final ScriptExecutedEventPublisher eventPublisher;

    public JavaScriptExecutorService(@Qualifier("writeJdbcTemplate") JdbcTemplate writeJdbcTemplate,
                                     @Qualifier("readJdbcTemplate") JdbcTemplate readJdbcTemplate,
                                     ScriptExecutedEventPublisher eventPublisher) {
        this.writeJdbcTemplate = writeJdbcTemplate;
        this.readJdbcTemplate = readJdbcTemplate;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public ExecutionResult executeScript(ExecutionRequest request) {
        Instant startTime = Instant.now();
        String requestId = request.getRequestId() != null ? request.getRequestId() : UUID.randomUUID().toString();
        String tenantId = (String) request.getHeaders().get("tenantId");

        if (tenantId == null || tenantId.isEmpty()) {
            throw new SecurityException("Tenant ID is required but not provided");
        }

        log.info("Executing script for tenant: {}", tenantId);
        try {
            try (Context context = Context.newBuilder("js")
                    .engine(GRAAL_ENGINE)                    // переиспользуем прогретый Engine
                    .allowHostAccess(HostAccess.ALL)         // разрешение вызывать Java методы
                    .allowHostClassLookup(className -> false) // запрет на создание объектов
                    .build()) {

                // Создаем DatabaseAccessor для доступа к БД с изоляцией по tenant
                CqrsDatabaseAccessor dbAccessor = new CqrsDatabaseAccessor(
                        tenantId,
                        readJdbcTemplate,
                        writeJdbcTemplate
                );
                context.getBindings("js").putMember("DB", dbAccessor);

                if (request.getParams() != null) {
                    request.getParams().forEach((key, value) ->
                            context.getBindings("js").putMember(key, value)
                    );
                }

                Value result = context.eval("js", request.getScript());

                Instant endTime = Instant.now();
                Long executionTime = Duration.between(startTime, endTime).toMillis();

                Object javaResult = result.isNull() ? null : result.as(Object.class);

                publishScriptExecutedEvent(request, startTime, endTime, executionTime,
                        ExecutionResult.ExecutionStatus.SUCCESS, null);

                return ExecutionResult.builder()
                        .requestId(requestId)
                        .result(javaResult != null ? javaResult : "undefined")
                        .status(ExecutionResult.ExecutionStatus.SUCCESS)
                        .executionTime(executionTime)
                        .startTime(startTime)
                        .endTime(endTime)
                        .build();

                } catch (org.graalvm.polyglot.PolyglotException e) {
                Instant endTime = Instant.now();
                Long executionTime = Duration.between(startTime, endTime).toMillis();

                log.error("SCRIPT_ERROR - RequestId: {} - {} - Script failed in {}ms at {}",
                        requestId, e.getMessage(), executionTime, endTime);


                publishScriptExecutedEvent(request, startTime, endTime, executionTime,
                        ExecutionResult.ExecutionStatus.ERROR, e.getMessage());

                return ExecutionResult.builder()
                        .requestId(requestId)
                        .errorMessage(e.getMessage())
                        .status(ExecutionResult.ExecutionStatus.ERROR)
                        .executionTime(executionTime)
                        .startTime(startTime)
                        .endTime(endTime)
                        .build();
            }
            } catch (Exception e) {
                Instant endTime = Instant.now();
                Long executionTime = Duration.between(startTime, endTime).toMillis();

                log.error("SCRIPT_ERROR - RequestId: {} - {} - Script failed in {}ms at {}",
                        requestId, e.getMessage(), executionTime, endTime);


                publishScriptExecutedEvent(request, startTime, endTime, executionTime,
                        ExecutionResult.ExecutionStatus.ERROR, e.getMessage());

                return ExecutionResult.builder()
                        .requestId(requestId)
                        .errorMessage("Error: " + e.getMessage())
                        .status(ExecutionResult.ExecutionStatus.ERROR)
                        .executionTime(executionTime)
                        .startTime(startTime)
                        .endTime(endTime)
                        .build();
            }
        }






    @Override
    public boolean validateScript(String script) {
        try {
            org.graalvm.polyglot.Source.create("js", script);
            return true;
        } catch (org.graalvm.polyglot.PolyglotException e) {
            log.error("Script validation failed: {}", e.getMessage());
            return false;
        }
    }


    private void publishScriptExecutedEvent(ExecutionRequest request,
                                            Instant startTime,
                                            Instant endTime,
                                            Long executionTimeMs,
                                            ExecutionResult.ExecutionStatus status,
                                            String errorMessage) {
        try{
            String userId = (String) request.getHeaders().get("userId");
            String tenantId = (String) request.getHeaders().get("tenantId");

            ScriptExecutedEvent event = ScriptExecutedEvent.builder()
                    .requestId(request.getRequestId())
                    .userId(userId)
                    .tenantId(tenantId)
                    .scriptPreview(preview(request.getScript(),500))
                    .status(status)
                    .errorMessage(preview(errorMessage,1000))
                    .startTime(startTime)
                    .endTime(endTime)
                    .executionTimeMs(executionTimeMs)
                    .build();

            eventPublisher.publishEvent(event);
        } catch (Exception e){
            log.error("Failed to publish ScriptExecutedEvent for requestId: {}: {}",request.getRequestId(), e.getMessage(),e);
        }
    }

    private String preview(Object obj, int max) {
        if (obj == null) {
            return null;
        }
        String str = String.valueOf(obj);
        return str.length() > max ? str.substring(0, max) : str;
    }
}
