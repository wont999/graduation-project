package com.example.blockly_executor_service.service.execution;

import com.example.blockly_executor_service.dao.JSResult;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.springframework.jdbc.core.JdbcTemplate;

public class ProcedureExecutorHelper {

    private final Context context;
    private final JdbcTemplate readTemplate;
    private final String tenantId;

    public ProcedureExecutorHelper(Context context, JdbcTemplate readTemplate, String tenantId) {
        this.context = context;
        this.readTemplate = readTemplate;
        this.tenantId = tenantId;
    }

    @HostAccess.Export
    public Object execute(String procedureName) {
        String sql = "SELECT generated_js FROM saved_procedures WHERE name = ? AND tenant_id = ?";
        String generatedJs = readTemplate.queryForObject(sql, String.class, procedureName, tenantId);
        if (generatedJs == null || generatedJs.isEmpty()) {
            throw new RuntimeException("Procedure not found: " + procedureName);
        }
        return context.eval("js", generatedJs);
    }
}
