package com.example.blockly_executor_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Мониторинг задержки репликации PostgreSQL
 * Проверяет, насколько реплика отстает от мастера
 */
@Slf4j
@Component
@ConditionalOnProperty(
        value = "blockly.replication.monitoring.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ReplicationMonitor {

    private final JdbcTemplate readJdbcTemplate;

    private static final long WARNING_LAG_MS = 1000;   // 1 секунда
    private static final long CRITICAL_LAG_MS = 5000;  // 5 секунд

    public ReplicationMonitor(@Qualifier("readJdbcTemplate") JdbcTemplate readJdbcTemplate) {
        this.readJdbcTemplate = readJdbcTemplate;
    }


    //Проверяет задержку репликации каждые 10 секунд
    @Scheduled(fixedDelay = 10000, initialDelay = 30000)
    public void checkReplicationLag() {
        try {
            Long lagMs = getReplicationLagMs();

            if (lagMs == null) {
                log.debug("REPLICATION_LAG - Unable to determine lag");
                return;
            }

            if (lagMs > CRITICAL_LAG_MS) {
                log.error("REPLICATION_LAG - CRITICAL: Replica is {} seconds behind master",
                        lagMs / 1000.0);
                // TODO: добавить алерт или временное переключение на master
            } else if (lagMs > WARNING_LAG_MS) {
                log.warn("REPLICATION_LAG - WARNING: Replica is {} ms behind master", lagMs);
            } else {
                log.debug("REPLICATION_LAG - OK: Replica lag is {} ms", lagMs);
            }

        } catch (Exception e) {
            log.error("REPLICATION_LAG - Error checking replication lag: {}", e.getMessage());
        }
    }

    //Получает задержку репликации в миллисекундах
    private Long getReplicationLagMs() {
        try {
            // Запрос к реплике: когда была применена последняя транзакция
            String query = "SELECT EXTRACT(EPOCH FROM (now() - pg_last_xact_replay_timestamp())) * 1000 AS lag_ms";

            Double lagSeconds = readJdbcTemplate.queryForObject(query, Double.class);

            if (lagSeconds == null) {
                return null;
            }

            return lagSeconds.longValue();

        } catch (Exception e) {
            log.debug("Unable to query replication lag: {}", e.getMessage());
            return null;
        }
    }


    //Проверяет, является ли текущая БД репликой
    public boolean isReplica() {
        try {
            Boolean inRecovery = readJdbcTemplate.queryForObject(
                    "SELECT pg_is_in_recovery()",
                    Boolean.class
            );
            return Boolean.TRUE.equals(inRecovery);
        } catch (Exception e) {
            log.debug("Unable to check if database is replica: {}", e.getMessage());
            return false;
        }
    }

}
