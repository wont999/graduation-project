package com.example.blockly_executor_service.dao;

import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.HostAccess;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
public class CqrsTenantAwareDao {

    private final CqrsTenantAwareReadDao readDao;
    private final CqrsTenantAwareWriteDao writeDao;

    public CqrsTenantAwareDao(String tenantId, String tableName,
                              JdbcTemplate readJdbcTemplate,
                              JdbcTemplate writeJdbcTemplate) {
        this.readDao = new CqrsTenantAwareReadDao(tenantId, tableName, readJdbcTemplate);
        this.writeDao = new CqrsTenantAwareWriteDao(tenantId, tableName, writeJdbcTemplate);
        log.debug("Created CQRS DAO for table: {} (tenant: {})", tableName, tenantId);
    }

    @HostAccess.Export
    public Object findById(Object id) {
        return readDao.findById(convertId(id));
    }

    @HostAccess.Export
    public List<Map<String, Object>> findAll() {
        return readDao.findAll();
    }

    @HostAccess.Export
    public List<Map<String, Object>> where(Map<String, Object> conditions) {
        return readDao.where(conditions);
    }

    @HostAccess.Export
    public Object findOne(Map<String, Object> conditions) {
        return readDao.findOne(conditions);
    }

    @HostAccess.Export
    public Long count() {
        return readDao.count();
    }

    @HostAccess.Export
    public Object create(Map<String, Object> data) {
        return writeDao.create(convertMap(data));
    }

    @HostAccess.Export
    public Object update(Object id, Map<String, Object> data) {
        return writeDao.update(convertId(id), convertMap(data));
    }

    @HostAccess.Export
    public boolean delete(Object id) {
        return writeDao.delete(convertId(id));
    }

    private Object convertId(Object id) {
        if (id instanceof org.graalvm.polyglot.Value) {
            org.graalvm.polyglot.Value v = (org.graalvm.polyglot.Value) id;
            if (v.fitsInLong()) return v.asLong();
            if (v.fitsInInt()) return v.asInt();
            if (v.isString()) return v.asString();
        }
        return id;
    }
    private Map<String, Object> convertMap(Map<String, Object> data) {
        if (data == null) return null;
        java.util.HashMap<String, Object> result = new java.util.HashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            result.put(entry.getKey(), convertValue(entry.getValue()));
        }
        return result;
    }
    private Object convertValue(Object value) {
        if (value instanceof org.graalvm.polyglot.Value) {
            org.graalvm.polyglot.Value v = (org.graalvm.polyglot.Value) value;
            if (v.isNull()) return null;
            if (v.isBoolean()) return v.asBoolean();
            if (v.isString()) return v.asString();
            if (v.isNumber()) {
                if (v.fitsInInt()) return v.asInt();
                if (v.fitsInLong()) return v.asLong();
                if (v.fitsInDouble()) return v.asDouble();
                return v.asDouble();
            }
        }
        return value;
    }
}
