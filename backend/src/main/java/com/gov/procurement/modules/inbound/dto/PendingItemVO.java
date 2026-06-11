package com.gov.procurement.modules.inbound.dto;

import java.math.BigDecimal;

/**
 * 采购单待收明细（U9 入库前置查询）：供仓管在入库页查看某采购单各明细的采购量/已收量/待收量。
 * 仓管无权访问 editor 的采购单详情，故由入库模块提供此 warehouse 可见的只读视图。
 *
 * @param purchaseItemId 采购明细 id（入库时引用）
 * @param materialName   物料名
 * @param qty            采购数量
 * @param receivedQty    累计已收
 * @param remaining      待收（qty - receivedQty）
 */
public record PendingItemVO(
        Long purchaseItemId,
        String materialName,
        BigDecimal qty,
        BigDecimal receivedQty,
        BigDecimal remaining) {
}
