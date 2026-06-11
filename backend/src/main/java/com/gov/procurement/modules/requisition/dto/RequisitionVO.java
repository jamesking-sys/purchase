package com.gov.procurement.modules.requisition.dto;

import java.time.OffsetDateTime;

/**
 * 领用单列表摘要（详设 U11 §6.5）。
 *
 * @param id            领用单 id
 * @param status        状态
 * @param applicantName 申请人姓名
 * @param createdAt     发起时间
 * @param itemCount     明细数
 */
public record RequisitionVO(
        Long id,
        String status,
        String applicantName,
        OffsetDateTime createdAt,
        int itemCount) {
}
