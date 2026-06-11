package com.gov.procurement.modules.approval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.engine.RuntimeService;
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
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * U7 通用审批集成测试（详设 §7 T-1..T-11）。全上下文 + 本地 PostgreSQL（不依赖 Docker，无库则优雅跳过）。
 * 测试数据用前缀 U7T-/u7t_ 隔离，{@code @BeforeAll}/{@code @AfterAll} 物理清理（含结束遗留流程实例），保证可重复。
 *
 * <p>错误码采用编码阶段统一后的口径（详设草稿 §8 / TBD-7）：节点-角色不符 40301；状态冲突/任务已处理 40903；
 * 驳回意见必填 42203；对象不存在 40401。</p>
 *
 * <p>T-12（TaskListener 内写库异常引发引擎+投影一并回滚）依赖故障注入，其原子性由 {@code @Transactional} +
 * Flowable 与 Spring 共享事务结构性保证（详设 §5.3），此处不做自动化故障模拟，留代码审查核验。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIf("com.gov.procurement.support.LocalPg#available")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApprovalIntegrationTest {

    private static final String AUTH = "Authorization";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    BCryptPasswordEncoder passwordEncoder;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    RuntimeService runtimeService;

    private String editorToken;
    private String pmToken;
    private String dmToken;
    private long projectGroupId;

    @BeforeAll
    void setup() throws Exception {
        cleanup();
        Long sysDept = jdbcTemplate.queryForObject(
                "SELECT id FROM department WHERE code = 'SYS' AND is_deleted = 0 ORDER BY id LIMIT 1", Long.class);
        editorToken = createUserWithRole("u7t_editor", "editor", sysDept);
        pmToken = createUserWithRole("u7t_pm", "purchase_mgr", sysDept);
        dmToken = createUserWithRole("u7t_dm", "dept_mgr", sysDept);

        Long deptId = jdbcTemplate.queryForObject(
                "INSERT INTO department(name, code) VALUES ('U7T部门','U7T-DEPT') RETURNING id", Long.class);
        projectGroupId = jdbcTemplate.queryForObject(
                "INSERT INTO project_group(name, code, department_id) VALUES ('U7T组','U7T-PG',?) RETURNING id",
                Long.class, deptId);
    }

    @AfterAll
    void teardown() {
        cleanup();
    }

    // ---- T-1 两级全通过 ----

    @Test
    void t1_fullApprove() throws Exception {
        long budgetId = newBudget("U7T全通过");
        long approvalId = submit(budgetId, editorToken);
        approve(approvalId, pmToken).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        approve(approvalId, dmToken).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        assertEquals("approved", approvalStatus(approvalId));
        assertEquals("approved", budgetStatus(budgetId));
        assertEquals(2, recordCount(approvalId, "approve"));
        assertEquals(0, recordCount(approvalId, "reject"));
    }

    // ---- T-2 节点一驳回 ----

    @Test
    void t2_rejectAtNode1() throws Exception {
        long budgetId = newBudget("U7T节点一驳回");
        long approvalId = submit(budgetId, editorToken);
        reject(approvalId, "科目划分不合理，请修订后重提", pmToken)
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        assertEquals("draft", approvalStatus(approvalId));
        assertEquals("draft", budgetStatus(budgetId));
        assertEquals(1, recordCount(approvalId, "reject"));
        assertTrue(processInstanceEnded(approvalId), "驳回应结束流程实例");
    }

    // ---- T-3 节点二驳回 ----

    @Test
    void t3_rejectAtNode2() throws Exception {
        long budgetId = newBudget("U7T节点二驳回");
        long approvalId = submit(budgetId, editorToken);
        approve(approvalId, pmToken).andExpect(status().isOk());
        reject(approvalId, "预算超标，请压缩后重提", dmToken).andExpect(status().isOk());

        assertEquals("draft", approvalStatus(approvalId));
        assertEquals("draft", budgetStatus(budgetId));
        assertEquals(1, recordCount(approvalId, "approve"));
        assertEquals(1, recordCount(approvalId, "reject"));
    }

    // ---- T-4 驳回未填意见 ----

    @Test
    void t4_rejectWithoutOpinion() throws Exception {
        long budgetId = newBudget("U7T空意见");
        long approvalId = submit(budgetId, editorToken);
        reject(approvalId, "   ", pmToken)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42203));

        // 流程不动、无 record、仍待审
        assertEquals("pending_purchase_mgr", approvalStatus(approvalId));
        assertEquals(0, recordCount(approvalId, "reject"));
    }

    // ---- T-5 驳回重提为新实例 ----

    @Test
    void t5_resubmitNewInstance() throws Exception {
        long budgetId = newBudget("U7T重提");
        long approvalId = submit(budgetId, editorToken);
        String firstPi = processInstanceId(approvalId);
        reject(approvalId, "请修订重提", pmToken).andExpect(status().isOk());

        long approvalId2 = submit(budgetId, editorToken);
        assertEquals(approvalId, approvalId2, "重提应复用同一 approval 行，累积历史");
        String secondPi = processInstanceId(approvalId);
        assertNotEquals(firstPi, secondPi, "重提应启动新的流程实例");
        assertEquals("pending_purchase_mgr", approvalStatus(approvalId));
        // 历史累积：1 条 reject（旧实例）保留
        assertEquals(1, recordCount(approvalId, "reject"));
    }

    // ---- T-6 错误角色 ----

    @Test
    void t6_wrongRoleRejected() throws Exception {
        long budgetId = newBudget("U7T错角色");
        long approvalId = submit(budgetId, editorToken);
        // 节点一（采购主管）用部门主管处理 → 40301，流程不动
        approve(approvalId, dmToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        assertEquals("pending_purchase_mgr", approvalStatus(approvalId));
        assertEquals(0, recordCount(approvalId, "approve"));
    }

    // ---- T-7 待办按角色 ----

    @Test
    void t7_todoByRole() throws Exception {
        long budgetId = newBudget("U7T待办");
        long approvalId = submit(budgetId, editorToken);

        // 编制人无 pm/dm 角色 → 待办接口 403
        mockMvc.perform(get("/api/approvals/todo").header(AUTH, bearer(editorToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        assertTrue(todoContains(pmToken, approvalId), "节点一待审应出现在采购主管待办");
        assertFalse(todoContains(dmToken, approvalId), "节点一待审不应出现在部门主管待办");

        approve(approvalId, pmToken).andExpect(status().isOk());
        assertFalse(todoContains(pmToken, approvalId), "推进后采购主管待办不应再有该单");
        assertTrue(todoContains(dmToken, approvalId), "推进后应出现在部门主管待办");
    }

    // ---- T-8 任务已处理幂等 ----

    @Test
    void t8_alreadyHandledRejected() throws Exception {
        long budgetId = newBudget("U7T已处理");
        long approvalId = submit(budgetId, editorToken);
        approve(approvalId, pmToken).andExpect(status().isOk());
        approve(approvalId, dmToken).andExpect(status().isOk());
        // 已终审，再次处理 → 40903，无重复翻转
        approve(approvalId, dmToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40903));

        assertEquals("approved", approvalStatus(approvalId));
        assertEquals(2, recordCount(approvalId, "approve"));
    }

    // ---- T-9 重复提交已提交预算 ----

    @Test
    void t9_resubmitSubmittedBudget() throws Exception {
        long budgetId = newBudget("U7T重复提交");
        submit(budgetId, editorToken);
        // 预算已 submitted，再次提交 → 40903
        submitRaw(budgetId, editorToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40903));
        assertEquals("submitted", budgetStatus(budgetId));
    }

    // ---- T-10 流转历史完整 ----

    @Test
    void t10_historyComplete() throws Exception {
        long budgetId = newBudget("U7T历史");
        long approvalId = submit(budgetId, editorToken);
        reject(approvalId, "第一轮驳回", pmToken).andExpect(status().isOk());
        submit(budgetId, editorToken);
        approve(approvalId, pmToken).andExpect(status().isOk());
        approve(approvalId, dmToken).andExpect(status().isOk());

        String body = mockMvc.perform(get("/api/approvals/" + approvalId + "/history")
                        .header(AUTH, bearer(editorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasSize(3)))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        // 升序：驳回 → 采购主管通过 → 部门主管通过
        assertEquals("reject", data.get(0).path("action").asText());
        assertEquals("purchase_mgr", data.get(0).path("node").asText());
        assertEquals("approve", data.get(1).path("action").asText());
        assertEquals("purchase_mgr", data.get(1).path("node").asText());
        assertEquals("approve", data.get(2).path("action").asText());
        assertEquals("dept_mgr", data.get(2).path("node").asText());
        assertFalse(data.get(0).path("approverName").asText().isBlank(), "历史应含处理人姓名");
    }

    // ---- T-11 对象不存在 ----

    @Test
    void t11_objectNotFound() throws Exception {
        long missing = 99999999L;
        submitRaw(missing, editorToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
        approve(missing, pmToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
        mockMvc.perform(get("/api/approvals/" + missing + "/history").header(AUTH, bearer(editorToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    // ---- helpers ----

    private long submit(long budgetId, String token) throws Exception {
        String body = submitRaw(budgetId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").asLong();
    }

    private ResultActions submitRaw(long budgetId, String token) throws Exception {
        return mockMvc.perform(post("/api/approvals").header(AUTH, bearer(token)).contentType(APPLICATION_JSON)
                .content(String.format("{\"bizType\":\"budget\",\"bizId\":%d}", budgetId)));
    }

    private ResultActions approve(long approvalId, String token) throws Exception {
        return mockMvc.perform(post("/api/approvals/" + approvalId + "/approve").header(AUTH, bearer(token)));
    }

    private ResultActions reject(long approvalId, String opinion, String token) throws Exception {
        return mockMvc.perform(post("/api/approvals/" + approvalId + "/reject").header(AUTH, bearer(token))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("opinion", opinion))));
    }

    private boolean todoContains(String token, long approvalId) throws Exception {
        String body = mockMvc.perform(get("/api/approvals/todo").param("size", "100").header(AUTH, bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode records = objectMapper.readTree(body).path("data").path("records");
        for (JsonNode node : records) {
            if (node.path("approvalId").asLong() == approvalId) {
                return true;
            }
        }
        return false;
    }

    private long newBudget(String name) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO budget(project_group_id, name, status) VALUES (?, ?, 'draft') RETURNING id",
                Long.class, projectGroupId, name);
    }

    private String approvalStatus(long approvalId) {
        return jdbcTemplate.queryForObject("SELECT status FROM approval WHERE id = ?", String.class, approvalId);
    }

    private String budgetStatus(long budgetId) {
        return jdbcTemplate.queryForObject("SELECT status FROM budget WHERE id = ?", String.class, budgetId);
    }

    private String processInstanceId(long approvalId) {
        return jdbcTemplate.queryForObject(
                "SELECT process_instance_id FROM approval WHERE id = ?", String.class, approvalId);
    }

    private boolean processInstanceEnded(long approvalId) {
        String pi = processInstanceId(approvalId);
        // 驳回置空 pi；或 pi 仍在但运行实例已结束
        return pi == null || runtimeService.createProcessInstanceQuery().processInstanceId(pi).count() == 0;
    }

    private int recordCount(long approvalId, String action) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM approval_record WHERE approval_id = ? AND action = ?",
                Integer.class, approvalId, action);
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
        // 结束遗留运行中流程实例，避免污染待办计数
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT process_instance_id FROM approval "
                        + "WHERE biz_id IN (SELECT id FROM budget WHERE name LIKE 'U7T%')");
        for (Map<String, Object> row : rows) {
            Object pi = row.get("process_instance_id");
            if (pi != null) {
                try {
                    runtimeService.deleteProcessInstance(pi.toString(), "U7 test cleanup");
                } catch (RuntimeException ignored) {
                    // 实例已结束/不存在，清理尽力而为
                }
            }
        }
        jdbcTemplate.update("DELETE FROM approval_record WHERE approval_id IN "
                + "(SELECT id FROM approval WHERE biz_id IN (SELECT id FROM budget WHERE name LIKE 'U7T%'))");
        jdbcTemplate.update("DELETE FROM approval WHERE biz_id IN "
                + "(SELECT id FROM budget WHERE name LIKE 'U7T%')");
        jdbcTemplate.update("DELETE FROM budget_item WHERE budget_id IN "
                + "(SELECT id FROM budget WHERE name LIKE 'U7T%')");
        jdbcTemplate.update("DELETE FROM budget WHERE name LIKE 'U7T%'");
        jdbcTemplate.update("DELETE FROM user_role WHERE user_id IN "
                + "(SELECT id FROM sys_user WHERE account LIKE 'u7t\\_%')");
        jdbcTemplate.update("DELETE FROM sys_user WHERE account LIKE 'u7t\\_%'");
        jdbcTemplate.update("DELETE FROM project_group WHERE code LIKE 'U7T-%'");
        jdbcTemplate.update("DELETE FROM department WHERE code LIKE 'U7T-%'");
    }
}
