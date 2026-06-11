package com.gov.procurement.modules.stock.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 库存流水视图（详设 U10 §6.2）。流水即审计：每条自解释来源（ref_type + ref_id 指向产生该变动的单据）。
 *
 * @param txnId     流水 id
 * @param type      inbound/outbound/gain/loss
 * @param qtyChange 数量增减（带正负：inbound/gain 为正，outbound/loss 为负）
 * @param refType   inbound_order/outbound_order/stocktake
 * @param refId     来源单 id（多态逻辑引用）
 * @param createdAt 发生时间（倒序排序键）
 */
public record StockTxnVO(
        Long txnId,
        String type,
        BigDecimal qtyChange,
        String refType,
        Long refId,
        OffsetDateTime createdAt) {
}
