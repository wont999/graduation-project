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
    public Object findAll() {
        return findAll(100);
    }

    @HostAccess.Export
    public Object findAll(int limit) {
        return getTimer("findAll").record(() -> readJdbcTemplate.queryForList(findAllSql, limit));
    }

    @HostAccess.Export
    public Object findRecent(int count) {
        return getTimer("findRecent").record(() -> readJdbcTemplate.queryForList(findRecentSql, count));
    }

    @HostAccess.Export
    public Object where(Map<String, Object> conditions) {
        return getTimer("where").record(() -> {
            if (conditions == null || conditions.isEmpty()) {
                return findAll();
            }

            StringBuilder sql = new StringBuilder(String.format(
                    "SELECT * FROM %s WHERE 1=1",
                    fullyQualifiedTableName
            ));

            List<Object> params = new ArrayList<>();

            int limit = 100;
            int offset = 0;

            if (conditions.containsKey("__limit")) {
                try {
                    limit = Math.min(Math.max(1, ((Number) conditions.get("__limit")).intValue()), 1000);
                } catch (Exception ignored) {}
                conditions.remove("__limit");
            }
            if (conditions.containsKey("__offset")) {
                try {
                    offset = Math.max(0, ((Number) conditions.get("__offset")).intValue());
                } catch (Exception ignored) {}
                conditions.remove("__offset");
            }

            buildWhereClause(conditions, sql, params);

            sql.append(" ORDER BY id LIMIT ").append(limit);
            if (offset > 0) {
                sql.append(" OFFSET ").append(offset);
            }
            return readJdbcTemplate.queryForList(sql.toString(), params.toArray());
        });
    }

    @SuppressWarnings("unchecked")
    private void buildWhereClause(Map<String, Object> conditions, StringBuilder sql, List<Object> params) {
        for (Map.Entry<String, Object> entry : conditions.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (key.equals("__and")) {
                List<Object> list = (List<Object>) value;
                for (Object item : list) {
                    if (item instanceof Map) {
                        sql.append(" AND (");
                        buildWhereClause((Map<String, Object>) item, sql, params);
                        sql.append(")");
                    }
                }
            } else if (key.equals("__or")) {
                List<Object> list = (List<Object>) value;
                boolean first = true;
                for (Object item : list) {
                    if (item instanceof Map) {
                        sql.append(first ? " AND (" : " OR ");
                        buildWhereClause((Map<String, Object>) item, sql, params);
                        first = false;
                    }
                }
                sql.append(")");
            } else if (key.equals("__not")) {
                if (value instanceof Map) {
                    sql.append(" AND NOT (");
                    buildWhereClause((Map<String, Object>) value, sql, params);
                    sql.append(")");
                }
            } else if (!VALID_COLUMN_NAME.matcher(key).matches()) {
                throw new SecurityException("Invalid column name: " + key);
            } else if (value instanceof Map) {
                applyOperator(sql, params, key, (Map<String, Object>) value);
            } else {
                sql.append(" AND ").append(key).append(" = ?");
                params.add(value);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void applyOperator(StringBuilder sql, List<Object> params, String column, Map<String, Object> op) {
        String operator = (String) op.get("op");
        if (operator == null) {
            sql.append(" AND ").append(column).append(" = ?");
            params.add(op.get("value"));
            return;
        }

        switch (operator) {
            case "=", "==":
                sql.append(" AND ").append(column).append(" = ?");
                params.add(op.get("value"));
                break;
            case ">":
                sql.append(" AND ").append(column).append(" > ?");
                params.add(op.get("value"));
                break;
            case "<":
                sql.append(" AND ").append(column).append(" < ?");
                params.add(op.get("value"));
                break;
            case ">=", "≥":
                sql.append(" AND ").append(column).append(" >= ?");
                params.add(op.get("value"));
                break;
            case "<=", "≤":
                sql.append(" AND ").append(column).append(" <= ?");
                params.add(op.get("value"));
                break;
            case "!=", "≠":
                sql.append(" AND ").append(column).append(" != ?");
                params.add(op.get("value"));
                break;
            case "like":
            case "ilike":
                sql.append(" AND ").append(column).append(" ILIKE ?");
                params.add(op.get("value"));
                break;
            case "~":
                sql.append(" AND ").append(column).append(" ~ ?");
                params.add(op.get("value"));
                break;
            case "~*":
                sql.append(" AND ").append(column).append(" ~* ?");
                params.add(op.get("value"));
                break;
            case "!~":
                sql.append(" AND ").append(column).append(" !~ ?");
                params.add(op.get("value"));
                break;
            case "!~*":
                sql.append(" AND ").append(column).append(" !~* ?");
                params.add(op.get("value"));
                break;
            case "between":
                sql.append(" AND ").append(column).append(" BETWEEN ? AND ?");
                params.add(op.get("from"));
                params.add(op.get("to"));
                break;
            case "is_null":
                sql.append(" AND ").append(column).append(" IS NULL");
                break;
            case "is_not_null":
                sql.append(" AND ").append(column).append(" IS NOT NULL");
                break;
            case "in":
                List<Object> inList = (List<Object>) op.get("value");
                if (inList != null && !inList.isEmpty()) {
                    sql.append(" AND ").append(column).append(" IN (");
                    for (int i = 0; i < inList.size(); i++) {
                        sql.append(i > 0 ? ", " : "").append("?");
                        params.add(inList.get(i));
                    }
                    sql.append(")");
                }
                break;
            case "not_in":
                List<Object> notInList = (List<Object>) op.get("value");
                if (notInList != null && !notInList.isEmpty()) {
                    sql.append(" AND ").append(column).append(" NOT IN (");
                    for (int i = 0; i < notInList.size(); i++) {
                        sql.append(i > 0 ? ", " : "").append("?");
                        params.add(notInList.get(i));
                    }
                    sql.append(")");
                }
                break;
            default:
                throw new SecurityException("Unsupported operator: " + operator);
        }
    }

    @HostAccess.Export
    public Object findOne(Map<String, Object> conditions) {
        return getTimer("findOne").record(() -> {
            if (conditions == null || conditions.isEmpty()) {
                List<Map<String, Object>> results = readJdbcTemplate.queryForList(findAllSql, 1);
                return results.isEmpty() ? null : results.get(0);
            }

            StringBuilder sql = new StringBuilder(String.format(
                    "SELECT * FROM %s WHERE 1=1",
                    fullyQualifiedTableName
            ));

            List<Object> params = new ArrayList<>();
            buildWhereClause(conditions, sql, params);
            sql.append(" ORDER BY id LIMIT 1");

            List<Map<String, Object>> results = readJdbcTemplate.queryForList(sql.toString(), params.toArray());
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
    public Object executeRawQuery(String sql, Object... params) {
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
