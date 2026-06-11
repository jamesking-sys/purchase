package com.gov.procurement.modules.approval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 提交审批请求：启动业务单据的审批流程实例。本期 bizType 仅支持 budget。
 *
 * @param bizType 业务类型（非空，本期仅 budget）
 * @param bizId   业务单据 id（非空、正数）
 */
public record SubmitApprovalReq(
        @NotBlank(message = "业务类型不能为空") String bizType,
        @NotNull(message = "业务单据 id 不能为空") @Positive(message = "业务单据 id 须为正数") Long bizId) {
}
