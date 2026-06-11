package com.gov.procurement.modules.stocktake.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 录入实盘结果（详设 U12 §6.2）。回显每条明细重算后的差异，供原型 stocktake 屏渲染「差异/类型」列。
 *
 * @param items 各明细差异结果
 */
public record SaveActualsVO(List<ActualResultVO> items) {

    /**
     * 实盘差异结果行。
     *
     * @param stocktakeItemId 盘点明细 id
     * @param diff            actual_qty - book_qty
     * @param diffType        gain/loss/none
     */
    public record ActualResultVO(
            Long stocktakeItemId,
            BigDecimal diff,
            String diffType) {
    }
}
