package com.gov.procurement.modules.org;

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

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * U4 组织/项目组/用户角色集成测试（详设 §7 T-1..T-16）。全上下文 + 本地 PostgreSQL（不依赖 Docker）。
 * 测试数据用前缀 U4T-/u4t_ 隔离，{@code @BeforeAll}/{@code @AfterAll} 物理清理，保证可重复、不污染。
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIf("com.gov.procurement.support.LocalPg#available")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrgIntegrationTest {

    private static final String AUTH = "Authorization";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    BCryptPasswordEncoder passwordEncoder;
    @Autowired
    ObjectMapper objectMapper;

    private String adminToken;
    private String editorToken;
    private Long sysDeptId;
    private Long warehouseRoleId;
    private Long requesterRoleId;

    @BeforeAll
    void setup() throws Exception {
        cleanup();
        adminToken = login("admin", "admin123");
        sysDeptId = jdbcTemplate.queryForObject(
                "SELECT id FROM department WHERE code = 'SYS' AND is_deleted = 0 ORDER BY id LIMIT 1", Long.class);
        warehouseRoleId = roleId("warehouse");
        requesterRoleId = roleId("requester");
        Long editorRoleId = roleId("editor");
        Long editorUid = jdbcTemplate.queryForObject(
                "INSERT INTO sys_user(name, account, password_hash, department_id) VALUES ('U4编制','u4t_editor',?,?) "
                        + "RETURNING id",
                Long.class, passwordEncoder.encode("pass123"), sysDeptId);
        jdbcTemplate.update("INSERT INTO user_role(user_id, role_id) VALUES (?, ?)", editorUid, editorRoleId);
        editorToken = login("u4t_editor", "pass123");
    }

    @AfterAll
    void teardown() {
        cleanup();
    }

    // ---- 鉴权（T-12 / T-13） ----

    @Test
    void t13_loginUserCanListWithoutAdminRole() throws Exception {
        mockMvc.perform(get("/api/org/departments").header(AUTH, bearer(editorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void t12_nonAdminWriteForbidden() throws Exception {
        mockMvc.perform(post("/api/org/departments").header(AUTH, bearer(editorToken)).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"code\":\"U4T-NOADMIN\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    // ---- 部门（T-3 / T-10 / T-16） ----

    @Test
    void t3_duplicateDeptCodeRejected() throws Exception {
        createDept("U4T-DUP");
        mockMvc.perform(post("/api/org/departments").header(AUTH, bearer(adminToken)).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"再来\",\"code\":\"U4T-DUP\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40902));
    }

    @Test
    void t10_codeReusableAfterSoftDelete() throws Exception {
        long id = createDept("U4T-REUSE");
        mockMvc.perform(delete("/api/org/departments/" + id).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
        // 软删后同 code 可再建
        createDept("U4T-REUSE");
    }

    @Test
    void t16_updateDeptCodeToOthersValueRejected() throws Exception {
        createDept("U4T-D16A");
        long b = createDept("U4T-D16B");
        mockMvc.perform(put("/api/org/departments/" + b).header(AUTH, bearer(adminToken)).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"改\",\"code\":\"U4T-D16A\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40902));
    }

    // ---- 项目组（T-1 / T-2 / T-6） ----

    @Test
    void t1_createProjectGroupUnderExistingDept() throws Exception {
        long dept = createDept("U4T-PGOK");
        long pg = createProjectGroup("U4T-PG1", dept);
        mockMvc.perform(get("/api/org/project-groups/" + pg).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.departmentId").value(dept));
    }

    @Test
    void t2_createProjectGroupUnderMissingDeptRejected() throws Exception {
        mockMvc.perform(post("/api/org/project-groups").header(AUTH, bearer(adminToken)).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"code\":\"U4T-PGBAD\",\"departmentId\":99999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void t6_deleteProjectGroupReferencedByBudgetRejected() throws Exception {
        long dept = createDept("U4T-PGREF");
        long pg = createProjectGroup("U4T-PG6", dept);
        jdbcTemplate.update("INSERT INTO budget(project_group_id, name) VALUES (?, 'U4T-budget')", pg);
        mockMvc.perform(delete("/api/org/project-groups/" + pg).header(AUTH, bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));
    }

    // ---- 部门删除前置（T-4 / T-5） ----

    @Test
    void t4_deleteDeptWithProjectGroupRejected() throws Exception {
        long dept = createDept("U4T-D4");
        createProjectGroup("U4T-PG4", dept);
        mockMvc.perform(delete("/api/org/departments/" + dept).header(AUTH, bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));
    }

    @Test
    void t5_deleteDeptWithUserRejected() throws Exception {
        long dept = createDept("U4T-D5");
        createUser("u4t_inD5", dept);
        mockMvc.perform(delete("/api/org/departments/" + dept).header(AUTH, bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));
    }

    // ---- 用户（T-11 / T-14 / T-15） ----

    @Test
    void t11_createUserStoresBcryptAndVoHidesPassword() throws Exception {
        long dept = createDept("U4T-UDEPT");
        long uid = createUser("u4t_hash", dept);
        String hash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM sys_user WHERE id = ?", String.class, uid);
        org.junit.jupiter.api.Assertions.assertTrue(hash != null && hash.startsWith("$2"), "口令应为 BCrypt 密文");
        String body = mockMvc.perform(get("/api/org/users/" + uid).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("password"), "用户 VO 不应含口令字段");
    }

    @Test
    void t14_duplicateAccountRejected() throws Exception {
        long dept = createDept("U4T-DUPACC");
        createUser("u4t_dupacc", dept);
        mockMvc.perform(post("/api/org/users").header(AUTH, bearer(adminToken)).contentType(APPLICATION_JSON)
                        .content(String.format(
                                "{\"account\":\"u4t_dupacc\",\"name\":\"x\",\"password\":\"pass123\",\"departmentId\":%d}",
                                dept)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40902));
    }

    @Test
    void t15_deleteUserSoftDeletesAndClearsRoles() throws Exception {
        long dept = createDept("U4T-DELU");
        long uid = createUser("u4t_del", dept);
        assignRoles(uid, warehouseRoleId);
        mockMvc.perform(delete("/api/org/users/" + uid).header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk());
        // 用户不再出现在列表
        mockMvc.perform(get("/api/org/users/" + uid).header(AUTH, bearer(adminToken)))
                .andExpect(status().isNotFound());
        Integer roleRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_role WHERE user_id = ?", Integer.class, uid);
        assertEquals(0, roleRows, "删除用户应清除其角色绑定");
    }

    // ---- 授角（T-7 / T-8 / T-9） ----

    @Test
    void t7_assignMultipleRoles() throws Exception {
        long dept = createDept("U4T-ROLE7");
        long uid = createUser("u4t_role7", dept);
        mockMvc.perform(put("/api/org/users/" + uid + "/roles").header(AUTH, bearer(adminToken))
                        .contentType(APPLICATION_JSON)
                        .content(String.format("{\"roleIds\":[%d,%d]}", warehouseRoleId, requesterRoleId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].code", hasItems("warehouse", "requester")));
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_role WHERE user_id = ?", Integer.class, uid);
        assertEquals(2, rows);
    }

    @Test
    void t8_assignInvalidRoleIdRejectedNoWrite() throws Exception {
        long dept = createDept("U4T-ROLE8");
        long uid = createUser("u4t_role8", dept);
        assignRoles(uid, warehouseRoleId); // 先有 1 个角色
        mockMvc.perform(put("/api/org/users/" + uid + "/roles").header(AUTH, bearer(adminToken))
                        .contentType(APPLICATION_JSON)
                        .content(String.format("{\"roleIds\":[%d,99999999]}", warehouseRoleId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
        // 含无效角色 → 整体不写，原绑定不变
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_role WHERE user_id = ?", Integer.class, uid);
        assertEquals(1, rows, "含无效角色应整体回滚，原绑定保持");
    }

    @Test
    void t9_assignIsIdempotent() throws Exception {
        long dept = createDept("U4T-ROLE9");
        long uid = createUser("u4t_role9", dept);
        String payload = String.format("{\"roleIds\":[%d,%d,%d]}", warehouseRoleId, warehouseRoleId, requesterRoleId);
        assignRolesRaw(uid, payload);
        assignRolesRaw(uid, payload); // 重复调用
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_role WHERE user_id = ?", Integer.class, uid);
        assertEquals(2, rows, "重复 roleId / 重复调用应幂等去重");
    }

    // ---- 角色字典 ----

    @Test
    void roleDictReturnsSixBuiltins() throws Exception {
        mockMvc.perform(get("/api/org/roles").header(AUTH, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].code", hasItems("editor", "purchase_mgr", "dept_mgr",
                        "warehouse", "requester", "admin")));
    }

    // ---- helpers ----

    private Long roleId(String code) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM role WHERE code = ? AND is_deleted = 0", Long.class, code);
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

    private long createDept(String code) throws Exception {
        String body = mockMvc.perform(post("/api/org/departments").header(AUTH, bearer(adminToken))
                        .contentType(APPLICATION_JSON)
                        .content(String.format("{\"name\":\"%s\",\"code\":\"%s\"}", code, code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").asLong();
    }

    private long createProjectGroup(String code, long deptId) throws Exception {
        String body = mockMvc.perform(post("/api/org/project-groups").header(AUTH, bearer(adminToken))
                        .contentType(APPLICATION_JSON)
                        .content(String.format("{\"name\":\"%s\",\"code\":\"%s\",\"departmentId\":%d}", code, code, deptId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").asLong();
    }

    private long createUser(String account, long deptId) throws Exception {
        String body = mockMvc.perform(post("/api/org/users").header(AUTH, bearer(adminToken))
                        .contentType(APPLICATION_JSON)
                        .content(String.format(
                                "{\"account\":\"%s\",\"name\":\"%s\",\"password\":\"pass123\",\"departmentId\":%d}",
                                account, account, deptId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").asLong();
    }

    private void assignRoles(long userId, long roleId) throws Exception {
        assignRolesRaw(userId, String.format("{\"roleIds\":[%d]}", roleId));
    }

    private void assignRolesRaw(long userId, String payload) throws Exception {
        mockMvc.perform(put("/api/org/users/" + userId + "/roles").header(AUTH, bearer(adminToken))
                        .contentType(APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM user_role WHERE user_id IN "
                + "(SELECT id FROM sys_user WHERE account LIKE 'u4t\\_%')");
        jdbcTemplate.update("DELETE FROM budget WHERE name LIKE 'U4T-%'");
        jdbcTemplate.update("DELETE FROM project_group WHERE code LIKE 'U4T-%'");
        jdbcTemplate.update("DELETE FROM sys_user WHERE account LIKE 'u4t\\_%'");
        jdbcTemplate.update("DELETE FROM department WHERE code LIKE 'U4T-%'");
    }
}
