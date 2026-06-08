package com.gov.procurement.modules.org.dto;

/**
 * 部门视图对象。
 *
 * @param id   部门 id
 * @param name 名称
 * @param code 编码
 */
public record DepartmentVO(Long id, String name, String code) {
}
