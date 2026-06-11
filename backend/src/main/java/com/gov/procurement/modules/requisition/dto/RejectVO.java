package com.gov.procurement.modules.requisition.dto;

/**
 * 驳回结果。
 *
 * @param requisitionId 领用单 id
 * @param status        领用单状态（rejected）
 */
public record RejectVO(Long requisitionId, String status) {
}
