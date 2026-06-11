package com.gov.procurement.modules.requisition.dto;

/**
 * 发起领用结果。
 *
 * @param id     领用单 id
 * @param status 状态（pending_warehouse）
 */
public record CreateRequisitionVO(Long id, String status) {
}
