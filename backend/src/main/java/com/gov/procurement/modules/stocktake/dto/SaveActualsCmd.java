package com.gov.procurement.modules.stocktake.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * 录入实盘命令（详设 U12 §6.2）。actualQty 仅校验非空（presence）；为负的语义错误由服务层抛 42205
 * （区别于参数缺失 40001）。
 *
 * @param items 实盘录入项（≥1 行）
 */
public record SaveActualsCmd(
        @NotEmpty(message = "实盘明细不能为空") @Valid List<ActualItemReq> items) {

    /**
     * 实盘录入行。
     *
     * @param stocktakeItemId 盘点明细 id（须属本盘点单）
     * @param actualQty       实盘数（≥0，为负 → 42205）
     */
    public record ActualItemReq(
            @NotNull(message = "盘点明细 id 不能为空") Long stocktakeItemId,
            @NotNull(message = "实盘数不能为空") BigDecimal actualQty) {
    }
}
