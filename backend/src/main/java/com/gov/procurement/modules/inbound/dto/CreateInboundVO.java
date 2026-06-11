package com.gov.procurement.modules.inbound.dto;

import java.util.List;

/**
 * 入库结果（详设 U9 §6.1）。
 *
 * @param inboundOrderId       新建入库单 id
 * @param purchaseOrderStatus  入库后采购单状态（executing / inbounded）
 * @param items                各明细累计已收与写入库存项
 */
public record CreateInboundVO(
        Long inboundOrderId,
        String purchaseOrderStatus,
        List<CreateInboundLineVO> items) {

    /**
     * 入库结果明细行。
     *
     * @param purchaseItemId    采购明细 id
     * @param receivedQtyTotal  该明细累计已收
     * @param stockItemId       写入的库存项 id
     */
    public record CreateInboundLineVO(
            Long purchaseItemId,
            java.math.BigDecimal receivedQtyTotal,
            Long stockItemId) {
    }
}
