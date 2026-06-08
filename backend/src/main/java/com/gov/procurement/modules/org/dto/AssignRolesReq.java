package com.gov.procurement.modules.org.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 用户↔角色全量覆盖分配请求。空列表表示清空该用户全部角色。
 *
 * @param roleIds 角色 id 列表（全量覆盖，去重；每个须为存在未删角色）
 */
public record AssignRolesReq(
        @NotNull(message = "角色列表不能为 null（清空请传空数组）") List<Long> roleIds) {
}
