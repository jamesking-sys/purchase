package com.gov.procurement.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI 配置：在 /swagger-ui.html 暴露接口文档，
 * 并声明 Sa-Token 的 Authorization (Bearer) 鉴权头，便于联调时带 token 调试。
 */
@Configuration
public class OpenApiConfig {

    private static final String AUTH_SCHEME = "Authorization";

    @Bean
    public OpenAPI procurementOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("采购与资产管理系统 API")
                        .description("预算 → 审批 → 采购执行 → 领用/盘点")
                        .version("v0.0.1"))
                .components(new Components()
                        .addSecuritySchemes(AUTH_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .name(AUTH_SCHEME)));
    }
}
