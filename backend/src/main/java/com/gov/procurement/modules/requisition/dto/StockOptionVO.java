package com.gov.procurement.modules.requisition.dto;

import java.math.BigDecimal;

/**
 * 领用可选库存项（U11 领用前置查询）：供领用人在领用页按项目组挑选物料。
 * 领用人无权访问 warehouse 的库存查询（U10），故由领用模块提供此 requester 可见的只读视图。
 *
 * @param stockItemId  库存项 id（领用时引用）
 * @param materialName 物料名
 * @param quantity     当前库存量
 */
public record StockOptionVO(Long stockItemId, String materialName, BigDecimal quantity) {
}
