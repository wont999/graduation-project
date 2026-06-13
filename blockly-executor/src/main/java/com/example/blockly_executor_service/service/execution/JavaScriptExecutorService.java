package com.example.blockly_executor_service.service.execution;

import com.example.blockly_executor_service.dao.CqrsDatabaseAccessor;
import com.example.blockly_executor_service.model.dto.ExecutionRequest;
import com.example.blockly_executor_service.model.dto.ExecutionResult;

import com.example.blockly_executor_service.service.GraalContextPool;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import com.example.blockly_executor_service.dao.JSResult;
import org.graalvm.polyglot.*;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
public class JavaScriptExecutorService implements ScriptExecutionService {

    private static final Pattern WRITE_CALL = Pattern.compile("\\.(create|update|delete)\\s*\\(");

    private final JdbcTemplate writeJdbcTemplate;
    private final JdbcTemplate readJdbcTemplate;
    private final MeterRegistry meterRegistry;
    private final GraalContextPool contextPool;

    public JavaScriptExecutorService(@Qualifier("writeJdbcTemplate") JdbcTemplate writeJdbcTemplate,
                                     @Qualifier("readJdbcTemplate") JdbcTemplate readJdbcTemplate,
                                     MeterRegistry meterRegistry,
                                     GraalContextPool contextPool) {
        this.writeJdbcTemplate = writeJdbcTemplate;
        this.readJdbcTemplate = readJdbcTemplate;
        this.meterRegistry = meterRegistry;
        this.contextPool = contextPool;
    }

    @Override
    public ExecutionResult executeScript(ExecutionRequest request) {
        Instant startTime = Instant.now();
        String requestId = request.getRequestId() != null ? request.getRequestId() : UUID.randomUUID().toString();
        String tenantId = (String) request.getHeaders().get("tenantId");

        if (tenantId == null || tenantId.isEmpty()) {
            throw new SecurityException("Tenant ID is required but not provided");
        }

        boolean mutating = isMutating(request.getScript());
        JdbcTemplate readTemplate = mutating ? writeJdbcTemplate : readJdbcTemplate;

        log.info("Executing script for tenant: {}", tenantId);
        Context context = null;
        try{
            context = contextPool.acquire();

            // Создаем DatabaseAccessor для доступа к БД с изоляцией по tenant
            CqrsDatabaseAccessor dbAccessor = new CqrsDatabaseAccessor(
                    tenantId,
                    readTemplate, // read-путь: мастер при записи, иначе реплика
                    writeJdbcTemplate, // write-путь: всегда мастер
                    meterRegistry
            );
            Value bindings = context.getBindings("js");
            bindings.putMember("DB", dbAccessor);
            bindings.putMember("__toArr", new ToJSArray(context));
            if (request.getParams() != null) {
                request.getParams().forEach(bindings::putMember);
            }

            Value evalResult = context.eval("js", request.getScript());
            Object result = convert(evalResult);

            Instant endTime = Instant.now();
            Long executionTime = Duration.between(startTime, endTime).toMillis();
            recordTimer(tenantId, ExecutionResult.ExecutionStatus.SUCCESS, startTime, endTime);

            return ExecutionResult.builder()
                    .requestId(requestId)
                    .result(result)
                    .status(ExecutionResult.ExecutionStatus.SUCCESS)
                    .executionTime(executionTime)
                    .startTime(startTime)
                    .endTime(endTime)
                    .build();
        } catch (Exception e){
            Instant endTime = Instant.now();
            Long executionTime = Duration.between(startTime, endTime).toMillis();

            log.error("SCRIPT_ERROR - RequestId: {} - {} - Script failed in {}ms at {}",
                requestId,e.getMessage(), executionTime, endTime);

            recordTimer(tenantId, ExecutionResult.ExecutionStatus.ERROR, startTime, endTime);

            return ExecutionResult.builder()
                    .requestId(requestId)
                    .errorMessage("Error: " + e.getMessage())
                    .status(ExecutionResult.ExecutionStatus.ERROR)
                    .executionTime(executionTime)
                    .startTime(startTime)
                    .endTime(endTime)
                    .build();
        } finally {
            contextPool.release(context);
        }
    }

    private Object convert(Value v) {
        if (v == null || v.isNull()) return null;
        if (v.isHostObject()) {
            Object obj = v.asHostObject();
            if (obj instanceof JSResult jsr) return jsr.toMap();
            return obj;
        }
        if (v.isString()) return v.asString();
        if (v.isBoolean()) return v.asBoolean();
        if (v.isNumber()) return v.fitsInLong() ? v.asLong() : v.asDouble();
        if (v.hasArrayElements()) {
            java.util.List<Object> list = new java.util.ArrayList<>();
            for (long i = 0; i < v.getArraySize(); i++) list.add(convert(v.getArrayElement(i)));
            return list;
        }
        return v.toString();
    }

    private void recordTimer(String tenantId, ExecutionResult.ExecutionStatus status, Instant s, Instant e) {
        Timer.builder("blockly_script_execution")
                .tag("tenant", tenantId).tag("status", status.toString())
                .publishPercentileHistogram().register(meterRegistry)
                .record(Duration.between(s, e));
    }




    @Override
    public String validateScript(String script) {
        Context context = null;
        try {
            context = contextPool.acquire();
            context.parse("js", script);
            return null;
        } catch (Exception e) {
            log.error("Script validation failed: {}", e.getMessage());
            return e.getMessage();
        } finally {
            contextPool.release(context);
        }
    }

    // true, если скрипт содержит операции записи -> читать тоже с мастера
    private boolean isMutating(String script) {
        return script != null && WRITE_CALL.matcher(script).find();
    }
}
