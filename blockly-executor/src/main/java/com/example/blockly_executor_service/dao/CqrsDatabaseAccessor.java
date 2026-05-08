package com.example.blockly_executor_service.dao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.HostAccess;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RequiredArgsConstructor
public class CqrsDatabaseAccessor {

    private final String tenantId;
    private final JdbcTemplate readJdbcTemplate;
    private final JdbcTemplate writeJdbcTemplate;
    private final ConcurrentHashMap<String, CqrsTenantAwareDao> daoCache = new ConcurrentHashMap<>();

    @HostAccess.Export
    public CqrsTenantAwareDao table(String tableName) {
        return daoCache.computeIfAbsent(tableName, name -> {
            log.debug("Creating CQRS DAO for table: {} (tenant: {})", name, tenantId);
            return new CqrsTenantAwareDao(tenantId, name, readJdbcTemplate, writeJdbcTemplate);
        });
    }

    @HostAccess.Export
    public Object query(String sql, Object... params) {
        return readJdbcTemplate.queryForList(sql, params);
    }
}
