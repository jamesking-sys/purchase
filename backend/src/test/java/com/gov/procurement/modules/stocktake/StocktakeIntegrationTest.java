package com.gov.procurement.modules.stocktake;

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
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * U12 盘点 + 差异调整库存集成测试（详设 §7 T-1..T-12）。全上下文 + 本地 PostgreSQL（无库优雅跳过）。
 * 测试数据用前缀 U12T-/u12t_ 隔离，{@code @BeforeAll}/{@code @AfterAll} 物理清理；每用例自建项目组隔离盘点范围。
 *
 * <p>错误码采用编码归一口径（详设草稿早于归一）：状态冲突（已确认）→ 40903（非草稿 40901，与 U7/U9/U11 一致）；
 * 实盘数为负 → 42205（非草稿 42204，42204 已被 U9 超收占用）；对象不存在 → 40401；非 warehouse → 40301。
 * 库存写入经 M5 唯一记账入口 StockService.adjustTo，账实对账 INV-3：quantity == Σ stock_txn.qty_change。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIf("com.gov.procurement.support.LocalPg#available")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StocktakeIntegrationTest {

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
    private String editorToken;
    private long sysDept;

    @BeforeAll
    void setup() throws Exception {
        cleanup();
        sysDept = jdbcTemplate.queryForObject(
                "SELECT id FROM department WHERE code = 'SYS' AND is_deleted = 0 ORDER BY id LIMIT 1", Long.class);
        warehouseToken = createUserWithRole("u12t_wh", "warehouse", sysDept);
        editorToken = createUserWithRole("u12t_ed", "editor", sysDept);
    }

    @AfterAll
    void teardown() {
        cleanup();
    }

    // ---- T-1 发起盘点快照账面 ----

    @Test
    void t1_createSnapshot() throws Exception {
        long dept = newDept("U12T-D1");
        long pg = newPg("U12T-PG1", dept);
        long s1 = createStock("U12T-1-a", pg, dept, "10");
        long s2 = createStock("U12T-1-b", pg, dept, "40");

        MvcResult res = createStocktake(pg, warehouseToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("counting"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andReturn();

        long stId = node(res).path("data").path("stocktakeId").asLong();
        long sti1 = stItemIdFor(res, s1);
        // book_qty 快照 = 各库存项当时 quantity；actual_qty=book_qty；diff=0；diff_type=none
        assertEquals(10.0, stiBook(sti1));
        assertEquals(10.0, stiActual(sti1));
        assertEquals(0.0, stiDiff(sti1));
        assertEquals("none", stiDiffType(sti1));
        assertEquals(40.0, stiBook(stItemIdFor(res, s2)));
        assertEquals("counting", stStatus(stId));
    }

    // ---- T-2 实盘 > 账面 → gain，stock_item 不变 ----

    @Test
    void t2_gain() throws Exception {
        long dept = newDept("U12T-D2");
        long pg = newPg("U12T-PG2", dept);
        long s = createStock("U12T-2-m", pg, dept, "100");
        MvcResult res = createStocktake(pg, warehouseToken).andReturn();
        long sti = stItemIdFor(res, s);

        saveActuals(stId(res), warehouseToken, actual(sti, 105))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].diff").value(5))
                .andExpect(jsonPath("$.data.items[0].diffType").value("gain"));
        assertEquals(5.0, stiDiff(sti));
        assertEquals("gain", stiDiffType(sti));
        assertEquals(100.0, stockQty(s), "录入实盘不调库存");
    }

    // ---- T-3 实盘 < 账面 → loss ----

    @Test
    void t3_loss() throws Exception {
        long dept = newDept("U12T-D3");
        long pg = newPg("U12T-PG3", dept);
        long s = createStock("U12T-3-m", pg, dept, "40");
        MvcResult res = createStocktake(pg, warehouseToken).andReturn();
        long sti = stItemIdFor(res, s);

        saveActuals(stId(res), warehouseToken, actual(sti, 38))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].diff").value(-2))
                .andExpect(jsonPath("$.data.items[0].diffType").value("loss"));
        assertEquals(40.0, stockQty(s), "录入实盘不调库存");
    }

    // ---- T-4 实盘 == 账面 → none ----

    @Test
    void t4_none() throws Exception {
        long dept = newDept("U12T-D4");
        long pg = newPg("U12T-PG4", dept);
        long s = createStock("U12T-4-m", pg, dept, "40");
        MvcResult res = createStocktake(pg, warehouseToken).andReturn();
        long sti = stItemIdFor(res, s);

        saveActuals(stId(res), warehouseToken, actual(sti, 40))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].diff").value(0))
                .andExpect(jsonPath("$.data.items[0].diffType").value("none"));
    }

    // ---- T-5 / AC-9 确认调整：盘盈/盘亏/无差异混合 ----

    @Test
    void t5_confirmMixed() throws Exception {
        long dept = newDept("U12T-D5");
        long pg = newPg("U12T-PG5", dept);
        long a = createStock("U12T-5-a", pg, dept, "40");   // loss → 38
        long b = createStock("U12T-5-b", pg, dept, "100");  // gain → 105
        long c = createStock("U12T-5-c", pg, dept, "5");    // none → 5
        MvcResult res = createStocktake(pg, warehouseToken).andReturn();
        long stId = stId(res);
        saveActuals(stId, warehouseToken, actual(stItemIdFor(res, a), 38),
                actual(stItemIdFor(res, b), 105), actual(stItemIdFor(res, c), 5))
                .andExpect(status().isOk());

        confirm(stId, warehouseToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("confirmed"))
                .andExpect(jsonPath("$.data.adjustedCount").value(2));

        assertEquals(38.0, stockQty(a));
        assertEquals(105.0, stockQty(b));
        assertEquals(5.0, stockQty(c), "无差异项库存不变");
        assertEquals(1, txnCount(a, "loss"));
        assertEquals(1, txnCount(b, "gain"));
        assertEquals(0, txnCount(c, "gain") + txnCount(c, "loss"), "无差异项不记流水");
        // 流水 ref 指向盘点单
        assertEquals(stId, jdbcTemplate.queryForObject(
                "SELECT ref_id FROM stock_txn WHERE stock_item_id = ? AND type = 'loss'", Long.class, a));
        assertEquals("stocktake", jdbcTemplate.queryForObject(
                "SELECT ref_type FROM stock_txn WHERE stock_item_id = ? AND type = 'loss'", String.class, a));
        assertEquals("confirmed", stStatus(stId));
    }

    // ---- T-6 已 confirmed 再录入实盘 → 40903 ----

    @Test
    void t6_saveAfterConfirmed() throws Exception {
        long dept = newDept("U12T-D6");
        long pg = newPg("U12T-PG6", dept);
        long s = createStock("U12T-6-m", pg, dept, "40");
        MvcResult res = createStocktake(pg, warehouseToken).andReturn();
        long stId = stId(res);
        long sti = stItemIdFor(res, s);
        saveActuals(stId, warehouseToken, actual(sti, 38)).andExpect(status().isOk());
        confirm(stId, warehouseToken).andExpect(status().isOk());

        saveActuals(stId, warehouseToken, actual(sti, 30))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40903));
        assertEquals(38.0, stiActual(sti), "已确认后录入被拒，明细不变");
    }

    // ---- T-6b 已 confirmed 再确认 → 40903，幂等不重复写 ----

    @Test
    void t6b_confirmTwice() throws Exception {
        long dept = newDept("U12T-D6b");
        long pg = newPg("U12T-PG6b", dept);
        long s = createStock("U12T-6b-m", pg, dept, "40");
        MvcResult res = createStocktake(pg, warehouseToken).andReturn();
        long stId = stId(res);
        saveActuals(stId, warehouseToken, actual(stItemIdFor(res, s), 38)).andExpect(status().isOk());
        confirm(stId, warehouseToken).andExpect(status().isOk());

        confirm(stId, warehouseToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40903));
        assertEquals(38.0, stockQty(s), "重复确认不再次调库存");
        assertEquals(1, txnCount(s, "loss"), "重复确认不再次记流水");
    }

    // ---- T-7 实盘数为负 → 42205，无写入 ----

    @Test
    void t7_negativeActual() throws Exception {
        long dept = newDept("U12T-D7");
        long pg = newPg("U12T-PG7", dept);
        long s = createStock("U12T-7-m", pg, dept, "40");
        MvcResult res = createStocktake(pg, warehouseToken).andReturn();
        long sti = stItemIdFor(res, s);

        saveActuals(stId(res), warehouseToken, actual(sti, -1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42205));
        assertEquals(40.0, stiActual(sti), "为负被拒，明细不变");
        assertEquals("none", stiDiffType(sti));
    }

    // ---- T-8 非 warehouse 角色 → 40301 ----

    @Test
    void t8_forbidden() throws Exception {
        createStocktake(1L, editorToken)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        saveActuals(1L, editorToken, actual(1L, 1))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        confirm(1L, editorToken)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
    }

    // ---- T-9 项目组 / 盘点单不存在 → 40401 ----

    @Test
    void t9_notFound() throws Exception {
        createStocktake(99999999L, warehouseToken)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(40401));
        confirm(99999999L, warehouseToken)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(40401));
    }

    // ---- T-10 确认后对账（INV-3）----

    @Test
    void t10_reconciliation() throws Exception {
        long dept = newDept("U12T-D10");
        long pg = newPg("U12T-PG10", dept);
        long a = createStock("U12T-10-a", pg, dept, "40");
        long b = createStock("U12T-10-b", pg, dept, "100");
        MvcResult res = createStocktake(pg, warehouseToken).andReturn();
        long stId = stId(res);
        saveActuals(stId, warehouseToken, actual(stItemIdFor(res, a), 38),
                actual(stItemIdFor(res, b), 105)).andExpect(status().isOk());
        confirm(stId, warehouseToken).andExpect(status().isOk());

        // INV-3：每个调整库存项 quantity == Σ stock_txn.qty_change 且 == actual_qty
        assertEquals(txnSum(a), stockQty(a), "INV-3 盘亏项");
        assertEquals(38.0, stockQty(a));
        assertEquals(txnSum(b), stockQty(b), "INV-3 盘盈项");
        assertEquals(105.0, stockQty(b));
        // qty_change 符号与 diff_type 一致
        assertEquals(-2.0, jdbcTemplate.queryForObject(
                "SELECT qty_change FROM stock_txn WHERE stock_item_id = ? AND type='loss'", Double.class, a));
        assertEquals(5.0, jdbcTemplate.queryForObject(
                "SELECT qty_change FROM stock_txn WHERE stock_item_id = ? AND type='gain'", Double.class, b));
    }

    // ---- T-11 并发确认同一盘点单：仅一次生效，另一次 40903 ----

    @Test
    void t11_concurrentConfirm() throws Exception {
        long dept = newDept("U12T-D11");
        long pg = newPg("U12T-PG11", dept);
        long s = createStock("U12T-11-m", pg, dept, "40");
        MvcResult res = createStocktake(pg, warehouseToken).andReturn();
        long stId = stId(res);
        saveActuals(stId, warehouseToken, actual(stItemIdFor(res, s), 50)).andExpect(status().isOk());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> call = () -> confirm(stId, warehouseToken).andReturn().getResponse().getStatus();
            Future<Integer> f1 = pool.submit(call);
            Future<Integer> f2 = pool.submit(call);
            int s1 = f1.get(20, TimeUnit.SECONDS);
            int s2 = f2.get(20, TimeUnit.SECONDS);
            assertEquals(1, (s1 == 200 ? 1 : 0) + (s2 == 200 ? 1 : 0), "应恰有一次确认成功");
            assertEquals(1, (s1 == 409 ? 1 : 0) + (s2 == 409 ? 1 : 0), "应恰有一次因已确认被拒");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(50.0, stockQty(s), "并发后库存只调一次");
        assertEquals(1, txnCount(s, "gain"), "并发后只记一条流水（行锁串行化幂等）");
    }

    // ---- T-12 发起后、确认前库存被改动：按调整时当前值记账，INV-3 仍成立 ----

    @Test
    void t12_stockChangedBeforeConfirm() throws Exception {
        long dept = newDept("U12T-D12");
        long pg = newPg("U12T-PG12", dept);
        long s = createStock("U12T-12-m", pg, dept, "40");   // 账面快照基线 40（含 +40 inbound 流水）
        MvcResult res = createStocktake(pg, warehouseToken).andReturn();
        long stId = stId(res);
        long sti = stItemIdFor(res, s);
        saveActuals(stId, warehouseToken, actual(sti, 45)).andExpect(status().isOk());  // diff vs book40 = +5 gain

        // 模拟确认前并发入库：库存 40→50，并补一条 +10 流水（保持 INV-3 基线）
        jdbcTemplate.update("UPDATE stock_item SET quantity = 50 WHERE id = ?", s);
        jdbcTemplate.update("INSERT INTO stock_txn(stock_item_id, type, qty_change, ref_type, ref_id) "
                + "VALUES (?, 'inbound', 10, 'inbound_order', 1)", s);

        confirm(stId, warehouseToken).andExpect(status().isOk());

        // 置数到实盘 45；qty_change = 45 - 50(调整时当前) = -5（type 取 diff_type=gain，§5.2 边界）
        assertEquals(45.0, stockQty(s), "最终库存 == 实盘数");
        assertEquals(txnSum(s), stockQty(s), "INV-3 仍成立：库存 == 流水累计（40+10-5=45）");
        assertEquals(-5.0, jdbcTemplate.queryForObject(
                "SELECT qty_change FROM stock_txn WHERE stock_item_id = ? AND type='gain'", Double.class, s));
    }

    // ---- helpers ----

    private ResultActions createStocktake(long scopePg, String token) throws Exception {
        return mockMvc.perform(post("/api/stocktakes").header(AUTH, bearer(token)).contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("scopeProjectGroupId", scopePg))));
    }

    @SafeVarargs
    private ResultActions saveActuals(long stocktakeId, String token, Map<String, Object>... items) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("items", List.of(items)));
        return mockMvc.perform(put("/api/stocktakes/" + stocktakeId + "/items").header(AUTH, bearer(token))
                .contentType(APPLICATION_JSON).content(body));
    }

    private ResultActions confirm(long stocktakeId, String token) throws Exception {
        return mockMvc.perform(post("/api/stocktakes/" + stocktakeId + "/confirm").header(AUTH, bearer(token)));
    }

    private Map<String, Object> actual(long stocktakeItemId, Object actualQty) {
        return Map.of("stocktakeItemId", stocktakeItemId, "actualQty", actualQty);
    }

    private long stId(MvcResult res) throws Exception {
        return node(res).path("data").path("stocktakeId").asLong();
    }

    private long stItemIdFor(MvcResult res, long stockItemId) throws Exception {
        for (JsonNode it : node(res).path("data").path("items")) {
            if (it.path("stockItemId").asLong() == stockItemId) {
                return it.path("stocktakeItemId").asLong();
            }
        }
        throw new IllegalStateException("snapshot 缺库存项 " + stockItemId);
    }

    private long newDept(String code) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO department(name, code) VALUES (?, ?) RETURNING id", Long.class, code, code);
    }

    private long newPg(String code, long deptId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO project_group(name, code, department_id) VALUES (?, ?, ?) RETURNING id",
                Long.class, code, code, deptId);
    }

    /** 建库存项 + 基线 inbound 流水（quantity>0），使 INV-3「库存==流水累计」从一开始即成立。 */
    private long createStock(String material, long pg, long dept, String qty) {
        long id = jdbcTemplate.queryForObject(
                "INSERT INTO stock_item(material_name, project_group_id, department_id, quantity) "
                        + "VALUES (?, ?, ?, ?) RETURNING id", Long.class, material, pg, dept, new BigDecimal(qty));
        BigDecimal q = new BigDecimal(qty);
        if (q.signum() != 0) {
            jdbcTemplate.update("INSERT INTO stock_txn(stock_item_id, type, qty_change, ref_type, ref_id) "
                    + "VALUES (?, 'inbound', ?, 'inbound_order', 1)", id, q);
        }
        return id;
    }

    private Double stockQty(long stockItemId) {
        return jdbcTemplate.queryForObject("SELECT quantity FROM stock_item WHERE id = ?", Double.class, stockItemId);
    }

    private Double txnSum(long stockItemId) {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(qty_change),0) FROM stock_txn WHERE stock_item_id = ?", Double.class, stockItemId);
    }

    private int txnCount(long stockItemId, String type) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM stock_txn WHERE stock_item_id = ? AND type = ?", Integer.class, stockItemId, type);
        return n == null ? 0 : n;
    }

    private String stStatus(long stocktakeId) {
        return jdbcTemplate.queryForObject("SELECT status FROM stocktake WHERE id = ?", String.class, stocktakeId);
    }

    private Double stiBook(long stocktakeItemId) {
        return jdbcTemplate.queryForObject(
                "SELECT book_qty FROM stocktake_item WHERE id = ?", Double.class, stocktakeItemId);
    }

    private Double stiActual(long stocktakeItemId) {
        return jdbcTemplate.queryForObject(
                "SELECT actual_qty FROM stocktake_item WHERE id = ?", Double.class, stocktakeItemId);
    }

    private Double stiDiff(long stocktakeItemId) {
        return jdbcTemplate.queryForObject(
                "SELECT diff FROM stocktake_item WHERE id = ?", Double.class, stocktakeItemId);
    }

    private String stiDiffType(long stocktakeItemId) {
        return jdbcTemplate.queryForObject(
                "SELECT diff_type FROM stocktake_item WHERE id = ?", String.class, stocktakeItemId);
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

    private JsonNode node(MvcResult res) throws Exception {
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    private void cleanup() {
        String pgSub = "(SELECT id FROM project_group WHERE code LIKE 'U12T-%')";
        String stockSub = "(SELECT id FROM stock_item WHERE project_group_id IN " + pgSub + ")";
        String stSub = "(SELECT id FROM stocktake WHERE scope_project_group_id IN " + pgSub + ")";
        jdbcTemplate.update("DELETE FROM stock_txn WHERE stock_item_id IN " + stockSub);
        jdbcTemplate.update("DELETE FROM stocktake_item WHERE stocktake_id IN " + stSub);
        jdbcTemplate.update("DELETE FROM stocktake WHERE scope_project_group_id IN " + pgSub);
        jdbcTemplate.update("DELETE FROM stock_item WHERE project_group_id IN " + pgSub);
        jdbcTemplate.update("DELETE FROM user_role WHERE user_id IN "
                + "(SELECT id FROM sys_user WHERE account LIKE 'u12t\\_%')");
        jdbcTemplate.update("DELETE FROM sys_user WHERE account LIKE 'u12t\\_%'");
        jdbcTemplate.update("DELETE FROM project_group WHERE code LIKE 'U12T-%'");
        jdbcTemplate.update("DELETE FROM department WHERE code LIKE 'U12T-%'");
    }
}
