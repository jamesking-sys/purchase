package com.gov.procurement.modules.purchase.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 采购单视图。创建响应含 items；列表项为摘要，items 为 null。
 *
 * @param id             采购单 id
 * @param budgetId       来源预算 id
 * @param projectGroupId 项目组 id
 * @param supplierName   供应商名称
 * @param contractNo     合同号
 * @param status         状态
 * @param createdAt      创建时间
 * @param items          采购明细（列表摘要时为 null）
 */
public record PurchaseOrderVO(
        Long id,
        Long budgetId,
        Long projectGroupId,
        String supplierName,
        String contractNo,
        String status,
        OffsetDateTime createdAt,
        List<PurchaseItemVO> items) {
}
