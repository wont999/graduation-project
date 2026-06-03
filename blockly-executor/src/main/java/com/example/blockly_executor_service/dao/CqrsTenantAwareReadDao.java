package com.example.blockly_executor_service.dao;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.HostAccess;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
public class CqrsTenantAwareReadDao {

    private static final Pattern VALID_IDENTIFIER = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final Pattern VALID_COLUMN_NAME = Pattern.compile("^[a-zA-Z0-9_]+$");

    private final String tenantId;
    private final String tableName;
    private final JdbcTemplate readJdbcTemplate;
    private final MeterRegistry meterRegistry;
    private final String fullyQualifiedTableName;

    private final Map<String, Timer> timerCache = new HashMap<>();
    private final String findByIdSql;
    private final String findAllSql;
    private final String findRecentSql;
    private final String countSql;

    public CqrsTenantAwareReadDao(String tenantId, String tableName, String fullyQualifiedTableName, JdbcTemplate readJdbcTemplate, MeterRegistry meterRegistry) {
        this.tenantId = tenantId;
        this.tableName = tableName;
        this.fullyQualifiedTableName = fullyQualifiedTableName;
        this.readJdbcTemplate = readJdbcTemplate;
        this.meterRegistry = meterRegistry;

        this.findByIdSql = String.format("SELECT * FROM %s WHERE id = ?", fullyQualifiedTableName);
        this.findAllSql = String.format("SELECT * FROM %s ORDER BY id LIMIT ?", fullyQualifiedTableName);
        this.findRecentSql = String.format("SELECT * FROM %s ORDER BY id DESC LIMIT ?", fullyQualifiedTableName);
        this.countSql = String.format("SELECT COUNT(*) FROM %s", fullyQualifiedTableName);

        log.debug("Created READ DAO for table: {} with tenantId: {}", tableName, tenantId);
    }

    private Timer getTimer(String operation) {
        return timerCache.computeIfAbsent(operation, op ->
                Timer.builder("blockly_dao_read")
                        .tag("operation", op)
                        .tag("table", tableName)
                        .tag("tenant", tenantId)
                        .publishPercentileHistogram()
                        .register(meterRegistry)
        );
    }

    @HostAccess.Export
    public Object findById(Object id) {
        return getTimer("findById").record(() -> {
            List<Map<String, Object>> results = readJdbcTemplate.queryForList(findByIdSql, id);
            return results.isEmpty() ? null : results.get(0);
        });
    }

    @HostAccess.Export
    public List<Map<String, Object>> findAll() {
        return findAll(100);
    }

    @HostAccess.Export
    public List<Map<String, Object>> findAll(int limit) {
        return getTimer("findAll").record(() -> readJdbcTemplate.queryForList(findAllSql, limit));
    }

    @HostAccess.Export
    public List<Map<String, Object>> findRecent(int count) {
        return getTimer("findRecent").record(() -> readJdbcTemplate.queryForList(findRecentSql, count));
    }

    @HostAccess.Export
    public List<Map<String, Object>> where(Map<String, Object> conditions) {
        return getTimer("where").record(() -> {
            if (conditions == null || conditions.isEmpty()) {
                return findAll();
            }

            StringBuilder sql = new StringBuilder(String.format(
                    "SELECT * FROM %s WHERE 1=1",
                    fullyQualifiedTableName
            ));

            List<Object> params = new ArrayList<>();

            for (Map.Entry<String, Object> entry : conditions.entrySet()) {
                String columnName = entry.getKey();
                if (!VALID_COLUMN_NAME.matcher(columnName).matches()) {
                    throw new SecurityException("Invalid column name: " + columnName);
                }
                sql.append(" AND ").append(columnName).append(" = ?");
                params.add(entry.getValue());
            }

            sql.append(" ORDER BY id");
            return readJdbcTemplate.queryForList(sql.toString(), params.toArray());
        });
    }

    @HostAccess.Export
    public Object findOne(Map<String, Object> conditions) {
        return getTimer("findOne").record(() -> {
            List<Map<String, Object>> results = where(conditions);
            return results.isEmpty() ? null : results.get(0);
        });
    }

    @HostAccess.Export
    public Long count() {
        return getTimer("count").record(() ->
                readJdbcTemplate.queryForObject(countSql, Long.class)
        );
    }

    @HostAccess.Export
    public List<Map<String, Object>> executeRawQuery(String sql, Object... params) {
        log.debug("Executing READ query: {} (tenant: {})", sql, tenantId);
        if (!sql.trim().toUpperCase().startsWith("SELECT")) {
            throw new SecurityException("Only SELECT queries are allowed");
        }
        if (!sql.toLowerCase().contains("tenant_id")) {
            log.warn("Query without tenant_id filter: {}", sql);
        }
        return readJdbcTemplate.queryForList(sql, params);
    }

}
