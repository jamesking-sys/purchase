package com.gov.procurement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * U1 健康检查集成测试：对本地 PostgreSQL 启动完整 Spring 上下文（Flyway 业务表 + Flowable ACT_* 同库共存，
 * 对应 AC-5），验证服务存活与 db=ok。数据源取 application.yml 默认（localhost:5432/procurement）。
 *
 * <p>本地无 PostgreSQL（如 CI 未起库）时经 {@code LocalPg#available} 跳过；不依赖 Docker。
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIf("com.gov.procurement.support.LocalPg#available")
class HealthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void health_returnsServiceUpAndDbOk() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.service").value("up"))
                .andExpect(jsonPath("$.data.db").value("ok"));
    }
}
