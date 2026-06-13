package com.example.blockly_executor_service.service.worker;

import com.example.common.model.ProcedurePayload;
import com.example.common.model.ProcedureResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ProcedureProcessingService {

    private final JdbcTemplate writeJdbcTemplate;
    private final BlocklyProcedureWorkerService workerService;
    private final ObjectMapper objectMapper;
    private final ProcedureProcessingService self; // для вызова @Transactional через прокси
    private static final Pattern WRITE_CALL = Pattern.compile("\\.(create|update|delete)\\s*\\(");

    public ProcedureProcessingService(
            @Qualifier("writeJdbcTemplate") JdbcTemplate writeJdbcTemplate,
            BlocklyProcedureWorkerService workerService,
            ObjectMapper objectMapper,
            @Lazy ProcedureProcessingService self) {
        this.writeJdbcTemplate = writeJdbcTemplate;
        this.workerService = workerService;
        this.objectMapper = objectMapper;
        this.self = self;
    }


    /**
     * Оркестрация. НЕ транзакционный метод — управляет короткими транзакциями вручную,
     * чтобы выполнение скрипта шло в своей транзакции, а claim/done/failed в своих.
     */
    public ProcedureResponse<?> process(ProcedurePayload<?> request) {
        // Извлекаем скрипт из параметров для проверки
        String script = null;
        if (request.parameters() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) request.parameters();
            Object s = params.get("script");
            if (s instanceof String) script = (String) s;
        }

        // read-only: ни транзакции, ни dedup — повтор безопасен
        if (!isMutating(script)) {
            return workerService.executeProcedure(request);
        }

        UUID requestId = request.requestId();
        if (!self.tryClaim(requestId)) {
            ProcedureResponse<?> done = self.findResolved(requestId);
            if (done != null) {
                log.warn("Duplicate requestId {}, returning stored response", requestId);
                return done;
            }
            // строка есть, но не DONE/FAILED -> её обрабатывает кто-то другой, повторим позже
            throw new IllegalStateException("Request " + requestId + " is already in progress, will retry");
        }

        // claim получен этим потоком -> выполняем скрипт в ОДНОЙ транзакции с пометкой DONE
        try {
            return self.executeAndMarkDone(request);
        } catch (Exception e) {
            // основная транзакция откатилась (включая IN_PROGRESS).
            // Детерминированную ошибку фиксируем отдельной транзакцией, чтобы не крутить вечно.
            self.markFailed(requestId, e.getMessage());
            throw e;
        }
    }

    /** Короткая транзакция: занять requestId. true — заняли мы, false — уже существует. */
    @Transactional(transactionManager = "writeTransactionManager")
    public boolean tryClaim(UUID requestId) {
        return writeJdbcTemplate.update(
                "INSERT INTO processed_request(request_id, status) VALUES (?, 'IN_PROGRESS') " +
                        "ON CONFLICT (request_id) DO NOTHING",
                requestId) == 1;
    }

    /**
     * Одна транзакция: выполнение скрипта (все его запросы) + пометка DONE.
     * При исключении откатывается всё, включая строку IN_PROGRESS.
     */
    @Transactional(transactionManager = "writeTransactionManager")
    public ProcedureResponse<?> executeAndMarkDone(ProcedurePayload<?> request) {
        ProcedureResponse<?> response = workerService.executeProcedure(request);
        writeJdbcTemplate.update(
                "UPDATE processed_request SET status='DONE', response_json=?, processed_at=now() WHERE request_id=?",
                serialize(response), request.requestId());
        return response;
    }

    /** Отдельная транзакция: вернуть сохранённый ответ для уже разрешённого запроса. */
    @Transactional(transactionManager = "writeTransactionManager", readOnly = true)
    public ProcedureResponse<?> findResolved(UUID requestId) {
        String json = writeJdbcTemplate.query(
                "SELECT response_json FROM processed_request WHERE request_id = ? AND status = 'DONE'",
                rs -> rs.next() ? rs.getString("response_json") : null,
                requestId);
        return json != null ? deserialize(json) : null;
    }

    /**
     * Отдельная транзакция (REQUIRES_NEW): зафиксировать FAILED.
     * Основная транзакция к этому моменту откатила строку IN_PROGRESS, поэтому пишем заново.
     */
    @Transactional(transactionManager = "writeTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID requestId, String error) {
        writeJdbcTemplate.update(
                "INSERT INTO processed_request(request_id, status, response_json, processed_at) " +
                        "VALUES (?, 'FAILED', ?, now()) " +
                        "ON CONFLICT (request_id) DO UPDATE SET status='FAILED', response_json=EXCLUDED.response_json, processed_at=now()",
                requestId, error);
    }

    /** Watchdog: чистим зависшие IN_PROGRESS (упавший инстанс не докатил до DONE). */
    @Scheduled(fixedDelay = 60_000)
    @Transactional(transactionManager = "writeTransactionManager")
    public void cleanupStaleInProgress() {
        int removed = writeJdbcTemplate.update(
                "DELETE FROM processed_request WHERE status='IN_PROGRESS' AND processed_at < now() - interval '5 minutes'");
        if (removed > 0) {
            log.warn("Cleaned up {} stale IN_PROGRESS rows", removed);
        }
    }

    private boolean isMutating(String script) {
        return script != null && WRITE_CALL.matcher(script).find();
    }


    private String serialize(ProcedureResponse<?> response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize response", e);
        }
    }

    private ProcedureResponse<?> deserialize(String json) {
        try {
            return objectMapper.readValue(json, ProcedureResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize stored response", e);
        }
    }
}