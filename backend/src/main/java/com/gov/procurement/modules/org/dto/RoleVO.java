package com.gov.procurement.modules.org.dto;

/**
 * 角色视图对象（只读字典 / 用户角色展示）。
 *
 * @param id   角色 id
 * @param code 角色编码（如 warehouse）
 * @param name 角色名称（如 仓管员）
 */
public record RoleVO(Long id, String code, String name) {
}
