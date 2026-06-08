package com.gov.procurement.modules.org.dto;

import java.util.List;

/**
 * 用户视图对象。<b>绝不含 password_hash</b>（INV：口令永不返回）。
 *
 * @param id             用户 id
 * @param account        登录账号
 * @param name           姓名
 * @param departmentId   所属部门 id
 * @param departmentName 所属部门名称
 * @param roles          角色列表
 */
public record SysUserVO(Long id, String account, String name, Long departmentId, String departmentName,
                        List<RoleVO> roles) {
}
