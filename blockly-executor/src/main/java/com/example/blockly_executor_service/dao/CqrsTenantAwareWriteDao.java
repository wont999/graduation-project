package com.example.blockly_executor_service.dao;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.HostAccess;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class CqrsTenantAwareWriteDao {

    private static final Pattern VALID_IDENTIFIER = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final Pattern VALID_COLUMN_NAME = Pattern.compile("^[a-zA-Z0-9_]+$");

    private final String tenantId;
    private final String tableName;
    private final JdbcTemplate writeJdbcTemplate;
    private final MeterRegistry meterRegistry;

    public CqrsTenantAwareWriteDao(String tenantId, String tableName, JdbcTemplate writeJdbcTemplate, MeterRegistry meterRegistry) {
        this.tenantId = tenantId;
        this.tableName = tableName;
        this.writeJdbcTemplate = writeJdbcTemplate;
        this.meterRegistry = meterRegistry;
        log.debug("Created WRITE DAO for table: {} with tenantId: {}", tableName, tenantId);
    }

    @HostAccess.Export
    public Object create(Map<String, Object> data) {
        Timer timer = Timer.builder("blockly_dao_write")
                .tag("operation", "create")
                .tag("table", tableName)
                .tag("tenant", tenantId)
                .publishPercentileHistogram()
                .register(meterRegistry);
        return timer.record(() -> {
            log.debug("COMMAND - create in table: {}", fullyQualifiedTableName());

            data.remove("id");
            data.remove("created_at");
            data.remove("updated_at");

            for (String columnName : data.keySet()) {
                if (!VALID_COLUMN_NAME.matcher(columnName).matches()) {
                    throw new SecurityException("Invalid column name: " + columnName);
                }
            }

            List<String> columns = new ArrayList<>(data.keySet());
            String columnsSql = String.join(", ", columns);
            String valuesSql = columns.stream().map(c -> "?").collect(Collectors.joining(", "));

            String sql = String.format(
                    "INSERT INTO %s (%s) VALUES (%s) RETURNING *",
                    fullyQualifiedTableName(), columnsSql, valuesSql
            );

            Object[] values = columns.stream().map(data::get).toArray();

            List<Map<String, Object>> results = writeJdbcTemplate.queryForList(sql, values);
            return results.isEmpty() ? null : results.get(0);
        });
    }

    @HostAccess.Export
    public Object update(Object id, Map<String, Object> data) {
        Timer timer = Timer.builder("blockly_dao_write")
                .tag("operation", "update")
                .tag("table", tableName)
                .tag("tenant", tenantId)
                .publishPercentileHistogram()
                .register(meterRegistry);
        return timer.record(() -> {
            log.debug("COMMAND - update in table: {}", fullyQualifiedTableName());

            data.remove("id");
            data.remove("created_at");
            data.remove("updated_at");

            if (data.isEmpty()) {
                String sql = String.format("SELECT * FROM %s WHERE id = ?", fullyQualifiedTableName());
                List<Map<String, Object>> results = writeJdbcTemplate.queryForList(sql, id);
                return results.isEmpty() ? null : results.get(0);
            }

            for (String columnName : data.keySet()) {
                if (!VALID_COLUMN_NAME.matcher(columnName).matches()) {
                    throw new SecurityException("Invalid column name: " + columnName);
                }
            }

            List<String> setClauses = new ArrayList<>();
            List<Object> params = new ArrayList<>();

            for (Map.Entry<String, Object> entry : data.entrySet()) {
                setClauses.add(entry.getKey() + " = ?");
                params.add(entry.getValue());
            }

            setClauses.add("updated_at = CURRENT_TIMESTAMP");
            params.add(id);

            String sql = String.format(
                    "UPDATE %s SET %s WHERE id = ? RETURNING *",
                    fullyQualifiedTableName(),
                    String.join(", ", setClauses)
            );

            List<Map<String, Object>> results = writeJdbcTemplate.queryForList(sql, params.toArray());
            return results.isEmpty() ? null : results.get(0);
        });
    }

    @HostAccess.Export
    public boolean delete(Object id) {
        Timer timer = Timer.builder("blockly_dao_write")
                .tag("operation", "delete")
                .tag("table", tableName)
                .tag("tenant", tenantId)
                .publishPercentileHistogram()
                .register(meterRegistry);
        return timer.record(() -> {
            log.debug("COMMAND - delete from table: {}", fullyQualifiedTableName());

            String sql = String.format("DELETE FROM %s WHERE id = ?", fullyQualifiedTableName());
            int deleteCount = writeJdbcTemplate.update(sql, id);
            return deleteCount > 0;
        });
    }

    private String fullyQualifiedTableName() {
        return "tenant_" + tenantId + "." + tableName;
    }
}
