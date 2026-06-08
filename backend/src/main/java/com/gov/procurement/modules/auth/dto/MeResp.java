package com.gov.procurement.modules.auth.dto;

import java.util.List;

/**
 * 当前用户响应。刻意不含 password_hash（INV-1：口令永不返回）。
 *
 * @param userId       用户 id
 * @param account      登录账号
 * @param name         姓名
 * @param departmentId 所属部门 id
 * @param roles        角色 code 列表（如 ["warehouse","requester"]）
 */
public record MeResp(Long userId, String account, String name, Long departmentId, List<String> roles) {
}
