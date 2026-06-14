package com.example.blockly_executor_service.service.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class TableCatalogProjectionHandler {

    @Qualifier("writeJdbcTemplate")
    private final JdbcTemplate writeJdbcTemplate;
    private final ObjectMapper objectMapper;

    private static final Pattern VALID = Pattern.compile("^[a-zA-Z0-9_]+$");

    @KafkaListener(
            topics = "domain-events",
            groupId = "projection-table-catalog",
            containerFactory = "domainEventKafkaListenerContainerFactory")

    public void onEvent(String json, Acknowledgment ack) {
        try {
            JsonNode e = objectMapper.readTree(json);
            //При событии TABLE_CHANGED - пересчитывает count целевой таблицы и upsert'ит в table_catalog
            if ("TABLE_CHANGED".equals(e.path("eventType").asText())) {
                String tenantId = e.path("tenantId").asText();
                String table = e.path("payload").path("table").asText();

                if (!VALID.matcher(tenantId).matches() || !VALID.matcher(table).matches()) {
                    log.warn("Skipping event with invalid identifiers: {}", json);
                    ack.acknowledge();
                    return;
                }

                Long rowCount = writeJdbcTemplate.queryForObject(
                        "SELECT count(*) FROM tenant_" + tenantId + "." + table, Long.class);

                // идемпотентный upsert -> повтор события безопасен
                writeJdbcTemplate.update(
                        "INSERT INTO table_catalog(tenant_id, table_name, row_count, updated_at) " +
                                "VALUES (?, ?, ?, now()) " +
                                "ON CONFLICT (tenant_id, table_name) DO UPDATE SET " +
                                "row_count = EXCLUDED.row_count, updated_at = now()",
                        tenantId, table, rowCount);
            }
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Projection failed for event {}: {}", json, ex.getMessage(), ex);
            ack.acknowledge(); // не зацикливаем
        }
    }

    @PostConstruct
    public void initCatalog() {
        List<Map<String, Object>> tenants = writeJdbcTemplate.queryForList(
                "SELECT DISTINCT schemaname FROM pg_tables WHERE schemaname LIKE 'tenant_%'");

        for (Map<String, Object> t : tenants) {
            String tenantId = ((String) t.get("schemaname")).replace("tenant_", "");
            List<Map<String, Object>> tables = writeJdbcTemplate.queryForList(
                    "SELECT tablename FROM pg_tables WHERE schemaname = ?",
                    "tenant_" + tenantId);

            for (Map<String, Object> tbl : tables) {
                String tableName = (String) tbl.get("tablename");
                Long rowCount = writeJdbcTemplate.queryForObject(
                        "SELECT count(*) FROM tenant_" + tenantId + "." + tableName, Long.class);

                writeJdbcTemplate.update(
                        "INSERT INTO table_catalog(tenant_id, table_name, row_count, updated_at) " +
                                "VALUES (?, ?, ?, now()) " +
                                "ON CONFLICT (tenant_id, table_name) DO UPDATE SET " +
                                "row_count = EXCLUDED.row_count, updated_at = now()",
                        tenantId, tableName, rowCount);
            }
        }
        log.info("Table catalog initialized");
    }
}
