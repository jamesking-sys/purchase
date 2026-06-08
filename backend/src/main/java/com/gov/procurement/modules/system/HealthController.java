package com.gov.procurement.modules.system;

import cn.dev33.satoken.annotation.SaIgnore;
import com.gov.procurement.common.Result;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查：返回服务存活与数据库连通性（SELECT 1）。无需登录（@SaIgnore）。
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @SaIgnore
    @GetMapping
    public Result<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "up");
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            body.put("db", (one != null && one == 1) ? "ok" : "down");
        } catch (Exception e) {
            body.put("db", "down");
        }
        return Result.ok(body);
    }
}
