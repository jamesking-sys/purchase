package com.gov.procurement.modules.inbound;

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
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * U9 多次到货验收入库集成测试（详设 §7 T-1..T-10）。全上下文 + 本地 PostgreSQL（无库优雅跳过）。
 * 测试数据用前缀 U9T-/u9t_ 隔离，{@code @BeforeAll}/{@code @AfterAll} 物理清理。
 *
 * <p>错误码采用编码归一口径：累计超收 42204（U7 已占 42203）；采购单非 executing → 40903；对象不存在 → 40401；
 * 参数非法 → 40001；非 warehouse → 40301。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIf("com.gov.procurement.support.LocalPg#available")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InboundIntegrationTest {

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
    private long projectGroupId;
    private long approvedBudgetId;
    private long leafSubjectId;

    @BeforeAll
    void setup() throws Exception {
        cleanup();
        adminToken = login("admin", "admin123");
        Long sysDept = jdbcTemplate.queryForObject(
                "SELECT id FROM department WHERE code = 'SYS' AND is_deleted = 0 ORDER BY id LIMIT 1", Long.class);
        warehouseToken = createUserWithRole("u9t_wh", "warehouse", sysDept);

        Long deptId = jdbcTemplate.queryForObject(
                "INSERT INTO department(name, code) VALUES ('U9T部门','U9T-DEPT') RETURNING id", Long.class);
        projectGroupId = jdbcTemplate.queryForObject(
                "INSERT INTO project_group(name, code, department_id) VALUES ('U9T组','U9T-PG',?) RETURNING id",
                Long.class, deptId);
        approvedBudgetId = jdbcTemplate.queryForObject(
                "INSERT INTO budget(project_group_id, name, status) VALUES (?, 'U9T-budget', 'approved') RETURNING id",
                Long.class, projectGroupId);
        leafSubjectId = jdbcTemplate.queryForObject(
                "INSERT INTO budget_subject(name, code, level, is_leaf) VALUES ('U9T叶子','U9T-LEAF',1,true) RETURNING id",
                Long.class);
    }

    @AfterAll
    void teardown() {
        cleanup();
    }

    // ---- T-1 单次入库（实收 < 采购数量）----

    @Test
    void t1_singleInboundPartial() throws Exception {
        long po = newOrder();
        long item = addItem(po, "U9T-M1", 10);
        MvcResult res = inbound(po, warehouseToken, item(item, 4))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.purchaseOrderStatus").value("executing"))
                .andExpect(jsonPath("$.data.items[0].receivedQtyTotal").value(4))
                .andReturn();
        long stockItemId = objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("items").get(0).path("stockItemId").asLong();

        assertEquals(4.0, received(item));
        assertEquals(4.0, quantity(stockItemId));
        assertEquals(1, txnCount(stockItemId, "inbound"));
        assertEquals("executing", poStatus(po));
    }

    // ---- T-2 分批多次入库 ----

    @Test
    void t2_multipleBatches() throws Exception {
        long po = newOrder();
        long item = addItem(po, "U9T-M2", 10);
        inbound(po, warehouseToken, item(item, 3)).andExpect(status().isOk());
        MvcResult res = inbound(po, warehouseToken, item(item, 4)).andExpect(status().isOk()).andReturn();
        long stockItemId = objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("items").get(0).path("stockItemId").asLong();

        assertEquals(7.0, received(item));
        assertEquals(7.0, quantity(stockItemId));
        assertEquals(2, txnCount(stockItemId, "inbound"));

        // 入库记录查询返回两条
        mockMvc.perform(get("/api/inbounds").param("purchaseOrderId", String.valueOf(po))
                        .header(AUTH, bearer(warehouseToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[0].items[0].materialName").value("U9T-M2"));
    }

    // ---- T-3 累计超收拒绝（回滚）----

    @Test
    void t3_cumulativeOverReceiveRejected() throws Exception {
        long po = newOrder();
        long item = addItem(po, "U9T-M3", 10);
        inbound(po, warehouseToken, item(item, 4)).andExpect(status().isOk());
        inbound(po, warehouseToken, item(item, 7))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42204));

        assertEquals(4.0, received(item), "超收应回滚，received_qty 不变");
        Integer inbounds = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM inbound_order WHERE purchase_order_id = ?", Integer.class, po);
        assertEquals(1, inbounds, "超收不应写入第二张入库单");
    }

    // ---- T-3b 同请求同明细多行合计超收 ----

    @Test
    void t3b_sameRequestLinesOverReceive() throws Exception {
        long po = newOrder();
        long item = addItem(po, "U9T-M3b", 10);
        inbound(po, warehouseToken, item(item, 6), item(item, 6))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42204));
        assertEquals(0.0, received(item));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM inbound_order WHERE purchase_order_id = ?", Integer.class, po));
    }

    // ---- T-4 相同 material + project_group 聚合到同一库存项 ----

    @Test
    void t4_stockAggregatedByKey() throws Exception {
        long po = newOrder();
        long item1 = addItem(po, "U9T-AGG", 5);
        long item2 = addItem(po, "U9T-AGG", 5);
        inbound(po, warehouseToken, item(item1, 2), item(item2, 3)).andExpect(status().isOk());

        Integer stockRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM stock_item WHERE material_name = 'U9T-AGG' AND project_group_id = ? "
                        + "AND is_deleted = 0", Integer.class, projectGroupId);
        assertEquals(1, stockRows, "同 material+project_group 应聚合为一条库存项");
        Double qty = jdbcTemplate.queryForObject(
                "SELECT quantity FROM stock_item WHERE material_name = 'U9T-AGG' AND project_group_id = ? "
                        + "AND is_deleted = 0", Double.class, projectGroupId);
        assertEquals(5.0, qty, "聚合库存量为各次累加");
    }

    // ---- T-5 全部入完转 inbounded ----

    @Test
    void t5_allReceivedTransitionsInbounded() throws Exception {
        long po = newOrder();
        long item = addItem(po, "U9T-M5", 10);
        inbound(po, warehouseToken, item(item, 10))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.purchaseOrderStatus").value("inbounded"));
        assertEquals("inbounded", poStatus(po));
    }

    // ---- T-5b 仍有明细未满量则保持 executing ----

    @Test
    void t5b_partialKeepsExecuting() throws Exception {
        long po = newOrder();
        long item1 = addItem(po, "U9T-M5b-1", 10);
        addItem(po, "U9T-M5b-2", 10);
        inbound(po, warehouseToken, item(item1, 10)).andExpect(status().isOk());
        assertEquals("executing", poStatus(po), "尚有明细未满量，采购单保持 executing");
    }

    // ---- T-6 非 warehouse 角色 ----

    @Test
    void t6_nonWarehouseForbidden() throws Exception {
        long po = newOrder();
        long item = addItem(po, "U9T-M6", 10);
        inbound(po, adminToken, item(item, 1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
        assertEquals(0.0, received(item));
    }

    // ---- T-7 并发入库（FOR UPDATE 串行化，不超收/不丢更新）----

    @Test
    void t7_concurrentInboundSerialized() throws Exception {
        long po = newOrder();
        long item = addItem(po, "U9T-M7", 10);
        String body = objectMapper.writeValueAsString(Map.of(
                "purchaseOrderId", po, "items", List.of(item(item, 6))));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> call = () -> mockMvc.perform(post("/api/inbounds").header(AUTH, bearer(warehouseToken))
                    .contentType(APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
            Future<Integer> f1 = pool.submit(call);
            Future<Integer> f2 = pool.submit(call);
            int s1 = f1.get(20, TimeUnit.SECONDS);
            int s2 = f2.get(20, TimeUnit.SECONDS);
            // 一成功(200)、一超收(422)：6+6>10 被 FOR UPDATE 串行化后拦截
            assertEquals(1, (s1 == 200 ? 1 : 0) + (s2 == 200 ? 1 : 0), "应恰有一次入库成功");
            assertEquals(1, (s1 == 422 ? 1 : 0) + (s2 == 422 ? 1 : 0), "应恰有一次因超收被拒");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(6.0, received(item), "并发后累计已收为单次成功值，不超收、不丢更新");
        Long stockItemId = jdbcTemplate.queryForObject(
                "SELECT id FROM stock_item WHERE material_name = 'U9T-M7' AND project_group_id = ? AND is_deleted = 0",
                Long.class, projectGroupId);
        assertEquals(6.0, quantity(stockItemId));
        assertEquals(1, txnCount(stockItemId, "inbound"));
    }

    // ---- T-8 采购单/明细不存在 ----

    @Test
    void t8_notFound() throws Exception {
        inbound(99999999L, warehouseToken, item(1L, 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        long po = newOrder();
        addItem(po, "U9T-M8", 10);
        inbound(po, warehouseToken, item(88888888L, 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    // ---- T-9 参数非法（receivedQty<=0 / items 空）----

    @Test
    void t9_invalidParams() throws Exception {
        long po = newOrder();
        long item = addItem(po, "U9T-M9", 10);
        inbound(po, warehouseToken, item(item, 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(post("/api/inbounds").header(AUTH, bearer(warehouseToken)).contentType(APPLICATION_JSON)
                        .content(String.format("{\"purchaseOrderId\":%d,\"items\":[]}", po)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    // ---- T-10 入库后对账（INV-3/INV-4）----

    @Test
    void t10_reconciliation() throws Exception {
        long po = newOrder();
        long item = addItem(po, "U9T-M10", 10);
        inbound(po, warehouseToken, item(item, 3)).andExpect(status().isOk());
        inbound(po, warehouseToken, item(item, 5)).andExpect(status().isOk());
        Long stockItemId = jdbcTemplate.queryForObject(
                "SELECT id FROM stock_item WHERE material_name = 'U9T-M10' AND project_group_id = ? AND is_deleted = 0",
                Long.class, projectGroupId);

        Double quantity = quantity(stockItemId);
        Double txnSum = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(qty_change),0) FROM stock_txn WHERE stock_item_id = ?", Double.class, stockItemId);
        assertEquals(txnSum, quantity, "INV-3：库存量 == 流水累计");

        Double received = received(item);
        Double inboundSum = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(received_qty),0) FROM inbound_item WHERE purchase_item_id = ?",
                Double.class, item);
        assertEquals(inboundSum, received, "INV-4：累计已收 == 入库明细之和");
    }

    // ---- T-11 待收明细查询（pending-items）----

    @Test
    void t11_pendingItems() throws Exception {
        long po = newOrder();
        long item = addItem(po, "U9T-M11", 10);
        inbound(po, warehouseToken, item(item, 3)).andExpect(status().isOk());

        // 仓管查待收：采购量 10、已收 3、待收 7
        mockMvc.perform(get("/api/inbounds/pending-items").param("purchaseOrderId", String.valueOf(po))
                        .header(AUTH, bearer(warehouseToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].purchaseItemId").value((int) item))
                .andExpect(jsonPath("$.data[0].materialName").value("U9T-M11"))
                .andExpect(jsonPath("$.data[0].qty").value(10))
                .andExpect(jsonPath("$.data[0].receivedQty").value(3))
                .andExpect(jsonPath("$.data[0].remaining").value(7));

        // 采购单不存在 → 40401
        mockMvc.perform(get("/api/inbounds/pending-items").param("purchaseOrderId", "99999999")
                        .header(AUTH, bearer(warehouseToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        // 非 warehouse → 40301
        mockMvc.perform(get("/api/inbounds/pending-items").param("purchaseOrderId", String.valueOf(po))
                        .header(AUTH, bearer(adminToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    // ---- helpers ----

    private long newOrder() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO purchase_order(budget_id, project_group_id, status) VALUES (?, ?, 'executing') RETURNING id",
                Long.class, approvedBudgetId, projectGroupId);
    }

    private long addItem(long poId, String material, int qty) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO purchase_item(purchase_order_id, subject_id, material_name, qty, amount) "
                        + "VALUES (?, ?, ?, ?, 100.00) RETURNING id",
                Long.class, poId, leafSubjectId, material, qty);
    }

    private Map<String, Object> item(long purchaseItemId, Object receivedQty) {
        return Map.of("purchaseItemId", purchaseItemId, "receivedQty", receivedQty);
    }

    @SafeVarargs
    private ResultActions inbound(long poId, String token, Map<String, Object>... items) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "purchaseOrderId", poId, "items", List.of(items)));
        return mockMvc.perform(post("/api/inbounds").header(AUTH, bearer(token))
                .contentType(APPLICATION_JSON).content(body));
    }

    private Double received(long purchaseItemId) {
        return jdbcTemplate.queryForObject(
                "SELECT received_qty FROM purchase_item WHERE id = ?", Double.class, purchaseItemId);
    }

    private Double quantity(long stockItemId) {
        return jdbcTemplate.queryForObject(
                "SELECT quantity FROM stock_item WHERE id = ?", Double.class, stockItemId);
    }

    private String poStatus(long poId) {
        return jdbcTemplate.queryForObject("SELECT status FROM purchase_order WHERE id = ?", String.class, poId);
    }

    private int txnCount(long stockItemId, String type) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM stock_txn WHERE stock_item_id = ? AND type = ?", Integer.class, stockItemId, type);
        return n == null ? 0 : n;
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
        String pgSub = "(SELECT id FROM project_group WHERE code LIKE 'U9T-%')";
        String poSub = "(SELECT id FROM purchase_order WHERE project_group_id IN " + pgSub + ")";
        String stockSub = "(SELECT id FROM stock_item WHERE project_group_id IN " + pgSub + ")";
        jdbcTemplate.update("DELETE FROM inbound_item WHERE inbound_order_id IN "
                + "(SELECT id FROM inbound_order WHERE purchase_order_id IN " + poSub + ")");
        jdbcTemplate.update("DELETE FROM inbound_order WHERE purchase_order_id IN " + poSub);
        jdbcTemplate.update("DELETE FROM stock_txn WHERE stock_item_id IN " + stockSub);
        jdbcTemplate.update("DELETE FROM stock_item WHERE project_group_id IN " + pgSub);
        jdbcTemplate.update("DELETE FROM purchase_item WHERE purchase_order_id IN " + poSub);
        jdbcTemplate.update("DELETE FROM purchase_order WHERE project_group_id IN " + pgSub);
        jdbcTemplate.update("DELETE FROM budget WHERE name LIKE 'U9T%'");
        jdbcTemplate.update("DELETE FROM budget_subject WHERE code LIKE 'U9T-%'");
        jdbcTemplate.update("DELETE FROM user_role WHERE user_id IN "
                + "(SELECT id FROM sys_user WHERE account LIKE 'u9t\\_%')");
        jdbcTemplate.update("DELETE FROM sys_user WHERE account LIKE 'u9t\\_%'");
        jdbcTemplate.update("DELETE FROM project_group WHERE code LIKE 'U9T-%'");
        jdbcTemplate.update("DELETE FROM department WHERE code LIKE 'U9T-%'");
    }
}
