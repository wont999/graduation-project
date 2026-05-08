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
        return readDao.findById(id);
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
        return writeDao.create(data);
    }

    @HostAccess.Export
    public Object update(Object id, Map<String, Object> data) {
        return writeDao.update(id, data);
    }

    @HostAccess.Export
    public boolean delete(Object id) {
        return writeDao.delete(id);
    }
}
