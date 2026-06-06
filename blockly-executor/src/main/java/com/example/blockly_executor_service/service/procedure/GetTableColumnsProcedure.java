package com.example.blockly_executor_service.service.procedure;
import com.example.common.ProcedureExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("getTableColumns")
@RequiredArgsConstructor
@Slf4j
public class GetTableColumnsProcedure implements ProcedureExecutor<Map<String, Object>, Object> {

    private final JdbcTemplate readJdbcTemplate;

    @Override
    public Object execute(Map<String, Object> parameters) {
        String tenant = (String) parameters.get("tenant");
        String table = (String) parameters.get("table");
        String schema = "tenant_" + tenant;

        return readJdbcTemplate.queryForList(
                "SELECT column_name, data_type FROM information_schema.columns " +
                        "WHERE table_schema = ? AND table_name = ? " +
                        "ORDER BY ordinal_position",
                schema, table
        );
    }
}