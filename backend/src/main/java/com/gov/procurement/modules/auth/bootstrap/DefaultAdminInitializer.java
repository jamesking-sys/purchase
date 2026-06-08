package com.gov.procurement.modules.auth.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 默认管理员初始化：首次启动若不存在 admin 账号，则创建系统管理部 + admin 用户（BCrypt 口令）并绑定 admin 角色。
 * 幂等：admin 已存在即跳过。口令取 app.admin.default-password（缺省 admin123），生产须改并强制首登改密。
 *
 * <p>U2 本体只读，唯一例外是本 bootstrap 写入——由 U1 的 V2 seed 注释显式约定（管理员口令需真实 BCrypt，
 * 不在迁移里硬编码占位哈希）。
 */
@Component
public class DefaultAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultAdminInitializer.class);

    private static final String ADMIN_ACCOUNT = "admin";
    private static final String ADMIN_ROLE_CODE = "admin";
    private static final String SYS_DEPT_CODE = "SYS";

    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder;
    private final String defaultPassword;

    public DefaultAdminInitializer(JdbcTemplate jdbcTemplate,
                                   BCryptPasswordEncoder passwordEncoder,
                                   @Value("${app.admin.default-password:admin123}") String defaultPassword) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.defaultPassword = defaultPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sys_user WHERE account = ? AND is_deleted = 0",
                Integer.class, ADMIN_ACCOUNT);
        if (existing != null && existing > 0) {
            return;
        }

        Long roleId = jdbcTemplate.query(
                "SELECT id FROM role WHERE code = ? AND is_deleted = 0",
                rs -> rs.next() ? rs.getLong(1) : null, ADMIN_ROLE_CODE);
        if (roleId == null) {
            log.warn("内置 admin 角色缺失，跳过默认管理员创建（请确认 V2 seed 已执行）");
            return;
        }

        Long departmentId = ensureSystemDepartment();
        String passwordHash = passwordEncoder.encode(defaultPassword);
        Long userId = jdbcTemplate.queryForObject(
                "INSERT INTO sys_user(name, account, password_hash, department_id) "
                        + "VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, "系统管理员", ADMIN_ACCOUNT, passwordHash, departmentId);
        jdbcTemplate.update(
                "INSERT INTO user_role(user_id, role_id) VALUES (?, ?)", userId, roleId);

        log.warn("已创建默认管理员 account={}（使用默认口令，请尽快登录修改）", ADMIN_ACCOUNT);
    }

    private Long ensureSystemDepartment() {
        Long deptId = jdbcTemplate.query(
                "SELECT id FROM department WHERE code = ? AND is_deleted = 0",
                rs -> rs.next() ? rs.getLong(1) : null, SYS_DEPT_CODE);
        if (deptId != null) {
            return deptId;
        }
        return jdbcTemplate.queryForObject(
                "INSERT INTO department(name, code) VALUES (?, ?) RETURNING id",
                Long.class, "系统管理部", SYS_DEPT_CODE);
    }
}
