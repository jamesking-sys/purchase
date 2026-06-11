package com.gov.procurement.modules.budget;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * U13 预算 vs 实际集成测试（详设 §7 T-1..T-7）。全上下文 + 本地 PostgreSQL（无库优雅跳过）。
 * 测试数据用前缀 U13T-/u13t_ 隔离，{@code @BeforeAll}/{@code @AfterAll} 物理清理。
 *
 * <p>只读读模型：超支仅标识不拦截；金额两位小数；实际侧仅统计同一预算的采购。错误码：参数非法 40001、
 * 无查看角色 40301、未登录 40110、预算不存在 40401。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIf("com.gov.procurement.support.LocalPg#available")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BudgetVsActualIntegrationTest {

    private static final String AUTH = "Authorization";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    BCryptPasswordEncoder passwordEncoder;
    @Autowired
    ObjectMapper objectMapper;

    private String editorToken;
    private String warehouseToken;
    private long sysDept;

    @BeforeAll
    void setup() throws Exception {
        cleanup();
        sysDept = jdbcTemplate.queryForObject(
                "SELECT id FROM department WHERE code = 'SYS' AND is_deleted = 0 ORDER BY id LIMIT 1", Long.class);
        editorToken = createUserWithRole("u13t_ed", "editor", sysDept);
        warehouseToken = createUserWithRole("u13t_wh", "warehouse", sysDept);
    }

    @AfterAll
    void teardown() {
        cleanup();
    }

    // ---- T-1 正常对比 + 按科目聚合（含跨多张采购单/多明细的实际侧汇总）----

    @Test
    void t1_compare() throws Exception {
        long pg = newPg("U13T-PG1");
        long subA = newSubject("计算节点", "U13T-A");
        long subB = newSubject("存储节点", "U13T-B");
        long budget = newBudget(pg, "U13T-budget1");
        addBudgetItem(budget, subA, "120000.00");
        addBudgetItem(budget, subB, "66000.00");
        // 实际：A 52400（单 PO 单明细）；B 71000（跨 2 张 PO，40000 + 31000）
        long po1 = newPo(budget, pg);
        addPurchaseItem(po1, subA, "U13T-mA", "52400.00");
        addPurchaseItem(po1, subB, "U13T-mB1", "40000.00");
        long po2 = newPo(budget, pg);
        addPurchaseItem(po2, subB, "U13T-mB2", "31000.00");

        JsonNode data = queryData(budget, editorToken);
        assertEquals("U13T-budget1", data.path("budgetName").asText());
        assertEquals(2, data.path("rows").size());

        JsonNode a = rowFor(data, subA);
        assertEquals("计算节点", a.path("subjectName").asText());
        assertEquals("U13T-A", a.path("subjectCode").asText());
        assertEquals(120000.0, a.path("budgeted").asDouble());
        assertEquals(52400.0, a.path("actual").asDouble());
        assertEquals(67600.0, a.path("remaining").asDouble());
        assertFalse(a.path("overspent").asBoolean());

        JsonNode b = rowFor(data, subB);
        assertEquals(66000.0, b.path("budgeted").asDouble());
        assertEquals(71000.0, b.path("actual").asDouble(), "B 实际跨 2 张采购单汇总");
        assertEquals(-5000.0, b.path("remaining").asDouble());
        assertTrue(b.path("overspent").asBoolean());

        assertEquals(186000.0, data.path("totalBudgeted").asDouble());
        assertEquals(123400.0, data.path("totalActual").asDouble());
        assertEquals(62600.0, data.path("totalRemaining").asDouble());
    }

    // ---- T-2 超支不拦截 + 只读无副作用 ----

    @Test
    void t2_overspentNotBlockedReadOnly() throws Exception {
        long pg = newPg("U13T-PG2");
        long sub = newSubject("超支科目", "U13T-OS");
        long budget = newBudget(pg, "U13T-budget2");
        addBudgetItem(budget, sub, "1000.00");
        long po = newPo(budget, pg);
        addPurchaseItem(po, sub, "U13T-os", "1500.00");

        int biBefore = count("budget_item");
        int piBefore = count("purchase_item");

        JsonNode data = queryData(budget, editorToken);   // 内部已断言 200/code=0
        JsonNode row = rowFor(data, sub);
        assertEquals(-500.0, row.path("remaining").asDouble());
        assertTrue(row.path("overspent").asBoolean());

        assertEquals(biBefore, count("budget_item"), "只读：budget_item 不变");
        assertEquals(piBefore, count("purchase_item"), "只读：purchase_item 不变");
    }

    // ---- T-3 有预算无采购 → actual=0，仍在 rows 中 ----

    @Test
    void t3_noPurchaseActualZero() throws Exception {
        long pg = newPg("U13T-PG3");
        long sub = newSubject("无采购科目", "U13T-NP");
        long budget = newBudget(pg, "U13T-budget3");
        addBudgetItem(budget, sub, "5000.00");

        JsonNode data = queryData(budget, editorToken);
        JsonNode row = rowFor(data, sub);
        assertEquals(0.0, row.path("actual").asDouble());
        assertEquals(5000.0, row.path("remaining").asDouble());
        assertFalse(row.path("overspent").asBoolean());
    }

    // ---- T-4 跨多明细聚合，不同科目互不串 ----

    @Test
    void t4_aggregationPerSubject() throws Exception {
        long pg = newPg("U13T-PG4");
        long subX = newSubject("X", "U13T-X");
        long subY = newSubject("Y", "U13T-Y");
        long budget = newBudget(pg, "U13T-budget4");
        addBudgetItem(budget, subX, "10000.00");
        addBudgetItem(budget, subY, "10000.00");
        long po = newPo(budget, pg);
        addPurchaseItem(po, subX, "U13T-x1", "3000.00");
        addPurchaseItem(po, subX, "U13T-x2", "2000.00");   // X 两条 → 5000
        addPurchaseItem(po, subY, "U13T-y1", "7000.00");   // Y 一条 → 7000

        JsonNode data = queryData(budget, editorToken);
        assertEquals(5000.0, rowFor(data, subX).path("actual").asDouble(), "X 两明细汇总");
        assertEquals(7000.0, rowFor(data, subY).path("actual").asDouble(), "Y 独立不串 X");
    }

    // ---- T-5 实际侧仅同预算（AC-8）----

    @Test
    void t5_actualIsolatedByBudget() throws Exception {
        long pg = newPg("U13T-PG5");
        long sub = newSubject("共享科目", "U13T-SH");
        long budget1 = newBudget(pg, "U13T-budget5a");
        long budget2 = newBudget(pg, "U13T-budget5b");
        addBudgetItem(budget1, sub, "20000.00");
        addBudgetItem(budget2, sub, "20000.00");
        addPurchaseItem(newPo(budget1, pg), sub, "U13T-sh1", "1000.00");
        addPurchaseItem(newPo(budget2, pg), sub, "U13T-sh2", "9999.00");

        JsonNode data = queryData(budget1, editorToken);
        assertEquals(1000.0, rowFor(data, sub).path("actual").asDouble(),
                "仅统计本预算采购，不串他预算的 9999");
    }

    // ---- T-6 预算不存在 → 40401 ----

    @Test
    void t6_budgetNotFound() throws Exception {
        mockMvc.perform(get("/api/budgets/99999999/vs-actual").header(AUTH, bearer(editorToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    // ---- T-7 参数非法 / 鉴权 ----

    @Test
    void t7_paramAndAuth() throws Exception {
        // id 非正整数 → 40001（@Positive 经 ConstraintViolation 映射）
        mockMvc.perform(get("/api/budgets/0/vs-actual").header(AUTH, bearer(editorToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        // id 非数字 → 40001（MethodArgumentTypeMismatch 映射，避免落 50000 兜底）
        mockMvc.perform(get("/api/budgets/abc/vs-actual").header(AUTH, bearer(editorToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        // 无查看角色（warehouse 不在允许集）→ 40301
        mockMvc.perform(get("/api/budgets/1/vs-actual").header(AUTH, bearer(warehouseToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
        // 未登录 → 401/40110
        mockMvc.perform(get("/api/budgets/1/vs-actual"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40110));
    }

    // ---- helpers ----

    private JsonNode queryData(long budgetId, String token) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/budgets/" + budgetId + "/vs-actual").header(AUTH, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data");
    }

    private JsonNode rowFor(JsonNode data, long subjectId) {
        for (JsonNode r : data.path("rows")) {
            if (r.path("subjectId").asLong() == subjectId) {
                return r;
            }
        }
        throw new IllegalStateException("rows 缺科目 " + subjectId);
    }

    private long newPg(String code) {
        long dept = jdbcTemplate.queryForObject(
                "INSERT INTO department(name, code) VALUES (?, ?) RETURNING id", Long.class, code + "-D", code + "-D");
        return jdbcTemplate.queryForObject(
                "INSERT INTO project_group(name, code, department_id) VALUES (?, ?, ?) RETURNING id",
                Long.class, code, code, dept);
    }

    private long newSubject(String name, String code) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO budget_subject(name, code, level, is_leaf) VALUES (?, ?, 1, true) RETURNING id",
                Long.class, name, code);
    }

    private long newBudget(long pg, String name) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO budget(project_group_id, name, status) VALUES (?, ?, 'approved') RETURNING id",
                Long.class, pg, name);
    }

    private void addBudgetItem(long budgetId, long subjectId, String amount) {
        jdbcTemplate.update("INSERT INTO budget_item(budget_id, subject_id, amount) VALUES (?, ?, ?)",
                budgetId, subjectId, new BigDecimal(amount));
    }

    private long newPo(long budgetId, long pg) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO purchase_order(budget_id, project_group_id, status) VALUES (?, ?, 'executing') RETURNING id",
                Long.class, budgetId, pg);
    }

    private void addPurchaseItem(long poId, long subjectId, String material, String amount) {
        jdbcTemplate.update(
                "INSERT INTO purchase_item(purchase_order_id, subject_id, material_name, qty, amount) "
                        + "VALUES (?, ?, ?, 1, ?)", poId, subjectId, material, new BigDecimal(amount));
    }

    private int count(String table) {
        Integer n = jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
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
        String pgSub = "(SELECT id FROM project_group WHERE code LIKE 'U13T-%')";
        String budgetSub = "(SELECT id FROM budget WHERE project_group_id IN " + pgSub + ")";
        String poSub = "(SELECT id FROM purchase_order WHERE budget_id IN " + budgetSub + ")";
        jdbcTemplate.update("DELETE FROM purchase_item WHERE purchase_order_id IN " + poSub);
        jdbcTemplate.update("DELETE FROM purchase_order WHERE budget_id IN " + budgetSub);
        jdbcTemplate.update("DELETE FROM budget_item WHERE budget_id IN " + budgetSub);
        jdbcTemplate.update("DELETE FROM budget WHERE project_group_id IN " + pgSub);
        jdbcTemplate.update("DELETE FROM budget_subject WHERE code LIKE 'U13T-%'");
        jdbcTemplate.update("DELETE FROM user_role WHERE user_id IN "
                + "(SELECT id FROM sys_user WHERE account LIKE 'u13t\\_%')");
        jdbcTemplate.update("DELETE FROM sys_user WHERE account LIKE 'u13t\\_%'");
        jdbcTemplate.update("DELETE FROM project_group WHERE code LIKE 'U13T-%'");
        jdbcTemplate.update("DELETE FROM department WHERE code LIKE 'U13T-%'");
    }
}
