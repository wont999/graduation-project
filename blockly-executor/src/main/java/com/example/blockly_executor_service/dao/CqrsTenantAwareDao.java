package com.example.blockly_executor_service.dao;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.HostAccess;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
public class CqrsTenantAwareDao {

    private final String fullyQualifiedTableName;
    private volatile CqrsTenantAwareReadDao readDao;
    private volatile CqrsTenantAwareWriteDao writeDao;

    private final String tenantId;
    private final String tableName;
    private final JdbcTemplate readJdbcTemplate;
    private final JdbcTemplate writeJdbcTemplate;
    private final MeterRegistry meterRegistry;

    public CqrsTenantAwareDao(String tenantId, String tableName,
                              JdbcTemplate readJdbcTemplate,
                              JdbcTemplate writeJdbcTemplate,
                              MeterRegistry meterRegistry) {
        this.tenantId = tenantId;
        this.tableName = tableName;
        this.readJdbcTemplate = readJdbcTemplate;
        this.writeJdbcTemplate = writeJdbcTemplate;
        this.meterRegistry = meterRegistry;
        this.fullyQualifiedTableName = "tenant_" + tenantId + "." + tableName;
        log.debug("Created CQRS DAO for table: {} (tenant: {})", tableName, tenantId);
    }

    private CqrsTenantAwareReadDao getReadDao() {
        if (readDao == null) {
            readDao = new CqrsTenantAwareReadDao(tenantId, tableName, fullyQualifiedTableName, readJdbcTemplate, meterRegistry);
        }
        return readDao;
    }

    private CqrsTenantAwareWriteDao getWriteDao() {
        if (writeDao == null) {
            writeDao = new CqrsTenantAwareWriteDao(tenantId, tableName, fullyQualifiedTableName, writeJdbcTemplate, meterRegistry);
        }
        return writeDao;
    }

    @HostAccess.Export
    public Object findById(Object id) {
        return getReadDao().findById(id);
    }

    @HostAccess.Export
    public List<Map<String, Object>> findAll() {
        return getReadDao().findAll();
    }

    @HostAccess.Export
    public List<Map<String, Object>> findAll(int limit) {
        return getReadDao().findAll(limit);
    }

    @HostAccess.Export
    public List<Map<String, Object>> findRecent(int count) {
        return getReadDao().findRecent(count);
    }

    @HostAccess.Export
    public List<Map<String, Object>> where(Map<String, Object> conditions) {
        return getReadDao().where(conditions);
    }

    @HostAccess.Export
    public Object findOne(Map<String, Object> conditions) {
        return getReadDao().findOne(conditions);
    }

    @HostAccess.Export
    public Long count() {
        return getReadDao().count();
    }

    @HostAccess.Export
    public Object create(Map<String, Object> data) {
        return getWriteDao().create(data);
    }

    @HostAccess.Export
    public Object update(Object id, Map<String, Object> data) {
        return getWriteDao().update(id, data);
    }

    @HostAccess.Export
    public boolean delete(Object id) {
        return getWriteDao().delete(id);
    }
}
