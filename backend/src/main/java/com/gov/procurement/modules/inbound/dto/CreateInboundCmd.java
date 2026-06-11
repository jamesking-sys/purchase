package com.gov.procurement.modules.inbound.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 入库命令：对某 executing 采购单提交本次实收明细。
 *
 * @param purchaseOrderId 目标采购单 id（须 executing）
 * @param projectGroupId  归属项目组 id（可空，默认取采购单的 project_group_id）
 * @param items           本次实收明细（≥1 行）
 */
public record CreateInboundCmd(
        @NotNull(message = "采购单 id 不能为空") Long purchaseOrderId,
        Long projectGroupId,
        @NotEmpty(message = "本次实收明细不能为空") @Valid List<InboundItemReq> items) {
}
