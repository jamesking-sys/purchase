package com.gov.procurement.modules.inbound.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 入库记录视图（详设 U9 §6.2）。
 *
 * @param inboundOrderId  入库单 id
 * @param purchaseOrderId 采购单 id
 * @param receivedBy      验收人（仓管）id
 * @param inboundAt       入库时间
 * @param items           入库明细
 */
public record InboundOrderVO(
        Long inboundOrderId,
        Long purchaseOrderId,
        Long receivedBy,
        OffsetDateTime inboundAt,
        List<InboundLineVO> items) {

    /**
     * 入库记录明细行。
     *
     * @param purchaseItemId 采购明细 id
     * @param materialName   物料名
     * @param receivedQty    本次实收
     * @param stockItemId    库存项 id
     */
    public record InboundLineVO(
            Long purchaseItemId,
            String materialName,
            BigDecimal receivedQty,
            Long stockItemId) {
    }
}
