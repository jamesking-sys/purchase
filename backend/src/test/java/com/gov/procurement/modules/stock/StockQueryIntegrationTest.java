package com.gov.procurement.modules.stock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * U10 库存查询 + 库存流水集成测试（详设 §7 T-1..T-10；T-11 性能不在集成测试覆盖）。
 * 全上下文 + 本地 PostgreSQL（无库优雅跳过）。测试数据用前缀 U10T-/u10t_ 隔离，{@code @BeforeAll}/{@code @AfterAll} 物理清理。
 *
 * <p>错误码归一：参数非法 40001；非 warehouse/admin → 40301；库存项不存在 → 40401。
 * U10 全只读：每个用例查询前后表数据不变（AC-9 / T-10 单列校验）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIf("com.gov.procurement.support.LocalPg#available")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StockQueryIntegrationTest {

    private static final String AUTH = "Authorization";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    BCryptPasswordEncoder passwordEncoder;
    @Autowired
    ObjectMapper objectMapper;

    private String warehouseToken;
    private String adminToken;
    private String editorToken;
    private long sysDept;

    @BeforeAll
    void setup() throws Exception {
        cleanup();
        adminToken = login("admin", "admin123");
        sysDept = jdbcTemplate.queryForObject(
                "SELECT id FROM department WHERE code = 'SYS' AND is_deleted = 0 ORDER BY id LIMIT 1", Long.class);
        warehouseToken = createUserWithRole("u10t_wh", "warehouse", sysDept);
        editorToken = createUserWithRole("u10t_ed", "editor", sysDept);
    }

    @AfterAll
    void teardown() {
        cleanup();
    }

    // ---- T-1 不带过滤分页（按本测试自有项目组隔离，避免跨用例污染 total）----

    @Test
    void t1_pagination() throws Exception {
        long dept = newDept("U10T-D1");
        long pg = newPg("U10T-PG1", dept);
        insertStock("U10T-A-apple", pg, dept, "1");
        insertStock("U10T-A-banana", pg, dept, "2");
        insertStock("U10T-A-cherry", pg, dept, "3");

        // 第 1 页 size=2：material_name 升序 → apple, banana
        mockMvc.perform(get("/api/stocks").param("projectGroupId", String.valueOf(pg))
                        .param("page", "1").param("size", "2").header(AUTH, bearer(warehouseToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records.length()").value(2))
                .andExpect(jsonPath("$.data.records[0].materialName").value("U10T-A-apple"))
                .andExpect(jsonPath("$.data.records[1].materialName").value("U10T-A-banana"));

        // 第 2 页：cherry，与第 1 页不重叠
        mockMvc.perform(get("/api/stocks").param("projectGroupId", String.valueOf(pg))
                        .param("page", "2").param("size", "2").header(AUTH, bearer(warehouseToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].materialName").value("U10T-A-cherry"))
                .andExpect(jsonPath("$.data.records[0].quantity").value(3));
    }

    // ---- T-2 按项目组过滤 ----

    @Test
    void t2_filterByProjectGroup() throws Exception {
        long dept = newDept("U10T-D2");
        long pgA = newPg("U10T-PG2A", dept);
        long pgB = newPg("U10T-PG2B", dept);
        insertStock("U10T-2-x", pgA, dept, "1");
        insertStock("U10T-2-y", pgA, dept, "1");
        insertStock("U10T-2-z", pgB, dept, "1");

        MvcResult res = mockMvc.perform(get("/api/stocks").param("projectGroupId", String.valueOf(pgA))
                        .header(AUTH, bearer(warehouseToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andReturn();
        JsonNode records = node(res).path("data").path("records");
        for (JsonNode r : records) {
            assertEquals(pgA, r.path("projectGroupId").asLong(), "仅返回 pgA 的库存项");
        }
    }

    // ---- T-2b 部门 + 物料名组合过滤（交集，ILIKE 大小写不敏感）----

    @Test
    void t2b_filterByDeptAndMaterial() throws Exception {
        long deptX = newDept("U10T-D2bX");
        long deptY = newDept("U10T-D2bY");
        long pgX = newPg("U10T-PG2bX", deptX);
        long pgY = newPg("U10T-PG2bY", deptY);
        insertStock("U10T-2b-Foo", pgX, deptX, "1");
        insertStock("U10T-2b-Bar", pgX, deptX, "1");
        insertStock("U10T-2b-Foo", pgY, deptY, "1");

        // departmentId=deptX 且 materialName=foo（小写，验证 ILIKE）→ 仅 pgX 的 Foo（交集）
        mockMvc.perform(get("/api/stocks")
                        .param("departmentId", String.valueOf(deptX))
                        .param("materialName", "u10t-2b-foo")
                        .header(AUTH, bearer(warehouseToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].materialName").value("U10T-2b-Foo"))
                .andExpect(jsonPath("$.data.records[0].departmentId").value(deptX));
    }

    // ---- T-3 流水时间倒序 ----

    @Test
    void t3_txnDescending() throws Exception {
        long dept = newDept("U10T-D3");
        long pg = newPg("U10T-PG3", dept);
        long stock = insertStock("U10T-3-m", pg, dept, "4");
        insertTxn(stock, "inbound", "5", "inbound_order", 100, "2026-06-01 10:00:00+00");
        insertTxn(stock, "outbound", "-2", "outbound_order", 200, "2026-06-02 10:00:00+00");
        insertTxn(stock, "inbound", "1", "inbound_order", 101, "2026-06-03 10:00:00+00");

        mockMvc.perform(get("/api/stocks/" + stock + "/txns").header(AUTH, bearer(warehouseToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.total").value(3))
                // 最新（6-03）在前
                .andExpect(jsonPath("$.data.page.records[0].type").value("inbound"))
                .andExpect(jsonPath("$.data.page.records[0].qtyChange").value(1))
                .andExpect(jsonPath("$.data.page.records[0].refType").value("inbound_order"))
                .andExpect(jsonPath("$.data.page.records[0].refId").value(101))
                .andExpect(jsonPath("$.data.page.records[1].refId").value(200))
                .andExpect(jsonPath("$.data.page.records[2].qtyChange").value(5));
    }

    // ---- T-4 流水深翻页稳定（同 created_at 用 id DESC tie-breaker，不重不漏）----

    @Test
    void t4_txnDeepPagingStable() throws Exception {
        long dept = newDept("U10T-D4");
        long pg = newPg("U10T-PG4", dept);
        long stock = insertStock("U10T-4-m", pg, dept, "5");
        Set<Long> inserted = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            inserted.add(insertTxn(stock, "inbound", "1", "inbound_order", 300 + i, "2026-06-05 12:00:00+00"));
        }

        List<Long> collected = new ArrayList<>();
        long prev = Long.MAX_VALUE;
        for (int page = 1; page <= 3; page++) {
            MvcResult res = mockMvc.perform(get("/api/stocks/" + stock + "/txns")
                            .param("page", String.valueOf(page)).param("size", "2")
                            .header(AUTH, bearer(warehouseToken)))
                    .andExpect(status().isOk())
                    .andReturn();
            for (JsonNode r : node(res).path("data").path("page").path("records")) {
                long id = r.path("txnId").asLong();
                collected.add(id);
                assertTrue(id < prev, "流水按 id 严格递减，深翻页稳定不重复");
                prev = id;
            }
        }
        assertEquals(5, new HashSet<>(collected).size(), "5 条流水无重复无遗漏");
        assertEquals(inserted, new HashSet<>(collected));
    }

    // ---- T-5 空结果（库存空 / 流水空均为成功，非 40401）----

    @Test
    void t5_emptyResults() throws Exception {
        long dept = newDept("U10T-D5");
        long pg = newPg("U10T-PG5", dept);

        // 库存查询无匹配 → 空分页
        mockMvc.perform(get("/api/stocks").param("materialName", "U10T-NOMATCH-zzz")
                        .param("projectGroupId", String.valueOf(pg)).header(AUTH, bearer(warehouseToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(0));

        // 存在但无流水的库存项 → 流水空分页（成功，非 40401），对账 txnSum=0
        long stock = insertStock("U10T-5-empty", pg, dept, "0");
        mockMvc.perform(get("/api/stocks/" + stock + "/txns").header(AUTH, bearer(warehouseToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.total").value(0))
                .andExpect(jsonPath("$.data.bookQty").value(0))
                .andExpect(jsonPath("$.data.txnSum").value(0));
    }

    // ---- T-6 库存项不存在 ----

    @Test
    void t6_stockNotFound() throws Exception {
        mockMvc.perform(get("/api/stocks/99999999/txns").header(AUTH, bearer(warehouseToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    // ---- T-7 对账：quantity == txnSum == bookQty（INV-1）----

    @Test
    void t7_reconciliation() throws Exception {
        long dept = newDept("U10T-D7");
        long pg = newPg("U10T-PG7", dept);
        long stock = insertStock("U10T-7-m", pg, dept, "7");
        insertTxn(stock, "inbound", "5", "inbound_order", 700, "2026-06-01 10:00:00+00");
        insertTxn(stock, "inbound", "3", "inbound_order", 701, "2026-06-02 10:00:00+00");
        insertTxn(stock, "outbound", "-1", "outbound_order", 702, "2026-06-03 10:00:00+00");

        mockMvc.perform(get("/api/stocks/" + stock + "/txns").header(AUTH, bearer(warehouseToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookQty").value(7))
                .andExpect(jsonPath("$.data.txnSum").value(7));
    }

    // ---- T-8 鉴权：editor 被拒（40301）；admin 放行（warehouse|admin OR）----

    @Test
    void t8_authorization() throws Exception {
        mockMvc.perform(get("/api/stocks").header(AUTH, bearer(editorToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
        mockMvc.perform(get("/api/stocks/1/txns").header(AUTH, bearer(editorToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        // admin 角色放行（OR 命中）
        mockMvc.perform(get("/api/stocks").header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ---- T-9 分页参数越界 → 40001 ----

    @Test
    void t9_pagingOutOfRange() throws Exception {
        mockMvc.perform(get("/api/stocks").param("page", "0").header(AUTH, bearer(warehouseToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(get("/api/stocks").param("size", "0").header(AUTH, bearer(warehouseToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(get("/api/stocks").param("size", "1000").header(AUTH, bearer(warehouseToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    // ---- T-10 只读无副作用：查询前后 stock_item/stock_txn 行数不变 ----

    @Test
    void t10_readOnlyNoSideEffect() throws Exception {
        long dept = newDept("U10T-D10");
        long pg = newPg("U10T-PG10", dept);
        long stock = insertStock("U10T-10-m", pg, dept, "2");
        insertTxn(stock, "inbound", "2", "inbound_order", 1000, "2026-06-01 10:00:00+00");

        int itemsBefore = count("stock_item");
        int txnsBefore = count("stock_txn");

        mockMvc.perform(get("/api/stocks").param("projectGroupId", String.valueOf(pg))
                .header(AUTH, bearer(warehouseToken))).andExpect(status().isOk());
        mockMvc.perform(get("/api/stocks/" + stock + "/txns").header(AUTH, bearer(warehouseToken)))
                .andExpect(status().isOk());

        assertEquals(itemsBefore, count("stock_item"), "只读：stock_item 行数不变");
        assertEquals(txnsBefore, count("stock_txn"), "只读：stock_txn 行数不变");
    }

    // ---- helpers ----

    private long newDept(String code) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO department(name, code) VALUES (?, ?) RETURNING id", Long.class, code, code);
    }

    private long newPg(String code, long deptId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO project_group(name, code, department_id) VALUES (?, ?, ?) RETURNING id",
                Long.class, code, code, deptId);
    }

    private long insertStock(String material, long pgId, long deptId, String quantity) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO stock_item(material_name, project_group_id, department_id, quantity) "
                        + "VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, material, pgId, deptId, new BigDecimal(quantity));
    }

    private long insertTxn(long stockItemId, String type, String qtyChange, String refType, long refId,
                           String createdAt) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO stock_txn(stock_item_id, type, qty_change, ref_type, ref_id, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?::timestamptz) RETURNING id",
                Long.class, stockItemId, type, new BigDecimal(qtyChange), refType, refId, createdAt);
    }

    private int count(String table) {
        Integer n = jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        return n == null ? 0 : n;
    }

    private JsonNode node(MvcResult res) throws Exception {
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    private String createUserWithRole(String account, String roleCode, Long deptId) throws Exception {
        Long roleId = jdbcTemplate.queryForObject(
                "SELECT id FROM role WHERE code = ? AND is_deleted = 0", Long.class, roleCode);
        Long uid = jdbcTemplate.queryForObject(
                "INSERT INTO sys_user(name, account, password_hash, department_id) VALUES (?,?,?,?) RETURNING id",
                Long.class, account, account, passwordEncoder.encode("pass123"), deptId);
        jdbcTemplate.update("INSERT INTO user_role(user_id, role_id) VALUES (?, ?)", uid, roleId);
        return login(account, "pass123");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String login(String account, String password) throws Exception {
        String content = mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content(String.format("{\"account\":\"%s\",\"password\":\"%s\"}", account, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content).path("data").path("token").asText();
    }

    private void cleanup() {
        String pgSub = "(SELECT id FROM project_group WHERE code LIKE 'U10T-%')";
        String stockSub = "(SELECT id FROM stock_item WHERE project_group_id IN " + pgSub + ")";
        jdbcTemplate.update("DELETE FROM stock_txn WHERE stock_item_id IN " + stockSub);
        jdbcTemplate.update("DELETE FROM stock_item WHERE project_group_id IN " + pgSub);
        jdbcTemplate.update("DELETE FROM project_group WHERE code LIKE 'U10T-%'");
        jdbcTemplate.update("DELETE FROM user_role WHERE user_id IN "
                + "(SELECT id FROM sys_user WHERE account LIKE 'u10t\\_%')");
        jdbcTemplate.update("DELETE FROM sys_user WHERE account LIKE 'u10t\\_%'");
        jdbcTemplate.update("DELETE FROM department WHERE code LIKE 'U10T-%'");
    }
}
