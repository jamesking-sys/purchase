package com.gov.procurement.modules.requisition;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * U11 领用 + 仓管审批出库集成测试（详设 §7 T-1..T-13）。全上下文 + 本地 PostgreSQL（无库优雅跳过）。
 * 测试数据用前缀 U11T-/u11t_ 隔离，{@code @BeforeAll}/{@code @AfterAll} 物理清理。
 *
 * <p>错误码采用编码归一口径：状态不符（已处理）40903；库存不足（防超发）40904；驳回意见必填 42203；
 * 对象不存在 40401；参数非法 40001；越权 40301。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIf("com.gov.procurement.support.LocalPg#available")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RequisitionIntegrationTest {

    private static final String AUTH = "Authorization";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    BCryptPasswordEncoder passwordEncoder;
    @Autowired
    ObjectMapper objectMapper;

    private String requesterToken;
    private String warehouseToken;
    private String adminToken;
    private long projectGroupId;
    private long departmentId;

    @BeforeAll
    void setup() throws Exception {
        cleanup();
        adminToken = login("admin", "admin123");
        Long sysDept = jdbcTemplate.queryForObject(
                "SELECT id FROM department WHERE code = 'SYS' AND is_deleted = 0 ORDER BY id LIMIT 1", Long.class);
        requesterToken = createUserWithRole("u11t_req", "requester", sysDept);
        warehouseToken = createUserWithRole("u11t_wh", "warehouse", sysDept);

        departmentId = jdbcTemplate.queryForObject(
                "INSERT INTO department(name, code) VALUES ('U11T部门','U11T-DEPT') RETURNING id", Long.class);
        projectGroupId = jdbcTemplate.queryForObject(
                "INSERT INTO project_group(name, code, department_id) VALUES ('U11T组','U11T-PG',?) RETURNING id",
                Long.class, departmentId);
    }

    @AfterAll
    void teardown() {
        cleanup();
    }

    // ---- T-1 发起领用 ----

    @Test
    void t1_createRequisition() throws Exception {
        long stock = newStock("U11T-S1", 10);
        MvcResult res = createReq(requesterToken, "项目调试用", item(stock, 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("pending_warehouse"))
                .andReturn();
        long reqId = dataId(res);
        assertEquals("pending_warehouse", reqStatus(reqId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM requisition_item WHERE requisition_id = ?", Integer.class, reqId));
    }

    // ---- T-2 参数非法 ----

    @Test
    void t2_invalidParams() throws Exception {
        long stock = newStock("U11T-S2", 10);
        createReq(requesterToken, "x", item(stock, 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(post("/api/requisitions").header(AUTH, bearer(requesterToken)).contentType(APPLICATION_JSON)
                        .content(String.format("{\"projectGroupId\":%d,\"items\":[]}", projectGroupId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    // ---- T-3 待办查询 ----

    @Test
    void t3_warehouseTodo() throws Exception {
        long stock = newStock("U11T-S3", 5);
        long reqId = createReqId(requesterToken, "待办用途", item(stock, 2));
        boolean found = false;
        String body = mockMvc.perform(get("/api/requisitions/todo").param("size", "100")
                        .header(AUTH, bearer(warehouseToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var records = objectMapper.readTree(body).path("data").path("records");
        for (var node : records) {
            if (node.path("id").asLong() == reqId) {
                found = true;
                assertEquals("U11T-S3", node.path("items").get(0).path("materialName").asText());
                assertEquals(5.0, node.path("items").get(0).path("currentQuantity").asDouble());
                org.junit.jupiter.api.Assertions.assertTrue(node.path("items").get(0).path("enough").asBoolean());
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(found, "待办应含该 pending 领用单");
    }

    // ---- T-4 正常出库扣减 ----

    @Test
    void t4_approveOutboundDeducts() throws Exception {
        long stock = newStock("U11T-S4", 40);
        long reqId = createReqId(requesterToken, "出库", item(stock, 3));
        approve(reqId, warehouseToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("outbound"));

        assertEquals(37.0, stockQty(stock));
        assertEquals(1, txnCount(stock, "outbound"));
        assertEquals("outbound", reqStatus(reqId));
        assertEquals(1, outboundCount(reqId));

        // 详情含出库单
        mockMvc.perform(get("/api/requisitions/" + reqId).header(AUTH, bearer(warehouseToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outbound.items[0].qty").value(3));
    }

    // ---- T-5 库存不足拒绝（回滚）----

    @Test
    void t5_insufficientStockRejected() throws Exception {
        long stock = newStock("U11T-S5", 2);
        long reqId = createReqId(requesterToken, "超发", item(stock, 3));
        approve(reqId, warehouseToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40904));

        assertEquals(2.0, stockQty(stock), "库存不足应回滚，库存不变");
        assertEquals(0, txnCount(stock, "outbound"));
        assertEquals(0, outboundCount(reqId));
        assertEquals("pending_warehouse", reqStatus(reqId));
    }

    // ---- T-6 并发出库防超发 ----

    @Test
    void t6_concurrentOutboundNoOversell() throws Exception {
        long stock = newStock("U11T-S6", 2);
        long r1 = createReqId(requesterToken, "并发1", item(stock, 2));
        long r2 = createReqId(requesterToken, "并发2", item(stock, 2));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> c1 = () -> approve(r1, warehouseToken).andReturn().getResponse().getStatus();
            Callable<Integer> c2 = () -> approve(r2, warehouseToken).andReturn().getResponse().getStatus();
            Future<Integer> f1 = pool.submit(c1);
            Future<Integer> f2 = pool.submit(c2);
            int s1 = f1.get(20, TimeUnit.SECONDS);
            int s2 = f2.get(20, TimeUnit.SECONDS);
            assertEquals(1, (s1 == 200 ? 1 : 0) + (s2 == 200 ? 1 : 0), "应恰有一笔出库成功");
            assertEquals(1, (s1 == 409 ? 1 : 0) + (s2 == 409 ? 1 : 0), "另一笔应因库存不足被拒（40904）");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(0.0, stockQty(stock), "合计扣减不超库存，且不为负");
        assertEquals(1, txnCount(stock, "outbound"), "仅一笔成功出库写流水");
    }

    // ---- T-7 DB CHECK 兜底（quantity>=0）----

    @Test
    void t7_checkConstraintBackstop() {
        long stock = newStock("U11T-S7", 1);
        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("UPDATE stock_item SET quantity = quantity - 5 WHERE id = ?", stock));
        assertEquals(1.0, stockQty(stock), "CHECK(quantity>=0) 拒绝负库存，库存不变");
    }

    // ---- T-8 驳回填意见 ----

    @Test
    void t8_rejectWithOpinion() throws Exception {
        long stock = newStock("U11T-S8", 5);
        long reqId = createReqId(requesterToken, "待驳回", item(stock, 2));
        reject(reqId, "库存紧张，暂缓", warehouseToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("rejected"));
        assertEquals("rejected", reqStatus(reqId));
        assertEquals("库存紧张，暂缓", jdbcTemplate.queryForObject(
                "SELECT reject_opinion FROM requisition WHERE id = ?", String.class, reqId));
    }

    // ---- T-9 驳回未填意见 ----

    @Test
    void t9_rejectWithoutOpinion() throws Exception {
        long stock = newStock("U11T-S9", 5);
        long reqId = createReqId(requesterToken, "空意见", item(stock, 2));
        reject(reqId, "   ", warehouseToken)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42203));
        assertEquals("pending_warehouse", reqStatus(reqId));
    }

    // ---- T-10 / T-11 重复处理 / 二次出库 ----

    @Test
    void t10_duplicateHandlingRejected() throws Exception {
        long stock = newStock("U11T-S10", 10);
        long reqId = createReqId(requesterToken, "重复", item(stock, 2));
        approve(reqId, warehouseToken).andExpect(status().isOk());
        // 已 outbound，再次审批 → 40903
        approve(reqId, warehouseToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40903));
        // 再次驳回 → 40903
        reject(reqId, "x", warehouseToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40903));
        assertEquals(1, outboundCount(reqId), "不生成第二张出库单");
    }

    // ---- T-12 越权 ----

    @Test
    void t12_roleEnforcement() throws Exception {
        long stock = newStock("U11T-S12", 10);
        // 非 requester 发起 → 40301
        createReq(warehouseToken, "越权发起", item(stock, 1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
        long reqId = createReqId(requesterToken, "越权审批", item(stock, 1));
        // 非 warehouse 审批 → 40301
        approve(reqId, requesterToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    // ---- T-13 库存项不存在 ----

    @Test
    void t13_stockItemNotFound() throws Exception {
        createReq(requesterToken, "坏库存项", item(99999999L, 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    // ---- T-14 领用可选库存查询（stock-options，requester 可见）----

    @Test
    void t14_stockOptions() throws Exception {
        newStock("U11T-OPT-b", 8);
        newStock("U11T-OPT-a", 5);

        // requester 可见，按物料名升序
        String body = mockMvc.perform(get("/api/requisitions/stock-options")
                        .param("projectGroupId", String.valueOf(projectGroupId))
                        .header(AUTH, bearer(requesterToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        var arr = objectMapper.readTree(body).path("data");
        boolean foundA = false;
        boolean foundB = false;
        for (var n : arr) {
            if ("U11T-OPT-a".equals(n.path("materialName").asText())) {
                foundA = true;
                assertEquals(5.0, n.path("quantity").asDouble());
            }
            if ("U11T-OPT-b".equals(n.path("materialName").asText())) {
                foundB = true;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(foundA && foundB, "应含本项目组的库存项");

        // 项目组不存在 → 40401
        mockMvc.perform(get("/api/requisitions/stock-options").param("projectGroupId", "99999999")
                        .header(AUTH, bearer(requesterToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        // 非 requester（warehouse）→ 40301
        mockMvc.perform(get("/api/requisitions/stock-options")
                        .param("projectGroupId", String.valueOf(projectGroupId))
                        .header(AUTH, bearer(warehouseToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    // ---- helpers ----

    private long newStock(String material, int qty) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO stock_item(material_name, project_group_id, department_id, quantity) "
                        + "VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, material, projectGroupId, departmentId, qty);
    }

    private Map<String, Object> item(long stockItemId, Object qty) {
        return Map.of("stockItemId", stockItemId, "qty", qty);
    }

    @SafeVarargs
    private ResultActions createReq(String token, String purpose, Map<String, Object>... items) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "projectGroupId", projectGroupId, "purpose", purpose, "items", List.of(items)));
        return mockMvc.perform(post("/api/requisitions").header(AUTH, bearer(token))
                .contentType(APPLICATION_JSON).content(body));
    }

    @SafeVarargs
    private long createReqId(String token, String purpose, Map<String, Object>... items) throws Exception {
        return dataId(createReq(token, purpose, items).andExpect(status().isOk()).andReturn());
    }

    private ResultActions approve(long reqId, String token) throws Exception {
        return mockMvc.perform(post("/api/requisitions/" + reqId + "/approve-outbound").header(AUTH, bearer(token)));
    }

    private ResultActions reject(long reqId, String opinion, String token) throws Exception {
        return mockMvc.perform(post("/api/requisitions/" + reqId + "/reject").header(AUTH, bearer(token))
                .contentType(APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("opinion", opinion))));
    }

    private long dataId(MvcResult res) throws Exception {
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private Double stockQty(long stockItemId) {
        return jdbcTemplate.queryForObject("SELECT quantity FROM stock_item WHERE id = ?", Double.class, stockItemId);
    }

    private String reqStatus(long reqId) {
        return jdbcTemplate.queryForObject("SELECT status FROM requisition WHERE id = ?", String.class, reqId);
    }

    private int txnCount(long stockItemId, String type) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM stock_txn WHERE stock_item_id = ? AND type = ?", Integer.class, stockItemId, type);
        return n == null ? 0 : n;
    }

    private int outboundCount(long reqId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbound_order WHERE requisition_id = ?", Integer.class, reqId);
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
        String pgSub = "(SELECT id FROM project_group WHERE code LIKE 'U11T-%')";
        String reqSub = "(SELECT id FROM requisition WHERE project_group_id IN " + pgSub + ")";
        String stockSub = "(SELECT id FROM stock_item WHERE project_group_id IN " + pgSub + ")";
        jdbcTemplate.update("DELETE FROM outbound_item WHERE outbound_order_id IN "
                + "(SELECT id FROM outbound_order WHERE requisition_id IN " + reqSub + ")");
        jdbcTemplate.update("DELETE FROM outbound_order WHERE requisition_id IN " + reqSub);
        jdbcTemplate.update("DELETE FROM requisition_item WHERE requisition_id IN " + reqSub);
        jdbcTemplate.update("DELETE FROM requisition WHERE project_group_id IN " + pgSub);
        jdbcTemplate.update("DELETE FROM stock_txn WHERE stock_item_id IN " + stockSub);
        jdbcTemplate.update("DELETE FROM stock_item WHERE project_group_id IN " + pgSub);
        jdbcTemplate.update("DELETE FROM user_role WHERE user_id IN "
                + "(SELECT id FROM sys_user WHERE account LIKE 'u11t\\_%')");
        jdbcTemplate.update("DELETE FROM sys_user WHERE account LIKE 'u11t\\_%'");
        jdbcTemplate.update("DELETE FROM project_group WHERE code LIKE 'U11T-%'");
        jdbcTemplate.update("DELETE FROM department WHERE code LIKE 'U11T-%'");
    }
}
