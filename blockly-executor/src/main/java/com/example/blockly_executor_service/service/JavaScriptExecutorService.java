package com.example.blockly_executor_service.service;

import com.example.blockly_executor_service.dao.CqrsDatabaseAccessor;
import com.example.blockly_executor_service.event.ScriptExecutedEvent;
import com.example.blockly_executor_service.event.ScriptExecutedEventPublisher;
import com.example.blockly_executor_service.model.ExecutionRequest;
import com.example.blockly_executor_service.model.ExecutionResult;
import com.example.blockly_executor_service.repository.ScriptExecutionLogRepository;
import com.example.common.exception.ProcedureExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import javax.script.*;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@EnableAsync(proxyTargetClass = true)
public class JavaScriptExecutorService implements ScriptExecutionService {

    private final ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
    private final ScriptExecutionLogRepository scriptExecutionLogRepository;
    private final JdbcTemplate writeJdbcTemplate;
    private final JdbcTemplate readJdbcTemplate;
    private final ScriptExecutedEventPublisher eventPublisher;

    public JavaScriptExecutorService(ScriptExecutionLogRepository scriptExecutionLogRepository,
                                     @Qualifier("writeJdbcTemplate") JdbcTemplate writeJdbcTemplate,
                                     @Qualifier("readJdbcTemplate") JdbcTemplate readJdbcTemplate,
                                     ScriptExecutedEventPublisher eventPublisher) {
        this.scriptExecutionLogRepository = scriptExecutionLogRepository;
        this.writeJdbcTemplate = writeJdbcTemplate;
        this.readJdbcTemplate = readJdbcTemplate;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public ExecutionResult executeScript(ExecutionRequest request) {

        var scriptEngine = scriptEngineManager.getEngineByName("graal.js");
        if (scriptEngine == null) {
            throw new ProcedureExecutionException("GraalVM JavaScript engine not found");
        }

        Instant startTime = Instant.now();
        String requestId = request.getRequestId() != null ? request.getRequestId() : UUID.randomUUID().toString();
        String tenantId = (String) request.getHeaders().get("tenantId");

        if (tenantId == null || tenantId.isEmpty()) {
            throw new SecurityException("Tenant ID is required but not provided");
        }

        log.info("Executing script for tenant: {}", tenantId);

        try{
            //Разрешает JavaScript коду вызывать Java методы
            scriptEngine.getContext().setAttribute("polyglot.js.allowHostAccess", true, ScriptContext.ENGINE_SCOPE);
            //Запрещает JavaScript коду создавать новые Java объекты
            scriptEngine.getContext().setAttribute("polyglot.js.allowHostClassLookup", false, ScriptContext.ENGINE_SCOPE);

            // Создаем DatabaseAccessor для доступа к БД с изоляцией по tenant
            /*DatabaseAccessor dbAccessor = new DatabaseAccessor(tenantId, jdbcTemplate);
            scriptEngine.put("DB", dbAccessor);*/

            CqrsDatabaseAccessor dbAccessor = new CqrsDatabaseAccessor(
                    tenantId,
                    writeJdbcTemplate,
                    readJdbcTemplate
            );
            scriptEngine.put("DB", dbAccessor);

            if(request.getParams() != null){
                request.getParams().forEach(scriptEngine::put);
            }

            Object result = scriptEngine.eval(request.getScript());

            Instant endTime = Instant.now();
            Long executionTime = Duration.between(startTime, endTime).toMillis();


            publishScriptExecutedEvent(request, startTime, endTime, executionTime,
                    ExecutionResult.ExecutionStatus.SUCCESS, null);

            return ExecutionResult.builder()
                    .requestId(requestId)
                    .result(result)
                    .status(ExecutionResult.ExecutionStatus.SUCCESS)
                    .executionTime(executionTime)
                    .startTime(startTime)
                    .endTime(endTime)
                    .build();

        } catch (ScriptException e) {
            Instant endTime = Instant.now();
            Long executionTime = Duration.between(startTime, endTime).toMillis();

            log.error("SCRIPT_ERROR - RequestId: {} - {} - Script failed in {}ms at {}",
                requestId,e.getMessage(), executionTime, endTime);


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
        } catch (Exception e){
            Instant endTime = Instant.now();
            Long executionTime = Duration.between(startTime, endTime).toMillis();

            log.error("SCRIPT_ERROR - RequestId: {} - {} - Script failed in {}ms at {}",
                requestId,e.getMessage(), executionTime, endTime);


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
        var scriptEngine = scriptEngineManager.getEngineByName("graal.js");
        if (scriptEngine == null) {
            throw new ProcedureExecutionException("GraalVM JavaScript engine not found");
        }
        log.info("ScriptEngine found: {}",scriptEngine.getClass().getName());

        try{
            CompiledScript compiledScript = ((Compilable) scriptEngine).compile(script);
            return true;
        } catch (ScriptException e) {
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
