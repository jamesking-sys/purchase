package com.gov.procurement.modules.requisition.dto;

/**
 * 审批出库结果。
 *
 * @param requisitionId   领用单 id
 * @param outboundOrderId 生成的出库单 id
 * @param status          领用单状态（outbound）
 */
public record ApproveOutboundVO(Long requisitionId, Long outboundOrderId, String status) {
}
