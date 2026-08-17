package io.llmplatform.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 按 PRAGMA user_version 顺序升级 SQLite，避免一次性 schema.sql 无法给已有库加列。 */
public final class SchemaMigrator {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrator.class);
    private static final int TARGET_VERSION = 9;

    private SchemaMigrator() {}

    /** 从当前 user_version 逐级执行迁移。每一级完成后才提升版本号，使中途失败的数据库能在下次启动时重试。 */
    public static void migrate(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            // SQLite 默认自动提交；每级脚本跑完立刻写 user_version，中途失败不会把版本提前抬高。
            int version = userVersion(statement);
            if (version < 1) {
                executeScript(statement, "db/migration/V1__baseline.sql");
                setUserVersion(statement, 1);
                version = 1;
            }
            if (version < 2) {
                addColumnIfMissing(
                        statement, "sessions", "next_sequence", "INTEGER NOT NULL DEFAULT 1");
                addColumnIfMissing(
                        statement, "messages", "sequence_no", "INTEGER NOT NULL DEFAULT 0");
                addColumnIfMissing(
                        statement, "messages", "token_count", "INTEGER NOT NULL DEFAULT 0");
                backfillMessageSequences(statement);
                executeScript(statement, "db/migration/V2__model_config_and_session_context.sql");
                statement.execute(
                        "CREATE INDEX IF NOT EXISTS idx_messages_session_sequence"
                                + " ON messages (session_id, sequence_no)");
                seedModelConfigsFromLegacySettings(statement);
                setUserVersion(statement, 2);
                version = 2;
            }
            if (version < 3) {
                executeScript(statement, "db/migration/V3__model_config_profiles.sql");
                setUserVersion(statement, 3);
                version = 3;
            }
            if (version < 4) {
                executeScript(statement, "db/migration/V4__reset_hardware_device.sql");
                setUserVersion(statement, 4);
                version = 4;
            }
            if (version < 5) {
                executeScript(statement, "db/migration/V5__automatic_gpu_layers.sql");
                setUserVersion(statement, 5);
                version = 5;
            }
            if (version < 6) {
                executeScript(statement, "db/migration/V6__uncommon_llamafile_port.sql");
                setUserVersion(statement, 6);
                version = 6;
            }
            if (version < 7) {
                executeScript(statement, "db/migration/V7__remote_models.sql");
                setUserVersion(statement, 7);
                version = 7;
            }
            if (version < 8) {
                addColumnIfMissing(
                        statement, "rag_chunks", "ordinal", "INTEGER NOT NULL DEFAULT 0");
                addColumnIfMissing(
                        statement, "rag_chunks", "heading_path", "TEXT NOT NULL DEFAULT ''");
                addColumnIfMissing(statement, "rag_chunks", "parent_id", "TEXT");
                addColumnIfMissing(
                        statement, "rag_chunks", "char_start", "INTEGER NOT NULL DEFAULT 0");
                addColumnIfMissing(
                        statement, "rag_chunks", "char_end", "INTEGER NOT NULL DEFAULT 0");
                addColumnIfMissing(
                        statement, "rag_chunks", "role", "TEXT NOT NULL DEFAULT 'child'");
                executeScript(statement, "db/migration/V8__rag_smart_index.sql");
                setUserVersion(statement, 8);
                version = 8;
            }
            if (version < 9) {
                executeScript(statement, "db/migration/V9__rag_graph.sql");
                setUserVersion(statement, 9);
                version = 9;
            }
            if (version != TARGET_VERSION) {
                log.warn("数据库版本 {} 高于当前代码目标 {}", version, TARGET_VERSION);
            }
        } catch (SQLException | IOException ex) {
            throw new IllegalStateException("无法升级本地 SQLite 结构", ex);
        }
    }

    private static int userVersion(Statement statement) throws SQLException {
        try (ResultSet rs = statement.executeQuery("PRAGMA user_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static void setUserVersion(Statement statement, int version) throws SQLException {
        statement.execute("PRAGMA user_version = " + version);
    }

    private static void executeScript(Statement statement, String classpath)
            throws IOException, SQLException {
        String sql = readClasspath(classpath);
        for (String part : splitStatements(sql)) {
            statement.execute(part);
        }
    }

    private static String readClasspath(String classpath) throws IOException {
        try (InputStream input =
                SchemaMigrator.class.getClassLoader().getResourceAsStream(classpath)) {
            if (input == null) {
                throw new IOException("找不到迁移脚本 " + classpath);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> splitStatements(String sql) {
        // 项目迁移脚本不含触发器或字符串内分号，按行末分号切分可保持实现轻量且可审计。
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : sql.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                continue;
            }
            current.append(trimmed).append('\n');
            if (trimmed.endsWith(";")) {
                statements.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) {
            statements.add(current.toString());
        }
        return statements;
    }

    private static void addColumnIfMissing(
            Statement statement, String table, String column, String definition)
            throws SQLException {
        if (hasColumn(statement, table, column)) {
            return;
        }
        statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }

    private static boolean hasColumn(Statement statement, String table, String column)
            throws SQLException {
        try (ResultSet rs = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void backfillMessageSequences(Statement statement) throws SQLException {
        // created_at 可能同毫秒重复，因此用 rowid 作为旧库一次性回填时的稳定次序兜底。
        statement.execute(
                """
                UPDATE messages
                SET sequence_no = (
                    SELECT COUNT(*)
                    FROM messages AS older
                    WHERE older.session_id = messages.session_id
                      AND (
                          older.created_at < messages.created_at
                          OR (older.created_at = messages.created_at AND older.rowid <= messages.rowid)
                      )
                )
                WHERE sequence_no = 0
                """);
        statement.execute(
                """
                UPDATE sessions
                SET next_sequence = (
                    SELECT COALESCE(MAX(sequence_no), 0) + 1
                    FROM messages
                    WHERE messages.session_id = sessions.id
                )
                """);
    }

    /** 旧版全局模型参数曾写在 settings JSON 里；升级后复制到当时已有的每个模型配置。 */
    private static void seedModelConfigsFromLegacySettings(Statement statement)
            throws SQLException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode settings = readSettingsJson(statement, mapper);
        List<String> modelIds = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery("SELECT id FROM models")) {
            while (rs.next()) {
                modelIds.add(rs.getString(1));
            }
        }
        long now = System.currentTimeMillis();
        for (String modelId : modelIds) {
            if (modelConfigExists(statement, modelId)) {
                continue;
            }
            insertDefaultModelConfig(statement, modelId, settings, now);
        }
    }

    private static JsonNode readSettingsJson(Statement statement, ObjectMapper mapper)
            throws SQLException {
        try (ResultSet rs =
                statement.executeQuery("SELECT value FROM settings WHERE key = 'app.settings'")) {
            if (!rs.next()) {
                return mapper.createObjectNode();
            }
            String json = rs.getString(1);
            if (json == null || json.isBlank()) {
                return mapper.createObjectNode();
            }
            try {
                return mapper.readTree(json);
            } catch (IOException ex) {
                // 旧设置损坏时仍应完成结构升级，模型参数使用默认模板。
                return mapper.createObjectNode();
            }
        }
    }

    private static boolean modelConfigExists(Statement statement, String modelId)
            throws SQLException {
        try (ResultSet rs =
                statement.executeQuery(
                        "SELECT 1 FROM model_configs WHERE model_id = '"
                                + modelId.replace("'", "''")
                                + "'")) {
            return rs.next();
        }
    }

    private static void insertDefaultModelConfig(
            Statement statement, String modelId, JsonNode settings, long now) throws SQLException {
        // 只迁移旧版确实存在的公共字段，其余新增参数使用与新导入模型一致的模板值。
        int contextSize = intOr(settings, "contextSize", 4096);
        int threads = intOr(settings, "threads", 4);
        int gpuLayers = intOr(settings, "gpuLayers", 0);
        double temperature = doubleOr(settings, "temperature", 0.7);
        double topP = doubleOr(settings, "topP", 0.9);
        int maxTokens = intOr(settings, "maxTokens", 1024);
        String escapedId = modelId.replace("'", "''");
        statement.execute(
                "INSERT INTO model_configs("
                        + "model_id, context_size, threads, gpu_layers, batch_size, ubatch_size,"
                        + " flash_attention, memory_map, memory_lock, temperature, top_p, top_k, min_p,"
                        + " max_tokens, repeat_penalty, repeat_last_n, seed, frequency_penalty,"
                        + " presence_penalty, stop_json, compression_enabled, compression_trigger_ratio,"
                        + " keep_recent_messages, summary_max_tokens, advanced_inference_params, updated_at)"
                        + " VALUES('"
                        + escapedId
                        + "', "
                        + contextSize
                        + ", "
                        + threads
                        + ", "
                        + gpuLayers
                        + ", 512, 512, 0, 1, 0, "
                        + temperature
                        + ", "
                        + topP
                        + ", 40, 0.05, "
                        + maxTokens
                        + ", 1.1, 64, NULL, 0, 0, '[]', 1, 0.75, 8, 256, '{}', "
                        + now
                        + ")");
    }

    private static int intOr(JsonNode node, String field, int fallback) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.intValue() : fallback;
    }

    private static double doubleOr(JsonNode node, String field, double fallback) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.doubleValue() : fallback;
    }
}
