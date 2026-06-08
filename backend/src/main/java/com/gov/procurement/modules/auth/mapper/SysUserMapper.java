package com.gov.procurement.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gov.procurement.modules.auth.domain.SysUser;

/**
 * sys_user Mapper（只读）。按账号/主键查询由 MyBatis-Plus 条件构造器完成，逻辑删除自动过滤。
 */
public interface SysUserMapper extends BaseMapper<SysUser> {
}
