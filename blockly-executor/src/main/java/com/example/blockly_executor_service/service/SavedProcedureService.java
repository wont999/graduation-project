package com.example.blockly_executor_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SavedProcedureService {

    @Qualifier("writeJdbcTemplate")
    private final JdbcTemplate writeJdbcTemplate;

    @Qualifier("readJdbcTemplate")
    private final JdbcTemplate readJdbcTemplate;

    private final RowMapper<Map<String, Object>> rowMapper = (rs, rowNum) -> Map.of(
            "id", rs.getLong("id"),
            "tenantId", rs.getString("tenant_id"),
            "name", rs.getString("name"),
            "description", rs.getString("description"),
            "blocklyXml", rs.getString("blockly_xml"),
            "generatedJs", rs.getString("generated_js"),
            "createdAt", rs.getTimestamp("created_at").toLocalDateTime().toString(),
            "updatedAt", rs.getTimestamp("updated_at").toLocalDateTime().toString()
    );

    public List<Map<String, Object>> listByTenant(String tenantId) {
        return readJdbcTemplate.query(
                "SELECT id, tenant_id, name, description, created_at, updated_at " +
                        "FROM saved_procedures WHERE tenant_id = ? ORDER BY updated_at DESC",
                rowMapper, tenantId
        );
    }

    public Map<String, Object> getByName(String name, String tenantId) {
        List<Map<String, Object>> results = readJdbcTemplate.query(
                "SELECT * FROM saved_procedures WHERE name = ? AND tenant_id = ?",
                rowMapper, name, tenantId
        );
        return results.isEmpty() ? null : results.get(0);
    }

    public Map<String, Object> save(String tenantId, String name, String description,
                                    String blocklyXml, String generatedJs) {
        writeJdbcTemplate.update(
                "INSERT INTO saved_procedures (tenant_id, name, description, blockly_xml, generated_js) " +
                        "VALUES (?, ?, ?, ?, ?) " +
                        "ON CONFLICT (tenant_id, name) DO UPDATE SET " +
                        "description = EXCLUDED.description, blockly_xml = EXCLUDED.blockly_xml, " +
                        "generated_js = EXCLUDED.generated_js, updated_at = NOW()",
                tenantId, name, description, blocklyXml, generatedJs
        );
        return getByName(name, tenantId);
    }

    public boolean delete(String name, String tenantId) {
        int rows = writeJdbcTemplate.update(
                "DELETE FROM saved_procedures WHERE name = ? AND tenant_id = ?",
                name, tenantId
        );
        return rows > 0;
    }
}