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

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * U5 预算科目树集成测试（详设 §7 T-1..T-13）。全上下文 + 本地 PostgreSQL（不依赖 Docker）。
 * 测试数据用前缀 U5T-/u5t_ 隔离，@BeforeAll/@AfterAll 物理清理，可重复、不污染。
 * 写操作需 editor 角色 → 用 editorToken；非 editor（admin）用于 40301 校验。
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIf("com.gov.procurement.support.LocalPg#available")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BudgetSubjectIntegrationTest {

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
    private String adminToken;
    private Long budgetId;

    @BeforeAll
    void setup() throws Exception {
        cleanup();
        adminToken = login("admin", "admin123");

        Long sysDept = jdbcTemplate.queryForObject(
                "SELECT id FROM department WHERE code = 'SYS' AND is_deleted = 0 ORDER BY id LIMIT 1", Long.class);
        Long editorRole = jdbcTemplate.queryForObject(
                "SELECT id FROM role WHERE code = 'editor' AND is_deleted = 0", Long.class);
        Long uid = jdbcTemplate.queryForObject(
                "INSERT INTO sys_user(name, account, password_hash, department_id) VALUES ('U5编制','u5t_editor',?,?) "
                        + "RETURNING id",
                Long.class, passwordEncoder.encode("pass123"), sysDept);
        jdbcTemplate.update("INSERT INTO user_role(user_id, role_id) VALUES (?, ?)", uid, editorRole);
        editorToken = login("u5t_editor", "pass123");

        // 预算链（供 budget_item 引用）：部门 → 项目组 → 预算
        Long deptId = jdbcTemplate.queryForObject(
                "INSERT INTO department(name, code) VALUES ('U5T部门','U5T-DEPT') RETURNING id", Long.class);
        Long pgId = jdbcTemplate.queryForObject(
                "INSERT INTO project_group(name, code, department_id) VALUES ('U5T组','U5T-PG',?) RETURNING id",
                Long.class, deptId);
        budgetId = jdbcTemplate.queryForObject(
                "INSERT INTO budget(project_group_id, name) VALUES (?, 'U5T-BUDGET') RETURNING id", Long.class, pgId);
    }

    @AfterAll
    void teardown() {
        cleanup();
    }

    // ---- 鉴权（T-12） ----

    @Test
    void t12_nonEditorWriteForbidden() throws Exception {
        mockMvc.perform(post("/api/subjects").header(AUTH, bearer(adminToken)).contentType(APPLICATION_JSON)
                        .content("{\"parentId\":null,\"name\":\"x\",\"code\":\"U5T-NOEDIT\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    // ---- 新增子级（T-4 / T-5 / T-6 / T-7 / T-13） ----

    @Test
    void t4_addChildBeyondFiveLevelsMaintainsInvariants() throws Exception {
        long l1 = addSubject(null, "U5T一级", "U5T-L1");
        long l2 = addSubject(l1, "U5T二级", "U5T-L2");
        long l3 = addSubject(l2, "U5T三级", "U5T-L3");
        long l4 = addSubject(l3, "U5T四级", "U5T-L4");
        long l5 = addSubject(l4, "U5T五级", "U5T-L5");
        long l6 = addSubject(l5, "U5T六级", "U5T-L6");
        assertEquals(6, subjectLevel(l6), "可超 5 级，新节点 level=6");
        assertTrue(subjectIsLeaf(l6), "新节点为叶子");
        assertTrue(!subjectIsLeaf(l5), "父节点应转为非叶");
    }

    @Test
    void t5_addUnderBudgetedLeafRejected() throws Exception {
        long leaf = addSubject(null, "U5T挂款叶子", "U5T-BUDGETED");
        jdbcTemplate.update("INSERT INTO budget_item(budget_id, subject_id, amount) VALUES (?, ?, 100.00)",
                budgetId, leaf);
        mockMvc.perform(post("/api/subjects").header(AUTH, bearer(editorToken)).contentType(APPLICATION_JSON)
                        .content(String.format("{\"parentId\":%d,\"name\":\"子\",\"code\":\"U5T-UNDER-BUDGETED\"}", leaf)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));
    }

    @Test
    void t6_addUnderUnbudgetedLeafTurnsParentNonLeaf() throws Exception {
        long leaf = addSubject(null, "U5T空叶子", "U5T-FREE");
        assertTrue(subjectIsLeaf(leaf));
        addSubject(leaf, "U5T子", "U5T-FREE-CHILD");
        assertTrue(!subjectIsLeaf(leaf), "未挂金额的叶子新增子级后转为非叶");
    }

    @Test
    void t7_duplicateCodeRejected() throws Exception {
        addSubject(null, "U5T重复码", "U5T-DUP");
        mockMvc.perform(post("/api/subjects").header(AUTH, bearer(editorToken)).contentType(APPLICATION_JSON)
                        .content("{\"parentId\":null,\"name\":\"再来\",\"code\":\"U5T-DUP\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40902));
    }

    @Test
    void t13_addUnderMissingParentRejected() throws Exception {
        mockMvc.perform(post("/api/subjects").header(AUTH, bearer(editorToken)).contentType(APPLICATION_JSON)
                        .content("{\"parentId\":99999999,\"name\":\"孤儿\",\"code\":\"U5T-ORPHAN\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    // ---- 树查询与金额（T-1 / T-2） ----

    @Test
    void t1_treeAmountRollsUpFromLeaves() throws Exception {
        long root = addSubject(null, "U5T汇总根", "U5T-T1ROOT");
        long leafA = addSubject(root, "U5T叶A", "U5T-T1A");
        long leafB = addSubject(root, "U5T叶B", "U5T-T1B");
        jdbcTemplate.update("INSERT INTO budget_item(budget_id, subject_id, amount) VALUES (?, ?, 100.00)", budgetId, leafA);
        jdbcTemplate.update("INSERT INTO budget_item(budget_id, subject_id, amount) VALUES (?, ?, 200.00)", budgetId, leafB);

        String body = mockMvc.perform(get("/api/subjects/tree").header(AUTH, bearer(editorToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        assertEquals(0, amountOf(data, "U5T-T1ROOT").compareTo(new BigDecimal("300")), "非叶=子树叶子之和");
        assertEquals(0, amountOf(data, "U5T-T1A").compareTo(new BigDecimal("100")), "叶子=自身预算");
        assertEquals(0, amountOf(data, "U5T-T1B").compareTo(new BigDecimal("200")));
    }

    @Test
    void t2_lazyReturnsSingleLayer() throws Exception {
        long root = addSubject(null, "U5T懒根", "U5T-LAZY");
        addSubject(root, "U5T懒子", "U5T-LAZY-C");
        String body = mockMvc.perform(get("/api/subjects/tree")
                        .param("lazy", "true").param("parentId", String.valueOf(root))
                        .header(AUTH, bearer(editorToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        assertEquals(1, data.size(), "仅返回单层");
        assertEquals("U5T-LAZY-C", data.get(0).path("code").asText());
        assertEquals(0, data.get(0).path("children").size(), "lazy 时 children 为空");
    }

    // ---- 模糊搜索（T-3） ----

    @Test
    void t3_fuzzySearchWithAncestorPathAndEmptyKeyword() throws Exception {
        long root = addSubject(null, "U5T检索根", "U5T-SR");
        long child = addSubject(root, "U5T试剂耗材", "U5T-SR-C");
        String body = mockMvc.perform(get("/api/subjects/search").param("keyword", "U5T试剂耗材")
                        .header(AUTH, bearer(editorToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode hits = objectMapper.readTree(body).path("data");
        JsonNode hit = null;
        for (JsonNode h : hits) {
            if (child == h.path("id").asLong()) {
                hit = h;
            }
        }
        assertNotNull(hit, "应命中目标科目");
        JsonNode path = hit.path("ancestorPath");
        assertEquals(2, path.size(), "祖先路径 = 根 → 命中");
        assertEquals("U5T检索根", path.get(0).path("name").asText());

        // 空 keyword → 40001
        mockMvc.perform(get("/api/subjects/search").param("keyword", "  ").header(AUTH, bearer(editorToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    // ---- 比对与确认新增（T-8 / T-9 / T-10） ----

    @Test
    void t8_compareMatchesFullPathNoCrossParentConfusion() throws Exception {
        long rootA = addSubject(null, "U5T共享A", "U5T-SHA");
        long jiaA = addSubject(rootA, "U5T甲", "U5T-SHA-J");
        long rootB = addSubject(null, "U5T共享B", "U5T-SHB");
        addSubject(rootB, "U5T甲", "U5T-SHB-J"); // 同名异父

        String body = mockMvc.perform(post("/api/subjects/compare").header(AUTH, bearer(editorToken))
                        .contentType(APPLICATION_JSON)
                        .content("{\"paths\":[[\"U5T共享A\",\"U5T甲\"]]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode r0 = objectMapper.readTree(body).path("data").get(0);
        assertEquals("EXISTS", r0.path("status").asText());
        assertEquals(jiaA, r0.path("subjectId").asLong(), "应命中 A 下的甲，不与 B 下同名混淆");
    }

    @Test
    void t9_compareMissingGivesSuggestedCode() throws Exception {
        String body = mockMvc.perform(post("/api/subjects/compare").header(AUTH, bearer(editorToken))
                        .contentType(APPLICATION_JSON)
                        .content("{\"paths\":[[\"U5T不存在域\",\"U5T不存在项\"]]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode r0 = objectMapper.readTree(body).path("data").get(0);
        assertEquals("MISSING", r0.path("status").asText());
        assertTrue(!r0.path("suggestedCode").asText().isBlank(), "缺失项应给出建议编码");
    }

    @Test
    void t10_confirmAddBuildsOnlyMissingLevels() throws Exception {
        long root = addSubject(null, "U5T确认根", "U5T-CA-ROOT"); // 已存在的根
        mockMvc.perform(post("/api/subjects/confirm-add").header(AUTH, bearer(editorToken))
                        .contentType(APPLICATION_JSON)
                        .content("{\"items\":[{\"path\":[\"U5T确认根\",\"U5T新子\",\"U5T新孙\"],"
                                + "\"codes\":[\"U5T-IGN\",\"U5T-CA-C\",\"U5T-CA-G\"]}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasSize(1)));
        // 根未被重复创建，新子/新孙已建
        Integer rootCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM budget_subject WHERE code = 'U5T-CA-ROOT' AND is_deleted = 0", Integer.class);
        assertEquals(1, rootCount, "已存在的根不应被重复创建");
        Integer built = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM budget_subject WHERE code IN ('U5T-CA-C','U5T-CA-G') AND is_deleted = 0",
                Integer.class);
        assertEquals(2, built, "仅补建缺失的子/孙两级");
        assertTrue(root > 0);
    }

    // ---- 删除（T-11） ----

    @Test
    void t11_deleteRulesAndParentLeafReset() throws Exception {
        // 被预算引用 → 40901
        long budgeted = addSubject(null, "U5T待删挂款", "U5T-DEL-BUDGETED");
        jdbcTemplate.update("INSERT INTO budget_item(budget_id, subject_id, amount) VALUES (?, ?, 50.00)", budgetId, budgeted);
        mockMvc.perform(delete("/api/subjects/" + budgeted).header(AUTH, bearer(editorToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));

        // 有子级 → 40901
        long parent = addSubject(null, "U5T待删父", "U5T-DEL-PARENT");
        long child = addSubject(parent, "U5T待删子", "U5T-DEL-CHILD");
        mockMvc.perform(delete("/api/subjects/" + parent).header(AUTH, bearer(editorToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));

        // 删可删子 → 成功，父 is_leaf 复位
        mockMvc.perform(delete("/api/subjects/" + child).header(AUTH, bearer(editorToken)))
                .andExpect(status().isOk());
        assertTrue(subjectIsLeaf(parent), "删除唯一子级后父复位为叶子");
    }

    // ---- helpers ----

    private long addSubject(Long parentId, String name, String code) throws Exception {
        String body = mockMvc.perform(post("/api/subjects").header(AUTH, bearer(editorToken))
                        .contentType(APPLICATION_JSON)
                        .content(String.format("{\"parentId\":%s,\"name\":\"%s\",\"code\":\"%s\"}",
                                parentId == null ? "null" : parentId, name, code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").asLong();
    }

    private int subjectLevel(long id) {
        return jdbcTemplate.queryForObject("SELECT level FROM budget_subject WHERE id = ?", Integer.class, id);
    }

    private boolean subjectIsLeaf(long id) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT is_leaf FROM budget_subject WHERE id = ?", Boolean.class, id));
    }

    private BigDecimal amountOf(JsonNode nodes, String code) {
        for (JsonNode n : nodes) {
            if (code.equals(n.path("code").asText())) {
                return new BigDecimal(n.path("amount").asText());
            }
            BigDecimal found = amountOf(n.path("children"), code);
            if (found != null) {
                return found;
            }
        }
        return null;
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
        jdbcTemplate.update("DELETE FROM budget_item WHERE budget_id IN (SELECT id FROM budget WHERE name LIKE 'U5T-%')");
        jdbcTemplate.update("DELETE FROM budget WHERE name LIKE 'U5T-%'");
        jdbcTemplate.update("DELETE FROM project_group WHERE code LIKE 'U5T-%'");
        jdbcTemplate.update("DELETE FROM department WHERE code LIKE 'U5T-%'");
        jdbcTemplate.update("DELETE FROM budget_subject WHERE code LIKE 'U5T-%'");
        jdbcTemplate.update("DELETE FROM user_role WHERE user_id IN "
                + "(SELECT id FROM sys_user WHERE account LIKE 'u5t\\_%')");
        jdbcTemplate.update("DELETE FROM sys_user WHERE account LIKE 'u5t\\_%'");
    }
}
