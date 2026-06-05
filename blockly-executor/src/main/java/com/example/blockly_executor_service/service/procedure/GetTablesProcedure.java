package com.example.blockly_executor_service.service.procedure;
import com.example.common.ProcedureExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("getTables")
@RequiredArgsConstructor
@Slf4j
public class GetTablesProcedure implements ProcedureExecutor<Map<String, Object>, Object> {

    private final JdbcTemplate readJdbcTemplate;

    @Override
    public Object execute(Map<String, Object> parameters) {
        String tenant = (String) parameters.get("tenant");
        if (tenant == null || tenant.isEmpty()) {
            throw new IllegalArgumentException("tenant parameter is required");
        }
        String schema = "tenant_" + tenant;
        log.info("Fetching tables for schema: {}", schema);
        return readJdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables " +
                        "WHERE table_schema = ? AND table_type = 'BASE TABLE' " +
                        "ORDER BY table_name",
                String.class, schema
        );
    }
}