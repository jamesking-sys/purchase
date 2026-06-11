package com.gov.procurement.modules.purchase;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * U8 采购执行 + 到货单集成测试（详设 §7 T-1..T-11）。全上下文 + 本地 PostgreSQL（无库优雅跳过）。
 * 测试数据用前缀 U8T-/u8t_ 隔离，{@code @BeforeAll}/{@code @AfterAll} 物理清理。
 *
 * <p>错误码采用编码归一口径（与 U7 一致）：预算非 approved / 采购单非 executing → 40903(409)；
 * 来源预算/采购单/到货单不存在 → 40401(404)；科目非法 / 金额数量非法 / 无文件 → 40001(400)；无 editor → 40301(403)。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIf("com.gov.procurement.support.LocalPg#available")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PurchaseIntegrationTest {

    private static final String AUTH = "Authorization";
    private static final Path UPLOAD_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "u8test-uploads");

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
    private long approvedBudgetId;
    private long draftBudgetId;
    private long leafSubjectId;
    private long nonLeafSubjectId;

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
        editorToken = createUserWithRole("u8t_editor", "editor", sysDept);

        Long deptId = jdbcTemplate.queryForObject(
                "INSERT INTO department(name, code) VALUES ('U8T部门','U8T-DEPT') RETURNING id", Long.class);
        projectGroupId = jdbcTemplate.queryForObject(
                "INSERT INTO project_group(name, code, department_id) VALUES ('U8T组','U8T-PG',?) RETURNING id",
                Long.class, deptId);

        approvedBudgetId = jdbcTemplate.queryForObject(
                "INSERT INTO budget(project_group_id, name, status) VALUES (?, 'U8T-approved', 'approved') RETURNING id",
                Long.class, projectGroupId);
        draftBudgetId = jdbcTemplate.queryForObject(
                "INSERT INTO budget(project_group_id, name, status) VALUES (?, 'U8T-draft', 'draft') RETURNING id",
                Long.class, projectGroupId);

        leafSubjectId = jdbcTemplate.queryForObject(
                "INSERT INTO budget_subject(name, code, level, is_leaf) VALUES ('U8T叶子','U8T-LEAF',1,true) RETURNING id",
                Long.class);
        nonLeafSubjectId = jdbcTemplate.queryForObject(
                "INSERT INTO budget_subject(name, code, level, is_leaf) VALUES ('U8T非叶','U8T-NONLEAF',1,false) "
                        + "RETURNING id", Long.class);
    }

    @AfterAll
    void teardown() throws Exception {
        cleanup();
        deleteRecursively(UPLOAD_DIR);
    }

    // ---- T-1 / T-6 创建（approved 预算）----

    @Test
    void t1_createOrderFromApprovedBudget() throws Exception {
        MvcResult res = createOrder(approvedBudgetId, "华为", "HT-2026-001",
                List.of(item(leafSubjectId, "服务器", 5, 100000.00)), editorToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("executing"))
                .andExpect(jsonPath("$.data.items[0].receivedQty").value(0))
                .andReturn();
        long orderId = dataId(res);
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM purchase_order WHERE id = ?", String.class, orderId);
        assertEquals("executing", status);
    }

    // ---- T-2 非 approved 预算 ----

    @Test
    void t2_nonApprovedBudgetRejected() throws Exception {
        createOrder(draftBudgetId, null, null, List.of(item(leafSubjectId, "x", 1, 10.00)), editorToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40903));
        assertNoOrderForBudget(draftBudgetId);
    }

    // ---- T-3 预算不存在 ----

    @Test
    void t3_budgetNotFound() throws Exception {
        createOrder(99999999L, null, null, List.of(item(leafSubjectId, "x", 1, 10.00)), editorToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    // ---- T-4 多明细单事务 + received_qty=0 ----

    @Test
    void t4_createWithMultipleItems() throws Exception {
        MvcResult res = createOrder(approvedBudgetId, null, null, List.of(
                item(leafSubjectId, "物料A", 2, 200.00),
                item(leafSubjectId, "物料B", 3, 300.00)), editorToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", org.hamcrest.Matchers.hasSize(2)))
                .andReturn();
        long orderId = dataId(res);
        Integer items = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM purchase_item WHERE purchase_order_id = ?", Integer.class, orderId);
        assertEquals(2, items);
        Integer zeroReceived = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM purchase_item WHERE purchase_order_id = ? AND received_qty = 0",
                Integer.class, orderId);
        assertEquals(2, zeroReceived, "received_qty 初值应为 0");
    }

    // ---- T-5 科目非法（不存在 / 非叶子）回滚 ----

    @Test
    void t5_illegalSubjectRejectedAndRolledBack() throws Exception {
        createOrder(approvedBudgetId, "U8T回滚A", null,
                List.of(item(99999999L, "x", 1, 10.00)), editorToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        createOrder(approvedBudgetId, "U8T回滚B", null,
                List.of(item(nonLeafSubjectId, "x", 1, 10.00)), editorToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        // 校验失败零落库：以上两单的供应商名不应出现
        Integer orders = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM purchase_order WHERE supplier_name IN ('U8T回滚A','U8T回滚B')", Integer.class);
        assertEquals(0, orders, "科目非法应整体回滚");
    }

    // ---- T-6 可选字段不填 ----

    @Test
    void t6_optionalFieldsOmitted() throws Exception {
        createOrder(approvedBudgetId, null, null, List.of(item(leafSubjectId, "无供应商", 1, 50.00)), editorToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.supplierName").doesNotExist())
                .andExpect(jsonPath("$.data.status").value("executing"));
    }

    // ---- T-7 多张到货单上传 ----

    @Test
    void t7_uploadMultipleDeliveryNotes() throws Exception {
        long orderId = newOrder();
        uploadNotes(orderId, editorToken,
                new MockMultipartFile("files", "dn1.pdf", "application/pdf", "pdf-1".getBytes()),
                new MockMultipartFile("files", "dn2.png", "image/png", "png-2".getBytes()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        uploadNotes(orderId, editorToken,
                new MockMultipartFile("files", "dn3.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "doc-3".getBytes()))
                .andExpect(status().isOk());

        Integer notes = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM delivery_note WHERE purchase_order_id = ?", Integer.class, orderId);
        assertEquals(3, notes, "多次 + 多文件上传应全部入库");
    }

    // ---- T-8 上传后查询 + 下载 ----

    @Test
    void t8_listAndDownloadDeliveryNote() throws Exception {
        long orderId = newOrder();
        byte[] content = "到货单内容-bytes".getBytes();
        uploadNotes(orderId, editorToken,
                new MockMultipartFile("files", "签收单.pdf", "application/pdf", content))
                .andExpect(status().isOk());

        MvcResult listRes = mockMvc.perform(get("/api/purchase/orders/" + orderId + "/delivery-notes")
                        .header(AUTH, bearer(editorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].uploadedByName").value("u8t_editor"))
                .andReturn();
        JsonNode note = objectMapper.readTree(listRes.getResponse().getContentAsString()).path("data").get(0);
        String downloadUrl = note.path("downloadUrl").asText();
        org.junit.jupiter.api.Assertions.assertFalse(note.path("uploadedAt").asText().isBlank(), "应含上传时间");

        byte[] downloaded = mockMvc.perform(get(downloadUrl).header(AUTH, bearer(editorToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(content, downloaded, "下载应返回原文件字节");
    }

    // ---- T-9 鉴权 ----

    @Test
    void t9_authRequired() throws Exception {
        // 未登录 → 401
        mockMvc.perform(get("/api/purchase/orders/" + newOrder()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40110));
        // 非 editor（admin）→ 40301
        createOrder(approvedBudgetId, null, null, List.of(item(leafSubjectId, "x", 1, 10.00)), adminToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    // ---- T-10 金额/数量非法回滚 ----

    @Test
    void t10_invalidQtyOrAmountRejected() throws Exception {
        createOrder(approvedBudgetId, "U8T非法量", null,
                List.of(item(leafSubjectId, "x", 0, 10.00)), editorToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        createOrder(approvedBudgetId, "U8T负金额", null,
                List.of(item(leafSubjectId, "x", 1, -1)), editorToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        Integer orders = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM purchase_order WHERE supplier_name IN ('U8T非法量','U8T负金额')", Integer.class);
        assertEquals(0, orders, "参数非法应整体回滚");
    }

    // ---- T-11 向非 executing 采购单上传 ----

    @Test
    void t11_uploadToNonExecutingOrderRejected() throws Exception {
        long orderId = newOrder();
        jdbcTemplate.update("UPDATE purchase_order SET status = 'void' WHERE id = ?", orderId);
        uploadNotes(orderId, editorToken,
                new MockMultipartFile("files", "x.pdf", "application/pdf", "x".getBytes()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40903));
    }

    // ---- helpers ----

    private long newOrder() throws Exception {
        MvcResult res = createOrder(approvedBudgetId, null, null,
                List.of(item(leafSubjectId, "物料", 1, 100.00)), editorToken)
                .andExpect(status().isOk())
                .andReturn();
        return dataId(res);
    }

    private ResultActions createOrder(Long budgetId, String supplier, String contract,
                                      List<Map<String, Object>> items, String token) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("budgetId", budgetId);
        body.put("projectGroupId", projectGroupId);
        if (supplier != null) {
            body.put("supplierName", supplier);
        }
        if (contract != null) {
            body.put("contractNo", contract);
        }
        body.put("items", items);
        return mockMvc.perform(post("/api/purchase/orders").header(AUTH, bearer(token))
                .contentType(APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private Map<String, Object> item(Long subjectId, String name, Object qty, Object amount) {
        Map<String, Object> m = new HashMap<>();
        m.put("subjectId", subjectId);
        m.put("materialName", name);
        m.put("qty", qty);
        m.put("amount", amount);
        return m;
    }

    private ResultActions uploadNotes(long orderId, String token, MockMultipartFile... files) throws Exception {
        var builder = multipart("/api/purchase/orders/" + orderId + "/delivery-notes");
        for (MockMultipartFile f : files) {
            builder.file(f);
        }
        return mockMvc.perform(builder.header(AUTH, bearer(token)));
    }

    private long dataId(MvcResult res) throws Exception {
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private void assertNoOrderForBudget(long budgetId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM purchase_order WHERE budget_id = ?", Integer.class, budgetId);
        assertEquals(0, n, "非法来源不应落库采购单");
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
        jdbcTemplate.update("DELETE FROM delivery_note WHERE purchase_order_id IN "
                + "(SELECT id FROM purchase_order WHERE project_group_id IN "
                + "(SELECT id FROM project_group WHERE code LIKE 'U8T-%'))");
        jdbcTemplate.update("DELETE FROM purchase_item WHERE purchase_order_id IN "
                + "(SELECT id FROM purchase_order WHERE project_group_id IN "
                + "(SELECT id FROM project_group WHERE code LIKE 'U8T-%'))");
        jdbcTemplate.update("DELETE FROM purchase_order WHERE project_group_id IN "
                + "(SELECT id FROM project_group WHERE code LIKE 'U8T-%')");
        jdbcTemplate.update("DELETE FROM budget_item WHERE budget_id IN "
                + "(SELECT id FROM budget WHERE name LIKE 'U8T%')");
        jdbcTemplate.update("DELETE FROM budget WHERE name LIKE 'U8T%'");
        jdbcTemplate.update("DELETE FROM budget_subject WHERE code LIKE 'U8T-%'");
        jdbcTemplate.update("DELETE FROM user_role WHERE user_id IN "
                + "(SELECT id FROM sys_user WHERE account LIKE 'u8t\\_%')");
        jdbcTemplate.update("DELETE FROM sys_user WHERE account LIKE 'u8t\\_%'");
        jdbcTemplate.update("DELETE FROM project_group WHERE code LIKE 'U8T-%'");
        jdbcTemplate.update("DELETE FROM department WHERE code LIKE 'U8T-%'");
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
