package com.gov.procurement.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * U1 数据库基线 · 迁移集成测试（详细设计 §7 T-1..T-9 / AC-1..AC-4 / Q1..Q6）。
 *
 * <p>用 Testcontainers 起一次性 PostgreSQL（镜像与 docker-compose 对齐 = postgres:16），
 * 用 Flyway API 执行 {@code classpath:db/migration} 下的 V1/V2，对真实 PG 行为做断言：
 * 部分唯一索引、GIN trigram、updated_at 触发器、pg_trgm 扩展均为 PG 特性，H2 不可替代。
 *
 * <p>无 Docker 的本地环境通过 {@link #dockerAvailable()} 整类跳过（而非 {@code @Disabled} 永久关闭），
 * CI（有 Docker）则正常执行，可重复绿。
 */
@Testcontainers
@EnabledIf("dockerAvailable")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MigrationBaselineTest {

    /** 详细设计 §1：23 张业务表（不含 flyway_schema_history 与 Flowable ACT_*）。 */
    private static final Set<String> EXPECTED_TABLES = Set.of(
            "department", "role", "project_group", "sys_user", "user_role",
            "budget_subject", "budget", "budget_item",
            "approval", "approval_record",
            "stock_item", "stock_txn",
            "purchase_order", "purchase_item", "delivery_note",
            "inbound_order", "inbound_item",
            "stocktake", "stocktake_item",
            "requisition", "requisition_item",
            "outbound_order", "outbound_item");

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("procurement")
            .withUsername("procurement")
            .withPassword("procurement");

    static Flyway flyway;

    /** 类级执行条件：Docker 可用才跑（@EnabledIf 在容器启动前评估，缺 Docker 则整类跳过）。 */
    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @BeforeAll
    static void migrate() {
        flyway = Flyway.configure()
                .dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();
    }

    private static Connection conn() throws SQLException {
        return DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
    }

    /** T-1：全新空库迁移成功，23 张业务表全部就位（AC-1 / Q1）。 */
    @Test
    @Order(1)
    void t1_allBusinessTablesCreated() throws SQLException {
        Set<String> actual = new HashSet<>();
        try (Connection c = conn(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")) {
            while (rs.next()) {
                actual.add(rs.getString(1));
            }
        }
        assertTrue(actual.containsAll(EXPECTED_TABLES),
                () -> "缺失业务表：" + diff(EXPECTED_TABLES, actual));
    }

    /** T-3：pg_trgm 扩展已安装（Q4）。 */
    @Test
    @Order(2)
    void t3_pgTrgmExtensionInstalled() throws SQLException {
        try (Connection c = conn(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm'")) {
            assertTrue(rs.next(), "pg_trgm 扩展未安装");
        }
    }

    /** T-2：部分唯一索引带 WHERE is_deleted=0；trigram 索引为 GIN（AC-1 / Q2）。 */
    @Test
    @Order(3)
    void t2_indexesInPlace() throws SQLException {
        String ukDept = indexDef("uk_department_code");
        assertTrue(ukDept.toLowerCase().contains("unique"), "uk_department_code 应为唯一索引");
        assertTrue(ukDept.contains("is_deleted = 0"),
                "uk_department_code 应为部分唯一索引（WHERE is_deleted = 0）");

        String trgm = indexDef("idx_subject_name_trgm");
        assertTrue(trgm.toLowerCase().contains("gin"), "idx_subject_name_trgm 应为 GIN 索引");
    }

    /** T-6：role 表恰好 6 个内置角色，code 集合匹配（AC-1 / Q5）。 */
    @Test
    @Order(4)
    void t6_sixSeedRoles() throws SQLException {
        Set<String> codes = new TreeSet<>();
        try (Connection c = conn(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT code FROM role")) {
            while (rs.next()) {
                codes.add(rs.getString(1));
            }
        }
        assertEquals(
                new TreeSet<>(Set.of("editor", "purchase_mgr", "dept_mgr", "warehouse", "requester", "admin")),
                codes,
                "内置角色集合不匹配");
    }

    /** T-9：flyway_schema_history 中 V1、V2 均 success，安装顺序正确（AC-3 / Q6）。 */
    @Test
    @Order(5)
    void t9_historyRecorded() throws SQLException {
        try (Connection c = conn(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT version, success FROM flyway_schema_history "
                             + "WHERE version IN ('1','2') ORDER BY installed_rank")) {
            assertTrue(rs.next(), "缺少 V1 历史记录");
            assertEquals("1", rs.getString("version"));
            assertTrue(rs.getBoolean("success"), "V1 应为 success");
            assertTrue(rs.next(), "缺少 V2 历史记录");
            assertEquals("2", rs.getString("version"));
            assertTrue(rs.getBoolean("success"), "V2 应为 success");
            assertFalse(rs.next(), "V1/V2 之外不应有多余记录");
        }
    }

    /** T-4：updated_at 触发器生效——手工写入旧值会被触发器覆盖为 now()（Q3）。 */
    @Test
    @Order(6)
    void t4_updatedAtTriggerFires() throws SQLException {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.executeUpdate("INSERT INTO department(name, code) VALUES ('触发器测试', 'TRG-T4')");
            // 强制把 updated_at 写成 2000 年；BEFORE UPDATE 触发器应覆盖为 now()
            s.executeUpdate("UPDATE department SET updated_at = TIMESTAMPTZ '2000-01-01 00:00:00+00' "
                    + "WHERE code = 'TRG-T4'");
            try (ResultSet rs = s.executeQuery(
                    "SELECT EXTRACT(YEAR FROM updated_at)::int FROM department WHERE code = 'TRG-T4'")) {
                assertTrue(rs.next());
                assertNotEquals(2000, rs.getInt(1), "updated_at 未被触发器刷新为 now()");
            }
        }
    }

    /** T-5：部分唯一索引语义——未删时 code 冲突；软删后同 code 可复用（§5.2）。 */
    @Test
    @Order(7)
    void t5_partialUniqueSemantics() throws SQLException {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.executeUpdate("INSERT INTO department(name, code) VALUES ('单位A', 'DUP-T5')");

            // 同 code 在 is_deleted=0 时冲突
            assertThrows(SQLException.class, () ->
                    s.executeUpdate("INSERT INTO department(name, code) VALUES ('单位B', 'DUP-T5')"));

            // 软删后同 code 可被新记录复用
            s.executeUpdate("UPDATE department SET is_deleted = 1 WHERE code = 'DUP-T5'");
            int inserted = s.executeUpdate("INSERT INTO department(name, code) VALUES ('单位C', 'DUP-T5')");
            assertEquals(1, inserted, "软删后应可复用同一 code");
        }
    }

    /** T-7：重复迁移幂等——再次 migrate() 不执行任何版本、不报错（AC-2 / INV-1）。 */
    @Test
    @Order(8)
    void t7_migrationIsIdempotent() {
        MigrateResult result = flyway.migrate();
        assertEquals(0, result.migrationsExecuted, "已应用版本不应被重复执行");
    }

    /**
     * T-8：校验和 fail-fast——篡改已应用脚本的校验和后 validate() 快速失败（AC-4 / INV-2）。
     * 放在最后执行：篡改 flyway_schema_history 后不影响其它用例。
     */
    @Test
    @Order(9)
    void t8_checksumMismatchFailsFast() throws SQLException {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.executeUpdate("UPDATE flyway_schema_history SET checksum = checksum + 1 WHERE version = '1'");
        }
        assertThrows(FlywayException.class, () -> flyway.validate(),
                "校验和不符应触发 fail-fast");
    }

    // ---- helpers ----

    private static String indexDef(String indexName) throws SQLException {
        try (Connection c = conn(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT indexdef FROM pg_indexes WHERE indexname = '" + indexName + "'")) {
            assertTrue(rs.next(), "索引不存在：" + indexName);
            return rs.getString(1);
        }
    }

    private static Set<String> diff(Set<String> expected, Set<String> actual) {
        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(actual);
        return missing;
    }
}
