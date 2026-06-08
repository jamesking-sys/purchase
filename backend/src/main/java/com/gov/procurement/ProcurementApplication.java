package com.gov.procurement;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 采购与资产管理系统 启动类。
 *
 * <p>基建阶段（U0/U1）：Web + PostgreSQL + Flyway + MyBatis-Plus + Sa-Token + Flowable 依赖就位，
 * 业务模块在后续功能点逐步启用。功能点与依赖关系见 docs/plan/procurement-plan.md。
 */
@SpringBootApplication
@MapperScan("com.gov.procurement.modules.**.mapper")
public class ProcurementApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProcurementApplication.class, args);
        System.out.println("----------NO BUG----------");
    }
}
