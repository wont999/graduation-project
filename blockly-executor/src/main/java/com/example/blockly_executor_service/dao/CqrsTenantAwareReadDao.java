package com.example.blockly_executor_service.dao;

import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.HostAccess;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
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

    public CqrsTenantAwareReadDao(String tenantId, String tableName, JdbcTemplate readJdbcTemplate) {
        this.tenantId = tenantId;
        this.tableName = tableName;
        this.readJdbcTemplate = readJdbcTemplate;
        log.debug("Created READ DAO for table: {} with tenantId: {}", tableName, tenantId);
    }

    @HostAccess.Export
    public Object findById(Object id) {
        String sql = String.format("SELECT * FROM %s WHERE id = ?", fullyQualifiedTableName());
        log.debug("QUERY - findById from table: {}", fullyQualifiedTableName());
        List<Map<String, Object>> results = readJdbcTemplate.queryForList(sql, id);
        return results.isEmpty() ? null : results.get(0);
    }

    @HostAccess.Export
    public List<Map<String, Object>> findAll() {
        String sql = String.format("SELECT * FROM %s ORDER BY id", fullyQualifiedTableName());
        log.debug("QUERY - findAll from table: {}", fullyQualifiedTableName());
        return readJdbcTemplate.queryForList(sql);
    }

    @HostAccess.Export
    public List<Map<String, Object>> where(Map<String, Object> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return findAll();
        }

        log.debug("QUERY - where from table: {}", fullyQualifiedTableName());

        StringBuilder sql = new StringBuilder(String.format(
                "SELECT * FROM %s WHERE 1=1",
                fullyQualifiedTableName()
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
    }

    @HostAccess.Export
    public Object findOne(Map<String, Object> conditions) {
        List<Map<String, Object>> results = where(conditions);
        return results.isEmpty() ? null : results.get(0);
    }

    @HostAccess.Export
    public Long count() {
        String sql = String.format("SELECT COUNT(*) FROM %s", fullyQualifiedTableName());
        log.debug("QUERY - count from table: {}", fullyQualifiedTableName());
        return readJdbcTemplate.queryForObject(sql, Long.class);
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

    private String fullyQualifiedTableName() {
        return "tenant_" + tenantId + "." + tableName;
    }
}
