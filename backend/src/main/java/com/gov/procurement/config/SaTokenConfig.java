package com.gov.procurement.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 接入配置：注册 {@link SaInterceptor} 对全路径做登录态校验，放行白名单（登录 / 健康检查 / 接口文档）。
 * 角色校验由方法上的 {@code @SaCheckRole} 注解经本拦截器的注解处理生效——先认证（拦截器）、后鉴权（注解切面）。
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /** 免登录白名单：登录本身、健康检查与接口文档。logout / me 不在内，需登录态。 */
    private static final String[] WHITELIST = {
            "/api/auth/login",
            "/api/health",
            "/actuator/health",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    };

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/**")
                .excludePathPatterns(WHITELIST);
    }
}
