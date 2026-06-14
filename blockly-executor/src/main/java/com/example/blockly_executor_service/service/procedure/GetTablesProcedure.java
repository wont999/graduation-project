package com.example.blockly_executor_service.service.procedure;
import com.example.common.ProcedureExecutor;
import com.example.common.model.ExecutionMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("getTables")
@RequiredArgsConstructor
@Slf4j
public class GetTablesProcedure implements ProcedureExecutor<Map<String, Object>, Object> {

    @Qualifier("readJdbcTemplate")
    private final JdbcTemplate readJdbcTemplate;

    @Override
    public Object execute(Map<String, Object> parameters) {
        ExecutionMetadata metadata = (ExecutionMetadata) parameters.get("__metadata");
        String tenant = metadata.tenantId();

        log.info("Getting tables for tenant: {}", tenant);

        return readJdbcTemplate.queryForList(
                "SELECT table_name, row_count, updated_at FROM table_catalog " +
                        "WHERE tenant_id = ? ORDER BY table_name", tenant);
    }
}