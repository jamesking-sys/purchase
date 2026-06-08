package com.gov.procurement.modules.budget;

import cn.idev.excel.FastExcel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gov.procurement.modules.budget.dto.BudgetRow;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * U6 预算模板导入集成测试（详设 §7 T-1..T-9）。全上下文 + 本地 PostgreSQL（不依赖 Docker）。
 * FastExcel 在内存生成 .xlsx 经 multipart 上传；测试数据用前缀 U6T-/u6t_ 隔离并物理清理。
 * 写接口需 editor 角色 → editorToken；admin（非 editor）用于 40301。T-10 事务性由 @Transactional 结构保证。
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIf("com.gov.procurement.support.LocalPg#available")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BudgetImportIntegrationTest {

    private static final String AUTH = "Authorization";
    private static final String XLSX_CT = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final Path UPLOAD_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "u6test-uploads");

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
    private long projectGroupId;

    @DynamicPropertySource
    static void uploadProps(DynamicPropertyRegistry registry) {
        registry.add("app.upload.dir", UPLOAD_DIR::toString);
    }

    @BeforeAll
    void setup() throws Exception {
        cleanup();
        adminToken = login("admin", "admin123");

        Long sysDept = jdbcTemplate.queryForObject(
                "SELECT id FROM department WHERE code = 'SYS' AND is_deleted = 0 ORDER BY id LIMIT 1", Long.class);
        Long editorRole = jdbcTemplate.queryForObject(
                "SELECT id FROM role WHERE code = 'editor' AND is_deleted = 0", Long.class);
        Long uid = jdbcTemplate.queryForObject(
                "INSERT INTO sys_user(name, account, password_hash, department_id) VALUES ('U6编制','u6t_editor',?,?) "
                        + "RETURNING id",
                Long.class, passwordEncoder.encode("pass123"), sysDept);
        jdbcTemplate.update("INSERT INTO user_role(user_id, role_id) VALUES (?, ?)", uid, editorRole);
        editorToken = login("u6t_editor", "pass123");

        Long deptId = jdbcTemplate.queryForObject(
                "INSERT INTO department(name, code) VALUES ('U6T部门','U6T-DEPT') RETURNING id", Long.class);
        projectGroupId = jdbcTemplate.queryForObject(
                "INSERT INTO project_group(name, code, department_id) VALUES ('U6T组','U6T-PG',?) RETURNING id",
                Long.class, deptId);

        // 科目：U6T-DEV(非叶) → U6T-SVR(叶)
        Long devId = jdbcTemplate.queryForObject(
                "INSERT INTO budget_subject(name, code, level, is_leaf) VALUES ('U6T设备','U6T-DEV',1,false) RETURNING id",
                Long.class);
        jdbcTemplate.update(
                "INSERT INTO budget_subject(name, code, parent_id, level, is_leaf) VALUES ('U6T服务器','U6T-SVR',?,2,true)",
                devId);
    }

    @AfterAll
    void teardown() throws Exception {
        cleanup();
        deleteRecursively(UPLOAD_DIR);
    }

    // ---- T-1 下载模板 ----

    @Test
    void t1_downloadTemplate() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/budget/template/download").header(AUTH, bearer(editorToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andReturn();
        byte[] body = res.getResponse().getContentAsByteArray();
        assertTrue(body.length > 0 && body[0] == 'P' && body[1] == 'K', "应为可打开的 xlsx（ZIP 魔数 PK）");
    }

    // ---- T-2 正常导入 ----

    @Test
    void t2_normalImport() throws Exception {
        byte[] xlsx = writeXlsx(List.of(
                new BudgetRow(null, "U6T-SVR", "1000.00")));
        MvcResult res = doImport(xlsx, "U6T正常预算", projectGroupId, editorToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.importedRows").value(1))
                .andReturn();
        long budgetId = objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("budgetId").asLong();
        String status = jdbcTemplate.queryForObject("SELECT status FROM budget WHERE id = ?", String.class, budgetId);
        assertEquals("draft", status);
        Integer items = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM budget_item WHERE budget_id = ?", Integer.class, budgetId);
        assertEquals(1, items);
    }

    // ---- T-3 缺列 ----

    @Test
    void t3_missingColumnRejected() throws Exception {
        byte[] xlsx = writeRawXlsx(List.of("科目路径", "科目编码"),
                List.of(List.of("U6T设备 / U6T服务器", "U6T-SVR")));
        doImport(xlsx, "U6T缺列", projectGroupId, editorToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(42201))
                .andExpect(jsonPath("$.data.errorRows[0].reason",
                        org.hamcrest.Matchers.containsString("金额")));
        assertNoBudget("U6T缺列");
    }

    // ---- T-4 金额空/非法 ----

    @Test
    void t4_invalidAmountRow() throws Exception {
        byte[] xlsx = writeXlsx(List.of(new BudgetRow(null, "U6T-SVR", "")));
        doImport(xlsx, "U6T空金额", projectGroupId, editorToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(42201))
                .andExpect(jsonPath("$.data.errorRows[0].reason").value("金额必填且须为正数"));
        assertNoBudget("U6T空金额");
    }

    // ---- T-5 金额挂非叶子 ----

    @Test
    void t5_amountOnNonLeafRejected() throws Exception {
        byte[] xlsx = writeXlsx(List.of(new BudgetRow(null, "U6T-DEV", "500.00")));
        doImport(xlsx, "U6T非叶", projectGroupId, editorToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(42202))
                .andExpect(jsonPath("$.data.errorRows[0].reason").value("金额只能录在叶子级科目"));
        assertNoBudget("U6T非叶");
    }

    // ---- T-6 附件留档 + 回填 ----

    @Test
    void t6_attachmentStoredAndLinked() throws Exception {
        MvcResult upload = mockMvc.perform(multipart("/api/budget/attachment")
                        .file(new MockMultipartFile("file", "立项.pdf", "application/pdf", "dummy-pdf".getBytes()))
                        .param("projectGroupId", String.valueOf(projectGroupId))
                        .header(AUTH, bearer(editorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String path = objectMapper.readTree(upload.getResponse().getContentAsString())
                .path("data").path("path").asText();
        assertTrue(Files.exists(UPLOAD_DIR.resolve(path)), "附件应已落盘");

        byte[] xlsx = writeXlsx(List.of(new BudgetRow(null, "U6T-SVR", "2000.00")));
        MvcResult res = mockMvc.perform(multipart("/api/budget/import")
                        .file(new MockMultipartFile("file", "b.xlsx", XLSX_CT, xlsx))
                        .param("projectGroupId", String.valueOf(projectGroupId))
                        .param("name", "U6T附件预算")
                        .param("sourceDocPath", path)
                        .header(AUTH, bearer(editorToken)))
                .andExpect(status().isOk())
                .andReturn();
        long budgetId = objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("budgetId").asLong();
        String stored = jdbcTemplate.queryForObject(
                "SELECT source_doc_path FROM budget WHERE id = ?", String.class, budgetId);
        assertEquals(path, stored, "导入应把留档路径写入 budget.source_doc_path");
    }

    // ---- T-7 项目组不存在 ----

    @Test
    void t7_projectGroupNotFound() throws Exception {
        byte[] xlsx = writeXlsx(List.of(new BudgetRow(null, "U6T-SVR", "100.00")));
        doImport(xlsx, "U6T无组", 99999999L, editorToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    // ---- T-8 多错误汇总 ----

    @Test
    void t8_multipleErrorsAggregated() throws Exception {
        byte[] xlsx = writeXlsx(List.of(
                new BudgetRow(null, "U6T-NOPE", "100.00"),  // 科目不存在
                new BudgetRow(null, "U6T-SVR", "200.00"),   // 合法
                new BudgetRow(null, "U6T-SVR", "300.00"),   // 科目重复
                new BudgetRow(null, "U6T-EMPTY", "")));      // 金额空
        doImport(xlsx, "U6T多错误", projectGroupId, editorToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(42201))
                .andExpect(jsonPath("$.data.errorRows", org.hamcrest.Matchers.hasSize(3)));
        assertNoBudget("U6T多错误");
    }

    // ---- T-9 非 editor 导入 ----

    @Test
    void t9_nonEditorForbidden() throws Exception {
        byte[] xlsx = writeXlsx(List.of(new BudgetRow(null, "U6T-SVR", "100.00")));
        doImport(xlsx, "U6T非编制", projectGroupId, adminToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    // ---- helpers ----

    private org.springframework.test.web.servlet.ResultActions doImport(byte[] xlsx, String name, long pgId,
                                                                        String token) throws Exception {
        return mockMvc.perform(multipart("/api/budget/import")
                .file(new MockMultipartFile("file", "b.xlsx", XLSX_CT, xlsx))
                .param("projectGroupId", String.valueOf(pgId))
                .param("name", name)
                .header(AUTH, bearer(token)));
    }

    private byte[] writeXlsx(List<BudgetRow> rows) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FastExcel.write(baos, BudgetRow.class).sheet("预算").doWrite(rows);
        return baos.toByteArray();
    }

    private byte[] writeRawXlsx(List<String> headers, List<List<String>> dataRows) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        List<List<String>> head = new ArrayList<>();
        for (String h : headers) {
            head.add(List.of(h));
        }
        FastExcel.write(baos).head(head).sheet("预算").doWrite(dataRows);
        return baos.toByteArray();
    }

    private void assertNoBudget(String name) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM budget WHERE name = ?", Integer.class, name);
        assertEquals(0, count, "校验失败应零落库");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String login(String account, String password) throws Exception {
        String content = mockMvc.perform(post("/api/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(String.format("{\"account\":\"%s\",\"password\":\"%s\"}", account, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content).path("data").path("token").asText();
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM budget_item WHERE budget_id IN (SELECT id FROM budget WHERE name LIKE 'U6T%')");
        jdbcTemplate.update("DELETE FROM budget WHERE name LIKE 'U6T%'");
        jdbcTemplate.update("DELETE FROM budget_subject WHERE code LIKE 'U6T-%'");
        jdbcTemplate.update("DELETE FROM project_group WHERE code LIKE 'U6T-%'");
        jdbcTemplate.update("DELETE FROM department WHERE code LIKE 'U6T-%'");
        jdbcTemplate.update("DELETE FROM user_role WHERE user_id IN "
                + "(SELECT id FROM sys_user WHERE account LIKE 'u6t\\_%')");
        jdbcTemplate.update("DELETE FROM sys_user WHERE account LIKE 'u6t\\_%'");
    }

    private void deleteRecursively(Path dir) throws Exception {
        if (!Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // 测试清理尽力而为
                }
            });
        }
    }
}
