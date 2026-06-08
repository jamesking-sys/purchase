package com.gov.procurement.modules.auth;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gov.procurement.common.Result;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * U2 认证/权限基座集成测试（详细设计 §7 T-1..T-8、T-11，及 T-9 角色软删过滤）。
 * 全 Spring 上下文 + Testcontainers PostgreSQL：默认管理员由 DefaultAdminInitializer 启动时创建（admin/admin123）。
 * 无 Docker 的本地环境经 {@link #dockerAvailable()} 整类跳过；CI 有 Docker 则执行。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@EnabledIf("dockerAvailable")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(AuthIntegrationTest.ProbeConfig.class)
class AuthIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("procurement")
            .withUsername("procurement")
            .withPassword("procurement");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    BCryptPasswordEncoder passwordEncoder;
    @Autowired
    ObjectMapper objectMapper;

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
    }

    @BeforeAll
    void seedUsers() {
        Long deptId = jdbcTemplate.queryForObject(
                "SELECT id FROM department WHERE code = 'SYS' AND is_deleted = 0", Long.class);
        insertUser("editor1", "编制员", "pass123", deptId, "editor");
        long zhaoId = insertUser("zhao", "赵仓管", "pass123", deptId, "warehouse", "requester");
        // 软删角色（is_deleted=1）分配给 zhao，验证 INV-3：不出现在角色列表（T-9）
        Long ghostRoleId = jdbcTemplate.queryForObject(
                "INSERT INTO role(name, code, is_deleted) VALUES ('幽灵角色', 'ghost_role', 1) RETURNING id",
                Long.class);
        jdbcTemplate.update("INSERT INTO user_role(user_id, role_id) VALUES (?, ?)", zhaoId, ghostRoleId);
    }

    @Test
    void t1_loginThenAccessMe() throws Exception {
        String token = loginAndGetToken("admin", "admin123");
        assertFalse(token.isBlank(), "登录应返回非空 token");
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.account").value("admin"));
    }

    @Test
    void t2_wrongPasswordReturns40101() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"account\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void t3_unknownAccountReturns40101() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"account\":\"ghost\",\"password\":\"whatever\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void t4_meWithoutTokenReturns40110() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40110));
    }

    @Test
    void t5_roleMismatchReturns40301() throws Exception {
        String token = loginAndGetToken("editor1", "pass123");
        mockMvc.perform(get("/api/test/purchase-mgr-only").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void t6_meReturnsAllRolesWithoutPassword() throws Exception {
        String token = loginAndGetToken("zhao", "pass123");
        String body = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles", hasItems("warehouse", "requester")))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("passwordHash"), "响应不应含口令字段");
        assertFalse(body.contains("ghost_role"), "软删角色不应出现（T-9 / INV-3）");
    }

    @Test
    void t7_logoutInvalidatesToken() throws Exception {
        String token = loginAndGetToken("admin", "admin123");
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40110));
    }

    @Test
    void t8_blankParamsReturns40001() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"account\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void t11_whitelistHealthReachableWithoutToken() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ---- helpers ----

    private long insertUser(String account, String name, String rawPwd, Long deptId, String... roleCodes) {
        Long userId = jdbcTemplate.queryForObject(
                "INSERT INTO sys_user(name, account, password_hash, department_id) "
                        + "VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, name, account, passwordEncoder.encode(rawPwd), deptId);
        for (String code : roleCodes) {
            Long roleId = jdbcTemplate.queryForObject(
                    "SELECT id FROM role WHERE code = ? AND is_deleted = 0", Long.class, code);
            jdbcTemplate.update("INSERT INTO user_role(user_id, role_id) VALUES (?, ?)", userId, roleId);
        }
        return userId;
    }

    private String loginAndGetToken(String account, String password) throws Exception {
        String content = mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content(String.format("{\"account\":\"%s\",\"password\":\"%s\"}", account, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content).path("data").path("token").asText();
    }

    /** 测试专用：注册一个标注 {@code @SaCheckRole("purchase_mgr")} 的受保护探针接口（验证 AC-5 角色校验）。 */
    @Configuration
    static class ProbeConfig {
        @Bean
        RoleProbeController roleProbeController() {
            return new RoleProbeController();
        }
    }

    @RestController
    @RequestMapping("/api/test")
    static class RoleProbeController {
        @SaCheckRole("purchase_mgr")
        @GetMapping("/purchase-mgr-only")
        public Result<String> probe() {
            return Result.ok("granted");
        }
    }
}
