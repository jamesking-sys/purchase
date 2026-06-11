package com.gov.procurement.modules.stocktake.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 发起盘点命令（详设 U12 §6.1）。
 *
 * @param scopeProjectGroupId 盘点范围（按项目组），须存在未删
 */
public record CreateStocktakeCmd(
        @NotNull(message = "盘点范围项目组 id 不能为空") Long scopeProjectGroupId) {
}
