package com.gov.procurement.modules.org.dto;

/**
 * 项目组视图对象（含所属部门名，便于列表展示）。
 *
 * @param id             项目组 id
 * @param name           名称
 * @param code           编码
 * @param departmentId   所属部门 id
 * @param departmentName 所属部门名称
 */
public record ProjectGroupVO(Long id, String name, String code, Long departmentId, String departmentName) {
}
