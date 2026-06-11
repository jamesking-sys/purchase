package com.gov.procurement.modules.approval.dto;

import jakarta.validation.constraints.Size;

/**
 * 驳回请求。意见必填校验在服务层做（空白 → 42203），此处仅约束长度上限；
 * 不用 {@code @NotBlank} 以便空白返回业务码 42203 而非通用参数错误 40001（详设 U7 AC-5）。
 *
 * @param opinion 驳回意见（必填，≤512，服务层校验非空白）
 */
public record RejectReq(
        @Size(max = 512, message = "驳回意见不超过 512 字") String opinion) {
}
