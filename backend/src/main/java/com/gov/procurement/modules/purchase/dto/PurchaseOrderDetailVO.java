package com.gov.procurement.modules.purchase.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 采购单详情视图：主单 + 明细 + 到货单列表（详设 U8 §6.2）。
 *
 * @param id             采购单 id
 * @param budgetId       来源预算 id
 * @param projectGroupId 项目组 id
 * @param supplierName   供应商名称
 * @param contractNo     合同号
 * @param status         状态
 * @param createdAt      创建时间
 * @param items          采购明细
 * @param deliveryNotes  到货单列表
 */
public record PurchaseOrderDetailVO(
        Long id,
        Long budgetId,
        Long projectGroupId,
        String supplierName,
        String contractNo,
        String status,
        OffsetDateTime createdAt,
        List<PurchaseItemVO> items,
        List<DeliveryNoteVO> deliveryNotes) {
}
