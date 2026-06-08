package com.gov.procurement.modules.auth;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.gov.procurement.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试专用探针控制器：暴露一个 {@code @SaCheckRole("purchase_mgr")} 受保护接口，供集成测试验证角色校验（AC-5）。
 * 位于 test 源集，仅在 {@code @SpringBootTest} 组件扫描时注册，不进生产构件。
 */
@RestController
@RequestMapping("/api/test")
public class RoleProbeController {

    @SaCheckRole("purchase_mgr")
    @GetMapping("/purchase-mgr-only")
    public Result<String> probe() {
        return Result.ok("granted");
    }
}
