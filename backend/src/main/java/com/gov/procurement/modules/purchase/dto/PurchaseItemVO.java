package com.gov.procurement.modules.purchase.dto;

import java.math.BigDecimal;

/**
 * 采购明细视图。
 *
 * @param id           明细 id
 * @param subjectId    预算科目 id
 * @param materialName 物料名称
 * @param qty          采购数量
 * @param amount       金额
 * @param receivedQty  已收数量（U9 累加，本功能恒为 0）
 */
public record PurchaseItemVO(
        Long id,
        Long subjectId,
        String materialName,
        BigDecimal qty,
        BigDecimal amount,
        BigDecimal receivedQty) {
}
