package com.gov.procurement.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 口令编码器配置：{@link BCryptPasswordEncoder} 单例 Bean（无状态、线程安全）。
 * 供 U2 登录校验与 U4 用户口令入库共同复用。
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
