package com.gov.procurement.modules.requisition.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 发起领用命令。
 *
 * @param projectGroupId 归属项目组 id
 * @param purpose        用途（可选，≤512）
 * @param items          领用明细（≥1 行）
 */
public record CreateRequisitionCmd(
        @NotNull(message = "项目组 id 不能为空") Long projectGroupId,
        @Size(max = 512, message = "用途不超过 512 字") String purpose,
        @NotEmpty(message = "领用明细不能为空") @Valid List<RequisitionItemReq> items) {
}
